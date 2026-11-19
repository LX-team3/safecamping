<script setup>
// 지도 위쪽 가운데 "캠핑장 | 차박" 전환 토글 (탐색 화면 · 차박 화면이 같이 씀)
// 화면이 바뀌는 것이라 버튼이 아니라 링크(RouterLink) → 휴대폰 뒤로가기로 원래 지도로 돌아온다
defineProps({ active: { type: String, required: true } }) // 'camp' | 'chabak'

const MODES = [
  { key: 'camp', label: '캠핑장', to: '/explore' },
  { key: 'chabak', label: '차박', to: '/chabak' },
]
</script>

<template>
  <nav class="mode-toggle" aria-label="지도 종류">
    <RouterLink v-for="m in MODES" :key="m.key" :to="m.to" class="mode" :class="{ on: m.key === active }"
      :aria-current="m.key === active ? 'page' : undefined" replace>
      {{ m.label }}
    </RouterLink>
  </nav>
</template>

<style scoped>
/* 지도 위 왼쪽 위. 오른쪽 위 지도 버튼(.map-btn)과 같은 흰 바탕 · 그림자 · 높이(44px) */
.mode-toggle {
  display: flex;
  gap: 2px;
  padding: 4px;
  border-radius: 22px;
  background: var(--color-card);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.18);
}
.mode {
  width: 72px;
  height: 36px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.mode.on {
  background: var(--color-text);
  color: var(--color-card);
  font-weight: 700;
}
</style>
