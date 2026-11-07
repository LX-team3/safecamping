<script setup>
// 아래에서 올라오는 시트 (뒤 화면은 어둡게). 지역 선택 · 누구와 언제 시트가 같이 쓴다
// 부모가 position: relative 인 영역을 덮는다 (탐색 화면 전체)
import { onBeforeUnmount, onMounted } from 'vue'

defineProps({
  title: { type: String, required: true },
})
const emit = defineEmits(['close'])

// Esc 키로 닫기
function onKey(e) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <section class="sheet" role="dialog" aria-modal="true" :aria-label="title">
      <span class="grip" aria-hidden="true" />
      <header class="head">
        <h2>{{ title }}</h2>
        <button type="button" class="close" aria-label="닫기" @click="emit('close')">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2"
            stroke-linecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </header>
      <div class="body">
        <slot />
      </div>
      <footer v-if="$slots.footer" class="foot">
        <slot name="footer" />
      </footer>
    </section>
  </div>
</template>

<style scoped>
.backdrop {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}
.sheet {
  width: 100%;
  max-height: calc(100% - 64px);
  display: flex;
  flex-direction: column;
  border-radius: 24px 24px 0 0;
  background: var(--color-card);
}
.grip {
  align-self: center;
  width: 44px;
  height: 5px;
  margin-top: 10px;
  border-radius: 3px;
  background: var(--color-border);
}
.head {
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 12px;
}
h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 900;
}
.close {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 16px 16px;
}
.foot {
  flex: none;
  padding: 12px 16px 20px;
  border-top: 1px solid var(--color-track);
}
</style>
