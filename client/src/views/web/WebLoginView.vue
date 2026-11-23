<script setup>
// 웹 로그인 (설계 1-3): 헤더·하단 없이 가운데 카드. 동작은 모바일 로그인과 같다 (서버 로그인 API)
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import '@/styles/tokens-web.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loginId = ref('')
const password = ref('')
const keep = ref(false)
const error = ref('')
const loading = ref(false)

async function submit() {
  if (!loginId.value || !password.value) {
    error.value = '아이디와 비밀번호를 입력해 주세요.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.login(loginId.value, password.value, keep.value)
    // next: 로그인이 필요해서 보낸 화면이 있으면 그리로 (예약하셨나요? → 예약 정보 확인). 우리 사이트 주소만 받는다
    const next = typeof route.query.next === 'string' && route.query.next.startsWith('/') && !route.query.next.startsWith('//') ? route.query.next : ''
    router.push(next || '/web') // 로그인 후에는 웹 메인으로
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <main class="card">
      <div class="brand">
        <h1><img src="/web/logo.png" alt="CAMPINE" /></h1>
        <p>로그인하고 관심 캠핑장을 저장해 보세요</p>
      </div>

      <p v-if="route.query.joined" class="notice">가입되었습니다. 로그인해 주세요.</p>

      <form @submit.prevent="submit">
        <label for="lg-id">아이디
          <input id="lg-id" v-model.trim="loginId" type="text" placeholder="아이디" autocomplete="username" />
        </label>
        <label for="lg-pw">비밀번호
          <input id="lg-pw" v-model="password" type="password" placeholder="비밀번호" autocomplete="current-password" />
        </label>
        <label for="lg-keep" class="keep">
          <input id="lg-keep" v-model="keep" type="checkbox" />로그인 상태 유지
        </label>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button type="submit" class="submit" :disabled="loading">로그인</button>
      </form>

      <div class="below">
        <p>처음이세요? <RouterLink to="/web/signup" class="join">회원가입</RouterLink></p>
        <div class="rule"></div>
        <RouterLink to="/web" class="guest">로그인 없이 둘러보기</RouterLink>
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  width: 100%;
  min-height: 100dvh;
  box-sizing: border-box;
  padding: 64px 24px;
  background: var(--web-bg-page);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  word-break: keep-all;
  overflow-wrap: break-word;
}
.card {
  width: 100%;
  max-width: 440px;
  background: var(--web-bg);
  border: 1px solid var(--web-line-input);
  border-radius: 16px;
  padding: 44px 40px 36px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.brand h1 {
  margin: 0;
}
.brand img {
  height: 44px;
  width: auto;
  display: block;
}
.brand p {
  margin: 0;
  font-size: 14px;
  color: var(--web-text-sub);
}
.notice {
  margin: -12px 0 0;
  text-align: center;
  font-size: 14px;
  color: var(--web-green);
}
form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 0;
}
label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}
input[type='text'],
input[type='password'] {
  height: 50px;
  padding: 0 16px;
  border: 1px solid var(--web-line-input);
  border-radius: 12px;
  font-family: inherit;
  font-size: 15px;
  color: var(--web-text);
  background: var(--web-bg);
  outline-color: var(--web-green);
}
input::placeholder {
  color: var(--web-placeholder);
}
.keep {
  flex-direction: row;
  align-items: center;
  font-weight: 400;
  color: var(--web-text-sub);
  cursor: pointer;
}
.keep input {
  width: 18px;
  height: 18px;
  margin: 0;
  accent-color: var(--web-green);
}
.error {
  margin: 0;
  font-size: 13px;
  color: var(--web-terracotta);
}
.submit {
  margin-top: 4px;
  height: 52px;
  border: 0;
  border-radius: 12px;
  background: var(--web-green);
  color: var(--web-white);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.submit:disabled {
  opacity: 0.6;
  cursor: default;
}
.below {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
}
.below p {
  margin: 0;
  font-size: 14px;
  color: var(--web-text-sub);
}
.join {
  margin-left: 4px;
  font-weight: 700;
  color: var(--web-green);
}
.rule {
  width: 100%;
  height: 1px;
  background: var(--web-line-soft);
}
.guest {
  font-size: 14px;
  color: var(--web-text-sub);
  text-decoration: underline;
}
</style>
