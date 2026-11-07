import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'
import { useAuthStore } from '@/stores/auth'

// 관광지 탭 여행 취향. 질문·선택지는 서버(TourPreferenceOptions)가 정본이라 API 로 받는다
// 답: 로그인했으면 서버에 저장하고, 안 했으면 이 브라우저 탭(sessionStorage)에만 둔다 (창을 닫으면 사라짐)
const KEY = 'campin.tourPreference'
const URL = '/api/member/tour-preferences'

function emptyAnswers() {
  return { activity: [], nature: [] }
}

function readSession() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY)) ?? emptyAnswers()
  } catch {
    return emptyAnswers() // 개인 정보 보호 모드 등으로 저장소를 못 쓸 때
  }
}

function writeSession(value) {
  try {
    if (value) sessionStorage.setItem(KEY, JSON.stringify(value))
    else sessionStorage.removeItem(KEY)
  } catch {
    // 저장 못 해도 이번 화면에서는 그대로 쓴다
  }
}

export const useTourPreferenceStore = defineStore('tourPreference', () => {
  const auth = useAuthStore()
  const questions = ref([]) // [{ code, title, max, options: [{ code, label }] }]
  const answers = ref(readSession()) // { activity: [code], nature: [code] }

  // 관광지 API 의 prefer 로 보낼 값
  const codes = computed(() => [...answers.value.activity, ...answers.value.nature])

  async function fetchQuestions() {
    if (!questions.value.length) questions.value = await api('/api/tour-preferences/questions')
  }

  // 회원이면 서버의 답을 쓴다. 비회원 때 고른 답은 서버로 옮기지 않으므로 브라우저 사본을 지운다
  async function load() {
    if (!auth.isLoggedIn) return
    answers.value = await api(URL)
    writeSession(null)
  }

  async function save(value) {
    if (auth.isLoggedIn) {
      answers.value = await api(URL, { method: 'put', body: value })
    } else {
      answers.value = value
      writeSession(value)
    }
  }

  function reset() {
    answers.value = emptyAnswers()
    writeSession(null)
  }

  // 로그아웃하거나 회원이 바뀌면 지운다 → 다음 사람에게 이전 사람의 취향이 보이지 않게
  watch(
    () => auth.member?.id,
    (id, before) => {
      if (before && id !== before) reset()
    },
  )

  return { questions, answers, codes, fetchQuestions, load, save }
})
