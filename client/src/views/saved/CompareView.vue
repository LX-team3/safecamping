<script setup>
// 관심 캠핑장 비교 (설계: Compare.dc.html). 값은 서버가 DB(camp · camp_facility · camps)에서 읽은 실제 데이터
// 캠핑장은 탐색 목록·캠핑장 상세의 하트로 담는다 (stores/favorite.js)
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/utils/api'
import { useAuthStore } from '@/stores/auth'
import { useSavedStore } from '@/stores/saved'

const route = useRoute()
const auth = useAuthStore()
const saved = useSavedStore()

const data = ref(null) // CompareResponse: { listName, totalCount, camps, rows }
const error = ref('')
const busy = ref(false)

const listId = computed(() => route.params.id)

// 막대 길이(%): 안전점수는 100점 기준, 시간은 이 항목에서 가장 긴 값 기준 (짧을수록 가까움)
// 0이 되면 막대가 안 보이므로 최소 4%
function barWidth(row, cell) {
  if (cell.value == null) return 0
  const max = row.kind === 'score' ? 100 : Math.max(...row.cells.map((c) => c.value ?? 0))
  return max > 0 ? Math.max(4, Math.round((cell.value / max) * 100)) : 0
}
// 이 항목에 막대를 그릴지. 1곳뿐이면 견줄 대상이 없어 막대가 의미 없으므로(늘 꽉 참) 글자만 보여 준다
// 가능/불가, 걸어서·차로가 섞인 대피소도 막대 없이 글자만
const canCompare = computed(() => (data.value?.camps.length ?? 0) >= 2)
const hasBars = (row) => canCompare.value && row.cells.some((c) => c.value != null)

async function load() {
  try {
    data.value = await api(`/api/saved/lists/${listId.value}/compare`)
    error.value = ''
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => {
  if (auth.isLoggedIn) load()
})

// 담기·빼기 버튼 공통 처리: 실패하면 문구만 보여 주고, 성공하면 표를 다시 불러온다
async function run(action) {
  busy.value = true
  try {
    await action()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function remove(campId) {
  return run(() => saved.removeCamp(listId.value, campId))
}
</script>

<template>
  <div class="compare">
    <div class="head">
      <RouterLink to="/saved" class="back" aria-label="저장으로 돌아가기">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </RouterLink>
      <h1>{{ data ? `${data.listName} 비교` : '캠핑장 비교' }}</h1>
    </div>

    <div v-if="!auth.isLoggedIn" class="empty">
      <p>저장은 로그인하면 쓸 수 있어요.</p>
      <RouterLink to="/login" class="primary">로그인</RouterLink>
    </div>

    <p v-else-if="error" class="error" role="alert">{{ error }}</p>

    <template v-else-if="data">
      <div v-if="data.camps.length === 0" class="empty">
        <p>아직 담은 캠핑장이 없어요. 탐색에서 하트를 눌러 담아 보세요.</p>
        <RouterLink to="/explore" class="primary">캠핑장 둘러보기</RouterLink>
      </div>

      <template v-else>
        <!-- 1곳뿐이면 비교가 아니라 그 캠핑장 정보만 보여 주고, 더 담으라고 안내한다 -->
        <div v-if="!canCompare" class="need-more">
          <p>2곳 이상 담으면 항목별로 비교할 수 있어요.</p>
          <RouterLink to="/explore" class="primary">캠핑장 더 담기</RouterLink>
        </div>

        <span v-else-if="data.totalCount > data.camps.length" class="sub">
          담은 {{ data.totalCount }}곳 중 앞의 {{ data.camps.length }}곳만 비교해요.
        </span>

        <!-- 비교 중인 캠핑장 (빼기) -->
        <div class="camps">
          <div v-for="c in data.camps" :key="c.id" class="camp">
            <span class="photo" />
            <b>{{ c.name }}</b>
            <button type="button" class="remove" :disabled="busy" @click="remove(c.id)">빼기</button>
          </div>
        </div>

        <!-- 항목별 카드: 캠핑장마다 한 줄 (이름 · 막대 · 값 · 가장 좋은 곳 칩) -->
        <section v-for="r in data.rows" :key="r.label" class="item">
          <h2>{{ r.label }}</h2>
          <div v-for="(v, i) in r.cells" :key="i" class="line" :class="{ best: v.best }">
            <span class="name">{{ data.camps[i].name }}</span>
            <span v-if="hasBars(r)" class="bar" aria-hidden="true">
              <span :class="{ score: r.kind === 'score' }" :style="{ width: barWidth(r, v) + '%' }" />
            </span>
            <span class="value" :class="{ no: r.kind === 'yesno' && !v.best && v.text === '불가' }">{{ v.text }}</span>
            <span v-if="v.best" class="chip">{{ r.bestLabel }}</span>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.compare {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
}
.back {
  width: 44px;
  height: 44px;
  margin-left: -10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.sub {
  font-size: 13px;
  color: var(--color-text-sub);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.primary {
  height: 48px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  display: inline-flex;
  align-items: center;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none; /* 링크(RouterLink)로도 쓴다 */
  cursor: pointer;
}
.error {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
.camps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.camp {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
/* 캠핑장 사진 자리 */
.photo {
  aspect-ratio: 4 / 3;
  border-radius: 10px;
  background: var(--color-photo);
}
.camp b {
  font-size: 14px;
  line-height: 1.3;
}
.need-more {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
}
.need-more p {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-mid);
}
.item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--color-divider);
}
.item h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}
.line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  font-size: 14px;
  color: var(--color-text-mid);
}
.line .name {
  flex: none;
  width: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar {
  flex: 1;
  min-width: 0;
  height: 10px;
  border-radius: 5px;
  background: var(--color-fill-soft);
  overflow: hidden;
}
.bar span {
  display: block;
  height: 100%;
  border-radius: 5px;
  background: var(--color-photo);
}
.value {
  flex: none;
  min-width: 64px;
  text-align: right;
}
/* 가장 좋은 곳: 이름·값 진하게, 막대 진하게, 칩 */
.line.best {
  color: var(--color-text);
  font-weight: 700;
}
.line.best .bar span {
  background: var(--color-text);
}
.line.best .bar span.score,
.bar span.score {
  background: var(--color-green);
}
.line:not(.best) .bar span.score {
  opacity: 0.45;
}
.value.no {
  color: var(--color-text-sub);
}
/* 막대가 없는 줄(가능/불가 등)은 값이 이름 바로 뒤로 오지 않게 오른쪽으로 민다 */
.line .name + .value {
  margin-left: auto;
}
.chip {
  flex: none;
  height: 22px;
  padding: 0 8px;
  border-radius: 11px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
}
.remove {
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 12px;
  color: var(--color-text-sub);
  text-decoration: underline;
  cursor: pointer;
}
</style>
