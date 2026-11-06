import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 가족 모임방 (내 캠핑 → 가족 모임방 버튼). 서버(family_* · meeting_place · child_card 테이블)가 정본이고,
// 여기에는 화면에 그릴 내 방과 탭별 목록만 둔다. 한 사람당 방 하나라 방 번호를 따로 들고 다니지 않는다
// 탭 목록(모일 곳·아이 카드·일정)은 그 탭을 열 때 불러온다 → 첫 화면이 가볍다
export const useFamilyStore = defineStore('family', () => {
  const room = ref(null) // { id, name, inviteCode, members: [{ name, me }] } 또는 null (방 없음)
  const places = ref([]) // [{ id, name, description, lon, lat, createdByName }]
  const children = ref([]) // [{ id, name, birthDate, bloodType, allergy, disease, medicine, memo, updatedByName, updatedAt }]
  const schedules = ref([]) // [{ id, title, startAt, memo, createdByName }] 시간 순

  // 방이 바뀌면(만들기·들어가기·나가기) 이전 방의 목록이 남지 않게 비운다
  function clearLists() {
    places.value = []
    children.value = []
    schedules.value = []
  }

  async function fetchRoom() {
    room.value = await api('/api/family/room')
  }

  async function createRoom(name) {
    room.value = await api('/api/family/room', { method: 'post', body: { name } })
    clearLists()
  }

  async function join(inviteCode) {
    room.value = await api('/api/family/room/join', { method: 'post', body: { inviteCode } })
    clearLists()
  }

  async function leave() {
    await api('/api/family/room/me', { method: 'delete' })
    room.value = null
    clearLists()
  }

  // 모일 곳
  async function fetchPlaces() {
    places.value = await api('/api/family/places')
  }
  async function addPlace(place) {
    places.value.push(await api('/api/family/places', { method: 'post', body: place }))
  }
  async function deletePlace(id) {
    await api(`/api/family/places/${id}`, { method: 'delete' })
    places.value = places.value.filter((p) => p.id !== id)
  }

  // 아이 건강 응급 카드
  async function fetchChildren() {
    children.value = await api('/api/family/children')
  }
  async function addChild(card) {
    children.value.push(await api('/api/family/children', { method: 'post', body: card }))
  }
  async function updateChild(id, card) {
    const updated = await api(`/api/family/children/${id}`, { method: 'put', body: card })
    children.value = children.value.map((c) => (c.id === id ? updated : c))
  }
  async function deleteChild(id) {
    await api(`/api/family/children/${id}`, { method: 'delete' })
    children.value = children.value.filter((c) => c.id !== id)
  }

  // 일정 (추가 후 서버 순서(시간 순)를 그대로 쓰려고 목록을 다시 불러온다)
  async function fetchSchedules() {
    schedules.value = await api('/api/family/schedules')
  }
  async function addSchedule(schedule) {
    await api('/api/family/schedules', { method: 'post', body: schedule })
    await fetchSchedules()
  }
  async function deleteSchedule(id) {
    await api(`/api/family/schedules/${id}`, { method: 'delete' })
    schedules.value = schedules.value.filter((s) => s.id !== id)
  }

  return {
    room, places, children, schedules,
    fetchRoom, createRoom, join, leave,
    fetchPlaces, addPlace, deletePlace,
    fetchChildren, addChild, updateChild, deleteChild,
    fetchSchedules, addSchedule, deleteSchedule,
  }
})
