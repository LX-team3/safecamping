<script setup>
// 웹 회원가입 (설계 1-4): 동작은 모바일 회원가입과 같다 (아이디 중복 확인 → 가입 API)
// 주소 검색은 아직 연결하지 않음 → 주소를 직접 입력한다
import { reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '@/utils/api'
import '@/styles/tokens-web.css'

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
    idMessage.value = available ? '✓ 사용할 수 있는 아이디예요' : '이미 사용 중인 아이디입니다.'
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
    router.push({ path: '/web/login', query: { joined: 1 } })
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
      <div class="head">
        <RouterLink to="/web/login" class="back" aria-label="로그인으로 돌아가기">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
        </RouterLink>
        <h1>회원가입</h1>
      </div>

      <form @submit.prevent="submit">
        <div class="group">
          <label for="su-id">아이디</label>
          <div class="inline">
            <input id="su-id" v-model.trim="form.loginId" type="text" placeholder="영문 · 숫자 4~16자" autocomplete="username" />
            <button type="button" class="side" @click="checkId">중복 확인</button>
          </div>
          <span v-if="idMessage" :class="idOk ? 'ok' : 'warn'">{{ idMessage }}</span>
        </div>

        <div class="group">
          <label for="su-pw">비밀번호</label>
          <input id="su-pw" v-model="form.password" type="password" placeholder="비밀번호" autocomplete="new-password" />
        </div>
        <div class="group">
          <label for="su-pw2">비밀번호 재확인</label>
          <input id="su-pw2" v-model="form.passwordConfirm" type="password" placeholder="비밀번호를 한 번 더 입력해 주세요" autocomplete="new-password" />
        </div>

        <div class="two">
          <div class="group">
            <label for="su-name">사용자 이름</label>
            <input id="su-name" v-model.trim="form.name" type="text" placeholder="이름" autocomplete="name" />
          </div>
          <div class="group">
            <label for="su-tel">전화번호</label>
            <input id="su-tel" v-model.trim="form.phone" type="tel" placeholder="010 0000 0000" autocomplete="tel" />
          </div>
        </div>

        <div class="group sos">
          <label for="su-sos">보호자 전화번호 <span class="opt">(선택)</span></label>
          <span class="hint">위급할 때 연락하기 위해서만 사용해요. 나중에 내 정보에서 넣어도 돼요.</span>
          <input id="su-sos" v-model.trim="form.emergencyPhone" type="tel" placeholder="010 0000 0000" />
        </div>

        <div class="group">
          <label for="su-addr">주소</label>
          <div class="inline">
            <input id="su-addr" v-model.trim="form.address" type="text" placeholder="주소를 검색해 주세요" />
            <button type="button" class="side" @click="addressSearch">주소 검색</button>
          </div>
          <input id="su-addr2" v-model.trim="form.addressDetail" type="text" aria-label="상세 주소" placeholder="상세 주소" />
        </div>

        <p v-if="error" class="warn" role="alert">{{ error }}</p>
        <button type="submit" class="submit" :disabled="loading">가입하기</button>
      </form>

      <p class="foot">이미 회원이세요? <RouterLink to="/web/login" class="login">로그인</RouterLink></p>
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
  max-width: 560px;
  background: var(--web-bg);
  border: 1px solid var(--web-line-input);
  border-radius: 16px;
  padding: 36px 40px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.back {
  width: 40px;
  height: 40px;
  margin-left: -10px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--web-text);
}
h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
form {
  display: flex;
  flex-direction: column;
  gap: 22px;
  margin: 0;
}
.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.group label {
  font-size: 14px;
  font-weight: 500;
}
.opt {
  font-weight: 400;
  color: var(--web-text-sub);
}
.inline {
  display: flex;
  gap: 8px;
}
.two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
input {
  height: 50px;
  padding: 0 16px;
  border: 1px solid var(--web-line-input);
  border-radius: 12px;
  font-family: inherit;
  font-size: 15px;
  color: var(--web-text);
  background: var(--web-bg);
  outline-color: var(--web-green);
  min-width: 0;
}
.inline input {
  flex: 1;
}
input::placeholder {
  color: var(--web-placeholder);
}
.side {
  flex: 0 0 auto;
  height: 50px;
  padding: 0 18px;
  border: 1px solid var(--web-green);
  border-radius: 12px;
  background: var(--web-bg);
  color: var(--web-green);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.ok {
  font-size: 13px;
  color: var(--web-green);
}
.warn {
  margin: 0;
  font-size: 13px;
  color: var(--web-terracotta);
}
.sos {
  padding: 18px;
  border-radius: 12px;
  background: var(--web-bg-field);
}
.hint {
  font-size: 13px;
  line-height: 1.5;
  color: var(--web-text-sub);
}
.submit {
  margin-top: 6px;
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
.foot {
  margin: 0;
  text-align: center;
  font-size: 14px;
  color: var(--web-text-sub);
}
.login {
  margin-left: 4px;
  font-weight: 700;
  color: var(--web-green);
}
</style>
