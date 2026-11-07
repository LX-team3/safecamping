<script setup>
// 경로 지도: 출발지(캠핑장)에서 시설까지 선을 긋는다. 고른 시설은 검은 줄, 나머지는 회색 줄
// 카카오 키가 있으면 카카오맵, 없으면 좌표 비율로 그린 임시 그림 (CampMap 과 같은 방식)
// 지금 선은 두 좌표를 잇는 직선이다 → 도로 경로 데이터가 생기면 line() 에 그 좌표들을 넘기면 된다
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'

const props = defineProps({
  origin: { type: Object, default: null }, // { name, lat, lng }
  targets: { type: Array, default: () => [] }, // [{ id, label, lat, lng, no }] no: 마커 번호 (없으면 십자)
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
  btn.className = `route-marker${target.id === props.selectedId ? ' selected' : ''}`
  if (target.no) {
    const no = document.createElement('i')
    no.textContent = target.no
    btn.appendChild(no)
  }
  btn.appendChild(document.createTextNode(target.label))
  btn.addEventListener('click', () => emit('select', target.id))
  return btn
}

function originEl() {
  const span = document.createElement('span')
  span.className = 'route-origin'
  span.textContent = props.origin.name
  return span
}

function line(target, selected) {
  return new kakao.maps.Polyline({
    map,
    path: [new kakao.maps.LatLng(props.origin.lat, props.origin.lng), new kakao.maps.LatLng(target.lat, target.lng)],
    strokeWeight: selected ? 5 : 4,
    strokeColor: cssColor(selected ? '--color-text' : '--color-text-sub'),
    strokeOpacity: selected ? 1 : 0.45,
    strokeStyle: props.dotted ? 'shortdot' : 'solid',
    zIndex: selected ? 2 : 1,
  })
}

function draw() {
  if (!map) return
  drawn.forEach((o) => o.setMap(null))
  drawn = []
  if (props.origin) props.targets.forEach((t) => drawn.push(line(t, t.id === props.selectedId)))
  props.targets.forEach((t) =>
    drawn.push(
      new kakao.maps.CustomOverlay({
        map,
        position: new kakao.maps.LatLng(t.lat, t.lng),
        content: markerEl(t),
        clickable: true, // 마커를 누른 것이 지도 조작으로 넘어가지 않게 (휴대폰 터치에 필요)
        zIndex: t.id === props.selectedId ? 10 : 3,
      }),
    ),
  )
  if (props.origin) {
    drawn.push(
      new kakao.maps.CustomOverlay({
        map,
        position: new kakao.maps.LatLng(props.origin.lat, props.origin.lng),
        content: originEl(),
        zIndex: 5,
      }),
    )
  }
}

function fit() {
  if (!map) return
  const points = [...props.targets, ...(props.origin ? [props.origin] : [])]
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
watch(() => props.selectedId, draw)

// ---- 임시 그림 (카카오 키 없음): 좌표의 최소·최대를 0~100 으로 바꿔 그린다 (실제 축척 아님) ----
const fake = computed(() => {
  const points = [...props.targets, ...(props.origin ? [props.origin] : [])]
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
    // 고른 시설을 맨 뒤로 → 나중에 그려져서 회색 줄 위에 보인다
    targets: props.targets
      .map((t) => ({ ...t, ...pos(t) }))
      .sort((a, b) => (a.id === props.selectedId) - (b.id === props.selectedId)),
  }
})
</script>

<template>
  <div class="route-map">
    <div ref="el" class="canvas" :class="{ fake: mode === 'fake' }">
      <div v-if="mode === 'fake'" class="area" :style="{ bottom: bottomInset + 48 + 'px' }">
        <svg v-if="fake.origin" class="lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <line v-for="t in fake.targets" :key="t.id" :x1="fake.origin.x" :y1="fake.origin.y" :x2="t.x" :y2="t.y"
            :class="{ selected: t.id === selectedId, dotted }" vector-effect="non-scaling-stroke" />
        </svg>
        <button v-for="t in fake.targets" :key="t.id" type="button" class="route-marker fake-pos"
          :class="{ selected: t.id === selectedId }" :style="{ left: t.x + '%', top: t.y + '%' }"
          @click="emit('select', t.id)">
          <i v-if="t.no">{{ t.no }}</i>{{ t.label }}
        </button>
        <span v-if="fake.origin" class="route-origin fake-pos" :style="{ left: fake.origin.x + '%', top: fake.origin.y + '%' }">
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
.lines line {
  stroke: var(--color-text-sub);
  stroke-width: 3;
  stroke-opacity: 0.45;
}
.lines line.selected {
  stroke: var(--color-text);
  stroke-width: 5;
  stroke-opacity: 1;
}
.lines line.dotted {
  stroke-dasharray: 2 7;
  stroke-linecap: round;
}
</style>

<!-- 마커는 카카오 지도 안에 직접 붙이는 요소라 scoped 가 적용되지 않는다 → 전역 style -->
<style>
.route-marker {
  height: 30px;
  padding: 0 10px 0 5px;
  border: none;
  border-radius: 15px;
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
.route-marker i {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-text-sub);
  color: var(--color-card);
  font-style: normal;
}
.route-marker.selected {
  color: var(--color-text);
}
.route-marker.selected i {
  background: var(--color-text);
}
.route-origin {
  padding: 3px 8px;
  border-radius: 10px;
  background: var(--color-primary);
  color: var(--color-card);
  font: 700 12px var(--font-base);
  white-space: nowrap;
}
.route-marker.fake-pos,
.route-origin.fake-pos {
  position: absolute;
  transform: translate(-50%, -50%);
}
</style>
