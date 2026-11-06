<script setup>
// 가족방 탭 오른쪽 위의 + 버튼 (모일 곳·아이 카드·일정이 같이 쓴다)
defineProps({ label: { type: String, required: true } })
</script>

<template>
  <button type="button" class="icon" :aria-label="label">
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      stroke-linecap="round" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  </button>
</template>
