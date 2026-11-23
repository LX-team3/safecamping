import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { answerTags, computeResult, loadSavedResult, saveResult } from '@/utils/webSurvey'

// 1분 진단. 질문·결과 계산은 웹과 같은 utils/webSurvey.js 를 쓴다 (웹·앱이 항상 같은 질문)
// 답: 로그인했으면 이 브라우저(localStorage, 사용자별 — 웹 진단과 같은 저장소라 웹에서 한 진단이 앱에도 보인다)에 저장하고,
//     안 했으면 이 브라우저 탭(sessionStorage)에만 둔다 (창을 닫으면 사라짐)
// 서버 진단 API(/api/survey)와 survey_answer 테이블은 이제 앱이 부르지 않는다 (지우지 않고 남겨 둠)
const KEY = 'survey-answers'

function readSession() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY)) ?? null
  } catch {
    return null // 개인 정보 보호 모드 등으로 저장소를 못 쓸 때
  }
}
function writeSession(value) {
  try {
    if (value) sessionStorage.setItem(KEY, JSON.stringify(value))
    else sessionStorage.removeItem(KEY)
  } catch {
    // 저장 못 해도 화면은 그대로 쓸 수 있다
  }
}

// 예전(서버 5문항) 형식의 답이 탭에 남아 있으면 새 질문과 맞지 않으니 버린다
const isCurrent = (a) => !!a && Array.isArray(a.who) && 'site' in a

export const useSurveyStore = defineStore('survey', () => {
  const auth = useAuthStore()
  // { who[], kid, site, season, exp, car, q5, q6, q7 } 또는 null
  const answers = ref(isCurrent(readSession()) ? readSession() : null)

  const done = computed(() => answers.value !== null)

  // 지도 "내 유형 점수"(응급실 가산점) 켜짐. 진단을 마치면 켜진다 (팀 결정). 화면 사이에서만 기억하고 새로 열면 다시 켜짐
  const bonusOn = ref(true)

  // 로그인했으면 이 브라우저에 저장된 내 답을 읽는다. 없는데 로그인 전에 한 답이 있으면 그 답을 내 것으로 저장해 준다
  async function load(isLoggedIn) {
    if (!isLoggedIn || !auth.member) return
    const saved = loadSavedResult(auth.member.id)
    if (saved?.answers && isCurrent(saved.answers)) {
      answers.value = saved.answers
    } else if (answers.value) {
      saveResult(auth.member.id, answers.value, computeResult(answers.value))
    }
    writeSession(null) // 사용자별 저장소에 있으니 탭 사본은 지운다
  }

  async function save(value, isLoggedIn) {
    answers.value = value
    bonusOn.value = true // 진단을 마치면 지도 "내 유형 점수"를 켠다
    if (isLoggedIn && auth.member) {
      saveResult(auth.member.id, value, computeResult(value))
    } else {
      writeSession(value)
    }
  }

  // 로그아웃할 때: 다음 사람에게 이전 사람의 답이 보이지 않게 (저장된 사용자별 답은 그대로 둔다)
  function reset() {
    answers.value = null
    writeSession(null)
  }

  // 내 정보 카드에 쓸 요약 (예: 아이 동반 · 어린이 · 오토캠핑 텐트 · 여름 · 첫 캠핑 · 차량 대피)
  const summary = computed(() => (answers.value ? answerTags(answers.value).join(' · ') : ''))

  return { answers, done, summary, bonusOn, load, save, reset }
})
