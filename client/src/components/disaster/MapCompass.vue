<script setup>
// 나침반: 좌우로 끌면 지도가 360도 돌고, 그냥 누르면 북쪽이 위로 온다
defineProps({
  needle: { type: Number, default: 0 },   // 바늘 각도(도)
  map: { type: Object, default: null },   // utils/disasterMap.js 가 돌려준 동작들
})

let drag = null
function down(e, map) {
  if (!map) return
  map.compassStart()
  drag = { x: e.clientX, moved: 0 }
  e.currentTarget.setPointerCapture(e.pointerId)
}
function move(e, map) {
  if (!drag || !map) return
  const d = e.clientX - drag.x
  drag.x = e.clientX; drag.moved += Math.abs(d)
  map.compassMove(d)
}
function up(map) {
  if (drag && drag.moved < 4 && map) map.compassNorth()   // 거의 안 움직였으면 '누름' → 북쪽 위로
  drag = null
}
</script>

<template>
  <button type="button" class="compass" aria-label="지도 돌리기. 좌우로 끌면 회전, 누르면 북쪽이 위로"
    @pointerdown="down($event, map)" @pointermove="move($event, map)" @pointerup="up(map)"
    @keydown.left.prevent="map?.compassKey(-1)" @keydown.right.prevent="map?.compassKey(1)">
    <svg viewBox="0 0 44 44" aria-hidden="true">
      <circle cx="22" cy="22" r="19" fill="none" stroke="#3A4752" stroke-width="2" stroke-dasharray="3 3" />
      <g :transform="`rotate(${needle} 22 22)`">
        <path d="M22 6 L27 22 L17 22 Z" fill="#EF5B4C" /><path d="M22 38 L27 22 L17 22 Z" fill="#C9D2D8" />
        <text x="22" y="5.5" text-anchor="middle" font-size="7" font-weight="700" fill="#EF5B4C">N</text>
      </g>
    </svg>
    <span class="hint">↔ 돌리기</span>
  </button>
</template>

<style scoped>
.compass {
  position: absolute; z-index: 6; right: 12px; top: calc(146px + env(safe-area-inset-top, 0px));
  width: 52px; height: 52px; border-radius: 50%; border: 1px solid var(--dm-line); background: rgba(20, 27, 33, 0.9);
  padding: 0; cursor: grab; box-shadow: 0 6px 18px rgba(0, 0, 0, 0.4); touch-action: none;
  display: grid; place-items: center; backdrop-filter: blur(8px);
}
.compass:active { cursor: grabbing; }
.compass:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; }
svg { width: 44px; height: 44px; display: block; }
.hint { position: absolute; top: 56px; right: 0; white-space: nowrap; font-size: 10px; color: var(--dm-muted); text-shadow: 0 1px 2px #000; pointer-events: none; }
</style>
