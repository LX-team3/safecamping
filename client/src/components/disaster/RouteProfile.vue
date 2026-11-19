<script setup>
// 대피 길의 높낮이 그래프: 경로를 따라 자른 실제 땅 단면(10m DEM) + 홍수 때 물 높이
// "왜 이 길이 안전한가"를 숫자로 보여 주려고 넣음. 숫자는 utils/disasterMap.js 가 계산해 state.profile 에 넣는다
import { computed } from 'vue'

const props = defineProps({ state: { type: Object, required: true } })

const W = 320, H = 120, X0 = 30, X1 = W - 6, Y0 = 8, Y1 = H - 18
const P = computed(() => props.state.profile)

// 세로축 범위: 가장 낮은 땅 - 2m ~ 가장 높은 땅·다리·물 + 3m, 5m 단위로 맞춤
const range = computed(() => {
  const pts = P.value.pts
  if (!pts.length) return { lo: 0, hi: 1 }
  const top = q => Math.max(q.g, q.deck ?? -Infinity, q.w ?? -Infinity)
  const lo = Math.floor((Math.min(...pts.map(q => q.g)) - 2) / 5) * 5
  const hi = Math.ceil((Math.max(...pts.map(top)) + 3) / 5) * 5
  return { lo, hi: Math.max(hi, lo + 5) }
})
const X = m => X0 + (X1 - X0) * m / (P.value.L || 1)
const Y = h => Y1 - (Y1 - Y0) * (h - range.value.lo) / (range.value.hi - range.value.lo)
const xy = (m, h) => `${X(m).toFixed(1)},${Y(h).toFixed(1)}`

// 가로 눈금선 (3~4줄)
const grid = computed(() => {
  const { lo, hi } = range.value
  const step = [5, 10, 20, 25, 50].find(v => (hi - lo) / v <= 4) || 50
  const out = []
  for (let h = Math.ceil(lo / step) * step; h <= hi; h += step) out.push(h)
  return out
})
const ground = computed(() => P.value.pts.map(q => xy(q.m, q.g)).join('L'))
// 물이 땅보다 높은 구간끼리 묶어서 파란 면으로
const waterRuns = computed(() => {
  const runs = []; let cur = null
  P.value.pts.forEach(q => { if (q.w != null && q.w > q.g) (cur = cur || []).push(q); else if (cur) { runs.push(cur); cur = null } })
  if (cur) runs.push(cur)
  return runs.map(r => ({
    area: 'M' + r.map(q => xy(q.m, q.w)).join('L') + 'L' + r.slice().reverse().map(q => xy(q.m, q.g)).join('L') + 'Z',
    top: 'M' + r.map(q => xy(q.m, q.w)).join('L'),
  }))
})
const deck = computed(() => {
  const b = P.value.pts.filter(q => q.deck != null)
  if (b.length < 2) return null
  const mid = (b[0].m + b[b.length - 1].m) / 2
  return { d: 'M' + b.map(q => xy(q.m, q.deck)).join('L'), x: X(mid), y: Math.max(Y0 + 8, Y(Math.max(...b.map(q => Math.max(q.deck, q.g)))) - 6) }
})
// 캐릭터 위치 점: 경로에서 간 비율 → 가까운 표본의 땅 높이
const dot = computed(() => {
  const pts = P.value.pts
  if (!pts.length) return null
  const m = props.state.meU * P.value.L
  const q = pts.reduce((a, b) => Math.abs(b.m - m) < Math.abs(a.m - m) ? b : a)
  return { x: X(m), y: Y(q.g) }
})
const km = m => m < 1000 ? Math.round(m / 10) * 10 + 'm' : (m / 1000).toFixed(2) + 'km'
</script>

<template>
  <div v-if="P.pts.length" class="profile">
    <div class="top"><span>대피 길의 높낮이 (실제 땅 단면)</span><b>{{ P.gain }}</b></div>
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" aria-label="대피 경로의 높이 단면">
      <g v-for="h in grid" :key="h">
        <line :x1="X0" :x2="X1" :y1="Y(h)" :y2="Y(h)" class="grid" />
        <text :x="X0 - 4" :y="Y(h) + 3" text-anchor="end" class="lab">{{ h }}</text>
      </g>
      <g v-for="(r, i) in waterRuns" :key="'w' + i">
        <path :d="r.area" class="water" :class="{ on: P.flooded }" />
        <path :d="r.top" class="water-top" :class="{ on: P.flooded }" />
      </g>
      <path :d="`M${X(0)},${Y1}L${ground}L${X(P.L)},${Y1}Z`" class="ground" />
      <path :d="'M' + ground" class="ground-line" />
      <template v-if="deck">
        <path :d="deck.d" class="deck" />
        <text :x="deck.x" :y="deck.y" text-anchor="middle" class="lab strong">{{ P.bridge }}</text>
      </template>
      <text :x="X0 + 2" :y="H - 5" class="lab">캠핑장</text>
      <text :x="X1" :y="H - 5" text-anchor="end" class="lab">{{ P.dest }} · {{ km(P.L) }}</text>
      <circle v-if="dot" :cx="dot.x" :cy="dot.y" r="4" class="dot" />
    </svg>
    <p>{{ P.note }}</p>
  </div>
</template>

<style scoped>
.profile { background: var(--color-dm-card); border-radius: 14px; padding: 12px 12px 10px; display: flex; flex-direction: column; gap: 6px; }
.top { display: flex; justify-content: space-between; align-items: baseline; font-size: 13px; color: var(--color-dm-muted); }
.top b { font-family: var(--dm-font-num); color: var(--color-dm-text); font-size: 13px; }
svg { width: 100%; height: auto; display: block; }
p { margin: 0; font-size: 12.5px; line-height: 1.5; color: var(--color-dm-muted); }
.grid { stroke: var(--color-dm-line); stroke-width: 1; }
.lab { font-size: 9px; fill: var(--color-dm-muted); font-family: var(--dm-font-num); }
.lab.strong { fill: var(--color-dm-text); }
/* 평소엔 "홍수 나면 여기까지" 옅은 점선, 물이 찬 단계에서는 진한 파란 면 */
.water { fill: var(--color-dm-water); fill-opacity: 0.18; }
.water.on { fill-opacity: 0.75; }
.water-top { fill: none; stroke: var(--color-flood-water-hi); stroke-width: 1.5; stroke-dasharray: 3 3; }
.water-top.on { stroke-dasharray: none; }
.ground { fill: var(--color-dm-grab); fill-opacity: 0.85; }
.ground-line { fill: none; stroke: var(--color-dm-muted); stroke-width: 1.6; stroke-linejoin: round; }
.deck { fill: none; stroke: var(--color-dm-text); stroke-width: 2.5; }
.dot { fill: var(--lv); stroke: var(--color-dm-ground); stroke-width: 1.5; }
</style>
