<script setup>
// ①-4 회원가입 (설계: docs/design/Signup.dc.html)
// 주소 검색은 아직 연결하지 않음 → 주소를 직접 입력한다
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/utils/api'

const router = useRouter()

const form = reactive({
  loginId: '',
  password: '',
  passwordConfirm: '',
  name: '',
  phone: '',
  emergencyPhone: '',
  address: '',
  addressDetail: '',
})

// 중복 확인: 어떤 아이디로 확인했는지 기억해 두고, 아이디를 바꾸면 다시 확인하게 한다
const checkedId = ref('')
const idMessage = ref('')
const idOk = ref(false)
const error = ref('')
const loading = ref(false)

watch(() => form.loginId, (value) => {
  if (value !== checkedId.value) {
    idOk.value = false
    idMessage.value = ''
  }
})

async function checkId() {
  if (!/^[A-Za-z0-9]{4,16}$/.test(form.loginId)) {
    idOk.value = false
    idMessage.value = '아이디는 영문·숫자 4~16자입니다.'
    return
  }
  try {
    const { available } = await api('/api/auth/check-id', { params: { loginId: form.loginId } })
    checkedId.value = form.loginId
    idOk.value = available
    idMessage.value = available ? '사용할 수 있는 아이디입니다.' : '이미 사용 중인 아이디입니다.'
  } catch (e) {
    idOk.value = false
    idMessage.value = e.message
  }
}

function addressSearch() {
  // TODO: 주소 검색 서비스 연결 (지금은 직접 입력)
  document.getElementById('su-addr')?.focus()
}

async function submit() {
  error.value = ''
  // 서버에서도 같은 검사를 하지만, 자주 틀리는 것은 미리 알려 준다
  if (!idOk.value || checkedId.value !== form.loginId) {
    error.value = '아이디 중복 확인을 해 주세요.'
    return
  }
  if (form.password !== form.passwordConfirm) {
    error.value = '비밀번호가 서로 다릅니다.'
    return
  }
  loading.value = true
  try {
    await api('/api/auth/signup', { method: 'post', body: form })
    router.push({ path: '/login', query: { joined: 1 } })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="signup">
    <div class="head">
      <RouterLink to="/login" aria-label="로그인으로 돌아가기" class="back">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </RouterLink>
      <h1>회원가입</h1>
    </div>

    <form id="signup-form" class="body" @submit.prevent="submit">
      <div class="group">
        <label for="su-id">아이디</label>
        <div class="row">
          <input id="su-id" v-model.trim="form.loginId" placeholder="영문 · 숫자 4~16자" autocomplete="username" />
          <button type="button" class="side" @click="checkId">중복 확인</button>
        </div>
        <span v-if="idMessage" :class="idOk ? 'ok' : 'warn'">{{ idMessage }}</span>
      </div>

      <div class="group">
        <label for="su-pw">비밀번호</label>
        <input id="su-pw" v-model="form.password" type="password" placeholder="비밀번호" autocomplete="new-password" />
        <input id="su-pw2" v-model="form.passwordConfirm" type="password" aria-label="비밀번호 다시 입력"
          placeholder="비밀번호를 한 번 더 입력해 주세요" autocomplete="new-password" />
      </div>

      <div class="group">
        <label for="su-name">이름</label>
        <input id="su-name" v-model.trim="form.name" placeholder="이름" autocomplete="name" />
      </div>

      <div class="group">
        <label for="su-tel">연락처</label>
        <input id="su-tel" v-model.trim="form.phone" type="tel" placeholder="010 0000 0000" autocomplete="tel" />
      </div>

      <div class="group sos">
        <label for="su-sos">비상연락처</label>
        <span class="hint">위급할 때 내 위치와 일정을 받을 가족 번호입니다</span>
        <input id="su-sos" v-model.trim="form.emergencyPhone" type="tel" placeholder="010 0000 0000" />
      </div>

      <div class="group">
        <label for="su-addr">주소</label>
        <div class="row">
          <input id="su-addr" v-model.trim="form.address" placeholder="주소를 검색해 주세요" />
          <button type="button" class="side" @click="addressSearch">주소 검색</button>
        </div>
        <input id="su-addr2" v-model.trim="form.addressDetail" aria-label="상세 주소" placeholder="상세 주소" />
      </div>
    </form>

    <div class="foot">
      <p v-if="error" class="warn" role="alert">{{ error }}</p>
      <button type="submit" form="signup-form" class="primary" :disabled="loading">가입하기</button>
    </div>
  </div>
</template>

<style scoped>
.signup {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}
.back {
  width: 44px;
  height: 44px;
  margin-left: -10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 20px 24px;
}
.group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.group label {
  font-size: 13px;
  font-weight: 700;
}
.row {
  display: flex;
  gap: 8px;
}
.row input {
  flex: 1;
  min-width: 0;
}
input {
  height: 50px;
  padding: 0 14px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font-size: 15px;
  outline: none;
}
input:focus {
  border-color: var(--color-primary);
}
.side {
  flex: none;
  height: 50px;
  padding: 0 14px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-text);
  background: var(--color-card);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.sos {
  padding: 14px;
  border-radius: 14px;
  background: var(--color-bg-soft);
}
.hint {
  font-size: 12px;
  color: var(--color-text-sub);
}
.ok,
.warn {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}
.ok {
  color: var(--color-green);
}
.foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 20px 24px;
  border-top: 1px solid var(--color-border);
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
</style>
