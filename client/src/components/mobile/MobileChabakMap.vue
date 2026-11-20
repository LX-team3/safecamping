<script setup>
// 차박 지도: 지도를 누르면 그 위치를 화면(ChabakView)에 알리고, 받은 필지 경계를 판정 색으로 칠한다
// 카카오 키가 있으면 카카오맵, 없으면 시연 지점 버튼을 눌러 볼 수 있는 임시 화면 (NearbyMap 과 같은 방식)
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'
import { applyMapType, mapType } from '@/utils/mapType'
import { DEMO_POINTS, INJE_CENTER, verdictColor } from '@/utils/chabak'
import { useRouter } from 'vue-router'
import { facilityBadgeEl, facilityPopupEl } from '@/utils/facilityIcon'
import { FACILITY_LABEL } from '@/utils/place'

const props = defineProps({
  picked: { type: Object, default: null }, // 누른 지점 { lat, lng }
  facilities: { type: Array, default: () => [] }, // 지도 설정에서 켠 종류의 시설 (지도 화면 안) [{ id, type, name, lat, lng }]
  parcel: { type: Object, default: null }, // 필지 경계 GeoJSON (Polygon · MultiPolygon)
  verdict: { type: String, default: null }, // 불가 · 주의 · 제한 없음 · 야영 가능
  // 판정 표시로 켠 레이어들 [{ type, verdict, items: [{ name, lat, lng, geometry }] }] (여러 개 가능)
  // 구역(불가·주의)은 geometry 면, 후보지·야영장은 lat·lng 점
  layers: { type: Array, default: () => [] },
})
// pick: 지도를 누름 (lat, lng, 점을 눌렀으면 그 item) / bounds: 지도를 움직이고 멈춤 → 보이는 범위 { swLat, swLng, neLat, neLng } (켠 시설을 이 범위로 받음)
// ready: 지도를 쓸 수 있게 됨 (카카오 지도 또는 임시 화면) — 화면이 "내 캠핑에서 온 자리"를 열 때 기다린다
const emit = defineEmits(['pick', 'bounds', 'ready'])

const router = useRouter()
const el = ref(null)
const mode = ref('loading') // loading → kakao | fake

let kakao = null
let map = null
let drawn = [] // 지도에 올린 다각형·핀 (다시 그릴 때 지운다)
let stopSizing = null

// 지도 종류를 바꾸면 바로 적용 (세 지도가 같은 값을 봄)
watch(mapType, () => applyMapType(kakao, map))

onMounted(async () => {
  try {
    kakao = await loadKakaoMap()
  } catch {
    kakao = null // 지도를 못 불러와도 임시 화면으로 계속 쓸 수 있게
  }
  if (!el.value) return // 불러오는 사이 다른 화면으로 나갔으면 그리지 않는다
  if (!kakao) {
    mode.value = 'fake'
    emit('ready')
    return
  }
  mode.value = 'kakao'
  map = new kakao.maps.Map(el.value, {
    center: new kakao.maps.LatLng(INJE_CENTER.lat, INJE_CENTER.lng),
    level: 10,
  })
  stopSizing = keepMapSized(map, el.value)
  applyMapType(kakao, map) // 지도 설정 시트에서 고른 종류 (지도·스카이뷰)
  // 지도를 누른 곳의 위도·경도를 화면에 알린다 → 화면이 판정 API 를 부른다
  kakao.maps.event.addListener(map, 'click', (e) => {
    closeFacilityPopup()
    emit('pick', e.latLng.getLat(), e.latLng.getLng())
  })
  // idle = 끌기·확대가 끝나 멈췄을 때 한 번 (움직이는 동안 계속 부르지 않게)
  kakao.maps.event.addListener(map, 'idle', () => {
    const b = map.getBounds()
    const sw = b.getSouthWest()
    const ne = b.getNorthEast()
    emit('bounds', { swLat: sw.getLat(), swLng: sw.getLng(), neLat: ne.getLat(), neLng: ne.getLng() })
  })
  draw()
  syncLayers()
  emit('ready')
})

onBeforeUnmount(() => {
  stopSizing?.()
  drawn.forEach((o) => o.setMap(null))
  Object.values(layerDrawn).forEach((list) => list.forEach((o) => o.setMap(null)))
  closeFacilityPopup()
})
// ---- 시설 말풍선: 배지를 누르면 위에 "종류 · 이름 · 상세 ›" (한 번에 하나만). 말풍선을 누르면 시설 상세 화면 ----
let facPopup = null
let facPopupId = null
function closeFacilityPopup() {
  facPopup?.setMap(null)
  facPopup = null
  facPopupId = null
}
function openFacilityPopup(f) {
  closeFacilityPopup()
  const content = facilityPopupEl(f, FACILITY_LABEL[f.type] ?? '시설', () => router.push(`/app/facilities/${f.id}`))
  facPopup = new kakao.maps.CustomOverlay({
    map, position: new kakao.maps.LatLng(f.lat, f.lng), content, yAnchor: 1, zIndex: 30,
    clickable: true, // 말풍선을 누른 것이 지도 클릭으로 넘어가지 않게
  })
  facPopupId = f.id
}
// 배지 하나: 누르면 말풍선 (지도 클릭(차박 판정 등)으로 넘어가지 않게 막는다)
function tappableBadge(f, badge) {
  badge.classList.add('tappable')
  badge.title = f.name
  badge.addEventListener('click', (e) => {
    e.stopPropagation()
    if (facPopupId === f.id) closeFacilityPopup()
    else openFacilityPopup(f)
  })
  return badge
}


// ---- 판정 표시 레이어 (불가 구역 · 주의 구역 · 차박 후보지 · 등록 야영장, 여러 개 같이) ----
// 판정 핀·필지와 따로, 레이어마다 따로 그린다 → 하나를 켜고 끌 때 나머지(구역 수백 개)는 다시 그리지 않게
const layerDrawn = {} // type → 지도에 올린 것들
const layerBounds = {} // type → 그 레이어가 다 보이는 범위
// 겹칠 때 위에 오는 순서: 점(후보지·야영장) > 불가 구역 > 주의 구역
const LAYER_Z = { caution: 1, no: 2, open: 3, camp: 3 }

function layerDotEl(item, color) {
  const dot = document.createElement('button')
  dot.type = 'button'
  dot.className = 'chabak-dot'
  dot.style.setProperty('--dot', color) // 보이는 점 색 (누르는 범위는 투명하게 더 넓음)
  dot.title = item.name
  dot.setAttribute('aria-label', `${item.name} 판정 보기`)
  // 점을 누르면 그 자리를 판정한다 (지도 클릭과 같은 흐름)
  dot.addEventListener('click', (e) => {
    e.stopPropagation()
    emit('pick', item.lat, item.lng, item) // 어느 점인지도 같이 (등록 야영장이면 판정 카드에 "내 캠핑에 등록")
  })
  return dot
}

// 레이어 하나를 그리고, 그 범위를 돌려준다
function drawOneLayer(layer) {
  const color = verdictColor(layer.verdict)
  const zIndex = LAYER_Z[layer.type] ?? 1
  const bounds = new kakao.maps.LatLngBounds()
  const list = []
  layer.items.forEach((item) => {
    if (item.geometry) {
      toPolygons(item.geometry).forEach((path) => {
        path.forEach((ring) => ring.forEach((p) => bounds.extend(p)))
        // 구역은 옅게 칠한다 (필지 판정 색보다 연하게 → 눌러서 본 필지가 더 잘 보임)
        list.push(new kakao.maps.Polygon({
          map, path, zIndex, strokeWeight: 1.5, strokeColor: color, strokeOpacity: 0.9, fillColor: color, fillOpacity: 0.18,
        }))
      })
    } else if (item.lat != null && item.lng != null) {
      const position = new kakao.maps.LatLng(item.lat, item.lng)
      bounds.extend(position)
      list.push(new kakao.maps.CustomOverlay({
        map, position, content: layerDotEl(item, color), clickable: true, zIndex,
      }))
    }
  })
  layerDrawn[layer.type] = list
  return list.length ? bounds : null
}

// 켠 레이어 목록이 바뀌면: 꺼진 것은 지우고, 새로 켠 것은 그리고, 목록이 바뀐 것(주변 안전 필터)은 다시 그린다
// 새로 켠 레이어가 있으면 다 보이게 범위를 맞춘다 (필터만 바꿨을 때는 지도를 움직이지 않음 — 덜 어지럽게)
const layerItemsOf = {} // type → 그릴 때 쓴 목록 (같은 목록이면 다시 그리지 않음)
function removeLayer(type) {
  layerDrawn[type].forEach((o) => o.setMap(null))
  delete layerDrawn[type]
  delete layerBounds[type]
  delete layerItemsOf[type]
}
function syncLayers() {
  if (!map) return
  const now = new Set(props.layers.map((l) => l.type))
  Object.keys(layerDrawn).forEach((type) => {
    if (!now.has(type)) removeLayer(type)
  })
  let added = false
  props.layers.forEach((layer) => {
    if (layerDrawn[layer.type]) {
      if (layerItemsOf[layer.type] === layer.items) return // 이미 그려 둔 그대로
      removeLayer(layer.type) // 필터로 목록이 바뀜 → 다시 그림 (범위는 그대로)
    } else {
      added = true
    }
    layerBounds[layer.type] = drawOneLayer(layer)
    layerItemsOf[layer.type] = layer.items
  })
  // 새로 켠 것이 있으면, 지금 켜 있는 레이어 전부가 보이게 범위를 맞춘다 (후보지 + 야영장이 같이 보이게)
  if (!added) return
  const all = new kakao.maps.LatLngBounds()
  let any = false
  props.layers.forEach((layer) => {
    const b = layerBounds[layer.type]
    if (!b) return
    all.extend(b.getSouthWest())
    all.extend(b.getNorthEast())
    any = true
  })
  if (any) map.setBounds(all, 70, 70, 110, 20)
}

watch(() => props.layers, syncLayers)

// GeoJSON 좌표는 [경도, 위도] 순서 → 카카오는 LatLng(위도, 경도)
function toPolygons(geo) {
  if (!geo) return []
  const polys = geo.type === 'MultiPolygon' ? geo.coordinates : geo.type === 'Polygon' ? [geo.coordinates] : []
  return polys.map((rings) => rings.map((ring) => ring.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng))))
}

function pinEl() {
  const span = document.createElement('span')
  span.className = 'chabak-pin'
  return span
}

function draw() {
  if (!map) return
  drawn.forEach((o) => o.setMap(null))
  drawn = []
  const color = verdictColor(props.verdict)
  // 바깥 테두리 + 구멍(안쪽 고리)을 path 배열로 한 번에 넘기면 구멍까지 그려진다
  toPolygons(props.parcel).forEach((path) =>
    drawn.push(new kakao.maps.Polygon({
      map, path, strokeWeight: 3, strokeColor: color, strokeOpacity: 1, fillColor: color, fillOpacity: 0.3,
    })),
  )
  // 안전 시설: 탐색 지도와 같은 흰 바탕 아이콘 배지 (plain)
  props.facilities.forEach((f) => {
    const badge = tappableBadge(f, facilityBadgeEl({ type: f.type, plain: true }))
    drawn.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(f.lat, f.lng), content: badge, zIndex: 3, clickable: true }))
  })
  if (facPopupId != null && !props.facilities.some((f) => f.id === facPopupId)) closeFacilityPopup() // 그 시설이 꺼졌으면 말풍선도
  if (props.picked) {
    const position = new kakao.maps.LatLng(props.picked.lat, props.picked.lng)
    drawn.push(new kakao.maps.CustomOverlay({ map, position, content: pinEl(), yAnchor: 1, zIndex: 5 }))
  }
}

watch(() => [props.picked, props.parcel, props.verdict, props.facilities], draw)

// 판정 결과(필지)가 오면, 지도가 멀리 축소돼 있을 때만 그 필지가 보이게 확대한다
// (차박 후보지 점처럼 축소된 지도에서 누르면 칠한 필지가 점·핀에 가려 안 보여서)
// 이미 충분히 확대돼 있으면(레벨 5 이하) 지도를 움직이지 않는다 — 덜 어지럽게
const FIT_LEVEL = 5
watch(() => props.parcel, (parcel) => {
  if (!map || !parcel || map.getLevel() <= FIT_LEVEL) return
  const bounds = new kakao.maps.LatLngBounds()
  toPolygons(parcel).forEach((path) => path.forEach((ring) => ring.forEach((p) => bounds.extend(p))))
  // 아래 판정 카드(화면 아래쪽 절반쯤)에 가리지 않게 아래 여백을 크게
  map.setBounds(bounds, 90, 60, 320, 60)
  if (map.getLevel() < 3) map.setLevel(3) // 아주 작은 필지는 너무 확대되지 않게
})

// 화면에서 지도를 옮길 때 (인제군으로 이동 · 근처 후보 누르기)
function moveTo(lat, lng, level) {
  if (!map) return
  if (level) map.setLevel(level)
  map.panTo(new kakao.maps.LatLng(lat, lng))
}

// 내 위치: 초록 점을 찍고 그 자리로 확대해 옮긴다 (판정 핀과 따로 남겨 둔다)
let meOverlay = null
function showMe(lat, lng) {
  if (!map) return
  const position = new kakao.maps.LatLng(lat, lng)
  const dot = document.createElement('span')
  dot.className = 'chabak-me'
  meOverlay?.setMap(null)
  meOverlay = new kakao.maps.CustomOverlay({ map, position, content: dot, zIndex: 4 })
  map.setLevel(4)
  map.panTo(position)
}
onBeforeUnmount(() => meOverlay?.setMap(null))

defineExpose({ moveTo, showMe })
</script>

<template>
  <div class="chabak-map">
    <div ref="el" class="canvas" :class="{ fake: mode === 'fake' }">
      <div v-if="mode === 'fake'" class="fake-area">
        <p class="fake-note">임시 지도 · 카카오 키 없음<br />아래 시연 지점을 눌러 판정을 볼 수 있어요</p>
        <button v-for="p in DEMO_POINTS" :key="p.label" type="button" class="demo"
          :class="{ on: picked && picked.lat === p.lat && picked.lng === p.lng }" @click="emit('pick', p.lat, p.lng)">
          {{ p.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chabak-map {
  position: absolute;
  inset: 0;
}
.canvas {
  width: 100%;
  height: 100%;
  isolation: isolate; /* 지도 안쪽 z-index 가 위에 뜬 토글·시트를 덮지 않게 가둔다 */
}
.canvas.fake {
  background: var(--color-map-bg);
}
.fake-area {
  padding: 80px 24px 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.fake-note {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.demo {
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-card);
  font-size: 14px;
  cursor: pointer;
}
.demo.on {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-card);
}
</style>

<!-- 핀은 카카오 지도 안에 직접 붙이는 요소라 scoped 가 적용되지 않는다 → 전역 style -->
<style>
.chabak-pin {
  display: block;
  width: 18px;
  height: 18px;
  border: 3px solid var(--color-card);
  border-radius: 50% 50% 50% 0;
  background: var(--color-text);
  transform: rotate(-45deg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  pointer-events: none; /* 핀 위를 눌러도 지도 클릭으로 전달 */
}
/* 칩 레이어 점 (차박 후보지 · 등록 야영장). 바탕색은 판정 색으로 스크립트가 넣는다 */
/* 누르는 범위는 44px(손가락으로 누르기 충분한 크기), 보이는 점은 가운데 16px
   → 지도는 깔끔하게, 누르기는 쉽게 (옆 땅이 잘못 눌려 엉뚱한 판정이 나오지 않게) */
.chabak-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}
.chabak-dot::after {
  content: '';
  width: 16px;
  height: 16px;
  box-sizing: border-box;
  border: 2px solid var(--color-card);
  border-radius: 50%;
  background: var(--dot);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}
/* 내 위치 점 (탐색 지도 CampMap 의 .my-location 과 같은 모양) */
.chabak-me {
  display: block;
  width: 16px;
  height: 16px;
  border: 3px solid var(--color-card);
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 6px rgba(79, 93, 51, 0.25);
  pointer-events: none;
}
</style>
