<script setup>
// 가족방 · 아이 건강 응급 카드 탭. 위급할 때 구조대·병원에 화면을 그대로 보여 줄 수 있게 큰 글씨 카드로 그린다
// 이름 말고는 모두 선택. 비운 칸은 "기록 없음"으로 보여 준다 ("없음"이라고 단정하지 않기 위해)
import { ref, reactive, onMounted } from 'vue'
import { useFamilyStore } from '@/stores/family'
import AddButton from './AddButton.vue'

const family = useFamilyStore()

const BLOOD_TYPES = ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-', '모름']
const EMPTY = { name: '', birthDate: '', bloodType: '', allergy: '', disease: '', medicine: '', memo: '' }

const editingId = ref(null) // null: 닫힘, 'new': 새 카드, 숫자: 그 카드 수정
const form = reactive({ ...EMPTY })
const formError = ref('')
const error = ref('')

onMounted(async () => {
  try {
    await family.fetchChildren()
  } catch (e) {
    error.value = e.message
  }
})

function openNew() {
  if (editingId.value === 'new') {
    editingId.value = null
    return
  }
  Object.assign(form, EMPTY)
  formError.value = ''
  editingId.value = 'new'
}

function openEdit(card) {
  // 서버의 null 은 입력칸에서 빈 글자로 바꿔 넣는다
  Object.assign(form, Object.fromEntries(Object.keys(EMPTY).map((k) => [k, card[k] ?? ''])))
  formError.value = ''
  editingId.value = card.id
}

async function save() {
  if (!form.name.trim()) {
    formError.value = '아이 이름을 입력해 주세요.'
    return
  }
  // 생년월일을 비워 두면 null 로 보낸다 (빈 글자는 서버가 날짜로 읽지 못함)
  const body = { ...form, birthDate: form.birthDate || null }
  try {
    if (editingId.value === 'new') await family.addChild(body)
    else await family.updateChild(editingId.value, body)
    editingId.value = null
    formError.value = ''
  } catch (e) {
    formError.value = e.message
  }
}

async function remove(card) {
  if (!window.confirm(`${card.name}의 응급 카드를 지울까요?`)) return
  try {
    await family.deleteChild(card.id)
    if (editingId.value === card.id) editingId.value = null
  } catch (e) {
    error.value = e.message
  }
}

// 만 나이 (생일이 지났는지까지 따짐). 약 용량 확인 때 쓰므로 정확히 계산한다
function age(birthDate) {
  if (!birthDate) return ''
  const b = new Date(birthDate)
  const now = new Date()
  let a = now.getFullYear() - b.getFullYear()
  if (now.getMonth() < b.getMonth() || (now.getMonth() === b.getMonth() && now.getDate() < b.getDate())) a--
  return `만 ${a}세`
}

function updatedText(card) {
  const d = new Date(card.updatedAt)
  const when = isNaN(d) ? '' : d.toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })
  return `${card.updatedByName || '알 수 없음'} · ${when} 수정`
}

const today = new Date().toISOString().slice(0, 10) // 생년월일 입력칸 최대값
</script>

<template>
  <div class="tab-head">
    <p class="sub">위급할 때 이 화면을 구조대·병원에 보여 주세요.</p>
    <AddButton label="아이 카드 추가" @click="openNew" />
  </div>

  <form v-if="editingId !== null" class="stack-form" @submit.prevent="save">
    <input v-model="form.name" maxlength="20" placeholder="아이 이름" />
    <label class="field">
      <span>생년월일 (선택)</span>
      <input v-model="form.birthDate" type="date" :max="today" />
    </label>
    <label class="field">
      <span>혈액형 (선택)</span>
      <select v-model="form.bloodType">
        <option value="">선택 안 함</option>
        <option v-for="t in BLOOD_TYPES" :key="t" :value="t">{{ t }}</option>
      </select>
    </label>
    <input v-model="form.allergy" maxlength="200" placeholder="알레르기 (예: 땅콩, 페니실린)" />
    <input v-model="form.disease" maxlength="200" placeholder="지병 (예: 천식)" />
    <input v-model="form.medicine" maxlength="200" placeholder="먹고 있는 약" />
    <textarea v-model="form.memo" maxlength="300" placeholder="그 밖에 알려 줄 것 (예: 열성경련 이력)" />
    <button type="submit" class="primary">{{ editingId === 'new' ? '추가' : '저장' }}</button>
    <button type="button" class="text-btn" @click="editingId = null">취소</button>
    <p v-if="formError" class="error" role="alert">{{ formError }}</p>
  </form>

  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="!family.children.length && editingId === null" class="empty">아직 등록한 아이 카드가 없어요.</p>

  <article v-for="c in family.children" :key="c.id" class="card">
    <header>
      <b class="name">{{ c.name }}</b>
      <span class="meta">{{ age(c.birthDate) }}</span>
      <span v-if="c.bloodType" class="blood">{{ c.bloodType }}형</span>
    </header>
    <dl>
      <div><dt>알레르기</dt><dd :class="{ none: !c.allergy }">{{ c.allergy || '기록 없음' }}</dd></div>
      <div><dt>지병</dt><dd :class="{ none: !c.disease }">{{ c.disease || '기록 없음' }}</dd></div>
      <div><dt>복용 약</dt><dd :class="{ none: !c.medicine }">{{ c.medicine || '기록 없음' }}</dd></div>
      <div v-if="c.memo"><dt>메모</dt><dd>{{ c.memo }}</dd></div>
    </dl>
    <footer>
      <small>{{ updatedText(c) }}</small>
      <span class="item-actions">
        <button type="button" class="text-btn" @click="openEdit(c)">수정</button>
        <button type="button" class="text-btn" @click="remove(c)">삭제</button>
      </span>
    </footer>
  </article>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
}
.card header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.name {
  font-size: 22px;
  font-weight: 900;
}
.meta {
  font-size: 15px;
  color: var(--color-text-mid);
}
.blood {
  margin-left: auto;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-soft);
  font-size: 15px;
  font-weight: 700;
}
dl {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
}
dl div {
  display: flex;
  gap: 12px;
}
dt {
  width: 64px;
  flex: none;
  font-size: 14px;
  color: var(--color-text-sub);
}
dd {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  word-break: keep-all;
}
dd.none {
  font-weight: 400;
  color: var(--color-text-sub);
}
.card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card footer small {
  font-size: 12px;
  color: var(--color-text-sub);
}
</style>
