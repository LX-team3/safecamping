<script setup>
// 재난 대피 3D 지도 (인북천 물빛테마공원 캠핑장)
// 지도 자체는 utils/disasterMap.js (CesiumJS) 가 그리고, 이 화면은 위·아래 안내 부품을 배치한다
// 화면을 떠나면 지도를 정리한다 (Cesium 은 그래픽 메모리를 많이 써서 꼭 destroy 해야 함)
import { onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { createDisasterMap, initialState } from '@/utils/disasterMap'
import MobileAlertPill from '@/components/mobile/MobileAlertPill.vue'
import MobileRainSlider from '@/components/mobile/MobileRainSlider.vue'
import MobileEvacSheet from '@/components/mobile/MobileEvacSheet.vue'

const stage = ref(null)     // 지도가 들어갈 칸
const tagLayer = ref(null)  // 지도 위 이름표 칸
const credits = ref(null)   // 지도 출처 표시 칸
const state = reactive(initialState())
const map = shallowRef(null)
let alive = true

onMounted(async () => {
  try {
    const m = await createDisasterMap({
      container: stage.value, tagLayer: tagLayer.value, credits: credits.value, state,
      base: import.meta.env.BASE_URL + 'disaster-map/',
    })
    if (!alive) { m.destroy(); return }   // 불러오는 사이에 화면을 떠났으면 바로 정리
    map.value = m
  } catch (e) {
    state.error = '3D 지도를 불러오지 못했어요: ' + e.message
    console.error(e)
  }
})
onBeforeUnmount(() => {
  alive = false
  map.value?.destroy()
})
</script>

<template>
  <div class="dm" :style="{ '--lv': state.lv.color }">
    <div ref="stage" class="stage"></div>
    <div ref="tagLayer" class="tags"></div>
    <div ref="credits" class="credits"></div>

    <header class="topbar">
      <div class="place">
        <span class="app">CAMPIN</span>
        <h1>호우 및 침수 시 대피경로</h1>
        <span class="loc">인북천 물빛테마공원 캠핑장 · 인제군 서화면</span>
      </div>
      <MobileAlertPill :alert="state.alert" />
    </header>

    <MobileRainSlider v-show="!state.tourOn" :state="state" @change="mm => map?.setRain(mm)" />
    <MobileEvacSheet :state="state" :map="map" />

    <div v-if="state.loading || state.error" class="loading">
      <div v-if="!state.error" class="spin"></div>
      {{ state.error || '3D 지도를 불러오는 중…' }}
    </div>
  </div>
</template>

<style scoped>
/* 이 화면의 어두운 색 (야간 대피 상황을 고려). 지도 위에 겹치는 부품들이 함께 쓴다
   색 값은 tokens.css 의 --color-dm-* · --color-stage-* (팀 규칙: 색은 tokens.css 변수만) */
.dm {
  --dm-ground: var(--color-dm-ground);
  --dm-sheet: var(--color-dm-sheet);
  --dm-card: var(--color-dm-card);
  --dm-line: var(--color-dm-line);
  --dm-text: var(--color-dm-text);
  --dm-muted: var(--color-dm-muted);
  --dm-water: var(--color-dm-water);
  --dm-safe: var(--color-stage-0);
  --dm-caution: var(--color-stage-1);
  --dm-severe: var(--color-stage-3);
  --dm-font-num: 'IBM Plex Mono', ui-monospace, monospace;
  position: relative;
  height: 100%;
  overflow: hidden;
  background: var(--dm-ground);
  color: var(--dm-text);
  font-size: 15px;
}
.stage { position: absolute; inset: 0; touch-action: none; }
.stage :deep(.cesium-widget-credits) { display: none !important; }
.tags { position: absolute; inset: 0; pointer-events: none; z-index: 3; }
.credits { position: absolute; right: 8px; bottom: 174px; z-index: 4; font-size: 9px; color: var(--color-dm-muted); pointer-events: none; text-align: right; }
.credits :deep(a) { color: inherit; pointer-events: auto; }

.topbar {
  position: absolute; left: 0; right: 0; top: 0; z-index: 5;
  display: flex; justify-content: space-between; align-items: flex-start; gap: 10px;
  padding: calc(14px + env(safe-area-inset-top, 0px)) 14px 28px;
  background: linear-gradient(var(--color-dm-shade), transparent);
  pointer-events: none;
}
.place { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.place .app { font-size: 12px; font-weight: 700; color: var(--dm-water); letter-spacing: 0.04em; }
.place h1 { margin: 0; font-size: 18px; font-weight: 700; line-height: 1.3; }
.place .loc { font-size: 12px; color: var(--dm-muted); }

/* 지도 위 이름표 (utils/disasterMap.js 가 만들어 붙임) */
.tags :deep(.tag) {
  position: absolute; transform: translate(-50%, -100%); white-space: nowrap;
  font-size: 12px; font-weight: 700; padding: 4px 9px; border-radius: 7px; margin-top: -8px;
}
.tags :deep(.tag.me) { background: var(--dm-water); color: var(--dm-ground); margin-top: -58px; }   /* 캐릭터 머리 위 */
.tags :deep(.tag.shelter) { background: var(--dm-safe); color: var(--dm-ground); }

.loading {
  position: absolute; inset: 0; z-index: 30; background: var(--dm-ground); color: var(--dm-muted);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; font-size: 14px;
  padding: 0 24px; text-align: center;
}
.spin { width: 32px; height: 32px; border: 3px solid var(--dm-line); border-top-color: var(--dm-water); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spin { animation: none; } }
</style>
