import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { nightsBetween } from '@/utils/date'

// 이번 여행 조건: 어디로(지역) · 누구와(인원·아이 나이대) · 언제(체크인·체크아웃)
// 탐색 탭의 지역·날짜 칩과 두 시트가 같이 쓴다. 새로고침해도 남도록 sessionStorage 에 저장
const KEY = 'campin.trip'

const EMPTY = {
  sido: null, // { id, name } — null 이면 전국
  sigungu: null, // { id, name, safetyReady } — null 이면 시·도 전체 (또는 전국)
  adults: 1,
  kids: 0,
  childAges: [], // 아이 수만큼: infant | preschool | elementary
  checkIn: null, // "2026-11-14"
  checkOut: null,
}

function load() {
  try {
    return { ...EMPTY, ...JSON.parse(sessionStorage.getItem(KEY)) }
  } catch {
    return { ...EMPTY }
  }
}

export const useTripStore = defineStore('trip', () => {
  const state = ref(load())

  watch(
    state,
    (v) => {
      try {
        sessionStorage.setItem(KEY, JSON.stringify(v))
      } catch {
        /* 저장 못 해도 이번 화면에서는 그대로 쓴다 */
      }
    },
    { deep: true },
  )

  const nights = computed(() =>
    state.value.checkIn && state.value.checkOut ? nightsBetween(state.value.checkIn, state.value.checkOut) : 0,
  )

  function setRegion(sido, sigungu) {
    state.value = { ...state.value, sido, sigungu }
  }

  // 누구와 언제 시트에서 서버 검사(POST /api/trips/check)를 통과한 값만 넣는다
  function setWhoWhen({ adults, kids, childAges, checkIn, checkOut }) {
    state.value = { ...state.value, adults, kids, childAges, checkIn, checkOut }
  }

  return { state, nights, setRegion, setWhoWhen }
})
