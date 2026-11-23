<script setup>
// 웹 캠핑장 지도: 카카오맵 위에 설계(4-1)의 물방울 핀 + 안전점수를 올린다
//  - 추천(내 유형 TOP 5)은 금빛→초록 그라데이션 큰 핀, 나머지는 등급 색 작은 핀, 고른 것은 가장 큰 핀 + 이름표
//  - 켜 둔 안전시설(facilities)은 모바일 지도와 같은 동그라미 배지 (utils/facilityIcon.js)
// 카카오 키가 없으면(.env.local) 지도 대신 안내를 보여 준다
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { facilityBadgeEl } from '@/utils/facilityIcon'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'
import { applyMapType, mapType, MAP_TYPES } from '@/utils/mapType'

const props = defineProps({
  camps: { type: Array, default: () => [] }, // [{ id, name, lat, lng, safetyScore, tier: rec|safe|mid|warn }]
  facilities: { type: Array, default: () => [] }, // [{ id, type, name, lat, lng }]
  selectedId: { type: [Number, String], default: null }, // 캠핑장 id (차박 후보지는 'open-0' 같은 글자 키)
  fitKey: { type: Number, default: 0 }, // 바뀌면 마커가 다 보이게 범위를 다시 맞춘다
  me: { type: Object, default: null }, // 내 위치 { lat, lng } (파란 점)
  // 고른 캠핑장에서 시설까지 가는 경로: [{ key, type, name, lat, lng, color, label, path? }] (path: [[위도, 경도], ...] 도로 경로)
  routes: { type: Array, default: () => [] },
  focusKey: { type: String, default: null }, // 강조할 경로 key (나머지는 흐리게)
})
// bounds: 지도가 멈췄을 때 보이는 범위 (부모가 켠 시설을 이 범위로 받는다)
const emit = defineEmits(['select', 'bounds'])

const DEFAULT_CENTER = { lat: 38.0697, lng: 128.1707 } // 캠핑장이 없을 때 (인제군청 근처)
const PIN_PATH = 'M12 1C5.9 1 1.5 5.6 1.5 11.4c0 7.2 8.4 15.6 9.6 16.8a1.3 1.3 0 0 0 1.8 0c1.2-1.2 9.6-9.6 9.6-16.8C22.5 5.6 18.1 1 12 1z'
// 핀 크기 (가로, 세로, 점수 글씨 위치 top, 글씨 크기)
const SIZE = { sel: [58, 72, 15, 21], big: [46, 57, 12, 17], small: [32, 40, 8, 13] }

const el = ref(null)
const mode = ref('loading') // loading → kakao | none
const message = ref('')
const layerOpen = ref(false)

// 카카오 객체는 Vue 반응형으로 감싸지 않는다 (지도 내부 객체가 커서 느려짐)
let kakao = null
let map = null
let overlays = []
let myOverlay = null
let stopSizing = null

watch(mapType, () => applyMapType(kakao, map))

onMounted(async () => {
  try {
    kakao = await loadKakaoMap()
  } catch (e) {
    message.value = e.message
  }
  if (!el.value) return
  if (!kakao) {
    mode.value = 'none'
    return
  }
  mode.value = 'kakao'
  map = new kakao.maps.Map(el.value, { center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng), level: 9 })
  stopSizing = keepMapSized(map, el.value)
  applyMapType(kakao, map)
  kakao.maps.event.addListener(map, 'idle', emitBounds)
  draw()
  fit()
})

onBeforeUnmount(() => {
  stopSizing?.()
  overlays.forEach((o) => o.setMap(null))
  myOverlay?.setMap(null)
})

function emitBounds() {
  const b = map.getBounds()
  const sw = b.getSouthWest()
  const ne = b.getNorthEast()
  emit('bounds', { swLat: sw.getLat(), swLng: sw.getLng(), neLat: ne.getLat(), neLng: ne.getLng() })
}

// 물방울 핀 하나 (카카오 CustomOverlay 에 붙이는 버튼). 모양은 아래 전역 style 의 .web-pin
function pinEl(camp) {
  const selected = camp.id === props.selectedId
  const big = camp.tier === 'rec'
  const kind = selected ? 'sel' : big ? 'big' : 'small'
  const [w, h, top, fs] = SIZE[kind]
  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = `web-pin ${kind} tier-${camp.tier}`
  btn.setAttribute('aria-pressed', String(selected))
  btn.setAttribute('aria-label', `${camp.name} 안전점수 ${camp.safetyScore ?? '없음'}`)
  const drop = document.createElement('span')
  drop.className = 'drop'
  drop.style.cssText = `width:${w}px;height:${h}px`
  // 이름은 textContent 로 넣는다 (캠핑장 이름은 서버 값이라 innerHTML 에 넣지 않는다)
  drop.innerHTML = `<svg width="${w}" height="${h}" viewBox="0 0 24 30" aria-hidden="true"><path d="${PIN_PATH}" class="body"/></svg>`
  const score = document.createElement('b')
  score.style.cssText = `top:${top}px;font-size:${fs}px`
  score.textContent = camp.safetyScore ?? '-' // 내 유형 점수를 켜면 탐색 화면이 가산점을 더한 값을 넘겨 준다
  drop.appendChild(score)
  const name = document.createElement('span')
  name.className = 'pin-name'
  name.textContent = camp.name
  btn.append(drop, name)
  btn.addEventListener('click', () => emit('select', camp.id))
  return btn
}

function draw() {
  if (!map) return
  overlays.forEach((o) => o.setMap(null))
  overlays = props.camps.map((camp) => {
    const selected = camp.id === props.selectedId
    return new kakao.maps.CustomOverlay({
      map,
      position: new kakao.maps.LatLng(camp.lat, camp.lng),
      content: pinEl(camp),
      xAnchor: 0.5,
      yAnchor: 1, // 핀 아래 끝(이름표 포함)이 좌표에 온다
      clickable: true, // 핀을 누른 것이 지도 조작으로 넘어가지 않게
      zIndex: selected ? 10 : camp.tier === 'rec' ? 5 : 2,
    })
  })
  drawRoutes()
  drawMe()
  props.facilities.forEach((f) => {
    const badge = facilityBadgeEl({ type: f.type, no: f.no, plain: true })
    badge.title = f.name
    overlays.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(f.lat, f.lng), content: badge, zIndex: 3, clickable: true }))
  })
}

// 고른 캠핑장 → 시설 경로: 직선 + 시설 배지 + "약 N분" (실제 도로 경로가 아니라 직선이다)
function drawRoutes() {
  const from = props.camps.find((c) => c.id === props.selectedId)
  if (!from || !props.routes.length) return
  props.routes.forEach((r) => {
    const dim = props.focusKey && props.focusKey !== r.key
    const pos = new kakao.maps.LatLng(r.lat, r.lng)
    // 도로 경로(path)를 받았으면 그 길을 따라, 없으면 캠핑장→시설 직선
    const line = r.path?.length >= 2
      ? r.path.map(([lat, lng]) => new kakao.maps.LatLng(lat, lng))
      : [new kakao.maps.LatLng(from.lat, from.lng), pos]
    overlays.push(new kakao.maps.Polyline({
      map,
      path: line,
      strokeWeight: props.focusKey === r.key ? 6 : 4,
      strokeColor: r.color,
      strokeOpacity: dim ? 0.3 : 1,
    }))
    const wrap = document.createElement('span')
    wrap.className = 'web-route-end'
    wrap.style.opacity = dim ? '0.45' : '1'
    const badge = facilityBadgeEl({ type: r.type, plain: true })
    badge.title = r.name
    const label = document.createElement('span')
    label.className = 'lbl'
    label.style.color = r.color
    label.textContent = r.label // 시간 글씨 (서버 값, textContent 로 넣는다)
    wrap.append(badge, label)
    overlays.push(new kakao.maps.CustomOverlay({ map, position: pos, content: wrap, zIndex: 4, yAnchor: 0.5 }))
  })
}
// 내 위치 점 (위치 찾기 버튼과 같은 모양)
function drawMe() {
  if (!props.me) return
  const dot = document.createElement('span')
  dot.className = 'my-location'
  overlays.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(props.me.lat, props.me.lng), content: dot, zIndex: 20 }))
}

function fit() {
  if (!map) return
  // 경로가 있으면 캠핑장과 시설이 다 보이게, 없으면 캠핑장 핀 기준
  const from = props.camps.find((c) => c.id === props.selectedId)
  const pts = props.routes.length && from ? [from, ...props.routes] : props.me && !props.camps.length ? [props.me] : props.camps
  fitPoints(kakao, map, pts, [90, 80, 90, 80])
}

watch(() => props.camps, draw)
watch(() => props.fitKey, fit)
watch(() => props.facilities, draw)
watch([() => props.routes, () => props.focusKey, () => props.me], draw)
watch(() => props.selectedId, (id) => {
  draw()
  const camp = props.camps.find((c) => c.id === id)
  // 경로를 보는 중이면 경로 전체가 보이게 맞추고(fit), 아니면 그 캠핑장으로 부드럽게 이동
  if (map && camp && !props.routes.length) map.panTo(new kakao.maps.LatLng(camp.lat, camp.lng))
})
watch(() => props.routes, () => { if (props.routes.length) fit() })

const zoomIn = () => map?.setLevel(map.getLevel() - 1)
const zoomOut = () => map?.setLevel(map.getLevel() + 1)
function locate() {
  message.value = ''
  if (!map) return
  if (!navigator.geolocation) {
    message.value = '이 브라우저는 위치 찾기를 지원하지 않아요.'
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      const here = new kakao.maps.LatLng(pos.coords.latitude, pos.coords.longitude)
      myOverlay?.setMap(null)
      const dot = document.createElement('span')
      dot.className = 'my-location'
      myOverlay = new kakao.maps.CustomOverlay({ map, position: here, content: dot, zIndex: 20 })
      map.setCenter(here)
    },
    () => (message.value = '위치 권한이 없어 내 위치를 찾지 못했어요.'),
  )
}
function pickType(key) {
  mapType.value = key
  layerOpen.value = false
}
</script>

<template>
  <div class="campmap">
    <!-- 추천 핀 그라데이션 (핀 SVG 가 url(#web-rec-grad) 로 가져다 쓴다) -->
    <svg width="0" height="0" aria-hidden="true" style="position: absolute">
      <defs>
        <linearGradient id="web-rec-grad" x1="0" y1="0" x2="0.3" y2="1">
          <stop offset="0" class="g0" /><stop offset="0.5" class="g1" /><stop offset="1" class="g2" />
        </linearGradient>
      </defs>
    </svg>

    <div ref="el" class="map" />
    <p v-if="mode !== 'kakao'" class="empty">
      {{ mode === 'loading' ? '지도를 불러오는 중...' : message || '카카오 지도 키(VITE_KAKAO_MAP_KEY)가 없어 지도를 보여 줄 수 없어요.' }}
    </p>

    <!-- 오른쪽 위: 지도 종류 -->
    <div class="ctrl-top">
      <span class="rel">
        <button type="button" class="sq" aria-label="지도 종류" :aria-expanded="layerOpen" @click="layerOpen = !layerOpen">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linejoin="round" aria-hidden="true"><path d="M12 3 2 8l10 5 10-5-10-5Z" /><path d="m2 13 10 5 10-5" /><path d="m2 17.5 10 5 10-5" /></svg>
        </button>
        <div v-if="layerOpen" class="kinds" role="radiogroup" aria-label="지도 종류">
          <button v-for="k in MAP_TYPES" :key="k.key" type="button" role="radio" :aria-checked="mapType === k.key"
            :class="{ on: mapType === k.key }" @click="pickType(k.key)">{{ k.label }}</button>
        </div>
      </span>
      <slot name="top-right" />
    </div>

    <!-- 오른쪽 아래: 확대 · 축소 · 내 위치 -->
    <div class="ctrl-bottom">
      <div class="zoom">
        <button type="button" aria-label="확대" @click="zoomIn">+</button>
        <button type="button" aria-label="축소" @click="zoomOut">−</button>
      </div>
      <button type="button" class="round" aria-label="내 위치로 이동" @click="locate">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true"><path d="M21 3 3 10.5l7.5 3L14 21l7-18Z" /></svg>
      </button>
    </div>
    <p v-if="mode === 'kakao' && message" class="note" role="status">{{ message }}</p>
  </div>
</template>

<style scoped>
.campmap {
  position: absolute;
  inset: 0;
  background: var(--web-bg-map);
}
.map {
  position: absolute;
  inset: 0;
}
.empty {
  position: absolute;
  inset: 0;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--web-text-sub);
  font-size: 15px;
  text-align: center;
  padding: 0 24px;
}
.ctrl-top {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.rel {
  position: relative;
}
.sq {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.kinds {
  position: absolute;
  right: 58px;
  top: 0;
  z-index: 5;
  padding: 8px;
  border-radius: 14px;
  background: var(--web-bg);
  box-shadow: 0 8px 24px rgba(28, 29, 27, 0.18);
  display: flex;
  gap: 6px;
}
.kinds button {
  border: 0;
  background: none;
  padding: 8px 14px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 13px;
  white-space: nowrap;
  color: var(--web-text);
  cursor: pointer;
}
.kinds button.on {
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.ctrl-bottom {
  position: absolute;
  right: 16px;
  bottom: 28px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.zoom {
  display: flex;
  flex-direction: column;
  background: var(--web-bg);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}
.zoom button {
  width: 44px;
  height: 44px;
  border: 0;
  background: var(--web-bg);
  font-size: 22px;
  color: var(--web-text);
  cursor: pointer;
}
.zoom button + button {
  border-top: 1px solid var(--web-line-panel-soft);
}
.round {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.note {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  margin: 0;
  padding: 8px 16px;
  border-radius: 999px;
  background: var(--web-text);
  color: var(--web-white);
  font-size: 13px;
  z-index: 4;
}
</style>

<!-- 카카오 CustomOverlay 안의 요소는 이 컴포넌트 밖에 만들어져서, scoped 가 아닌 전역 style 이 필요하다 -->
<style>
#web-rec-grad .g0 { stop-color: var(--web-rec-from); }
#web-rec-grad .g1 { stop-color: var(--web-rec-mid); }
#web-rec-grad .g2 { stop-color: var(--web-rec-to); }

.web-pin {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  white-space: nowrap;
  font-family: inherit;
}
.web-pin .drop {
  position: relative;
  display: block;
  filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.3));
}
.web-pin .drop svg {
  position: absolute;
  inset: 0;
}
.web-pin .drop .body {
  stroke: var(--web-white);
  stroke-width: 1.4;
}
.web-pin.tier-rec .body { fill: url(#web-rec-grad); }
.web-pin.tier-safe .body { fill: var(--web-grade-safe); }
.web-pin.tier-mid .body { fill: var(--web-grade-caution); }
.web-pin.tier-warn .body { fill: var(--web-grade-danger); }
.web-pin .drop b {
  position: absolute;
  left: 0;
  right: 0;
  text-align: center;
  font-weight: 800;
  color: var(--web-white);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}
.web-pin .pin-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--web-text);
  text-shadow: -1px -1px 0 #fff, 1px -1px 0 #fff, -1px 1px 0 #fff, 1px 1px 0 #fff, 0 0 4px #fff;
}
.web-pin.big .pin-name {
  font-size: 15px;
  font-weight: 800;
}
.web-pin.sel {
  gap: 4px;
}
/* 경로 끝의 시설 배지 + 시간 */
.web-route-end {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.web-route-end .lbl {
  font-size: 12px;
  font-weight: 700;
  text-shadow: -1px -1px 0 #fff, 1px -1px 0 #fff, -1px 1px 0 #fff, 1px 1px 0 #fff, 0 0 4px #fff;
  white-space: nowrap;
}
/* 내 위치 점 */
.my-location {
  display: block;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: var(--web-link-blue);
  border: 3px solid var(--web-white);
  box-shadow: 0 0 0 4px rgba(62, 111, 168, 0.25);
}
.web-pin.sel .pin-name {
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 16px;
  font-weight: 800;
  text-shadow: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
}
</style>
