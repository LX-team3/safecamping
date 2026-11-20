import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 가족 모임방 (내 캠핑 → 가족 모임방 버튼). 서버(family_* · meeting_place 테이블)가 정본이고,
// 여기에는 화면에 그릴 내 방과 탭별 목록만 둔다. 한 사람당 방 하나라 방 번호를 따로 들고 다니지 않는다
// 탭 목록(모일 곳·일정)은 그 탭을 열 때 불러온다 → 첫 화면이 가볍다
export const useFamilyStore = defineStore('family', () => {
  const room = ref(null) // { id, name, inviteCode, members: [{ name, me }] } 또는 null (방 없음)
  const places = ref([]) // [{ id, name, description, lon, lat, createdByName }]
  const schedules = ref([]) // [{ id, title, startAt, memo, createdByName }] 시간 순
  const locations = ref([]) // [{ id: 회원 id, name, lat, lng, me, updatedAt }] 위치 공유에 동의한 사람만

  // 방이 바뀌면(만들기·들어가기·나가기) 이전 방의 목록이 남지 않게 비운다
  function clearLists() {
    places.value = []
    schedules.value = []
    locations.value = []
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

  // 가족 위치 (family_location 테이블). 공유에 동의한 사람만 목록에 있다 → 목록에 "나"(me)가 있으면 동의한 것
  async function fetchLocations() {
    locations.value = await api('/api/family/locations')
  }
  // 내 위치 저장. { lat, lng } 는 브라우저 GPS 값 — 지금은 화면에서 부르지 않는다 (DB 좌표만 씀, 2026-11-19)
  async function saveMyLocation({ lat, lng }) {
    await api('/api/family/locations/me', { method: 'put', body: { lat, lng } })
  }
  // 위치 공유 끄기: 서버에서 내 줄을 지우고, 화면 목록에서도 나를 뺀다
  async function deleteMyLocation() {
    await api('/api/family/locations/me', { method: 'delete' })
    locations.value = locations.value.filter((l) => !l.me)
  }

  return {
    room, places, schedules,
    fetchRoom, createRoom, join, leave,
    fetchPlaces, addPlace, deletePlace,
    fetchSchedules, addSchedule, deleteSchedule,
    locations, fetchLocations, saveMyLocation, deleteMyLocation,
  }
})
