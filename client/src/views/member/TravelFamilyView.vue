<script setup>
// 내 정보 · 함께 가는 가족 (설계: Family.dc.html)
// 따로 저장하지 않고 가족방의 아이 건강 응급 카드(child_card)를 같이 쓴다 → 여기서 추가·삭제하면 가족방에도 바로 보인다
// 설계는 "태어난 해만"이지만 아이 카드는 이름(필수)·생년월일(선택)을 저장하므로 그 두 가지를 받는다
// 아이 카드는 가족방이 있어야 저장되므로, 방이 없으면 가족방 만들기로 안내한다
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'

const auth = useAuthStore()
const family = useFamilyStore()

const loaded = ref(false) // 방 유무를 확인하기 전에 "가족방 없음"이 잠깐 보이지 않게
const error = ref('')
const adding = ref(false)
const form = ref({ name: '', birthDate: '' })
const formError = ref('')
const today = new Date().toISOString().slice(0, 10) // 생년월일 입력칸 최대값

onMounted(async () => {
  if (!auth.isLoggedIn) return // 회원 전용: 로그인 안내만 보여 준다
  try {
    await family.fetchRoom()
    if (family.room) await family.fetchChildren()
  } catch (e) {
    error.value = e.message
  } finally {
    loaded.value = true
  }
})

// 만 나이 (생일이 지났는지까지 따짐). 생년월일이 없으면 null
function age(birthDate) {
  if (!birthDate) return null
  const b = new Date(birthDate)
  const now = new Date()
  let a = now.getFullYear() - b.getFullYear()
  if (now.getMonth() < b.getMonth() || (now.getMonth() === b.getMonth() && now.getDate() < b.getDate())) a--
  return Math.max(0, a)
}

// 연령대와 안내 문구는 설계 파일(Family.dc.html) 그대로. 나이 경계(3·7·12세)도 설계 값
function info(birthDate) {
  const a = age(birthDate)
  if (a === null) return { band: '나이 모름', age: '', effect: '생년월일을 넣으면 나이에 맞는 안내가 보입니다.' }
  const ageText = `만 ${a}세`
  if (a <= 3) return { band: '영유아', age: ageText, effect: '응급실 · 소아 진료 거리 비중을 가장 높게\n화로 · 난로와 거리 두기, 유아용 침낭 · 해열제 체크\n밤에 아이 체온 확인 알림' }
  if (a <= 7) return { band: '미취학', age: ageText, effect: '응급실 거리 비중을 높게\n화로 화상 · 물가 안전 수칙 먼저 안내\n밤 취침 전 난방 점검 알림' }
  if (a <= 12) return { band: '초등', age: ageText, effect: '물놀이 구명조끼 · 야간 혼자 이동 금지 수칙\n관광지에서 연령 제한 활동 표시' }
  return { band: '청소년', age: ageText, effect: '어른과 같은 기준으로 안내해요' }
}

function openAdd() {
  form.value = { name: '', birthDate: '' }
  formError.value = ''
  adding.value = true
}

async function add() {
  if (!form.value.name.trim()) {
    formError.value = '아이 이름을 입력해 주세요.'
    return
  }
  try {
    // 생년월일을 비워 두면 null 로 보낸다 (빈 글자는 서버가 날짜로 읽지 못함). 나머지 칸은 가족방에서 넣는다
    await family.addChild({ name: form.value.name.trim(), birthDate: form.value.birthDate || null })
    adding.value = false
  } catch (e) {
    formError.value = e.message
  }
}

async function remove(child) {
  if (!window.confirm(`${child.name}을(를) 뺄까요? 가족방의 응급 카드도 같이 지워집니다.`)) return
  try {
    await family.deleteChild(child.id)
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="travel-family">
    <div class="head">
      <RouterLink to="/my-info" aria-label="내 정보로 돌아가기" class="back">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </RouterLink>
      <h1>함께 가는 가족</h1>
    </div>

    <div class="body">
      <div v-if="!auth.isLoggedIn" class="empty">
        <p>함께 가는 가족은 로그인하면 넣을 수 있습니다.</p>
        <RouterLink to="/login" class="primary">로그인</RouterLink>
      </div>

      <template v-else-if="loaded">
        <p v-if="error" class="warn" role="alert">{{ error }}</p>

        <div v-if="!family.room" class="empty">
          <p>가족방을 만들면 아이를 추가할 수 있습니다.<br />아이 정보는 가족방 사람들과 함께 봅니다.</p>
          <RouterLink to="/my-camping/family" class="primary">가족방 만들기</RouterLink>
        </div>

        <template v-else>
          <span class="lead">아이 나이에 맞는 안전 안내를 보여 드립니다. 가족방의 아이 카드와 같은 정보입니다.</span>

          <div v-for="(c, i) in family.children" :key="c.id" class="card">
            <div class="card-head">
              <b>{{ c.name || `아이 ${i + 1}` }}</b>
              <button type="button" class="remove" @click="remove(c)">삭제</button>
            </div>
            <div class="band">
              <span class="chip">{{ info(c.birthDate).band }}</span>
              <span class="age">{{ info(c.birthDate).age }}</span>
            </div>
            <div class="effect">
              <b>이렇게 바뀌어요</b>
              <span>{{ info(c.birthDate).effect }}</span>
            </div>
          </div>

          <form v-if="adding" class="card" @submit.prevent="add">
            <b>아이 추가</b>
            <label>
              <span>이름</span>
              <input v-model="form.name" maxlength="20" placeholder="아이 이름" />
            </label>
            <label>
              <span>생년월일</span>
              <input v-model="form.birthDate" type="date" :max="today" aria-label="생년월일 (선택)" />
            </label>
            <p v-if="formError" class="warn" role="alert">{{ formError }}</p>
            <div class="form-actions">
              <button type="button" class="cancel" @click="adding = false">취소</button>
              <button type="submit" class="save">추가</button>
            </div>
          </form>
          <button v-else type="button" class="add" @click="openAdd">+ 아이 추가</button>

          <div class="note">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <rect x="5" y="11" width="14" height="10" rx="2" />
              <path d="M8 11V8a4 4 0 0 1 8 0v3" />
            </svg>
            <span>
              같은 가족방 사람만 볼 수 있습니다. 알레르기 · 혈액형 같은 응급 정보는
              <RouterLink to="/my-camping/family" class="link">가족방</RouterLink>에서 넣을 수 있습니다.
            </span>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.travel-family {
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
/* 설계의 "삭제" 알약 버튼 */
.remove {
  height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 16px;
  background: var(--color-fill-soft);
  font-size: 13px;
  color: var(--color-text-mid);
  cursor: pointer;
}
.band {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chip {
  height: 26px;
  padding: 0 10px;
  border-radius: 13px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
}
.age {
  font-size: 13px;
  color: var(--color-text-sub);
}
.effect {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-mid);
  line-height: 1.5;
}
.effect b {
  font-size: 12px;
  color: var(--color-text-sub);
}
.effect span {
  white-space: pre-line;
}
.card label {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.card label > span {
  width: 64px;
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
  font-family: inherit;
  color: var(--color-text);
  outline: none;
}
input:focus {
  border-color: var(--color-primary);
}
.form-actions {
  display: flex;
  gap: 8px;
}
.form-actions button {
  flex: 1;
  height: 46px;
  border-radius: var(--radius-input);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.cancel {
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
}
.save {
  border: 0;
  background: var(--color-text);
  color: var(--color-card);
}
/* 설계의 "+ 아이 추가" 점선 버튼 */
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
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-mid);
  line-height: 1.5;
}
.note svg {
  flex: none;
  margin-top: 2px;
}
.link {
  text-decoration: underline;
}
.warn {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
  text-align: center;
  line-height: 1.5;
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
