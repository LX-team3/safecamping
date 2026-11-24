<script setup>
// 경로 지도: 출발지(캠핑장)에서 시설까지 형광 파란 선을 긋는다. 고른 시설은 진하고 굵게, 나머지는 옅게
// 카카오 키가 있으면 카카오맵, 없으면 좌표 비율로 그린 임시 그림 (CampMap 과 같은 방식)
// 시설에 path(도로 경로 좌표)가 있으면 그 길을 따라 그리고, 없으면 두 좌표를 잇는 직선을 긋는다
// noLine: 선 없이 마커만 (실제 경로 사이에 직선이 섞이면 헷갈려서 — 예: 경로를 받지 않는 화장실)
// 시설 마커 = 종류별 아이콘 배지(색은 캠핑장 안전점수 등급) + 이름표(시간만, 예: 17분), 출발지 = 텐트 배지 + 캠핑장 이름 — utils/facilityIcon.js
// 시설 종류 이름은 화면에 쓰지 않고 아이콘으로 알아보게 한다 (2026-11-09). 화면 읽기 프로그램용 이름(name)은 aria-label 에만
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { facilityBadgeEl, facilityIcon, gradeClass } from '@/utils/facilityIcon'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'

const props = defineProps({
  origin: { type: Object, default: null }, // { name, lat, lng }
  // [{ id, type, name, label?, lat, lng, no, path?, noLine? }] type: 아이콘 고르기, name: 종류 이름(aria-label 용),
  // label: 이름표 글씨(없으면 아이콘만), no: 작은 번호, path: [[위도, 경도], ...]
  targets: { type: Array, default: () => [] },
  grade: { type: String, default: null }, // 캠핑장 안전점수 등급 safe·caution·danger (없으면 회색)
  selectedId: { type: Number, default: null },
  bottomInset: { type: Number, default: 0 }, // 아래 시트가 가리는 높이(px)
  dotted: { type: Boolean, default: false }, // 시설 상세처럼 점선 한 줄
})
const emit = defineEmits(['select'])

const el = ref(null)
const mode = ref('loading') // loading → kakao | fake

let kakao = null
let map = null
let drawn = [] // 지도에 올린 선·마커 (다시 그릴 때 지운다)
let stopSizing = null

function cssColor(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

onMounted(async () => {
  try {
    kakao = await loadKakaoMap()
  } catch {
    kakao = null // 지도를 못 불러와도 임시 그림으로 계속 보여 준다
  }
  if (!el.value) return // 불러오는 사이 다른 화면으로 나갔으면 그리지 않는다
  if (!kakao) {
    mode.value = 'fake'
    return
  }
  mode.value = 'kakao'
  const first = props.origin || props.targets[0]
  map = new kakao.maps.Map(el.value, {
    center: new kakao.maps.LatLng(first?.lat ?? 38.0697, first?.lng ?? 128.1707),
    level: 8,
  })
  stopSizing = keepMapSized(map, el.value)
  draw()
  fit()
})

onBeforeUnmount(() => {
  stopSizing?.()
  drawn.forEach((o) => o.setMap(null))
})

function markerEl(target) {
  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = `route-marker${target.id === props.selectedId ? ' selected' : ''}${target.label ? '' : ' icon-only'}`
  btn.setAttribute('aria-label', [target.name, target.label].filter(Boolean).join(' '))
  btn.appendChild(facilityBadgeEl({ type: target.type, grade: props.grade, no: target.no }))
  if (target.label) btn.appendChild(document.createTextNode(target.label))
  btn.addEventListener('click', () => emit('select', target.id))
  return btn
}

// 출발지(캠핑장): 텐트 아이콘 배지(등급 색) + 캠핑장 이름
function originEl() {
  const span = document.createElement('span')
  span.className = 'route-origin'
  span.appendChild(facilityBadgeEl({ type: 'camp', grade: props.grade }))
  span.appendChild(document.createTextNode(props.origin.name))
  return span
}

// 선의 점들: 도로 경로가 있으면 그 좌표 전체, 없으면 출발지·도착지 두 점
function linePoints(target) {
  if (target.path?.length >= 2) return target.path.map(([lat, lng]) => ({ lat, lng }))
  return [props.origin, target]
}

// 형광 느낌: 같은 길을 세 겹으로 겹쳐 그린다
//  ① 넓고 옅은 파란 빛(글로우) ② 선명한 파란 선 ③ (고른 길만) 안쪽 밝은 심
// 시설 상세처럼 점선(dotted)이면 빛·심 없이 점선 한 줄만
function lines(target, selected) {
  const path = linePoints(target).map((p) => new kakao.maps.LatLng(p.lat, p.lng))
  const blue = cssColor('--color-route')
  const z = selected ? 10 : 1 // 고른 길이 다른 길 위에 보이게 (겹마다 +1, 마커는 이보다 위에 있다)
  const layer = (weight, color, opacity, zOffset) =>
    new kakao.maps.Polyline({
      map,
      path,
      strokeWeight: weight,
      strokeColor: color,
      strokeOpacity: opacity,
      strokeStyle: props.dotted ? 'shortdot' : 'solid',
      zIndex: z + zOffset,
    })
  if (props.dotted) return [layer(5, blue, 1, 0)]
  const result = [
    layer(selected ? 16 : 10, blue, selected ? 0.22 : 0.1, 0), // ① 빛
    layer(selected ? 6 : 4, blue, selected ? 1 : 0.5, 1), // ② 선
  ]
  if (selected) result.push(layer(2, cssColor('--color-route-core'), 0.95, 2)) // ③ 심
  return result
}

function draw() {
  if (!map) return
  drawn.forEach((o) => o.setMap(null))
  drawn = []
  if (props.origin) {
    props.targets.filter((t) => !t.noLine).forEach((t) => drawn.push(...lines(t, t.id === props.selectedId)))
  }
  props.targets.forEach((t) =>
    drawn.push(
      new kakao.maps.CustomOverlay({
        map,
        position: new kakao.maps.LatLng(t.lat, t.lng),
        content: markerEl(t),
        clickable: true, // 마커를 누른 것이 지도 조작으로 넘어가지 않게 (휴대폰 터치에 필요)
        zIndex: t.id === props.selectedId ? 30 : 20, // 선(1~12)보다 위
      }),
    ),
  )
  if (props.origin) {
    drawn.push(
      new kakao.maps.CustomOverlay({
        map,
        position: new kakao.maps.LatLng(props.origin.lat, props.origin.lng),
        content: originEl(),
        zIndex: 25,
      }),
    )
  }
}

// 지도 범위·임시 그림에 쓸 점: 출발지 + 시설 + 도로 경로 좌표 (산길이 크게 돌아가도 화면 밖으로 나가지 않게)
function allPoints() {
  const paths = props.targets.flatMap((t) => (t.path || []).map(([lat, lng]) => ({ lat, lng })))
  return [...props.targets, ...(props.origin ? [props.origin] : []), ...paths]
}

function fit() {
  if (!map) return
  const points = allPoints()
  // 시설 상세 지도는 높이가 210px 뿐이라 여백을 고정하면 그릴 자리가 없다 → 높이에 맞춰 줄인다
  const gap = Math.min(70, el.value.clientHeight / 5)
  fitPoints(kakao, map, points, [gap, 70, props.bottomInset + gap, 70])
}

watch(
  () => [props.origin, props.targets],
  () => {
    draw()
    fit()
  },
)
watch(() => [props.selectedId, props.grade], draw)

// ---- 임시 그림 (카카오 키 없음): 좌표의 최소·최대를 0~100 으로 바꿔 그린다 (실제 축척 아님) ----
const fake = computed(() => {
  const points = allPoints()
  if (!points.length) return { origin: null, targets: [] }
  const lats = points.map((p) => p.lat)
  const lngs = points.map((p) => p.lng)
  const [minLat, maxLat, minLng, maxLng] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)]
  const pos = (p) => ({
    x: maxLng === minLng ? 50 : ((p.lng - minLng) / (maxLng - minLng)) * 100,
    y: maxLat === minLat ? 50 : ((maxLat - p.lat) / (maxLat - minLat)) * 100,
  })
  return {
    origin: props.origin ? { ...props.origin, ...pos(props.origin) } : null,
    // 고른 시설을 맨 뒤로 → 나중에 그려져서 옅은 줄 위에 보인다
    // points: SVG polyline 의 "x,y x,y ..." (직선이면 출발지·도착지 두 점)
    targets: props.targets
      .map((t) => ({
        ...t,
        ...pos(t),
        points: props.origin ? linePoints(t).map((p) => { const q = pos(p); return `${q.x},${q.y}` }).join(' ') : '',
      }))
      .sort((a, b) => (a.id === props.selectedId) - (b.id === props.selectedId)),
  }
})
</script>

<template>
  <div class="route-map">
    <div ref="el" class="canvas" :class="{ fake: mode === 'fake' }">
      <div v-if="mode === 'fake'" class="area" :style="{ bottom: bottomInset + 48 + 'px' }">
        <svg v-if="fake.origin" class="lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <template v-for="t in fake.targets" :key="t.id">
            <polyline v-if="!t.noLine" :points="t.points" :class="{ selected: t.id === selectedId, dotted }"
              vector-effect="non-scaling-stroke" />
          </template>
        </svg>
        <button v-for="t in fake.targets" :key="t.id" type="button" class="route-marker fake-pos"
          :class="{ selected: t.id === selectedId, 'icon-only': !t.label }" :style="{ left: t.x + '%', top: t.y + '%' }"
          :aria-label="[t.name, t.label].filter(Boolean).join(' ')" @click="emit('select', t.id)">
          <!-- 카카오 지도 마커(facilityBadgeEl)와 같은 모양. 아이콘은 우리 상수에서만 오므로 v-html 이 안전 -->
          <span class="fac-badge" :class="gradeClass(grade)">
            <span v-if="facilityIcon(t.type)" class="fac-svg" v-html="facilityIcon(t.type)" />
            <template v-else>{{ t.no }}</template>
            <b v-if="facilityIcon(t.type) && t.no" class="fac-no">{{ t.no }}</b>
          </span>{{ t.label }}
        </button>
        <span v-if="fake.origin" class="route-origin fake-pos" :style="{ left: fake.origin.x + '%', top: fake.origin.y + '%' }">
          <span class="fac-badge" :class="gradeClass(grade)"><span class="fac-svg" v-html="facilityIcon('camp')" /></span>
          {{ fake.origin.name }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.route-map {
  position: absolute;
  inset: 0;
}
.canvas {
  width: 100%;
  height: 100%;
  isolation: isolate; /* 지도 안쪽 z-index 가 위에 뜬 버튼·시트를 덮지 않게 가둔다 */
}
.canvas.fake {
  position: relative;
  background: var(--color-map-bg);
}
.area {
  position: absolute;
  top: 80px;
  left: 64px;
  right: 64px;
}
.lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}
/* 임시 그림도 형광 파랑: drop-shadow 로 선 둘레에 같은 색 빛을 낸다 */
.lines polyline {
  fill: none;
  stroke: var(--color-route);
  stroke-width: 3;
  stroke-opacity: 0.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.lines polyline.selected {
  stroke-width: 5;
  stroke-opacity: 1;
  filter: drop-shadow(0 0 4px var(--color-route));
}
.lines polyline.dotted {
  stroke-dasharray: 2 7;
  stroke-linecap: round;
}
</style>

<!-- 마커는 카카오 지도 안에 직접 붙이는 요소라 scoped 가 적용되지 않는다 → 전역 style -->
<style>
/* 흰 알약 이름표 안 왼쪽에 아이콘 배지 (.fac-badge 는 styles/base.css) */
.route-marker {
  height: 38px;
  padding: 0 11px 0 3px;
  border: none;
  border-radius: 19px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-card);
  color: var(--color-text-sub);
  font: 700 12px var(--font-base);
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  cursor: pointer;
}
.route-marker .fac-badge {
  --fac-size: 32px;
  box-shadow: none;
}
/* 글씨 없는 마커(시설 상세): 흰 테 두른 동그라미만 */
.route-marker.icon-only {
  padding: 3px;
}
/* 고른 시설: 글씨 진하게 + 검은 테두리로 또렷하게 */
.route-marker.selected {
  color: var(--color-text);
  box-shadow: 0 0 0 2px var(--color-text), 0 2px 6px rgba(0, 0, 0, 0.3);
}
/* 출발지(캠핑장): 진한 이름표 + 왼쪽 텐트 배지 — 시설 이름표(흰색)와 구분되게 바탕은 primary 색 */
.route-origin {
  height: 38px;
  padding: 0 11px 0 3px;
  border-radius: 19px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-primary);
  color: var(--color-card);
  font: 700 12px var(--font-base);
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
}
.route-origin .fac-badge {
  --fac-size: 32px;
  box-shadow: none;
}
.route-marker.fake-pos,
.route-origin.fake-pos {
  position: absolute;
  transform: translate(-50%, -50%);
}
</style>
