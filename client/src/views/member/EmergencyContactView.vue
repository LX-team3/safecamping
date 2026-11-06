<script setup>
// 내 정보 · 비상연락처 (최대 5명). 설계 파일에 이 화면이 없어 '함께 가는 가족'(Family.dc.html) 모양을 따름
// "+ 추가하기"로 칸을 늘리고, 칸 오른쪽 위 X 로 뺀다. 바꾼 내용은 "저장하기"를 눌러야 서버에 반영된다
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/utils/api'

const auth = useAuthStore()
const URL = '/api/member/emergency-contacts'
const MAX = 5 // 서버(EmergencyContactService.MAX)와 같은 값

// 칸마다 key 를 따로 둔다 → 가운데 칸을 지워도 Vue 가 다른 칸의 입력값을 섞지 않는다
let nextKey = 0
const contacts = ref([])
const error = ref('')
const message = ref('')
const loading = ref(false)

function fill(list) {
  contacts.value = list.map((c) => ({ key: nextKey++, name: c.name ?? '', phone: c.phone ?? '' }))
}

onMounted(async () => {
  if (!auth.isLoggedIn) return // 회원 전용: 로그인 안내만 보여 준다
  try {
    fill(await api(URL))
  } catch (e) {
    error.value = e.message
  }
})

function add() {
  if (contacts.value.length >= MAX) return
  contacts.value.push({ key: nextKey++, name: '', phone: '' })
  message.value = ''
}

function remove(i) {
  contacts.value.splice(i, 1)
  message.value = ''
}

async function save() {
  error.value = ''
  message.value = ''
  loading.value = true
  try {
    const body = { contacts: contacts.value.map(({ name, phone }) => ({ name, phone })) }
    fill(await api(URL, { method: 'put', body }))
    message.value = '저장했습니다.'
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="emergency">
    <div class="head">
      <RouterLink to="/my-info" aria-label="내 정보로 돌아가기" class="back">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </RouterLink>
      <h1>비상연락처</h1>
    </div>

    <div v-if="!auth.isLoggedIn" class="empty">
      <p>비상연락처는 로그인하면 넣을 수 있습니다.</p>
      <RouterLink to="/login" class="primary">로그인</RouterLink>
    </div>

    <template v-else>
      <form id="emergency-form" class="body" @submit.prevent="save">
        <span class="lead">위급할 때 내 위치와 일정을 받을 가족입니다. {{ MAX }}명까지 넣을 수 있습니다.</span>

        <div v-for="(c, i) in contacts" :key="c.key" class="card">
          <div class="card-head">
            <b>비상연락처 {{ i + 1 }}</b>
            <button type="button" class="remove" :aria-label="`비상연락처 ${i + 1} 빼기`" @click="remove(i)">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                stroke-linecap="round" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>
          <label>
            <span>이름</span>
            <input v-model.trim="c.name" maxlength="20" placeholder="예: 엄마" :aria-label="`비상연락처 ${i + 1} 이름`" />
          </label>
          <label>
            <span>번호</span>
            <input v-model.trim="c.phone" type="tel" placeholder="010 0000 0000" :aria-label="`비상연락처 ${i + 1} 번호`" />
          </label>
        </div>

        <button v-if="contacts.length < MAX" type="button" class="add" @click="add">+ 추가하기</button>
        <span v-else class="note">{{ MAX }}명까지 넣을 수 있습니다.</span>

        <span class="note">추가하거나 뺀 내용은 저장하기를 눌러야 반영됩니다.</span>
      </form>

      <div class="foot">
        <p v-if="error" class="warn" role="alert">{{ error }}</p>
        <p v-else-if="message" class="ok" role="status">{{ message }}</p>
        <button type="submit" form="emergency-form" class="save" :disabled="loading">저장하기</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.emergency {
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
.lead {
  font-size: 14px;
  color: var(--color-text-mid);
  line-height: 1.5;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--color-divider);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-head b {
  flex: 1;
  font-size: 16px;
}
/* 오른쪽 위 X. 누르기 쉽도록 32px 원 (설계 Family 의 삭제 버튼 높이·색과 같음) */
.remove {
  width: 32px;
  height: 32px;
  margin: -4px -4px 0 0;
  border: 0;
  border-radius: 16px;
  background: var(--color-fill-soft);
  color: var(--color-text-mid);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.card label {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.card label > span {
  width: 40px;
  color: var(--color-text-sub);
}
input {
  flex: 1;
  min-width: 0;
  height: 46px;
  padding: 0 12px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font-size: 15px;
  outline: none;
}
input:focus {
  border-color: var(--color-primary);
}
/* 설계 Family 의 "+ 아이 추가" 버튼 모양 */
.add {
  height: 50px;
  border-radius: 14px;
  border: 1.5px dashed var(--color-arrow);
  background: var(--color-card);
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  cursor: pointer;
}
.note {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-mid);
}
.foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 20px 24px;
  border-top: 1px solid var(--color-divider);
}
.save {
  height: 54px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.save:disabled {
  opacity: 0.6;
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
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 20px 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.primary {
  height: 48px;
  padding: 0 20px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  display: inline-flex;
  align-items: center;
  font-size: 15px;
  font-weight: 700;
}
</style>
