import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'
import { useAuthStore } from '@/stores/auth'

// 하트(관심). 키는 캠핑장 id(숫자) 또는 관광지 'tour:3' 같은 글자
// - 캠핑장 + 로그인: 서버의 '관심 캠핑장' 기본 목록(saved_camp)에 담고 뺀다 → 저장 탭·비교 화면에 그대로 나온다
// - 그 밖(로그인 안 함, 관광지): 이 브라우저 탭(sessionStorage)에만 둔다
// 로그인하면 로그인 전에 누른 캠핑장 하트를 서버 목록으로 옮긴다
const KEY = 'campin.favorites'

const isCamp = (key) => typeof key === 'number'

function readLocal() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY)) || []
  } catch {
    return []
  }
}
function writeLocal(keys) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(keys))
  } catch {
    /* 저장 못 해도 이번 화면에서는 그대로 쓴다 */
  }
}

export const useFavoriteStore = defineStore('favorite', () => {
  const auth = useAuthStore()
  const ids = ref(readLocal())
  let listId = null // 서버 '관심 캠핑장' 목록 id (로그인했을 때만)
  let syncing = Promise.resolve() // 서버에서 하트를 불러오는 중이면 끝날 때까지 기다렸다가 누른다

  // 로그인했으면 캠핑장 하트는 서버가 정본이라 브라우저에는 관광지 하트만 남긴다
  watch(ids, (v) => writeLocal(auth.isLoggedIn ? v.filter((k) => !isCamp(k)) : v), { deep: true })

  async function syncWithServer() {
    const fav = await api('/api/saved/favorites')
    listId = fav.listId
    const server = fav.campIds.filter((id) => /^\d+$/.test(id)).map(Number)
    const local = readLocal()
    // 로그인 전에 누른 캠핑장 하트를 서버로 옮긴다 (없는 캠핑장이면 건너뛴다)
    for (const id of local.filter((k) => isCamp(k) && !server.includes(k))) {
      try {
        await api(`/api/saved/lists/${listId}/camps/${id}`, { method: 'post' })
        server.push(id)
      } catch {
        /* 옮기지 못한 하트는 버린다 */
      }
    }
    ids.value = [...server, ...local.filter((k) => !isCamp(k))]
  }

  // 로그인·로그아웃이 바뀔 때마다 하트를 다시 맞춘다
  watch(
    () => auth.isLoggedIn,
    (loggedIn) => {
      if (!loggedIn) {
        listId = null
        ids.value = readLocal()
        return
      }
      syncing = syncWithServer().catch(() => {
        listId = null // 못 불러오면 이번에는 브라우저에만 둔다
      })
    },
    { immediate: true },
  )

  function has(id) {
    return ids.value.includes(id)
  }

  async function toggle(id) {
    await syncing
    const on = !has(id)
    ids.value = on ? [...ids.value, id] : ids.value.filter((x) => x !== id)
    if (!isCamp(id) || !auth.isLoggedIn || listId == null) return
    try {
      await api(`/api/saved/lists/${listId}/camps/${id}`, { method: on ? 'post' : 'delete' })
    } catch {
      // 서버에 반영하지 못하면 하트를 원래대로 돌린다
      ids.value = on ? ids.value.filter((x) => x !== id) : [...ids.value, id]
    }
  }

  return { ids, has, toggle }
})
