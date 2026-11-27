<script setup>
// 가족방 · 모일 곳 탭. 비상시 가족이 모일 장소를 정해 둔다
// 좌표는 "지금 내 위치 넣기"로만 넣는다 (지도에서 고르기는 카카오맵 SDK 연결 때)
import { ref, onMounted } from 'vue'
import { useFamilyStore } from '@/stores/family'
import { askConfirm } from '@/utils/confirm'
import MobileAddButton from './MobileAddButton.vue'

const family = useFamilyStore()

const adding = ref(false)
const name = ref('')
const desc = ref('')
const coords = ref(null) // { lon, lat } 또는 null
const locating = ref(false)
const formError = ref('')
const error = ref('')

onMounted(async () => {
  try {
    await family.fetchPlaces()
  } catch (e) {
    error.value = e.message
  }
})

function useMyLocation() {
  if (!navigator.geolocation) {
    formError.value = '이 브라우저에서는 위치를 가져올 수 없습니다.'
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      coords.value = { lon: pos.coords.longitude, lat: pos.coords.latitude }
      locating.value = false
      formError.value = ''
    },
    () => {
      locating.value = false
      formError.value = '위치를 가져오지 못했습니다. 위치 권한을 확인해 주세요.'
    },
    { enableHighAccuracy: true, timeout: 10000 },
  )
}

// 입력칸 닫기 (뒤로): 쓰던 내용과 오류 문구도 비운다
function closeForm() {
  adding.value = false
  name.value = ''
  desc.value = ''
  coords.value = null
  formError.value = ''
}

async function add() {
  if (!name.value) {
    formError.value = '장소 이름을 입력해 주세요.'
    return
  }
  try {
    await family.addPlace({ name: name.value, description: desc.value, ...coords.value })
    adding.value = false
    name.value = ''
    desc.value = ''
    coords.value = null
    formError.value = ''
  } catch (e) {
    formError.value = e.message
  }
}

async function remove(place) {
  if (!(await askConfirm({ title: '모일 곳을 지울까요?', message: place.name, confirmLabel: '지우기' }))) return
  try {
    await family.deletePlace(place.id)
  } catch (e) {
    error.value = e.message
  }
}

// 카카오맵 웹 링크로 위치를 연다 (지도 SDK 연결 전 임시)
function mapLink(place) {
  return `https://map.kakao.com/link/map/${encodeURIComponent(place.name)},${place.lat},${place.lon}`
}
</script>

<template>
  <!-- 입력 중: 왼쪽 "뒤로" (입력칸을 닫고 목록·빈 카드로). 일정 탭과 같은 모양 -->
  <div v-if="adding" class="tab-head">
    <button type="button" class="form-back" @click="closeForm">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
      뒤로
    </button>
  </div>
  <!-- + 버튼 줄: 모일 곳이 있을 때만 (비었을 때는 아래 빈 카드의 버튼이 대신). 안내 문장은 빈 카드와 겹쳐 뺐다 -->
  <div v-else-if="family.places.length" class="tab-head end">
    <MobileAddButton label="모일 곳 추가" @click="adding = true" />
  </div>

  <form v-if="adding" class="stack-form" @submit.prevent="add">
    <input v-model.trim="name" maxlength="30" placeholder="장소 이름 (예: 관리동 앞 주차장)" />
    <input v-model.trim="desc" maxlength="200" placeholder="설명 (선택, 예: 계곡 물이 불면 여기로)" />
    <button type="button" class="secondary" :disabled="locating" @click="useMyLocation">
      {{ locating ? '위치 찾는 중...' : coords ? '현재 위치 넣음 ✓' : '지금 내 위치 넣기 (선택)' }}
    </button>
    <button type="submit" class="primary">추가</button>
    <p v-if="formError" class="error" role="alert">{{ formError }}</p>
  </form>

  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <!-- 비었을 때: 문장 하나 → 카드 (일정 탭 · 웹 저장 빈 카드와 같은 모양) + 바로 추가하는 버튼 -->
  <div v-if="!family.places.length && !adding" class="empty-card">
    <span class="empty-icon" aria-hidden="true">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.5" /></svg>
    </span>
    <b>현재 모일 곳이 없습니다</b>
    <p>비상 시 일행이 모일 곳을 등록하세요</p>
    <button type="button" class="empty-go" @click="adding = true">모일 곳 등록</button>
  </div>

  <ul class="item-list">
    <li v-for="p in family.places" :key="p.id" class="item">
      <div class="item-text">
        <b>{{ p.name }}</b>
        <span v-if="p.description">{{ p.description }}</span>
        <small>{{ p.createdByName || '알 수 없음' }} 지정</small>
      </div>
      <div class="item-actions">
        <a v-if="p.lat != null" :href="mapLink(p)" target="_blank" rel="noopener" class="text-btn">지도</a>
        <button type="button" class="text-btn" @click="remove(p)">삭제</button>
      </div>
    </li>
  </ul>
</template>

<style scoped>
/* 일정 탭(MobileScheduleTab.vue)과 같은 모양 */
.tab-head.end {
  justify-content: flex-end;
}
.form-back {
  height: 44px;
  margin-left: -6px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: var(--color-text);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.empty-card {
  margin-top: 8px;
  padding: 28px 20px 22px;
  border: 1px solid var(--color-divider);
  border-radius: 14px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}
.empty-icon {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
}
.empty-card b {
  font-size: 15px;
  font-weight: 700;
}
.empty-card p {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-text-sub);
}
.empty-go {
  margin-top: 12px;
  padding: 9px 18px;
  border: 0;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-card);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
</style>
