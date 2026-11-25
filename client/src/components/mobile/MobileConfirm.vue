<script setup>
// 앱 확인 팝업 ("그만둘까요?" 같은 두 버튼). 모양은 예약 확인 팝업(common/ReservationReturnPopup)과 같다
// 사용: <MobileConfirm :open title="…" message="…" confirm-label="…" cancel-label="…" @confirm @cancel />
import { onBeforeUnmount, onMounted } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  message: { type: String, default: '' },
  confirmLabel: { type: String, default: '확인' },
  cancelLabel: { type: String, default: '취소' },
})
const emit = defineEmits(['confirm', 'cancel'])

const onKey = (e) => {
  if (props.open && e.key === 'Escape') emit('cancel')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div v-if="open" class="dim" role="alertdialog" aria-modal="true" aria-labelledby="mconfirm-title" @click.self="emit('cancel')">
    <div class="box">
      <h2 id="mconfirm-title">{{ title }}</h2>
      <p v-if="message">{{ message }}</p>
      <div class="actions">
        <button type="button" class="cancel" @click="emit('cancel')">{{ cancelLabel }}</button>
        <button type="button" class="ok" @click="emit('confirm')">{{ confirmLabel }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  z-index: 110;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--color-nudge-dim);
}
.box {
  width: 320px;
  max-width: 100%;
  padding: 24px 20px 18px;
  border-radius: 18px;
  background: var(--color-bg);
  text-align: center;
  word-break: keep-all;
}
h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
}
p {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--color-text-sub);
}
.actions {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.actions button {
  height: 46px;
  border-radius: 12px;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.cancel {
  border: 1px solid var(--color-line);
  background: var(--color-bg);
  color: var(--color-text);
}
.ok {
  border: 0;
  background: var(--color-primary);
  color: var(--color-bg);
}
</style>
