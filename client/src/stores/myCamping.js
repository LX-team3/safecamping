import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 내 캠핑: 캠핑장 상세에서 "일정 등록"한 캠핑 목록 (내 캠핑 탭에 카드로 보인다)
// 서버(camp_plan 테이블, /api/member/camp-plans)가 정본이라 다른 기기에서 로그인해도 보인다. 회원 전용
export const useMyCampingStore = defineStore('myCamping', () => {
  const plans = ref([]) // [{ id, campId, campName, place, checkIn, checkOut, memo }] 체크인 빠른 순

  async function load() {
    plans.value = await api('/api/member/camp-plans')
  }

  // 지난 날짜·체크아웃 순서·중복은 서버가 다시 검사한다 → 틀리면 서버 문구를 담은 Error 가 난다
  async function add(plan) {
    await api('/api/member/camp-plans', { method: 'post', body: plan })
    await load() // 서버 순서(체크인 순)를 그대로 쓰려고 다시 불러온다
  }

  async function remove(id) {
    await api(`/api/member/camp-plans/${id}`, { method: 'delete' })
    plans.value = plans.value.filter((p) => p.id !== id)
  }

  // 로그아웃할 때: 다음 사람에게 이전 사람의 목록이 보이지 않게
  function reset() {
    plans.value = []
  }

  return { plans, load, add, remove, reset }
})
