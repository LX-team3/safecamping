import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/utils/api'

// 로그인 상태. 진짜 로그인 정보는 서버 세션에 있고, 여기에는 화면 표시용 회원 정보만 둔다
export const useAuthStore = defineStore('auth', () => {
  const member = ref(null) // { id, loginId, name } 또는 null
  const isLoggedIn = computed(() => member.value !== null)

  // 새로고침해도 세션(또는 로그인 상태 유지 쿠키)이 살아 있으면 다시 불러온다
  async function fetchMe() {
    try {
      member.value = await api('/api/auth/me')
    } catch {
      member.value = null // 401: 로그인 안 함
    }
  }

  async function login(loginId, password, keep) {
    member.value = await api('/api/auth/login', { method: 'post', body: { loginId, password, keep } })
  }

  async function logout() {
    await api('/api/auth/logout', { method: 'post' })
    member.value = null
  }

  return { member, isLoggedIn, fetchMe, login, logout }
})
