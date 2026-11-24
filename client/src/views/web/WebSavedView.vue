<script setup>
// 웹 저장 (설계 4-3 로그인 전 · 4-4 로그인 후)
//  - 로그인 전: 왼쪽에 로그인 안내, 오른쪽은 지도 (캠핑장 핀 · 시설 칩 · 기상특보)
//  - 로그인 후: 왼쪽에 관심 캠핑장(하트) 목록 + 체크(최대 3곳), 오른쪽은 지도(관심 캠핑장 핀)
//              체크한 캠핑장 비교표는 지도 위, 관심 캠핑장 목록 바로 옆 패널에 뜬다 (✕ 로 닫고, 체크를 바꾸면 다시 열림)
// 데이터: 관심 캠핑장 GET /api/saved/favorites, 캠핑장 GET /api/camps · /api/camps/{id} (비교 값은 가까운 시설 차로 n분)
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import WebMapShell from '@/components/web/WebMapShell.vue'
import WebCampMap from '@/components/web/WebCampMap.vue'
import WebFacChips from '@/components/web/WebFacChips.vue'
import WebChabakToggle from '@/components/web/WebChabakToggle.vue'
import { api } from '@/utils/api'
import { facilityOn } from '@/utils/facilityLayers'
import { useMapFacilities } from '@/utils/webMapFacilities'
import { useAuthStore } from '@/stores/auth'
import { useFavoriteStore } from '@/stores/favorite'

const MAX_PICK = 3
const TIER_BY_GRADE = { safe: 'safe', caution: 'mid', danger: 'warn' }
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' }
const typeLabel = (t) => TYPE_LABEL[t] || t || ''

const auth = useAuthStore()
const favorite = useFavoriteStore()

const allCamps = ref([])
const favIds = ref([]) // 관심 캠핑장 id (숫자)
const error = ref('')

async function loadCamps() {
  try {
    allCamps.value = await api('/api/camps', { params: { sort: 'score' } })
  } catch (e) {
    error.value = e.message
  }
}
async function loadFavorites() {
  try {
    const fav = await api('/api/saved/favorites')
    favIds.value = fav.campIds.filter((id) => /^\d+$/.test(id)).map(Number)
  } catch (e) {
    error.value = e.message
  }
}
const favCamps = computed(() => favIds.value.map((id) => allCamps.value.find((c) => c.id === id)).filter(Boolean))

// ---- 로그인 후: 비교 ----
const picked = ref([]) // 체크한 캠핑장 id (최대 3)
const full = ref(false)
const details = ref({}) // { id: CampDetailResponse }

function togglePick(id) {
  if (picked.value.includes(id)) {
    picked.value = picked.value.filter((x) => x !== id)
    full.value = false
  } else if (picked.value.length >= MAX_PICK) {
    full.value = true
  } else {
    picked.value = [...picked.value, id]
  }
}
async function removeFavorite(id) {
  await favorite.toggle(id)
  favIds.value = favIds.value.filter((x) => x !== id)
  picked.value = picked.value.filter((x) => x !== id)
}
watch(picked, async (ids) => {
  for (const id of ids) {
    if (details.value[id]) continue
    try {
      details.value = { ...details.value, [id]: await api(`/api/camps/${id}`) }
    } catch (e) {
      error.value = e.message
    }
  }
})

const chosen = computed(() =>
  picked.value.map((id) => favCamps.value.find((c) => c.id === id)).filter((c) => c && details.value[c.id]),
)
// 가까운 시설까지 차로 걸리는 분 (종류별 가장 짧은 값, 없으면 null)
const nearMin = (id, type) => {
  const list = (details.value[id]?.facilities || []).filter((f) => f.type === type).map((f) => f.driveMin)
  return list.length ? Math.min(...list) : null
}
// 항목: 값이 작을수록 좋은 항목(시간)과 클수록 좋은 항목(점수)
const ROWS = [
  { label: '안전점수', value: (c) => c.safetyScore, text: (v) => `안전 ${v}`, higherBetter: true },
  { label: '응급실', value: (c) => nearMin(c.id, 'er'), text: (v) => `약 ${v}분` },
  { label: '소방서', value: (c) => nearMin(c.id, 'fire'), text: (v) => `약 ${v}분` },
  { label: '지구대', value: (c) => nearMin(c.id, 'police'), text: (v) => `약 ${v}분` },
]
const rows = computed(() =>
  ROWS.map((r) => {
    const vals = chosen.value.map(r.value)
    const known = vals.filter((v) => v != null)
    const best = known.length ? (r.higherBetter ? Math.max(...known) : Math.min(...known)) : null
    return {
      label: r.label,
      cells: vals.map((v) => ({ text: v == null ? '정보 없음' : r.text(v), best: v != null && v === best })),
    }
  }),
)
const cols = computed(() => `64px repeat(${Math.max(chosen.value.length, 1)}, minmax(0, 1fr))`)
const anyDummy = computed(() => chosen.value.some((c) => details.value[c.id]?.dummy))
const limitText = computed(() =>
  full.value ? '비교는 최대 3곳까지예요 · 하나를 빼고 골라 주세요' : `체크해서 비교에 담기 · 최대 3곳 (${picked.value.length}/${MAX_PICK})`,
)

// ---- 지도 (로그인 전 · 후 모두) ----
// 로그인 전: 모든 캠핑장 핀 / 로그인 후: 관심 캠핑장 핀만 (비교하는 곳이 지도 어디인지 보이게)
const { layers, mapFacilities, loadLayers, toggleChip, onBounds } = useMapFacilities()
const pinOf = (c) => ({ ...c, tier: TIER_BY_GRADE[c.grade] })
const mapCamps = computed(() =>
  (auth.isLoggedIn ? favCamps.value : allCamps.value).filter((c) => TIER_BY_GRADE[c.grade]).map(pinOf),
)

// ---- 비교 패널 열고 닫기 (로그인 후) ----
// ✕ 로 닫으면 지도를 넓게 볼 수 있고, 왼쪽에서 체크를 바꾸면 다시 열린다
const cmpOpen = ref(true)
watch(picked, () => (cmpOpen.value = true))

onMounted(async () => {
  await loadCamps()
  loadLayers() // 지도 시설 칩 (로그인 전 · 후 모두 지도가 있다)
  if (auth.isLoggedIn) {
    await loadFavorites()
    picked.value = favIds.value.slice(0, MAX_PICK) // 처음엔 앞의 3곳을 비교해 보여 준다
  }
})
</script>

<template>
  <WebMapShell active="saved">
    <template #side>
      <!-- 로그인 전 -->
      <div v-if="!auth.isLoggedIn" class="gate">
        <span class="gate-icon">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linejoin="round" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>
        </span>
        <h1>로그인하세요</h1>
        <RouterLink to="/web/login" class="login">로그인</RouterLink>
        <p>저장 기능은 로그인 후 이용 가능합니다.<br />관심 캠핑장을 저장하고 비교해 보세요.</p>
        <p class="join">아직 회원이 아니신가요? <RouterLink to="/web/signup">회원가입</RouterLink></p>
      </div>

      <!-- 로그인 후: 관심 캠핑장 -->
      <div v-else class="mine">
        <div class="mine-head">
          <div class="mt">
            <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" class="hrt"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>
            관심 캠핑장 <span class="n">{{ favCamps.length }}</span>곳
          </div>
          <span class="limit" :class="{ warn: full }">{{ limitText }}</span>
        </div>
        <p v-if="error" class="state">{{ error }}</p>
        <p v-else-if="!favCamps.length" class="state">아직 담은 캠핑장이 없어요. 탐색에서 하트를 눌러 담아 보세요.</p>
        <div class="items">
          <div v-for="c in favCamps" :key="c.id" class="item" :class="{ on: picked.includes(c.id) }">
            <button type="button" class="pick" :aria-pressed="picked.includes(c.id)" @click="togglePick(c.id)">
              <span class="check">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"
                  stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12l5 5 9-10" /></svg>
              </span>
              <span class="thumb" role="img" :aria-label="`${c.name} 사진`">
                <img v-if="c.imageUrl" :src="c.imageUrl" :alt="c.name" />
                <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                  stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="m4 18 5-5 4 4 3-3 4 4" /></svg>
              </span>
              <span class="info">
                <span class="name">{{ c.name }}</span>
                <span class="meta">{{ [typeLabel(c.campType), c.eupmyeon].filter(Boolean).join(' · ') }}</span>
                <span class="score">안전 {{ c.safetyScore ?? '-' }}</span>
              </span>
            </button>
            <button type="button" class="heart" :aria-label="`${c.name} 찜 해제`" @click="removeFavorite(c.id)">
              <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- 지도 (로그인 전: 모든 캠핑장 / 로그인 후: 관심 캠핑장) -->
    <WebCampMap :camps="mapCamps" :facilities="mapFacilities" :selected-id="null" :fit-key="mapCamps.length" @bounds="onBounds">
      <!-- 차박지 찾기로 가는 버튼 (검색 지도와 같은 자리) -->
      <template #top-right><WebChabakToggle /></template>
    </WebCampMap>
    <!-- 비교 패널이 열려 있으면 칩을 패널 오른쪽으로 옮긴다 (가려지지 않게) -->
    <WebFacChips :layers="layers" :on="facilityOn" :class="{ 'beside-cmp': auth.isLoggedIn && favCamps.length && cmpOpen }" @toggle="toggleChip" />

    <!-- 로그인 후: 비교 패널 (지도 위, 관심 캠핑장 목록 바로 옆) -->
    <template v-if="auth.isLoggedIn && favCamps.length">
      <section v-if="cmpOpen" class="cmp" aria-label="관심 캠핑장 비교">
        <div class="ptop">
          <div class="ptitle">
            <h1>관심 캠핑장 비교</h1>
            <button type="button" class="pclose" aria-label="비교 닫기" @click="cmpOpen = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
            </button>
          </div>
          <p>항목마다 가장 좋은 값은 <b>초록 칸</b>으로 보여요.</p>
        </div>

        <template v-if="chosen.length >= 2">
          <div class="heads" :style="{ gridTemplateColumns: cols }">
            <span></span>
            <div v-for="c in chosen" :key="c.id" class="head">
              <span class="photo" role="img" :aria-label="`${c.name} 사진`">
                <img v-if="c.imageUrl" :src="c.imageUrl" :alt="c.name" />
                <svg v-else width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                  stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="m4 18 5-5 4 4 3-3 4 4" /></svg>
              </span>
              <div class="hname">
                <span>{{ c.name }}</span>
                <button type="button" :aria-label="`${c.name} 비교에서 빼기`" @click="togglePick(c.id)">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                    stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
                </button>
              </div>
            </div>
          </div>
          <div class="rows">
            <div v-for="r in rows" :key="r.label" class="crow" :style="{ gridTemplateColumns: cols }">
              <span class="lab">{{ r.label }}</span>
              <span v-for="(v, i) in r.cells" :key="i" class="cell" :class="{ best: v.best }">{{ v.text }}</span>
            </div>
          </div>
          <div class="pfoot">시간은 모두 자차 기준이에요.{{ anyDummy ? ' 일부는 더미 데이터예요.' : '' }}</div>
        </template>
        <div v-else class="notenough">
          <span class="a">2곳 이상 골라야 비교할 수 있어요</span>
          <span class="b">왼쪽 관심 캠핑장에서 체크해 주세요.</span>
        </div>
      </section>
      <!-- 닫았을 때: 다시 여는 버튼 -->
      <button v-else type="button" class="cmp-open" @click="cmpOpen = true">비교 보기 ({{ chosen.length }}곳)</button>
    </template>
  </WebMapShell>
</template>

<style scoped>
/* ---- 로그인 전 ---- */
.gate {
  flex: 1;
  padding: 0 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 14px;
}
.gate-icon {
  width: 72px;
  height: 72px;
  border-radius: 999px;
  background: var(--web-best);
  color: var(--web-green);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}
.gate h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}
.login {
  width: 100%;
  height: 50px;
  border-radius: 10px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
}
.gate p {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--web-text-sub);
}
.gate .join a {
  color: var(--web-green);
  font-weight: 700;
  text-decoration: underline;
}

/* ---- 로그인 후 왼쪽 ---- */
.mine {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.mine-head {
  padding: 16px 20px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mt {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}
.hrt,
.heart svg {
  fill: var(--web-terracotta);
  stroke: var(--web-terracotta);
  stroke-width: 2;
  stroke-linejoin: round;
}
.n {
  color: var(--web-green);
}
.limit {
  font-size: 12px;
  color: var(--web-text-sub);
  text-align: right;
}
.limit.warn {
  color: var(--web-text-warn);
}
.state {
  margin: 24px 20px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.items {
  padding: 0 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.item {
  display: flex;
  align-items: center;
  border-radius: 12px;
  background: transparent;
  border: 1px solid var(--web-line-panel-soft);
}
.item.on {
  background: var(--web-best);
  border-color: var(--web-best);
}
.pick {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 12px;
  align-items: center;
  text-align: left;
  padding: 10px;
  border: 0;
  background: transparent;
  color: var(--web-text);
  cursor: pointer;
  font-family: inherit;
}
.check {
  flex: 0 0 22px;
  height: 22px;
  border-radius: 6px;
  border: 2px solid var(--web-check-line);
  background: var(--web-bg);
  color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.item.on .check {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
}
.thumb {
  flex: 0 0 64px;
  height: 64px;
  border-radius: 10px;
  background: var(--web-photo-empty);
  color: var(--web-text-mute);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.thumb img,
.photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 15px;
  font-weight: 700;
}
.meta {
  font-size: 12px;
  color: var(--web-text-sub);
}
.score {
  font-size: 12px;
  color: var(--web-green);
  font-weight: 700;
}
.heart {
  flex: none;
  width: 44px;
  height: 44px;
  margin-right: 4px;
  border: 0;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---- 로그인 후 오른쪽 비교표 ---- */
.cmp {
  position: absolute;
  top: 16px;
  left: 16px;
  bottom: 16px;
  z-index: 5; /* 지도 위 */
  width: 520px;
  max-width: calc(100% - 96px); /* 오른쪽 차박 버튼과 겹치지 않게 */
  overflow-y: auto;
  background: var(--web-bg);
  border-radius: 16px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
}
.ptitle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.pclose {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text-sub);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.beside-cmp {
  left: 552px; /* 비교 패널 너비(520) + 여백 — scoped 스타일이 자식 부품의 바깥 요소에도 걸리는 점을 이용 */
}
.cmp-open {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 5;
  height: 40px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  cursor: pointer;
}
.ptop {
  padding: 20px 20px 4px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ptop h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
}
.ptop p {
  margin: 0;
  font-size: 14px;
  color: var(--web-text-sub);
}
.ptop b {
  color: var(--web-green);
  background: var(--web-best);
  padding: 1px 6px;
  border-radius: 4px;
}
.heads {
  margin: 12px 20px 4px;
  display: grid;
  gap: 12px;
  align-items: end;
}
.head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.photo {
  height: 84px;
  border-radius: 12px;
  background: var(--web-photo-empty);
  color: var(--web-text-mute);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.hname {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
}
.hname button {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text-sub);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.rows {
  padding: 4px 20px 16px;
}
.crow {
  display: grid;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--web-line-panel-soft);
}
.lab {
  font-size: 14px;
  color: var(--web-text-sub);
}
.cell {
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--web-cell);
  color: var(--web-text-cell);
  font-size: 14px;
}
.cell.best {
  background: var(--web-best);
  color: var(--web-green);
  font-weight: 700;
}
.pfoot {
  padding: 14px 20px 18px;
  border-top: 1px solid var(--web-line-panel-soft);
  font-size: 13px;
  color: var(--web-text-sub);
}
.notenough {
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}
.notenough .a {
  font-size: 17px;
  font-weight: 700;
}
.notenough .b {
  font-size: 14px;
  color: var(--web-text-sub);
}
</style>
