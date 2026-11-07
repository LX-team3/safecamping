import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

// 하트(관심 캠핑장). 지금은 이 브라우저에만 저장한다 (sessionStorage)
// TODO: 저장 탭·회원 저장(서버)을 만들면 로그인 회원은 서버에 저장
const KEY = 'campin.favorites'

function load() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY)) || []
  } catch {
    return []
  }
}

export const useFavoriteStore = defineStore('favorite', () => {
  const ids = ref(load()) // 캠핑장 id 목록

  watch(
    ids,
    (v) => {
      try {
        sessionStorage.setItem(KEY, JSON.stringify(v))
      } catch {
        /* 저장 못 해도 이번 화면에서는 그대로 쓴다 */
      }
    },
    { deep: true },
  )

  function has(id) {
    return ids.value.includes(id)
  }
  function toggle(id) {
    ids.value = has(id) ? ids.value.filter((x) => x !== id) : [...ids.value, id]
  }

  return { ids, has, toggle }
})
