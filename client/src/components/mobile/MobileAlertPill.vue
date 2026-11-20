<script setup>
// 기상특보 알림 (누르면 내용 카드가 펼쳐짐). 지금은 예시 데이터 — 서버가 기상청 특보 API 를 연결하면 바뀜
import { ref } from 'vue'

defineProps({ alert: { type: Object, required: true } })
const open = ref(false)
</script>

<template>
  <div class="alert">
    <button type="button" class="pill" :class="{ warn: alert.warn }" :aria-expanded="open" aria-controls="dm-alert-card" @click="open = !open">
      <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 3 2 20h20L12 3z" /><path d="M12 10v4" /><path d="M12 17h.01" />
      </svg>
      <span><b>{{ alert.title }}</b><small>{{ alert.time }}</small></span>
    </button>
    <div v-show="open" id="dm-alert-card" class="card">
      <p>{{ alert.area }}</p>
      <p>{{ alert.body }}</p>
      <p class="src">{{ alert.src }}</p>
    </div>
  </div>
</template>

<style scoped>
.alert { pointer-events: auto; max-width: 62%; display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.pill {
  font: inherit; display: flex; align-items: center; gap: 8px; border: 0; cursor: pointer; text-align: left;
  background: var(--dm-caution); color: var(--color-dm-on-light); border-radius: 12px; padding: 8px 12px; box-shadow: 0 6px 18px rgba(0, 0, 0, 0.35);
}
.pill.warn { background: var(--dm-severe); color: var(--color-dm-on-light); }
.pill:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; }
.ico { width: 18px; height: 18px; flex: none; }
.pill b { font-size: 14px; display: block; }
.pill small { font-size: 11px; opacity: 0.85; display: block; }
.card {
  background: var(--dm-sheet); border: 1px solid var(--dm-line); border-radius: 12px; padding: 12px 14px;
  font-size: 13px; line-height: 1.6; color: var(--dm-text); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}
.card p { margin: 0 0 6px; }
.card .src { color: var(--dm-muted); font-size: 11px; margin: 0; }
</style>
