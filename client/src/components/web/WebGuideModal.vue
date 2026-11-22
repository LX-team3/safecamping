<script setup>
// 로그인 안내 팝업 (설계 ⑨): 로그인 전에 내 예약·찜을 누르면 뜬다
import { RouterLink } from 'vue-router'

defineProps({
  open: { type: Boolean, default: false },
  text: { type: String, default: '' },
})
const emit = defineEmits(['close'])
</script>

<template>
  <div v-if="open" class="dim" role="dialog" aria-modal="true" aria-labelledby="web-guide-title" @click.self="emit('close')">
    <div class="box">
      <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <rect x="5" y="11" width="14" height="10" rx="2" />
        <path d="M8 11V8a4 4 0 0 1 8 0v3" />
      </svg>
      <h2 id="web-guide-title">로그인 후 이용할 수 있어요</h2>
      <p>{{ text }}</p>
      <div class="actions">
        <button type="button" class="close" @click="emit('close')">닫기</button>
        <RouterLink to="/web/login" class="login">로그인</RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  z-index: 25;
  padding-top: 260px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: var(--web-dim);
}
.box {
  width: 440px;
  max-width: calc(100% - 32px);
  padding: 40px 36px 32px;
  border-radius: 24px;
  background: var(--web-bg);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  color: var(--web-green);
}
h2 {
  margin: 16px 0 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.6px;
  color: var(--web-text);
}
p {
  margin: 10px 0 0;
  font-size: 16px;
  line-height: 1.6;
  color: var(--web-text-sub);
  white-space: pre-line;
}
.actions {
  margin-top: 28px;
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.close,
.login {
  height: 54px;
  border-radius: 999px;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.close {
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  color: var(--web-text);
  font-weight: 500;
  cursor: pointer;
}
.login {
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
</style>
