import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 1분 진단. 질문(선택지·규칙)은 서버(SurveyQuestions)가 정본이라 API 로 받는다
// 답: 로그인했으면 서버에 저장하고, 안 했으면 이 브라우저 탭(sessionStorage)에만 둔다 (창을 닫으면 사라짐)
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

export const useSurveyStore = defineStore('survey', () => {
  const questions = ref([]) // [{ key, title, type, max, exclusive, options: [{ code, label, sub }] }]
  const answers = ref(readSession()) // { campType, transport, heating[], safetyItems[], facilities[] } 또는 null

  const done = computed(() => answers.value !== null)

  async function fetchQuestions() {
    if (!questions.value.length) questions.value = await api('/api/survey/questions')
  }

  // 로그인했으면 서버의 답을 읽는다. 서버에 없는데 로그인 전에 한 답이 있으면 그 답을 저장해 준다
  async function load(isLoggedIn) {
    if (!isLoggedIn) return
    const saved = await api('/api/member/survey')
    if (saved) {
      answers.value = saved
    } else if (answers.value) {
      answers.value = await api('/api/member/survey', { method: 'put', body: answers.value })
    }
    writeSession(null) // 서버에 있으니 브라우저 사본은 지운다
  }

  async function save(value, isLoggedIn) {
    if (isLoggedIn) {
      answers.value = await api('/api/member/survey', { method: 'put', body: value })
    } else {
      answers.value = value
      writeSession(value)
    }
  }

  // 로그아웃할 때: 다음 사람에게 이전 사람의 답이 보이지 않게
  function reset() {
    answers.value = null
    writeSession(null)
  }

  // 내 정보 카드에 쓸 요약 (예: 오토캠핑장 · 자동차 · 가스·석유난로 · 응급실 1순위)
  const summary = computed(() => {
    const a = answers.value
    if (!a || !questions.value.length) return ''
    const label = (key, code) => {
      const q = questions.value.find((x) => x.key === key)
      const o = q?.options.find((x) => x.code === code)
      return o ? o.label.replaceAll(' · ', '·') : ''
    }
    return [
      label('campType', a.campType),
      label('transport', a.transport),
      a.heating.map((c) => label('heating', c)).join(', '),
      a.facilities.length ? `${label('facilities', a.facilities[0])} 1순위` : '',
    ].filter(Boolean).join(' · ')
  })

  return { questions, answers, done, summary, fetchQuestions, load, save, reset }
})
