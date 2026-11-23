import { ref } from 'vue'
import { defineStore } from 'pinia'

// 예약 사이트에 갔다가 돌아왔는지 기억한다 → 돌아오면 "○○캠핑장 예약하셨나요?" 팝업 (App.vue 의 ReservationReturnPopup)
// 외부 예약 사이트에서 실제로 예약했는지는 알 수 없어서 사용자에게 묻는다
// localStorage 에 두는 이유: 같은 탭에서 갔다가 뒤로 와서 새로고침돼도 남아 있게 (로그인과 상관없이 이 기기에서만)
const KEY = 'campin.reservationVisit'
const MIN_AWAY_MS = 10 * 1000 // 이보다 빨리 돌아오면 그냥 둘러본 것으로 보고 묻지 않는다
const MAX_AGE_MS = 24 * 60 * 60 * 1000 // 하루가 지난 기록은 버린다

function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || 'null')
  } catch {
    return null
  }
}

export const useReservationReturnStore = defineStore('reservationReturn', () => {
  const asking = ref(null) // 팝업에 띄울 기록 { campId, campName, planId, at } 또는 null

  // "예약 사이트 ↗"를 누를 때 부른다. planId: 내 예약 카드에서 눌렀으면 그 일정 (저장하면 고치기)
  function mark({ campId, campName, planId = null }) {
    if (!campId) return
    try {
      localStorage.setItem(KEY, JSON.stringify({ campId, campName, planId, at: Date.now() }))
    } catch {
      // 저장이 막힌 브라우저(사생활 보호 모드 등)면 팝업 없이 지나간다
    }
  }

  // 화면으로 돌아왔을 때 부른다. 묻을 때가 됐으면 asking 에 넣는다
  function check() {
    const v = read()
    if (!v) return
    const away = Date.now() - v.at
    if (away > MAX_AGE_MS) return clear()
    if (away >= MIN_AWAY_MS) asking.value = v
  }

  // 답했으면(예약함 · 아직) 기록을 지운다
  function clear() {
    asking.value = null
    try {
      localStorage.removeItem(KEY)
    } catch {
      // 무시
    }
  }

  return { asking, mark, check, clear }
})
