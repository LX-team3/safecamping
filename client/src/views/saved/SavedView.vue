<script setup>
// 저장 화면 (설계: Saved.dc.html). 이번에는 "목록" 탭만 동작하고 차박 위치·가족 공유는 준비 중
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useSavedStore } from '@/stores/saved'

const auth = useAuthStore()
const saved = useSavedStore()

const TABS = ['목록', '차박 위치', '가족 공유']
const tab = ref(0)
const error = ref('')

// 새 목록 만들기 (+ 버튼을 누르면 입력칸이 열린다)
const creating = ref(false)
const newName = ref('')
const createError = ref('')

onMounted(async () => {
  if (!auth.isLoggedIn) return // 회원 전용: 로그인 안내만 보여 준다
  try {
    await saved.fetchLists()
  } catch (e) {
    error.value = e.message
  }
})

async function create() {
  if (!newName.value) {
    createError.value = '목록 이름을 입력해 주세요.'
    return
  }
  try {
    await saved.createList(newName.value)
    creating.value = false
    newName.value = ''
    createError.value = ''
  } catch (e) {
    createError.value = e.message
  }
}

// 카드 썸네일 2×2 중 앞의 3칸: 담은 수만큼 회색으로 채운다. 4칸째는 3곳을 넘는 수(+2 등)
function filled(list, i) {
  return list.campCount > i
}
function more(list) {
  return list.campCount > 3 ? `+${list.campCount - 3}` : ''
}
// 2곳 이상이면 비교할 수 있다는 것을 알려 준다
function countText(list) {
  return list.campCount >= 2 ? `${list.campCount}곳 · 비교하기` : `${list.campCount}곳`
}
</script>

<template>
  <div class="saved">
    <div class="head">
      <h1>저장</h1>
      <button v-if="auth.isLoggedIn && tab === 0" type="button" class="icon" aria-label="새 목록 만들기"
        @click="creating = !creating">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
    </div>

    <div role="tablist" class="tabs">
      <button v-for="(label, i) in TABS" :key="label" type="button" role="tab" :aria-selected="i === tab"
        :class="{ on: i === tab }" @click="tab = i">{{ label }}</button>
    </div>

    <div v-if="!auth.isLoggedIn" class="empty">
      <p>저장은 로그인하면 쓸 수 있어요.</p>
      <RouterLink to="/login" class="primary">로그인</RouterLink>
    </div>

    <p v-else-if="tab !== 0" class="empty">준비 중이에요.</p>

    <template v-else>
      <form v-if="creating" class="create" @submit.prevent="create">
        <input v-model.trim="newName" maxlength="30" placeholder="새 목록 이름 (예: 11월 가족 캠핑 후보)" />
        <button type="submit" class="primary">만들기</button>
        <p v-if="createError" class="error" role="alert">{{ createError }}</p>
      </form>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="grid">
        <RouterLink v-for="list in saved.lists" :key="list.id" :to="`/saved/lists/${list.id}`" class="card">
          <span class="thumb">
            <span v-for="i in 3" :key="i" :class="{ filled: filled(list, i - 1) }" />
            <span class="more">{{ more(list) }}</span>
          </span>
          <span class="text">
            <b>{{ list.name }}</b>
            <span>{{ countText(list) }}</span>
          </span>
        </RouterLink>
      </div>
    </template>
  </div>
</template>

<style scoped>
.saved {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 20px;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
}
h1 {
  margin: 0 auto 0 0;
  font-size: 32px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
.icon {
  width: 44px;
  height: 44px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--color-divider);
}
.tabs button {
  height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 16px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.tabs button.on {
  font-weight: 700;
  color: var(--color-text);
  box-shadow: inset 0 -3px 0 var(--color-text);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.create {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.create input {
  flex: 1;
  min-width: 0;
  height: 48px;
  padding: 0 14px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font-size: 15px;
  outline: none;
}
.create input:focus {
  border-color: var(--color-primary);
}
.primary {
  height: 48px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  display: inline-flex;
  align-items: center;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.error {
  width: 100%;
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 14px;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.thumb {
  aspect-ratio: 1;
  border-radius: 14px;
  background: var(--color-fill-soft);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px;
  overflow: hidden;
}
.thumb > span {
  background: var(--color-fill-soft);
}
.thumb > span.filled {
  background: var(--color-photo);
}
.thumb .more {
  color: var(--color-text-mid);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}
.text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.text b {
  font-size: 16px;
}
.text span {
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
