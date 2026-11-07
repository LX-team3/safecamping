<script setup>
// ② 설문 화면 하나가 두 설문을 처리한다 (router 의 props 로 code·step 을 받음)
//  - /survey/diagnosis/1~3 : 1분 진단 (앱을 처음 켜면 여기로)
//  - /survey/travel        : 여행 취향 (관광지 탭에서 들어옴)
// 질문·선택지 내용은 서버 GET /api/surveys/{code} 에서 받는다 (DB: db/seed_survey.sql)
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import SurveyLayout from '@/components/survey/SurveyLayout.vue'
import QuestionHead from '@/components/survey/QuestionHead.vue'
import ChoiceButton from '@/components/survey/ChoiceButton.vue'
import { useSurveyStore } from '@/stores/survey'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  code: { type: String, required: true },
  step: { type: Number, default: 1 },
})

const router = useRouter()
const survey = useSurveyStore()
const auth = useAuthStore()

const def = ref(null) // 서버에서 받은 설문 내용
const loadError = ref('')
const submitError = ref('')
const submitting = ref(false)

async function loadDefinition() {
  loadError.value = ''
  def.value = null
  try {
    def.value = await survey.fetchDefinition(props.code)
    if (auth.isLoggedIn) await survey.fetchMine(props.code) // 회원이면 지난 답으로 미리 채움
  } catch (e) {
    loadError.value = e.message
  }
}
// 설문이 바뀌면(진단 → 취향) 다시 받는다. 같은 설문 안에서 단계만 바뀌면 store 에 받아 둔 걸 쓴다
watch(() => props.code, loadDefinition, { immediate: true })

const total = computed(() => def.value?.steps.length || 1)
const stepInfo = computed(() => def.value?.steps.find((s) => s.step === props.step))
const isLast = computed(() => props.step >= total.value)

// 제목 줄 왼쪽 글씨: 단계가 여러 개면 "1분 진단 · 1 / 3", 하나면 "여행 취향 · 약 30초"
const headLabel = computed(() => {
  if (!def.value) return ''
  if (total.value > 1) return `${def.value.title} · ${props.step} / ${total.value}`
  return def.value.durationText ? `${def.value.title} · ${def.value.durationText}` : def.value.title
})

// 질문 번호는 설문 전체에서 이어진다 (2단계 첫 질문 = 3번)
function numberOf(question) {
  if (!def.value?.numbered) return 0
  const all = def.value.steps.flatMap((s) => s.questions)
  return all.findIndex((q) => q.code === question.code) + 1
}

function hintOf(q) {
  if (q.inputType === 'rank') return `최대 ${q.maxSelect}개 · 중요한 순서대로`
  if (q.maxSelect === 1) return '1개 선택'
  if (q.maxSelect > 1) return `최대 ${q.maxSelect}개`
  return '복수 선택'
}

function isChecked(q, o) {
  return survey.selected(props.code, q.code).includes(o.code)
}

// 고른 선택지에 위험 안내가 있으면 질문 아래에 보여 준다 (같은 문구는 한 번만)
function warningsOf(q) {
  const texts = q.options.filter((o) => o.warning && isChecked(q, o)).map((o) => o.warning)
  return [...new Set(texts)]
}

// 이 단계의 필수 질문에 하나씩은 답해야 다음으로 넘어간다
const canNext = computed(
  () => stepInfo.value?.questions.every((q) => !q.required || survey.selected(props.code, q.code).length > 0) ?? false,
)

// 들어온 곳이 있으면 거기로, 없으면(주소로 바로 들어옴) 메인으로
function goBackOrHome() {
  if (window.history.state?.back) router.back()
  else router.push('/')
}

async function next() {
  if (!isLast.value) {
    router.push(`/survey/${props.code}/${props.step + 1}`)
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    await survey.submit(props.code)
    if (props.code === 'diagnosis') {
      survey.markDiagnosisSeen()
      // "내 유형으로 캠핑장 보기" → 탐색 탭 추천 목록 (결과 화면 A/B/C 를 만들면 그 사이에 넣는다)
      router.push('/explore')
    } else {
      // TODO: 관광지 탭을 만들면 그쪽으로 보낸다
      goBackOrHome()
    }
  } catch (e) {
    submitError.value = e.message
  } finally {
    submitting.value = false
  }
}

function prev() {
  if (props.step > 1) router.push(`/survey/${props.code}/${props.step - 1}`)
  else goBackOrHome()
}

function skip() {
  if (props.code === 'diagnosis') {
    survey.markDiagnosisSeen() // 다음에 앱을 켤 때는 진단을 띄우지 않는다
    router.push('/explore')
  } else {
    goBackOrHome()
  }
}
</script>

<template>
  <!-- 불러오는 중·실패 -->
  <div v-if="!def || !stepInfo" class="state">
    <template v-if="loadError">
      <p>{{ loadError }}</p>
      <button type="button" class="retry" @click="loadDefinition">다시 시도</button>
    </template>
    <p v-else-if="def">없는 단계입니다.</p>
    <p v-else>불러오는 중...</p>
  </div>

  <SurveyLayout v-else :label="headLabel" :step="step" :total="total" :show-prev="total > 1"
    :next-label="isLast ? def.finishLabel : '다음으로'" :next-disabled="!canNext || submitting"
    :finish="isLast && total > 1" :error="submitError" @prev="prev" @next="next" @skip="skip">
    <h1 class="title">{{ stepInfo.title }}</h1>
    <p v-if="stepInfo.sub" class="sub">{{ stepInfo.sub }}</p>

    <section v-for="q in stepInfo.questions" :key="q.code" class="question">
      <QuestionHead :no="numberOf(q)" :title="q.title" :hint="hintOf(q)" />

      <!-- 카드형: 한 줄에 하나 / 알약형: 줄바꿈하며 나열 (순위 질문은 동그라미 안에 순위 숫자) -->
      <div :class="q.inputType === 'card' ? 'cards' : 'chips'">
        <ChoiceButton v-for="o in q.options" :key="o.code" :variant="q.inputType === 'card' ? 'card' : 'chip'"
          :label="o.label" :description="o.description || ''" :checked="isChecked(q, o)"
          :show-dot="q.inputType !== 'chip'" :rank="q.inputType === 'rank' ? survey.selected(code, q.code).indexOf(o.code) + 1 : 0"
          @toggle="survey.toggle(code, q, o)" />
      </div>

      <p v-for="w in warningsOf(q)" :key="w" class="warn" role="alert">{{ w }}</p>
      <p v-if="q.note" class="q-note">{{ q.note }}</p>
    </section>

    <p v-if="stepInfo.note" class="step-note">{{ stepInfo.note }}</p>
  </SurveyLayout>
</template>

<style scoped>
.state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
  color: var(--color-text-sub);
}
.retry {
  height: 44px;
  padding: 0 20px;
  border: 1px solid var(--color-text);
  border-radius: var(--radius-pill);
  background: var(--color-card);
  font-weight: 700;
  cursor: pointer;
}
.title {
  margin: 0;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.sub {
  margin: 6px 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-text-sub);
}
.question {
  margin-top: 32px;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.warn {
  margin: 12px 0 0;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--color-warn-bg);
  color: var(--color-warn-text);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}
.q-note {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.step-note {
  margin: 32px 0 0;
  padding: 16px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-sub);
}
</style>
