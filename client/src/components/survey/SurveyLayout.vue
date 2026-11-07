<script setup>
// 설문 화면 공통 틀: 위(제목 줄·진행 막대) / 가운데(스크롤되는 질문) / 아래(이전·다음 버튼)
// 1분 진단: 진행 막대 + 이전/다음, 여행 취향: 막대 없이 버튼 하나
defineProps({
  label: { type: String, required: true }, // 예: "1분 진단 · 1 / 3", "여행 취향 · 약 30초"
  step: { type: Number, default: 1 },
  total: { type: Number, default: 1 },
  showPrev: { type: Boolean, default: true },
  nextLabel: { type: String, default: '다음으로' },
  nextDisabled: { type: Boolean, default: false },
  finish: { type: Boolean, default: false }, // 1분 진단 마지막 단계면 다음 버튼을 메인 색(올리브)으로
  error: { type: String, default: '' },
})
defineEmits(['prev', 'next', 'skip'])
</script>

<template>
  <div class="survey">
    <header class="top">
      <div class="row">
        <span class="label">{{ label }}</span>
        <button type="button" class="skip" @click="$emit('skip')">나중에 할게요</button>
      </div>
      <!-- 진행 막대: 지나온 단계까지 진하게 (단계가 하나뿐이면 안 보임) -->
      <div v-if="total > 1" class="bars" role="progressbar" :aria-valuenow="step" aria-valuemin="1"
        :aria-valuemax="total">
        <span v-for="n in total" :key="n" class="bar" :class="{ done: n <= step }" />
      </div>
    </header>

    <main class="body">
      <slot />
    </main>

    <footer class="bottom">
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="buttons">
        <button v-if="showPrev" type="button" class="prev" @click="$emit('prev')">이전</button>
        <button type="button" class="next" :class="{ finish }" :disabled="nextDisabled" @click="$emit('next')">
          {{ nextLabel }}
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.survey {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.top {
  flex: none;
  padding: 24px 20px 14px;
  border-bottom: 1px solid var(--color-track);
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.label {
  font-size: 14px;
  color: var(--color-text-sub);
}
.skip {
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  text-decoration: underline;
  cursor: pointer;
}
.bars {
  display: flex;
  gap: 6px;
  margin-top: 18px;
}
.bar {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: var(--color-track);
}
.bar.done {
  background: var(--color-text);
}
.body {
  flex: 1;
  overflow-y: auto; /* 질문이 길면 이 부분만 스크롤 (위·아래는 고정) */
  padding: 24px 20px 32px;
}
.bottom {
  flex: none;
  padding: 12px 20px 24px;
  border-top: 1px solid var(--color-track);
}
.error {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-warn-text);
}
.buttons {
  display: flex;
  gap: 10px;
}
.prev,
.next {
  height: 54px;
  border-radius: var(--radius-pill);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.prev {
  flex: 0 0 104px;
  border: 1px solid var(--color-text);
  background: var(--color-card);
}
.next {
  flex: 1;
  border: none;
  background: var(--color-text);
  color: var(--color-card);
}
.next.finish {
  background: var(--color-primary);
}
.next:disabled {
  opacity: 0.35;
  cursor: default;
}
</style>
