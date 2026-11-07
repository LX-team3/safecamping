<script setup>
// 선택지 버튼 하나. 카드형(넓은 칸)과 알약형(chip) 두 모양을 한 부품으로 처리한다
import CheckDot from '@/components/common/CheckDot.vue'

defineProps({
  label: { type: String, required: true },
  description: { type: String, default: '' },
  checked: { type: Boolean, default: false },
  variant: { type: String, default: 'card' }, // 'card' | 'chip'
  showDot: { type: Boolean, default: true }, // 알약형은 순위 질문에서만 동그라미를 보여 준다
  rank: { type: Number, default: 0 },
})
defineEmits(['toggle'])
</script>

<template>
  <button type="button" class="choice" :class="[variant, { on: checked }]" :aria-pressed="checked"
    @click="$emit('toggle')">
    <span class="text">
      <b class="label">{{ label }}</b>
      <span v-if="description" class="desc">{{ description }}</span>
    </span>
    <CheckDot v-if="showDot" :checked="checked" :rank="rank" :size="variant === 'card' ? 24 : 20" />
  </button>
</template>

<style scoped>
.choice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  text-align: left;
  cursor: pointer;
}
/* 고른 칸: 테두리를 진하게, 배경을 살짝 회색으로 */
.choice.on {
  border: 2px solid var(--color-text);
  background: var(--color-selected-bg);
}
.card {
  width: 100%;
  min-height: 56px;
  padding: 14px 16px;
  border-radius: 14px;
}
.chip {
  min-height: 44px;
  padding: 6px 16px;
  border-radius: var(--radius-pill);
}
.text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.label {
  font-size: 15px;
}
.desc {
  font-size: 13px;
  color: var(--color-text-sub);
}
.chip .desc {
  font-size: 11px;
}
</style>
