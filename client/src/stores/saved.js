import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 저장 목록. 서버(saved_list 테이블)가 정본이고, 여기에는 화면에 그릴 목록만 둔다
// 나중에 캠핑장 상세 화면의 하트 버튼도 이 store 의 addCamp 를 쓴다
export const useSavedStore = defineStore('saved', () => {
  const lists = ref([]) // [{ id, name, isDefault, campCount }]

  async function fetchLists() {
    lists.value = await api('/api/saved/lists')
  }

  async function createList(name) {
    const list = await api('/api/saved/lists', { method: 'post', body: { name } })
    lists.value.push(list)
  }

  async function addCamp(listId, campId) {
    await api(`/api/saved/lists/${listId}/camps/${campId}`, { method: 'post' })
  }

  async function removeCamp(listId, campId) {
    await api(`/api/saved/lists/${listId}/camps/${campId}`, { method: 'delete' })
  }

  return { lists, fetchLists, createList, addCamp, removeCamp }
})

// 서버 CampDummyData 의 더미 캠핑장 id. 캠핑장 상세 화면이 없어 비교 화면에서 시험용으로 담을 때만 쓴다
export const DUMMY_CAMP_IDS = ['c1', 'c2', 'c3']
