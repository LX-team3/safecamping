import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 설문(1분 진단 diagnosis · 여행 취향 travel) 내용과 답변
// - 답변은 단계를 오가거나 새로고침해도 남도록 sessionStorage 에 저장한다
// - diagnosisSeen(진단을 끝냈거나 '나중에 할게요'를 눌렀는지)은 앱을 다시 켜도 기억해야 해서 localStorage
const ANSWER_KEY = 'campin.survey.answers'
const SEEN_KEY = 'campin.survey.seen'

// 시크릿 창 등에서는 저장소 접근이 막힐 수 있어 try/catch 로 감싼다 (막혀도 화면은 동작)
function load(storage, key, fallback) {
  try {
    const v = storage.getItem(key)
    return v ? JSON.parse(v) : fallback
  } catch {
    return fallback
  }
}
function save(storage, key, value) {
  try {
    storage.setItem(key, JSON.stringify(value))
  } catch {
    /* 저장 못 해도 이번 화면에서는 그대로 쓴다 */
  }
}

export const useSurveyStore = defineStore('survey', () => {
  const definitions = ref({}) // { 설문code: 서버에서 받은 설문 내용 } — 한 번 받으면 다시 안 부름
  // { 설문code: { 질문code: [선택지code, ...] } } — 서버에 보내는 모양 그대로. 배열 순서 = 고른 순서(순위)
  const answers = ref(load(sessionStorage, ANSWER_KEY, {}))
  const diagnosisSeen = ref(load(localStorage, SEEN_KEY, false))

  watch(answers, (v) => save(sessionStorage, ANSWER_KEY, v), { deep: true })
  watch(diagnosisSeen, (v) => save(localStorage, SEEN_KEY, v))

  async function fetchDefinition(code) {
    if (!definitions.value[code]) {
      definitions.value[code] = await api(`/api/surveys/${code}`)
    }
    return definitions.value[code]
  }

  // 로그인 회원이 이 브라우저에서 아직 답하지 않았으면, 지난번에 서버에 낸 답으로 미리 채운다
  async function fetchMine(code) {
    if (answers.value[code]) return
    try {
      const mine = await api(`/api/surveys/${code}/answers/me`)
      if (Object.keys(mine).length) answers.value = { ...answers.value, [code]: mine }
    } catch {
      /* 못 불러오면 빈 상태로 시작 */
    }
  }

  function selected(code, questionCode) {
    return answers.value[code]?.[questionCode] || []
  }

  // 선택지 하나를 누를 때 규칙: 하나만 / 최대 n개 / 다른 것을 풀어 버리는 선택지(exclusive)
  // (서버 SurveyService 도 같은 규칙으로 한 번 더 검사한다)
  function toggle(code, question, option) {
    const cur = selected(code, question.code)
    let next

    if (cur.includes(option.code)) {
      next = cur.filter((c) => c !== option.code) // 다시 누르면 해제 (순위는 뒤가 한 칸씩 당겨짐)
    } else if (question.maxSelect === 1 || option.exclusive) {
      next = [option.code] // 하나만 고르는 질문이거나 "아직 없어요" 같은 선택지면 나머지는 해제
    } else {
      const exclusiveCodes = question.options.filter((o) => o.exclusive).map((o) => o.code)
      next = cur.filter((c) => !exclusiveCodes.includes(c))
      if (question.maxSelect && next.length >= question.maxSelect) return // 최대 개수면 더 안 받음
      next = [...next, option.code]
    }
    answers.value = { ...answers.value, [code]: { ...answers.value[code], [question.code]: next } }
  }

  // 제출 → { saved } (로그인 회원이면 true)
  function submit(code) {
    return api(`/api/surveys/${code}/answers`, { method: 'post', body: { answers: answers.value[code] || {} } })
  }

  function markDiagnosisSeen() {
    diagnosisSeen.value = true
  }

  return { definitions, answers, diagnosisSeen, fetchDefinition, fetchMine, selected, toggle, submit, markDiagnosisSeen }
})
