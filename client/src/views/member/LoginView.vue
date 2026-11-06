<script setup>
// ①-3 로그인 (설계: docs/design/Login.dc.html)
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loginId = ref('')
const password = ref('')
const keep = ref(true) // 설계에서 기본 체크
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
    router.push('/') // 로그인 후에는 메인으로
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="brand">
      <span class="logo">
        <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 20L12 4l9 16z" />
          <path d="M12 20v-6" />
        </svg>
      </span>
      <b class="name">캠핑메이트</b>
      <span class="sub">내 캠핑메이트</span>
    </div>

    <p v-if="route.query.joined" class="notice">가입되었습니다. 로그인해 주세요.</p>

    <form class="fields" @submit.prevent="submit">
      <label class="field">아이디
        <input v-model.trim="loginId" placeholder="아이디" autocomplete="username" />
      </label>
      <label class="field">비밀번호
        <input v-model="password" type="password" placeholder="비밀번호" autocomplete="current-password" />
      </label>
      <label class="keep">
        <input v-model="keep" type="checkbox" />로그인 상태 유지
      </label>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <button class="primary" type="submit" :disabled="loading">로그인</button>
      <div class="join">처음이세요?<RouterLink to="/signup">회원가입</RouterLink></div>
    </form>

    <RouterLink to="/" class="guest">로그인 없이 둘러보기</RouterLink>
  </div>
</template>

<style scoped>
.login {
  position: relative;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 96px 24px 96px;
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.logo {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: var(--color-primary);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.name {
  font-size: 26px;
  letter-spacing: -0.02em;
}
.sub {
  font-size: 14px;
  color: var(--color-text-sub);
}
.notice {
  margin: 0;
  text-align: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-green);
}
.fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
}
.field input {
  height: 52px;
  padding: 0 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font-size: 15px;
  font-weight: 400;
  outline: none;
}
.field input:focus {
  border-color: var(--color-primary);
}
.keep {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  font-size: 14px;
}
.keep input {
  width: 20px;
  height: 20px;
  margin: 0;
  accent-color: var(--color-text);
}
.error {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
.primary {
  height: 54px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.primary:disabled {
  opacity: 0.6;
}
.join {
  display: flex;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  color: var(--color-text-sub);
}
.join a {
  color: var(--color-text);
  font-weight: 700;
  text-decoration: underline;
}
.guest {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 36px;
  text-align: center;
  font-size: 14px;
  text-decoration: underline;
}
</style>
