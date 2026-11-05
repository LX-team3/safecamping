<script setup>
// 임시 메인: 서버 연결과 로그인 상태만 확인한다 (①-1 메인 화면을 만들면 바꾼다)
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const message = ref('연결 중...')

onMounted(async () => {
  try {
    const res = await axios.get('/api/test')
    message.value = res.data.message
  } catch (e) {
    message.value = '연결 실패: ' + e.message
  }
})
</script>

<template>
  <main class="home">
    <p>{{ message }}</p>
    <template v-if="auth.isLoggedIn">
      <p>{{ auth.member.name }}님, 로그인되었습니다.</p>
      <button type="button" @click="auth.logout()">로그아웃</button>
      <RouterLink to="/saved">저장</RouterLink>
      <RouterLink to="/my-camping">내 캠핑</RouterLink>
    </template>
    <RouterLink v-else to="/login">로그인</RouterLink>
  </main>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 24px;
}
a {
  text-decoration: underline;
}
</style>
