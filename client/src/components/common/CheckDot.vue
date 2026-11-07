<script setup>
// 선택 표시 동그라미. 고르면 검은 원 + 체크, 순위가 있으면 숫자를 보여 준다
defineProps({
  checked: { type: Boolean, default: false },
  rank: { type: Number, default: 0 }, // 0 이면 숫자 대신 체크
  size: { type: Number, default: 24 },
})
</script>

<template>
  <span class="dot" :class="{ on: checked }" :style="{ width: size + 'px', height: size + 'px' }" aria-hidden="true">
    <template v-if="checked">
      <b v-if="rank" class="rank">{{ rank }}</b>
      <svg v-else viewBox="0 0 24 24" width="60%" height="60%" fill="none" stroke="currentColor" stroke-width="3"
        stroke-linecap="round" stroke-linejoin="round">
        <path d="M5 12.5l4.5 4.5L19 7" />
      </svg>
    </template>
  </span>
</template>

<style scoped>
.dot {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1.5px solid var(--color-border);
  background: var(--color-card);
}
.dot.on {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-card);
}
.rank {
  font-size: 13px;
  line-height: 1;
}
</style>
