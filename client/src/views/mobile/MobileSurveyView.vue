<script setup>
// 1분 진단 (웹과 같은 질문 8개). 질문·선택지·결과 계산은 utils/webSurvey.js 를 웹과 같이 쓴다 (앱·웹이 항상 같은 질문)
// 단계는 웹의 묶음과 같다: ① 어떤 캠핑을 가나요? (5문항) ② 이럴 땐 어떻게 할까요? (3문항, 안전 상식 퀴즈)
// 답은 이 브라우저에 저장한다 (stores/survey.js). 끝나면 내 정보(또는 탐색)로 간다 — 앱에는 결과 화면 없이 내 정보 카드에 요약만 보인다
// 다시 진단할 때는 이전 답을 채워서 연다
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSurveyStore } from '@/stores/survey'
import { ALLQ, KID_OPTS, PARTS, emptyAnswers, isReady, pickAnswer } from '@/utils/webSurvey'

const auth = useAuthStore()
const survey = useSurveyStore()
const router = useRouter()
const route = useRoute()
// 탐색 화면 팝업에서 왔으면(?from=explore) 끝난 뒤·나갈 때 탐색으로, 아니면 내 정보로 돌아간다
const backTo = computed(() => (route.query.from === 'explore' ? '/app/explore' : '/app/my-info'))

const STEPS = PARTS // [{ title, desc, qs }]
const step = ref(0)
const error = ref('')
const loading = ref(false)
const A = ref(emptyAnswers())

onMounted(async () => {
  try {
    await survey.load(auth.isLoggedIn)
  } catch (e) {
    error.value = e.message
  }
  if (survey.answers) A.value = { ...emptyAnswers(), ...survey.answers, who: [...survey.answers.who] }
})

const current = computed(() => STEPS[step.value])
const number = (q) => ALLQ.indexOf(q) + 1
const isOn = (q, code) => (q.multi ? A.value[q.k].includes(code) : A.value[q.k] === code)
const showKid = (q) => q.k === 'who' && A.value.who.includes('child')
const pick = (k, v) => {
  error.value = ''
  A.value = pickAnswer(A.value, k, v)
}

const ruleText = (q) => (q.multi ? '복수 선택' : '1개 선택')

// 이 단계의 질문에 다 답했는지 (아이를 골랐다면 나이대까지)
function stepAnswered() {
  const ok = current.value.qs.every((q) => (q.multi ? A.value[q.k].length > 0 : !!A.value[q.k]))
  return ok && (!current.value.qs.some(showKid) || !!A.value.kid)
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
  if (!isReady(A.value)) return
  loading.value = true
  try {
    await survey.save(A.value, auth.isLoggedIn)
    router.push(backTo.value)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function prev() {
  error.value = ''
  if (step.value === 0) router.push(backTo.value)
  else step.value--
}
</script>

<template>
  <div class="survey">
    <div class="top">
      <div class="top-row">
        <span class="step-text">1분 진단 · {{ step + 1 }} / {{ STEPS.length }}</span>
        <RouterLink :to="backTo" class="later">나중에 할게요</RouterLink>
      </div>
      <div class="bars" :style="{ gridTemplateColumns: `repeat(${STEPS.length}, minmax(0, 1fr))` }">
        <span v-for="(s, i) in STEPS" :key="i" :class="{ on: i <= step }" />
      </div>
    </div>

    <div class="body">
      <div class="title">
        <h1>{{ current.title }}</h1>
        <span>{{ current.desc }}</span>
      </div>

      <div v-for="q in current.qs" :key="q.k" class="question">
        <div class="q-head">
          <span class="n">{{ number(q) }}</span>
          <b>{{ q.t }}</b>
          <span class="rule">{{ q.h || ruleText(q) }}</span>
        </div>

        <!-- 퀴즈 보기는 문장이라 세로 카드, 나머지는 알약 -->
        <div :class="q.quiz ? 'cards' : 'pills'">
          <button v-for="o in q.o" :key="o[0]" type="button" :class="[q.quiz ? 'card' : 'pill', { on: isOn(q, o[0]) }]"
            :aria-pressed="isOn(q, o[0])" @click="pick(q.k, o[0])">
            <span class="label"><span>{{ o[1] }}</span><small v-if="!q.quiz && o[2]">{{ o[2] }}</small></span>
            <span v-if="q.quiz" class="mark" aria-hidden="true">{{ isOn(q, o[0]) ? '✓' : '' }}</span>
          </button>
        </div>

        <!-- 아이를 골랐으면 나이대 -->
        <div v-if="showKid(q)" class="kid">
          <b>가장 어린 아이는 몇 살이에요?</b>
          <div class="pills">
            <button v-for="o in KID_OPTS" :key="o[0]" type="button" :class="['pill', { on: A.kid === o[0] }]"
              :aria-pressed="A.kid === o[0]" @click="pick('kid', o[0])">
              <span class="label"><span>{{ o[1] }}</span></span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="foot">
      <p v-if="error" class="warn" role="alert">{{ error }}</p>
      <div class="buttons">
        <button type="button" class="prev" @click="prev">이전</button>
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
/* 아이를 골랐을 때 나오는 "가장 어린 아이는 몇 살이에요?" */
.kid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
}
.kid b {
  font-size: 14px;
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
