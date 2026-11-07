<script setup>
// 상세 화면 위 뒤로·하트 버튼 (캠핑장·시설·관광지 공통). 사진이나 지도 위에 떠 있다
import { useRouter } from 'vue-router'

defineProps({
  favorite: { type: Boolean, default: null }, // null 이면 하트를 숨긴다 (시설 상세)
  fallback: { type: String, default: '/explore' }, // 주소로 바로 들어와 돌아갈 곳이 없을 때
})
defineEmits(['favorite'])

const router = useRouter()
</script>

<template>
  <div class="bar">
    <button type="button" class="round" aria-label="뒤로"
      @click="router.options.history.state.back ? router.back() : router.push(fallback)">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.4"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M15 5l-7 7 7 7" />
      </svg>
    </button>
    <button v-if="favorite !== null" type="button" class="round" :aria-pressed="favorite"
      :aria-label="favorite ? '저장 취소' : '저장'" @click="$emit('favorite')">
      <svg viewBox="0 0 24 24" width="22" height="22" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor"
        stroke-width="2" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.bar {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  z-index: 50;
  display: flex;
  justify-content: space-between;
  pointer-events: none; /* 버튼 사이 빈 곳은 아래 지도를 누를 수 있게 */
}
.round {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  pointer-events: auto;
}
</style>
