import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 저장 목록. 서버(saved_list 테이블)가 정본이고, 여기에는 화면에 그릴 목록만 둔다
// 하트(관심 캠핑장)는 stores/favorite.js 가 '관심 캠핑장' 기본 목록에 담고 뺀다
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
