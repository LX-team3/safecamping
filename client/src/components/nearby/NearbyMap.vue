<script setup>
// 내 주변 지도: 내 위치(검은 점 + 회색 원)와 번호 붙은 안전 시설 마커
// 카카오 키가 있으면 카카오맵, 없으면 좌표 비율로 그린 임시 그림 (CampMap·RouteMap 과 같은 방식)
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'

const props = defineProps({
  me: { type: Object, default: null }, // { lat, lng }
  facilities: { type: Array, default: () => [] }, // [{ no, id, name, lat, lng }] — 켜 둔 레이어만
  bottomInset: { type: Number, default: 0 }, // 아래 패널이 가리는 높이(px)
})

const el = ref(null)
const mode = ref('loading') // loading → kakao | fake

let kakao = null
let map = null
let drawn = [] // 지도에 올린 마커 (다시 그릴 때 지운다)
let stopSizing = null

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
  map = new kakao.maps.Map(el.value, {
    center: new kakao.maps.LatLng(props.me?.lat ?? 38.14, props.me?.lng ?? 128.205),
    level: 7,
  })
  stopSizing = keepMapSized(map, el.value)
  draw()
})

onBeforeUnmount(() => {
  stopSizing?.()
  drawn.forEach((o) => o.setMap(null))
})

function facilityEl(f) {
  const span = document.createElement('span')
  span.className = 'nearby-marker'
  span.textContent = f.no
  span.title = f.name
  return span
}

function meEl() {
  const span = document.createElement('span')
  span.className = 'nearby-me'
  span.appendChild(document.createElement('i'))
  return span
}

function draw() {
  if (!map) return
  drawn.forEach((o) => o.setMap(null))
  drawn = []
  props.facilities.forEach((f) =>
    drawn.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(f.lat, f.lng), content: facilityEl(f), zIndex: 3 })),
  )
  if (props.me) {
    drawn.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(props.me.lat, props.me.lng), content: meEl(), zIndex: 5 }))
  }
  const points = [...props.facilities, ...(props.me ? [props.me] : [])]
  fitPoints(kakao, map, points, [80, 50, props.bottomInset + 40, 50])
}

watch(() => [props.me, props.facilities], draw)

// ---- 임시 그림 (카카오 키 없음): 좌표의 최소·최대를 0~100 으로 바꿔 그린다 (실제 축척 아님) ----
const fake = computed(() => {
  const points = [...props.facilities, ...(props.me ? [props.me] : [])]
  if (!points.length) return { me: null, facilities: [] }
  const lats = points.map((p) => p.lat)
  const lngs = points.map((p) => p.lng)
  const [minLat, maxLat, minLng, maxLng] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)]
  const pos = (p) => ({
    x: maxLng === minLng ? 50 : ((p.lng - minLng) / (maxLng - minLng)) * 100,
    y: maxLat === minLat ? 50 : ((maxLat - p.lat) / (maxLat - minLat)) * 100,
  })
  return {
    me: props.me ? pos(props.me) : null,
    facilities: props.facilities.map((f) => ({ ...f, ...pos(f) })),
  }
})
</script>

<template>
  <div class="nearby-map">
    <div ref="el" class="canvas" :class="{ fake: mode === 'fake' }">
      <template v-if="mode === 'fake'">
        <div class="area" :style="{ bottom: bottomInset + 40 + 'px' }">
          <span v-for="f in fake.facilities" :key="f.id" class="nearby-marker fake-pos" :title="f.name"
            :style="{ left: f.x + '%', top: f.y + '%' }">{{ f.no }}</span>
          <span v-if="fake.me" class="nearby-me fake-pos" :style="{ left: fake.me.x + '%', top: fake.me.y + '%' }"><i /></span>
        </div>
        <span class="fake-note">임시 지도 · 카카오 키 없음 (위치는 좌표 비율)</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.nearby-map {
  position: absolute;
  inset: 0;
}
.canvas {
  width: 100%;
  height: 100%;
  isolation: isolate; /* 지도 안쪽 z-index 가 위에 뜬 버튼·패널을 덮지 않게 가둔다 */
}
.canvas.fake {
  position: relative;
  background: var(--color-map-bg);
}
.area {
  position: absolute;
  top: 110px;
  left: 56px;
  right: 56px;
}
.fake-note {
  position: absolute;
  top: 84px;
  left: 16px;
  font-size: 11px;
  color: var(--color-text-sub);
}
</style>

<!-- 마커는 카카오 지도 안에 직접 붙이는 요소라 scoped 가 적용되지 않는다 → 전역 style -->
<style>
.nearby-marker {
  width: 28px;
  height: 28px;
  border: 2px solid var(--color-text);
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-card);
  color: var(--color-text);
  font: 700 13px var(--font-base);
}
/* 내 위치: 검은 점 + 주변 회색 원 (설계 스크린샷 모양) */
.nearby-me {
  width: 110px;
  height: 110px;
  border: 1px dashed var(--color-text-sub);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(110, 112, 107, 0.18);
  pointer-events: none;
}
.nearby-me i {
  width: 16px;
  height: 16px;
  border: 4px solid var(--color-card);
  border-radius: 50%;
  background: var(--color-text);
  box-shadow: 0 0 0 2px var(--color-border);
}
.nearby-marker.fake-pos,
.nearby-me.fake-pos {
  position: absolute;
  transform: translate(-50%, -50%);
}
</style>
