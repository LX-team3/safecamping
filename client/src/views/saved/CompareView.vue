<script setup>
// 관심 캠핑장 비교 (설계: Compare.dc.html). 표의 값은 서버 더미(CampDummyData) — 화면에 더미라고 크게 표시한다
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/utils/api'
import { useAuthStore } from '@/stores/auth'
import { useSavedStore, DUMMY_CAMP_IDS } from '@/stores/saved'

const route = useRoute()
const auth = useAuthStore()
const saved = useSavedStore()

const data = ref(null) // CompareResponse: { dummy, listName, totalCount, camps, rows }
const error = ref('')
const busy = ref(false)

const listId = computed(() => route.params.id)
// 캠핑장 수만큼 칸을 나눈다 (맨 앞 74px 은 항목 이름)
const columns = computed(() => `74px repeat(${data.value?.camps.length || 1}, minmax(0, 1fr))`)

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

function addDummyCamps() {
  return run(async () => {
    for (const id of DUMMY_CAMP_IDS) await saved.addCamp(listId.value, id)
  })
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
      <!-- 안전 수치를 지어내지 않는다는 규칙: 더미 값이면 표 위에 항상 보이게 -->
      <p v-if="data.dummy" class="dummy" role="note">
        <b>더미 데이터</b> 설계 예시 값이에요. 실제 거리·시간·안전점수가 아니에요.
      </p>

      <div v-if="data.camps.length === 0" class="empty">
        <p>아직 담은 캠핑장이 없어요.</p>
        <!-- 캠핑장 상세 화면이 아직 없어서, 시험용으로 더미 캠핑장을 담는 버튼을 둔다 -->
        <button type="button" class="primary" :disabled="busy" @click="addDummyCamps">예시 캠핑장 3곳 담기 (더미)</button>
      </div>

      <template v-else>
        <span class="sub">
          가장 좋은 값은 진하게 보여요.
          <template v-if="data.totalCount > data.camps.length">담은 {{ data.totalCount }}곳 중 앞의 {{ data.camps.length }}곳만 비교해요.</template>
        </span>

        <div class="table" :style="{ gridTemplateColumns: columns }">
          <span />
          <div v-for="c in data.camps" :key="c.id" class="camp">
            <span class="photo" />
            <b>{{ c.name }}</b>
            <button type="button" class="remove" :disabled="busy" @click="remove(c.id)">빼기</button>
          </div>

          <template v-for="r in data.rows" :key="r.label">
            <span class="label">{{ r.label }}</span>
            <span v-for="(v, i) in r.cells" :key="i" class="cell"
              :class="{ score: r.kind === 'score', best: v.best }">{{ v.text }}</span>
          </template>
        </div>
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
.dummy {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-sub);
}
.dummy b {
  margin-right: 4px;
  color: var(--color-text);
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
  cursor: pointer;
}
.primary:disabled {
  opacity: 0.6;
}
.error {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
.table {
  display: grid;
  column-gap: 6px;
  font-size: 13px;
}
.camp {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 10px;
}
/* 캠핑장 사진 자리 */
.photo {
  aspect-ratio: 1;
  border-radius: 10px;
  background: var(--color-photo);
}
.camp b {
  font-size: 14px;
  line-height: 1.3;
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
.label,
.cell {
  display: flex;
  align-items: center;
  min-height: 52px;
  border-top: 1px solid var(--color-line);
  line-height: 1.3;
}
.cell {
  color: var(--color-text-mid);
}
.label {
  font-size: 12px;
  color: var(--color-text-sub);
}
.cell.best {
  font-weight: 900;
  color: var(--color-text);
}
.cell.score {
  font-weight: 700;
  color: var(--color-green);
}
</style>
