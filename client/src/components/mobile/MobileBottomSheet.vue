<script setup>
// 아래에서 올라오는 시트 (뒤 화면은 어둡게). 지역 선택 · 누구와 언제 · 지도 설정 시트가 같이 쓴다
// 부모가 position: relative 인 영역을 덮는다 (탐색·차박 화면 전체)
// 열 때 아래에서 미끄러져 올라오고, 닫을 때(X · 바깥 · Esc · 손잡이를 아래로 끌기) 내려간 뒤 close 를 보낸다
// 움직임은 지도 위 시트(styles/base.css .drag-sheet)와 같은 곡선
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { pushBack } from '@/utils/backStack'

defineProps({
  title: { type: String, required: true },
})
const emit = defineEmits(['close'])

const shown = ref(false) // true: 올라온 상태 (false 면 화면 아래에 숨어 있음)
const closing = ref(false) // 내려가는 중
const dragY = ref(0) // 손잡이를 아래로 끈 거리(px)
const dragging = ref(false)

// 끊기지 않게: 화면에 붙자마자 움직이면 첫 장면들이 새 요소를 그리느라 밀려 툭 튀어나온다
// → 아래에 숨긴 채 한 장면을 먼저 그리고(requestAnimationFrame 두 번), 그다음에 올라오게 한다
let raf = 0
let release = () => {}
onMounted(() => {
  release = pushBack(requestClose) // 휴대폰 뒤로가기: 화면을 나가지 않고 시트만 닫는다
  window.addEventListener('keydown', onKey)
  raf = requestAnimationFrame(() => {
    raf = requestAnimationFrame(() => (shown.value = true))
  })
})
onBeforeUnmount(() => {
  release()
  window.removeEventListener('keydown', onKey)
  cancelAnimationFrame(raf)
  clearTimeout(closeTimer)
})

// 바로 닫지 않고 내려간 뒤에 부모에게 알린다
// (transitionend 가 안 오는 경우 — 움직임 줄이기 설정 등 — 를 대비해 시간으로도 한 번 더)
let closeTimer = 0
function requestClose() {
  if (closing.value) return
  dragging.value = false
  closing.value = true
  shown.value = false
  closeTimer = setTimeout(() => emit('close'), 260)
}
function onTransitionEnd(e) {
  if (closing.value && e.target === e.currentTarget && e.propertyName === 'transform') {
    clearTimeout(closeTimer)
    emit('close')
  }
}

// Esc 키로 닫기
function onKey(e) {
  if (e.key === 'Escape') requestClose()
}

// 안쪽 부품이 고른 뒤 바로 닫고 싶을 때 (내려가는 애니메이션 후 close)
defineExpose({ close: requestClose })

// 손잡이를 아래로 끌면 따라 내려오고, 100px 넘게 내리고 놓으면 닫힌다 (아니면 제자리로)
let startY = 0
function onGripDown(e) {
  startY = e.clientY
  dragging.value = true
  e.currentTarget.setPointerCapture(e.pointerId)
}
function onGripMove(e) {
  if (!dragging.value) return
  dragY.value = Math.max(0, e.clientY - startY)
}
function onGripUp() {
  if (!dragging.value) return
  dragging.value = false
  if (dragY.value > 100) requestClose()
  else dragY.value = 0
}
</script>

<template>
  <div class="backdrop" :class="{ shown }" @click.self="requestClose">
    <section class="sheet" :class="{ shown, closing, dragging }" role="dialog" aria-modal="true" :aria-label="title"
      :style="shown && dragY ? { transform: `translate3d(0, ${dragY}px, 0)` } : null"
      @transitionend="onTransitionEnd">
      <!-- 손잡이: 아래로 끌어서 닫기 (닫기는 X 버튼으로도 됨) -->
      <div class="grip-area drag-handle" aria-hidden="true" @pointerdown="onGripDown" @pointermove="onGripMove"
        @pointerup="onGripUp" @pointercancel="onGripUp">
        <span class="grip" />
      </div>
      <header class="head">
        <h2>{{ title }}</h2>
        <button type="button" class="close" aria-label="닫기" @click="requestClose">
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
/* 부드럽게 보이도록: 움직이는 것은 transform(위치)과 opacity(투명도)만 쓴다
   → 브라우저가 화면을 다시 그리지 않고 GPU 로 처리해서 끊기지 않는다 (배경색·높이를 바꾸면 매 장면 다시 그림) */
.backdrop {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
/* 어두운 막: 따로 한 장을 깔고 투명도만 바꾼다 */
.backdrop::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.18s ease-in;
}
.backdrop.shown::before {
  opacity: 1;
  transition: opacity 0.22s ease-out;
}
.sheet {
  position: relative; /* 어두운 막(::before) 위에 */
  width: 100%;
  max-height: calc(100% - 64px);
  display: flex;
  flex-direction: column;
  border-radius: 24px 24px 0 0;
  background: var(--color-card);
  will-change: transform; /* 미리 GPU 층으로 올려 첫 장면부터 매끄럽게 */
  transform: translate3d(0, 100%, 0); /* 처음·닫을 때: 화면 아래에 숨김 */
  /* 닫을 때: 천천히 출발해서 빠르게 빠짐 (열 때보다 조금 짧게) */
  transition: transform 0.18s cubic-bezier(0.4, 0, 0.9, 0.6);
}
/* 열 때: 빠르게 올라와서 끝에서 살짝 천천히 멈춤 (끌다 놓았을 때 제자리로 돌아갈 때도 같음) */
.sheet.shown {
  transform: translate3d(0, 0, 0);
  transition: transform 0.22s cubic-bezier(0.2, 0.9, 0.3, 1);
}
.sheet.dragging {
  transition: none; /* 끄는 동안은 손가락을 바로 따라감 */
}
/* 움직임을 줄이도록 설정한 사람에게는 애니메이션 없이 */
@media (prefers-reduced-motion: reduce) {
  .backdrop::before,
  .sheet,
  .sheet.shown {
    transition-duration: 0.01s;
  }
}
.grip-area {
  flex: none;
  height: 24px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.grip {
  width: 44px;
  height: 5px;
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
