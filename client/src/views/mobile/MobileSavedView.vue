<script setup>
// 저장 화면 (설계: Saved.dc.html). 탭은 "목록" 하나 (차박 위치 탭은 준비 중 글만 있어 뺐다 · 가족 공유 탭은 가족방과 역할이 겹쳐 뺌)
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useSavedStore } from '@/stores/saved'
import MobileBottomNav from '@/components/mobile/MobileBottomNav.vue'

const auth = useAuthStore()
const saved = useSavedStore()

const TABS = ['목록']
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

// 카드 썸네일: 담은 캠핑장 사진을 카드처럼 겹쳐 쌓는다 (최대 3장, 2×2 칸은 빈칸이 생겨서 바꿈)
//  - 맨 앞 = 처음 담은 곳, 뒤 장은 오른쪽 위로 8%씩 비켜 놓는다
//  - 장 수가 적으면 크게 (1장 100% · 2장 92% · 3장 84%) → 빈 공간이 안 남게
const STEP = 8
function layerCount(list) {
  return Math.min(list.campCount, 3)
}
function layerStyle(list, i) {
  const size = 100 - (layerCount(list) - 1) * STEP
  return { width: `${size}%`, height: `${size}%`, left: `${i * STEP}%`, bottom: `${i * STEP}%`, zIndex: 3 - i }
}
// 장 i 의 사진 주소 (서버 thumbUrls: 담은 순서, 사진 있는 곳만 최대 3개). 없으면 회색 장
function thumb(list, i) {
  return list.thumbUrls?.[i] || ''
}
// 2곳 이상이면 비교할 수 있다는 것을 알려 준다
function countText(list) {
  return list.campCount >= 2 ? `${list.campCount}곳 · 비교하기` : `${list.campCount}곳`
}
</script>

<template>
  <div class="page">
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
        <RouterLink to="/app/login" class="primary">로그인</RouterLink>
      </div>

      <template v-else>
        <form v-if="creating" class="create" @submit.prevent="create">
          <input v-model.trim="newName" maxlength="30" placeholder="새 목록 이름 (예: 11월 가족 캠핑 후보)" />
          <button type="submit" class="primary">만들기</button>
          <p v-if="createError" class="error" role="alert">{{ createError }}</p>
        </form>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <div class="grid">
          <RouterLink v-for="list in saved.lists" :key="list.id" :to="`/app/saved/lists/${list.id}`" class="card">
            <span class="thumb">
              <!-- 담은 곳이 없으면 점선 빈 장 하나 -->
              <span v-if="!layerCount(list)" class="layer empty" />
              <span v-for="i in layerCount(list)" :key="i" class="layer" :class="{ back: i > 1 }"
                :style="layerStyle(list, i - 1)">
                <img v-if="thumb(list, i - 1)" :src="thumb(list, i - 1)" alt="" loading="lazy" />
              </span>
            </span>
            <span class="text">
              <b>{{ list.name }}</b>
              <span>{{ countText(list) }}</span>
            </span>
          </RouterLink>
        </div>
      </template>
    </div>
    <MobileBottomNav active="saved" />
  </div>
</template>

<style scoped>
/* 화면 전체 틀: 위는 내용(스크롤), 맨 아래는 하단바 (탐색 화면과 같은 방식) */
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.saved {
  flex: 1;
  min-height: 0; /* 이게 없으면 내용이 길 때 하단바가 아래로 밀려난다 */
  overflow-y: auto;
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
/* 겹쳐 쌓은 사진 (위치·크기는 layerStyle) */
.thumb {
  position: relative;
  aspect-ratio: 1;
}
.layer {
  position: absolute;
  border: 2px solid var(--color-card);
  border-radius: 14px;
  background: var(--color-photo); /* 사진 없는 캠핑장 = 회색 장 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}
/* 뒤 장은 살짝 어둡게 → 맨 앞 장이 또렷 */
.layer.back img {
  filter: brightness(0.88);
}
.layer.empty {
  inset: 0;
  border: 1.5px dashed var(--color-border);
  background: var(--color-bg-soft);
  box-shadow: none;
}
.layer img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
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
