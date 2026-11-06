<script setup>
// 1분 진단 (설계: Survey.dc.html). 3단계 5문항. 질문·선택지·규칙은 서버(/api/survey/questions)에서 받는다
// 여기에는 화면 묶음(단계별 제목·안내 문구)만 둔다. 지역·날짜·동행은 탐색 화면, 관광지·체험은 여행 취향에서 고른다
// 끝나면 내 정보로 간다 (설계는 탐색 화면인데 아직 없음). 다시 진단할 때는 이전 답을 채워서 연다
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSurveyStore } from '@/stores/survey'

const auth = useAuthStore()
const survey = useSurveyStore()
const router = useRouter()

// 단계 묶음과 문구는 설계 그대로. card: 세로 카드 모양(체크 원 있음), 아니면 알약 모양
const STEPS = [
  { title: '어떻게 캠핑하세요?', sub: '지역 · 날짜 · 동행은 여행마다 바뀌니까 나중에 지도에서 골라요', keys: ['campType', 'transport'] },
  { title: '밤에 어떻게 지내세요?', sub: '사고가 가장 많이 나는 시간이라 꼭 물어봐요', keys: ['heating', 'safetyItems'] },
  { title: '가까이 있으면 안심되는 곳은요?', sub: '이 답이 캠핑장 추천 순서에 가장 크게 반영돼요', keys: ['facilities'] },
]
const CARD = ['campType', 'heating']

const step = ref(0)
const error = ref('')
const loading = ref(false)
// 질문 key → 고른 code 목록 (1개만 고르는 질문도 목록으로 다룬다)
const picked = reactive({ campType: [], transport: [], heating: [], safetyItems: [], facilities: [] })

onMounted(async () => {
  try {
    await survey.fetchQuestions()
    await survey.load(auth.isLoggedIn)
  } catch (e) {
    error.value = e.message
  }
  const a = survey.answers
  if (a) {
    picked.campType = [a.campType]
    picked.transport = [a.transport]
    picked.heating = [...a.heating]
    picked.safetyItems = [...a.safetyItems]
    picked.facilities = [...a.facilities]
  }
})

const current = computed(() => STEPS[step.value])
const questionsOfStep = computed(() =>
  current.value.keys.map((k) => survey.questions.find((q) => q.key === k)).filter(Boolean),
)
const number = (key) => survey.questions.findIndex((q) => q.key === key) + 1

function ruleText(q) {
  if (q.type === 'single') return '1개 선택'
  if (q.type === 'rank') return `최대 ${q.max}개 · 중요한 순서대로`
  return '복수 선택'
}

// 설계와 같은 고르기 규칙: 다시 누르면 빠짐 / 혼자만 고르는 값(아직 없어요 등)은 다른 것을 지움 / 최대 개수까지만
function toggle(q, code) {
  error.value = ''
  let arr = picked[q.key]
  if (arr.includes(code)) arr = arr.filter((c) => c !== code)
  else if (code === q.exclusive) arr = [code]
  else {
    arr = arr.filter((c) => c !== q.exclusive)
    if (q.max === 1) arr = [code]
    else if (arr.length < q.max) arr = [...arr, code]
    else error.value = `${q.max}개까지 고를 수 있습니다.`
  }
  picked[q.key] = arr
}

// 질문 아래 안내 문구 (설계 그대로). 연료를 태우는 난방을 고르면 빨간 상자로 바뀐다
function note(q) {
  if (q.key === 'heating') {
    const fuel = picked.heating.includes('fuel') || picked.heating.includes('wood')
    return fuel
      ? { text: '연료를 태우는 난방은 텐트 안에서 일산화탄소 위험이 있어요. 결과에서 가장 먼저 알려드려요.', danger: true }
      : { text: '연료를 태우는 난방을 고르면 일산화탄소 안내를 먼저 보여드려요.' }
  }
  if (q.key === 'safetyItems') return { text: '고르지 않은 용품은 출발 전 체크리스트에 담아드려요.' }
  if (q.key === 'facilities') return { text: '1순위와 가까운 캠핑장일수록 지도에서 더 안심으로 보여요.' }
  return null
}

function stepAnswered() {
  return current.value.keys.every((k) => picked[k].length > 0)
}

async function next() {
  if (!stepAnswered()) {
    error.value = '모든 질문에 답해 주세요.'
    return
  }
  error.value = ''
  if (step.value < STEPS.length - 1) {
    step.value++
    return
  }
  loading.value = true
  try {
    await survey.save({
      campType: picked.campType[0],
      transport: picked.transport[0],
      heating: picked.heating,
      safetyItems: picked.safetyItems,
      facilities: picked.facilities,
    }, auth.isLoggedIn)
    router.push('/my-info')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function prev() {
  error.value = ''
  if (step.value === 0) router.push('/my-info')
  else step.value--
}
</script>

<template>
  <div class="survey">
    <div class="top">
      <div class="top-row">
        <span class="step-text">1분 진단 · {{ step + 1 }} / {{ STEPS.length }}</span>
        <RouterLink to="/my-info" class="later">나중에 할게요</RouterLink>
      </div>
      <div class="bars">
        <span v-for="(s, i) in STEPS" :key="i" :class="{ on: i <= step }" />
      </div>
    </div>

    <div class="body">
      <div class="title">
        <h1>{{ current.title }}</h1>
        <span>{{ current.sub }}</span>
      </div>

      <div v-for="q in questionsOfStep" :key="q.key" class="question">
        <div class="q-head">
          <span class="n">{{ number(q.key) }}</span>
          <b>{{ q.title }}</b>
          <span class="rule">{{ ruleText(q) }}</span>
        </div>

        <div :class="CARD.includes(q.key) ? 'cards' : 'pills'">
          <button v-for="o in q.options" :key="o.code" type="button"
            :class="[CARD.includes(q.key) ? 'card' : 'pill', { on: picked[q.key].includes(o.code) }]"
            :aria-pressed="picked[q.key].includes(o.code)" @click="toggle(q, o.code)">
            <span class="label"><span>{{ o.label }}</span><small v-if="o.sub">{{ o.sub }}</small></span>
            <!-- 카드는 체크, 순서 있는 질문은 몇 순위인지 숫자 -->
            <span v-if="CARD.includes(q.key) || q.type === 'rank'" class="mark" aria-hidden="true">
              {{ picked[q.key].includes(o.code) ? (q.type === 'rank' ? picked[q.key].indexOf(o.code) + 1 : '✓') : '' }}
            </span>
          </button>
        </div>

        <div v-if="note(q)" :class="['note', { danger: note(q).danger }]">{{ note(q).text }}</div>
      </div>
    </div>

    <div class="foot">
      <p v-if="error" class="warn" role="alert">{{ error }}</p>
      <div class="buttons">
        <button type="button" class="prev" @click="prev">이전</button>
        <!-- 설계 문구는 "내 유형으로 캠핑장 보기"(탐색 화면). 탐색 화면이 생기면 되돌린다 -->
        <button type="button" :class="['next', { last: step === STEPS.length - 1 }]" :disabled="loading" @click="next">
          {{ step === STEPS.length - 1 ? '진단 마치기' : '다음으로' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.survey {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.top {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-line);
}
.top-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-text {
  font-size: 14px;
  color: var(--color-text-sub);
}
.later {
  margin-left: auto;
  height: 44px;
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--color-text-mid);
  text-decoration: underline;
}
.bars {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}
.bars span {
  height: 5px;
  border-radius: 3px;
  background: var(--color-track);
}
.bars span.on {
  background: var(--color-text);
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 26px;
  padding: 20px 20px 28px;
}
.title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: -0.02em;
  line-height: 1.3;
}
.title span {
  font-size: 14px;
  color: var(--color-text-sub);
  line-height: 1.5;
}
.question {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.q-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.q-head .n {
  font-size: 13px;
  font-weight: 900;
  color: var(--color-text-sub);
}
.q-head b {
  flex: 1;
  font-size: 17px;
}
.q-head .rule {
  font-size: 12px;
  color: var(--color-text-sub);
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.card,
.pill {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  font-family: inherit;
  cursor: pointer;
}
.card {
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 10px 16px;
  border-radius: 14px;
}
.pill {
  min-height: 44px;
  padding: 0 16px;
  border-radius: 22px;
}
.card.on,
.pill.on {
  border: 2px solid var(--color-text);
  background: var(--color-fill-soft);
}
.label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}
.label span {
  font-size: 15px;
  font-weight: 700;
}
.label small {
  font-size: 12px;
  color: var(--color-text-sub);
}
.mark {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 12px;
  border: 1.5px solid var(--color-text-light);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 900;
}
.on .mark {
  border: 0;
  background: var(--color-text);
  color: var(--color-card);
}
.note {
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-sub);
}
.note.danger {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-weight: 700;
}
.foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 20px 24px;
  border-top: 1px solid var(--color-divider);
}
.buttons {
  display: flex;
  gap: 10px;
}
.prev,
.next {
  height: 54px;
  border-radius: 27px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.prev {
  flex: 1;
  border: 1.5px solid var(--color-text);
  background: var(--color-card);
  color: var(--color-text);
}
.next {
  flex: 2.4;
  border: 0;
  background: var(--color-text);
  color: var(--color-card);
}
.next.last {
  background: var(--color-primary);
}
.next:disabled {
  opacity: 0.6;
}
.warn {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}
</style>
