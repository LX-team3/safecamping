<script setup>
// 질문 제목 줄: "1 어떤 캠핑을 하나요?" + 오른쪽 안내("1개 선택" 등). 번호는 설문에 따라 없을 수 있다
defineProps({
  no: { type: Number, default: 0 }, // 0 이면 번호 안 보임
  title: { type: String, required: true },
  hint: { type: String, default: '' },
})
</script>

<template>
  <div class="head">
    <h2><span v-if="no" class="no">{{ no }}</span>{{ title }}</h2>
    <span v-if="hint" class="hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 14px;
}
h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}
.no {
  margin-right: 8px;
  font-size: 14px;
  color: var(--color-text-sub);
}
.hint {
  flex: none;
  font-size: 12px;
  color: var(--color-text-sub);
}
</style>
