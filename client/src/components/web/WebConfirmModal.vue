<script setup>
// 웹 확인 팝업: 브라우저 기본 confirm 창 대신 쓴다 (삭제 같은 되돌리기 어려운 동작 앞)
// 사용: <WebConfirmModal :open="…" title="…" text="…" confirm-label="삭제" @confirm="…" @cancel="…" />
import { onBeforeUnmount, onMounted } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  text: { type: String, default: '' },
  confirmLabel: { type: String, default: '확인' },
  cancelLabel: { type: String, default: '취소' },
  danger: { type: Boolean, default: true }, // true 면 확인 버튼을 주황(경고)색으로
  busy: { type: Boolean, default: false }, // 처리 중이면 버튼을 잠근다
})
const emit = defineEmits(['confirm', 'cancel'])

// Esc 로 닫기
const onKey = (e) => {
  if (props.open && e.key === 'Escape') emit('cancel')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div v-if="open" class="dim" role="alertdialog" aria-modal="true" aria-labelledby="web-confirm-title" @click.self="emit('cancel')">
    <div class="box">
      <h2 id="web-confirm-title">{{ title }}</h2>
      <p v-if="text">{{ text }}</p>
      <div class="actions">
        <button type="button" class="cancel" :disabled="busy" @click="emit('cancel')">{{ cancelLabel }}</button>
        <button type="button" class="ok" :class="{ danger }" :disabled="busy" @click="emit('confirm')">{{ confirmLabel }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  z-index: 50;
  padding-top: 220px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: var(--web-dim);
}
.box {
  width: 400px;
  max-width: calc(100% - 32px);
  padding: 32px 32px 26px;
  border-radius: 20px;
  background: var(--web-bg);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: center;
}
h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
  color: var(--web-text);
}
p {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--web-text-sub);
  white-space: pre-line;
}
.actions {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.cancel,
.ok {
  height: 50px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 16px;
  cursor: pointer;
}
.cancel {
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  color: var(--web-text);
  font-weight: 500;
}
.ok {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.ok.danger {
  background: var(--web-terracotta);
}
.cancel:disabled,
.ok:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
