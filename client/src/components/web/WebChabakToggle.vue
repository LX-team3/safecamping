<script setup>
// 지도 오른쪽 위 "차박지 찾기" 전환 버튼 (자동차 + 위치 아이콘)
// 탐색(검색) · 내 주변 · 저장 지도에 같이 붙인다 → 어느 지도에서든 차박지 찾기(/web/chabak)로 갈 수 있게
// on: 지금 차박지 찾기 화면이면 true → 흰 바탕으로 바뀌고, 누르면 검색(/web/explore)으로 돌아간다
import { RouterLink } from 'vue-router'

defineProps({
  on: { type: Boolean, default: false },
})
</script>

<template>
  <RouterLink :to="on ? '/web/explore' : '/web/chabak'" class="car" :class="{ on }" aria-label="안전한 차박지 찾기" :aria-pressed="on"
    title="안전한 차박지 찾기">
    <svg width="36" height="36" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M16.8 0.8a6.4 6.4 0 0 0-6.4 6.4c0 4.9 6.4 13.6 6.4 13.6s6.4-8.7 6.4-13.6A6.4 6.4 0 0 0 16.8 0.8Z" class="ink" />
      <circle cx="16.8" cy="7.2" r="2.3" class="bg" />
      <path d="M1.6 19.4v-2.6c0-1 .7-1.9 1.7-2.1l1.4-2.4c.5-.8 1.3-1.3 2.2-1.3h4.4c.9 0 1.7.5 2.2 1.3l1.4 2.4c1 .2 1.7 1.1 1.7 2.1v2.6c0 .5-.4.9-.9.9H2.5c-.5 0-.9-.4-.9-.9Z" class="ink stroke" />
      <path d="M6.2 12.4c.2-.3.5-.5.9-.5h1.6v2.7H4.9Z M10 11.9h1.6c.4 0 .7.2.9.5l1.3 2.2H10Z" class="bg" />
      <circle cx="5.2" cy="20.2" r="2.1" class="ink stroke" /><circle cx="12.8" cy="20.2" r="2.1" class="ink stroke" />
    </svg>
  </RouterLink>
</template>

<style scoped>
.car {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--web-green);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.car.on { background: var(--web-white); }
.car.on .ink { fill: var(--web-green); }
.car.on .bg { fill: var(--web-white); }
.car.on .stroke { stroke: var(--web-white); }
.car .ink { fill: var(--web-white); }
.car .bg { fill: var(--web-green); }
.car .stroke { stroke: var(--web-green); stroke-width: 1.4; paint-order: stroke; }
</style>
