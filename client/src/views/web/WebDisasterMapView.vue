<script setup>
// 웹 재난 대피 3D 지도 (인북천 물빛테마공원 캠핑장): 왼쪽 안내 패널 + 오른쪽 3D 지도
// 지도는 앱과 같은 utils/disasterMap.js (CesiumJS) 가 그리고, 이 화면은 패널과 지도 칸만 배치한다
// 화면을 떠나면 지도를 정리한다 (Cesium 은 그래픽 메모리를 많이 써서 꼭 destroy 해야 함)
// 실시간(/api/disaster/now, 10분마다)·기상특보는 앱과 같은 utils/disasterLive.js
import { onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { createDisasterMap, initialState } from '@/utils/disasterMap'
import { useDisasterLive } from '@/utils/disasterLive'
import WebDisasterPanel from '@/components/web/WebDisasterPanel.vue'

const stage = ref(null)     // 지도가 들어갈 칸
const tagLayer = ref(null)  // 지도 위 이름표 칸
const credits = ref(null)   // 지도 출처 표시 칸
const state = reactive(initialState())
const map = shallowRef(null)
const live = useDisasterLive(state, map)
let alive = true

onMounted(async () => {
  live.start()
  try {
    const m = await createDisasterMap({
      container: stage.value, tagLayer: tagLayer.value, credits: credits.value, state,
      base: import.meta.env.BASE_URL + 'disaster-map/',
    })
    if (!alive) { m.destroy(); return }   // 불러오는 사이에 화면을 떠났으면 바로 정리
    map.value = m
    await live.mapReady()
  } catch (e) {
    state.error = '3D 지도를 불러오지 못했어요: ' + e.message
    console.error(e)
  }
})
onBeforeUnmount(() => {
  alive = false
  live.stop()
  map.value?.destroy()
})
</script>

<template>
  <div class="dm" :style="{ '--lv': state.lv.color }">
    <WebDisasterPanel :state="state" :map="map" :alert="live.alert.value" @rain="mm => map?.setRain(mm)" @live="live.backToLive()" />

    <div class="map">
      <div ref="stage" class="stage"></div>
      <div ref="tagLayer" class="tags"></div>
      <div ref="credits" class="credits"></div>
    </div>

    <div v-if="state.loading || state.error" class="loading">
      <div v-if="!state.error" class="spin"></div>
      {{ state.error || '3D 지도를 불러오는 중…' }}
    </div>
  </div>
</template>

<style scoped>
/* 이 화면의 어두운 색 (앱과 같은 tokens.css 의 --color-dm-* · --color-stage-*). 패널 부품이 함께 쓴다 */
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
  height: 100dvh;
  display: flex;
  overflow: hidden;
  background: var(--dm-ground);
  color: var(--dm-text);
  font-size: 15px;
}
.map { position: relative; flex: 1; min-width: 0; height: 100%; }
.stage { position: absolute; inset: 0; }
.stage :deep(.cesium-widget-credits) { display: none !important; }
.tags { position: absolute; inset: 0; pointer-events: none; z-index: 3; }
.credits { position: absolute; right: 8px; bottom: 6px; z-index: 4; font-size: 10px; color: var(--color-dm-muted); pointer-events: none; text-align: right; }
.credits :deep(a) { color: inherit; pointer-events: auto; }

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
