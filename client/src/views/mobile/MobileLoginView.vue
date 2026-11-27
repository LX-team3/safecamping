<script setup>
// ①-3 로그인 (설계: docs/design/Login.dc.html)
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { goUp } from '@/utils/backStack'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loginId = ref('')
const password = ref('')
const keep = ref(true) // 설계에서 기본 체크
const error = ref('')
const loading = ref(false)

// 웹 ⇄ 앱 전환 (시연용): 로그아웃 상태의 이 화면 오른쪽 위에서 웹 로그인 화면으로
// 휴대폰처럼 좁은 화면에서는 /web 주소가 다시 앱으로 넘어오므로(router/index.js) PC 처럼 넓을 때만 보인다
const canSwitchWeb = window.matchMedia('(min-width: 768px)').matches

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
    goUp(router, next || '/app/explore') // 로그인 후에는 메인으로
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <RouterLink v-if="canSwitchWeb" to="/web/login" class="to-web" aria-label="같은 화면을 웹(PC)으로 보기" title="웹 화면으로 보기">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 20h8M12 16v4" /></svg>
    </RouterLink>
    <div class="brand">
      <span class="logo">
        <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 20L12 4l9 16z" />
          <path d="M12 20v-6" />
        </svg>
      </span>
      <b class="name">campine</b>
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
      <div class="join">처음이세요?<RouterLink replace to="/app/signup">회원가입</RouterLink></div>
    </form>

    <RouterLink to="/app/explore" class="guest">로그인 없이 둘러보기</RouterLink>
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
.to-web {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
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
  background: var(--color-primary);
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
