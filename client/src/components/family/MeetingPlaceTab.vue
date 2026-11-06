<script setup>
// 가족방 · 모일 곳 탭. 비상시 가족이 모일 장소를 정해 둔다
// 좌표는 "지금 내 위치 넣기"로만 넣는다 (지도에서 고르기는 카카오맵 SDK 연결 때)
import { ref, onMounted } from 'vue'
import { useFamilyStore } from '@/stores/family'
import AddButton from './AddButton.vue'

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
  if (!window.confirm(`'${place.name}'을(를) 모일 곳에서 지울까요?`)) return
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
  <div class="tab-head">
    <p class="sub">비상시 가족이 모일 곳을 정해 두세요.</p>
    <AddButton label="모일 곳 추가" @click="adding = !adding" />
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
  <p v-if="!family.places.length && !adding" class="empty">아직 정한 모일 곳이 없어요.</p>

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
