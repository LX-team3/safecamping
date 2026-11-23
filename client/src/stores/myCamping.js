import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'
import { scheduleBriefNotice } from '@/utils/localNotify'

// 내 캠핑: 캠핑장 상세에서 "일정 등록"한 캠핑 목록 (내 캠핑 탭에 카드로 보인다)
// 서버(camp_plan 테이블, /api/member/camp-plans)가 정본이라 다른 기기에서 로그인해도 보인다. 회원 전용
export const useMyCampingStore = defineStore('myCamping', () => {
  const plans = ref([]) // [{ id, campId, campName, place, lat, lng, checkIn, checkOut, memo }] 체크인 빠른 순 (campId 가 null 이면 차박 자리)
  const checklists = ref({}) // 일정 id → 출발 준비 응답 (GET /api/member/camp-plans/{id}/checklist) — 카드 줄과 체크리스트 화면이 같이 씀

  async function load() {
    plans.value = await api('/api/member/camp-plans')
  }

  // 지난 날짜·체크아웃 순서·중복은 서버가 다시 검사한다 → 틀리면 서버 문구를 담은 Error 가 난다
  async function add(plan) {
    await api('/api/member/camp-plans', { method: 'post', body: plan })
    await load() // 서버 순서(체크인 순)를 그대로 쓰려고 다시 불러온다
    // 방금 저장한 일정을 다시 찾아 (서버가 정한 id 가 필요해서) 앱이면 5초 뒤 브리핑 알림을 예약한다
    const saved = plans.value.find((p) =>
      p.checkIn === plan.checkIn && (plan.campId != null ? p.campId === plan.campId : p.lat === plan.lat && p.lng === plan.lng),
    )
    scheduleBriefNotice(saved) // 기다리지 않는다 — 알림이 실패해도 저장은 끝난 것
    return saved
  }

  // 고치기: 날짜 · 메모만 (예약 정보 확인 화면). 캠핑장은 그대로
  async function update(id, { checkIn, checkOut, memo }) {
    await api(`/api/member/camp-plans/${id}`, { method: 'put', body: { checkIn, checkOut, memo } })
    await load()
  }

  async function remove(id) {
    await api(`/api/member/camp-plans/${id}`, { method: 'delete' })
    plans.value = plans.value.filter((p) => p.id !== id)
  }

  // 출발 준비: 계절·공통 준비물 + (출발 3일 전부터) 그 지역 특보. 응답은 일정 id 로 기억해 둔다
  async function loadChecklist(planId) {
    const data = await api(`/api/member/camp-plans/${planId}/checklist`)
    checklists.value = { ...checklists.value, [planId]: data }
    return data
  }

  // 로그아웃할 때: 다음 사람에게 이전 사람의 목록이 보이지 않게
  function reset() {
    plans.value = []
    checklists.value = {}
  }

  return { plans, checklists, load, add, update, remove, loadChecklist, reset }
})
