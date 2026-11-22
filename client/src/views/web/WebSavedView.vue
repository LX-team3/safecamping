<script setup>
// 웹 저장 (설계 4-3 로그인 전 · 4-4 로그인 후)
//  - 로그인 전: 왼쪽에 로그인 안내, 오른쪽은 지도 (캠핑장 핀 · 시설 칩 · 기상특보)
//  - 로그인 후: 왼쪽에 관심 캠핑장(하트) 목록 + 체크(최대 3곳), 오른쪽에 체크한 캠핑장 비교표
// 데이터: 관심 캠핑장 GET /api/saved/favorites, 캠핑장 GET /api/camps · /api/camps/{id} (비교 값은 가까운 시설 차로 n분)
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import WebMapShell from '@/components/web/WebMapShell.vue'
import WebCampMap from '@/components/web/WebCampMap.vue'
import WebFacChips from '@/components/web/WebFacChips.vue'
import WebWeatherBar from '@/components/web/WebWeatherBar.vue'
import { api } from '@/utils/api'
import { facilityOn } from '@/utils/facilityLayers'
import { useMapFacilities } from '@/utils/webMapFacilities'
import { shortSido } from '@/utils/place'
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
const cols = computed(() => `120px repeat(${Math.max(chosen.value.length, 1)}, minmax(0, 1fr))`)
const anyDummy = computed(() => chosen.value.some((c) => details.value[c.id]?.dummy))
const limitText = computed(() =>
  full.value ? '비교는 최대 3곳까지예요 · 하나를 빼고 골라 주세요' : `체크해서 비교에 담기 · 최대 3곳 (${picked.value.length}/${MAX_PICK})`,
)

// ---- 로그인 전: 지도 ----
const { layers, mapFacilities, loadLayers, toggleChip, onBounds } = useMapFacilities()
const mapCamps = computed(() => allCamps.value.filter((c) => TIER_BY_GRADE[c.grade]).map((c) => ({ ...c, tier: TIER_BY_GRADE[c.grade] })))
const warnings = ref(null)
const warnRegion = ref('')
async function loadWarnings() {
  const sido = allCamps.value[0]?.sido
  warnRegion.value = shortSido(sido || '강원특별자치도')
  try {
    const res = await api('/api/alerts', { params: { sido: warnRegion.value } })
    warnings.value = res.activeWarnings?.length ?? null // 발효 중 특보 목록(배열)의 개수
  } catch {
    warnings.value = null
  }
}

onMounted(async () => {
  await loadCamps()
  if (auth.isLoggedIn) {
    await loadFavorites()
    picked.value = favIds.value.slice(0, MAX_PICK) // 처음엔 앞의 3곳을 비교해 보여 준다
  } else {
    loadLayers()
    loadWarnings()
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

    <!-- 로그인 전: 지도 -->
    <template v-if="!auth.isLoggedIn">
      <WebCampMap :camps="mapCamps" :facilities="mapFacilities" :selected-id="null" :fit-key="mapCamps.length" @bounds="onBounds" />
      <WebFacChips :layers="layers" :on="facilityOn" @toggle="toggleChip" />
      <WebWeatherBar :region="warnRegion" :count="warnings" />
    </template>

    <!-- 로그인 후: 비교표 -->
    <div v-else class="compare">
      <div class="paper">
        <div class="ptop">
          <h1>관심 캠핑장 비교</h1>
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
      </div>
    </div>
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
.compare {
  position: absolute;
  inset: 0;
  overflow-y: auto;
  background: var(--web-bg-gray);
  padding: 32px;
  box-sizing: border-box;
}
.paper {
  max-width: 960px;
  margin: 0 auto;
  background: var(--web-bg);
  border-radius: 16px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
}
.ptop {
  padding: 28px 32px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ptop h1 {
  margin: 0;
  font-size: 24px;
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
  margin: 16px 32px 8px;
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
  height: 120px;
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
  font-size: 16px;
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
  padding: 8px 32px 24px;
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
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--web-cell);
  color: var(--web-text-cell);
  font-size: 15px;
}
.cell.best {
  background: var(--web-best);
  color: var(--web-green);
  font-weight: 700;
}
.pfoot {
  padding: 20px 32px 28px;
  border-top: 1px solid var(--web-line-panel-soft);
  font-size: 13px;
  color: var(--web-text-sub);
}
.notenough {
  padding: 80px 32px;
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
