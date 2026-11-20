<script setup>
// 캠핑장 지도: 텐트 + 점수 마커(안전·주의·위험 색) + 고른 캠핑장 이름표 · 지도 설정 · 내 위치 버튼
// 카카오 키(VITE_KAKAO_MAP_KEY)가 있으면 카카오맵, 없으면 좌표 비율로 마커만 놓은 임시 그림을 보여 준다
// 텐트 아이콘은 경로 지도 출발지와 같은 것 (utils/facilityIcon.js)
// 지도 설정에서 켠 안전 시설(facilities, 지도 화면 안)도 종류별 회색 아이콘 배지로 그린다
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { facilityBadgeEl, facilityIcon, facilityPopupEl, gradeClass } from '@/utils/facilityIcon'
import { FACILITY_LABEL } from '@/utils/place'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'
import { applyMapType, mapType } from '@/utils/mapType'

const props = defineProps({
  camps: { type: Array, default: () => [] },
  facilities: { type: Array, default: () => [] }, // 켜 둔 종류의 시설 (지도 화면 안) [{ id, type, name, lat, lng }]
  selectedId: { type: Number, default: null },
  bottomInset: { type: Number, default: 0 }, // 아래 시트가 가리는 높이(px) → 버튼·마커를 그 위로 올린다
})
// layers: 오른쪽 위 지도 설정 버튼을 눌렀다 (시트는 부모가 연다)
// bounds: 지도를 움직이고 멈췄다 → 보이는 범위 { swLat, swLng, neLat, neLng } (부모가 켠 시설을 이 범위로 받아 온다)
const emit = defineEmits(['select', 'layers', 'bounds'])

const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '점수 없음' }
const router = useRouter()
const DEFAULT_CENTER = { lat: 38.0697, lng: 128.1707 } // 캠핑장이 하나도 없을 때 (인제군청 근처)

const el = ref(null)
const mode = ref('loading') // loading → kakao | fake
const message = ref('')

// 카카오 객체는 Vue 반응형으로 감싸지 않는다 (지도 내부 객체가 커서 느려짐)
let kakao = null
let map = null
let overlays = []
let myOverlay = null
let stopSizing = null

// 지도 종류를 바꾸면 바로 적용 (세 지도가 같은 값을 봄)
watch(mapType, () => applyMapType(kakao, map))

onMounted(async () => {
  try {
    kakao = await loadKakaoMap()
  } catch (e) {
    message.value = e.message
  }
  if (!el.value) return // 불러오는 사이 다른 화면으로 나갔으면 그리지 않는다
  if (!kakao) {
    mode.value = 'fake'
    return
  }
  mode.value = 'kakao'
  map = new kakao.maps.Map(el.value, {
    center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
    level: 9,
  })
  stopSizing = keepMapSized(map, el.value)
  applyMapType(kakao, map) // 지도 설정 시트에서 고른 종류 (지도·스카이뷰)
  // idle = 끌기·확대가 끝나 멈췄을 때 한 번 (움직이는 동안 계속 부르지 않게)
  kakao.maps.event.addListener(map, 'idle', emitBounds)
  kakao.maps.event.addListener(map, 'click', closeFacilityPopup) // 빈 곳을 누르면 말풍선 닫기
  draw()
  fit()
})

onBeforeUnmount(() => {
  stopSizing?.()
  overlays.forEach((o) => o.setMap(null))
  myOverlay?.setMap(null)
  closeFacilityPopup()
})

function emitBounds() {
  const b = map.getBounds()
  const sw = b.getSouthWest()
  const ne = b.getNorthEast()
  emit('bounds', { swLat: sw.getLat(), swLng: sw.getLng(), neLat: ne.getLat(), neLng: ne.getLng() })
}

// ---- 카카오 지도 마커 ----
// 마커는 카카오 CustomOverlay 에 직접 만든 버튼을 붙인다 (모양은 아래 전역 style 의 .camp-marker)
function markerEl(camp) {
  const selected = camp.id === props.selectedId
  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = `camp-marker ${camp.grade}${selected ? ' selected' : ''}`
  btn.setAttribute('aria-label', `${camp.name} ${GRADE_LABEL[camp.grade]} ${camp.safetyScore ?? ''}`)
  if (selected) {
    btn.appendChild(facilityBadgeEl({ type: 'camp', grade: camp.grade })) // 등급 색 텐트 배지
    btn.appendChild(document.createTextNode(`${camp.name} ${camp.safetyScore ?? '-'}`))
  } else {
    // 등급 색 바탕 위에 흰 텐트 + 점수 (아이콘은 우리 상수에서만 오므로 innerHTML 이 안전)
    const icon = document.createElement('span')
    icon.className = 'camp-icon'
    icon.innerHTML = facilityIcon('camp')
    btn.appendChild(icon)
    btn.appendChild(document.createTextNode(camp.safetyScore ?? '-'))
  }
  btn.addEventListener('click', () => emit('select', camp.id))
  return btn
}


function facilityEl(f) {
  return tappableBadge(f, facilityBadgeEl({ type: f.type, no: f.no, plain: true }))
}
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


function draw() {
  if (!map) return
  overlays.forEach((o) => o.setMap(null))
  overlays = props.camps.map(
    (camp) =>
      new kakao.maps.CustomOverlay({
        map,
        position: new kakao.maps.LatLng(camp.lat, camp.lng),
        content: markerEl(camp),
        clickable: true, // 마커를 누른 것이 지도 조작으로 넘어가지 않게 (휴대폰 터치에 필요)
        zIndex: camp.id === props.selectedId ? 10 : 1, // 고른 마커가 위로
      }),
  )
  // 시설은 캠핑장 마커보다 아래(zIndex 3 < 고른 캠핑장 10) — 고른 캠핑장 이름표를 가리지 않게
  props.facilities.forEach((f) =>
    overlays.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(f.lat, f.lng), content: facilityEl(f), zIndex: 3, clickable: true })),
  )
  if (facPopupId != null && !props.facilities.some((f) => f.id === facPopupId)) closeFacilityPopup() // 그 시설이 꺼졌으면 말풍선도
}

// 마커가 전부 보이게 지도 범위를 맞춘다 (아래 시트에 가리는 만큼 아래 여백을 더 준다)
function fit() {
  if (map) fitPoints(kakao, map, props.camps, [80, 60, props.bottomInset + 60, 60])
}

watch(
  () => props.camps,
  () => {
    draw()
    fit()
  },
)
watch(
  () => props.selectedId,
  (id) => {
    draw()
    const camp = props.camps.find((c) => c.id === id)
    if (map && camp) map.panTo(new kakao.maps.LatLng(camp.lat, camp.lng))
  },
)
// 시설 목록이 바뀜 = 레이어를 켜고 껐거나 지도를 움직여 새로 받았다 (지도 범위는 그대로 둔다)
watch(() => props.facilities, draw)

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

// ---- 임시 그림 (카카오 키 없음) ----
// 캠핑장 + 시설 좌표의 최소·최대를 0~100% 로 바꿔 마커 위치를 잡는다 (실제 지도 축척 아님)
const fake = computed(() => {
  const points = [...props.camps, ...props.facilities]
  if (!points.length) return { camps: [], facilities: [] }
  const lats = points.map((p) => p.lat)
  const lngs = points.map((p) => p.lng)
  const [minLat, maxLat, minLng, maxLng] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)]
  const pos = (p) => ({
    left: maxLng === minLng ? 50 : ((p.lng - minLng) / (maxLng - minLng)) * 100,
    top: maxLat === minLat ? 50 : ((maxLat - p.lat) / (maxLat - minLat)) * 100, // 위도는 위가 큼 → 뒤집음
  })
  return {
    camps: props.camps.map((c) => ({ camp: c, ...pos(c) })),
    facilities: props.facilities.map((f) => ({ ...f, ...pos(f) })),
  }
})
</script>

<template>
  <div class="camp-map">
    <!-- 카카오 지도가 그려질 자리 -->
    <div ref="el" class="canvas" :class="{ fake: mode === 'fake' }">
      <template v-if="mode === 'fake'">
        <span class="fake-note">임시 지도 · {{ message ? '카카오 지도 불러오기 실패' : '카카오 키 없음' }} (위치는 좌표 비율)</span>
        <div class="fake-area" :style="{ bottom: bottomInset + 60 + 'px' }">
          <!-- 시설 (카카오 지도의 facilityEl 과 같은 모양). 아이콘은 우리 상수에서만 오므로 v-html 이 안전 -->
          <span v-for="f in fake.facilities" :key="`f-${f.id}`" class="fac-badge plain camp-fac-pos"
            :title="f.name" :style="{ left: f.left + '%', top: f.top + '%' }">
            <span v-if="facilityIcon(f.type)" class="fac-svg" v-html="facilityIcon(f.type)" />
            <template v-else>{{ f.no }}</template>
            <b v-if="facilityIcon(f.type) && f.no" class="fac-no">{{ f.no }}</b>
          </span>
          <button v-for="m in fake.camps" :key="m.camp.id" type="button" class="camp-marker fake-pos"
            :class="[m.camp.grade, { selected: m.camp.id === selectedId }]"
            :style="{ left: m.left + '%', top: m.top + '%', zIndex: m.camp.id === selectedId ? 10 : 1 }"
            :aria-label="`${m.camp.name} ${GRADE_LABEL[m.camp.grade]} ${m.camp.safetyScore ?? ''}`"
            @click="emit('select', m.camp.id)">
            <!-- 카카오 지도 마커(markerEl)와 같은 모양 -->
            <template v-if="m.camp.id === selectedId">
              <span class="fac-badge" :class="gradeClass(m.camp.grade)"><span class="fac-svg" v-html="facilityIcon('camp')" /></span>
              {{ m.camp.name }} {{ m.camp.safetyScore ?? '-' }}
            </template>
            <template v-else><span class="camp-icon" v-html="facilityIcon('camp')" />{{ m.camp.safetyScore ?? '-' }}</template>
          </button>
        </div>
      </template>
    </div>

    <!-- 오른쪽 위에 모은 지도 버튼: 지도 설정 · 내 위치 (모양은 전역 .map-btns · .map-btn, 차박 지도와 같음). 등급 범례는 뺌 — 마커 색만으로 알 수 있어서 -->
    <div class="map-btns">
      <button type="button" class="map-btn" aria-label="지도 설정" aria-haspopup="dialog" @click="emit('layers')">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3l9 5-9 5-9-5z" />
          <path d="M3 13l9 5 9-5" />
        </svg>
        <small aria-hidden="true">지도</small>
      </button>
      <!-- 내 위치 (카카오 지도일 때만) — 지도 앱에서 흔히 쓰는 조준점 모양 -->
      <button v-if="mode === 'kakao'" type="button" class="map-btn" aria-label="내 위치로" @click="locate">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" aria-hidden="true">
          <circle cx="12" cy="12" r="7" />
          <circle cx="12" cy="12" r="2.5" fill="currentColor" />
          <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
        </svg>
        <small aria-hidden="true">내 위치</small>
      </button>
    </div>

    <p v-if="message" class="message" role="status" :style="{ bottom: bottomInset + 80 + 'px' }">{{ message }}</p>
  </div>
</template>

<style scoped>
.camp-map {
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
.fake-note {
  position: absolute;
  top: 72px; /* 위쪽 가운데 "캠핑장 | 차박" 토글(ExploreView) 아래 */
  left: 16px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.fake-area {
  position: absolute;
  top: 120px;
  left: 56px;
  right: 56px;
}
.message {
  position: absolute;
  left: 16px;
  right: 16px;
  margin: 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 13px;
  z-index: 20;
}
</style>

<!-- 마커는 카카오 지도 안에 직접 붙이는 요소라 scoped 가 적용되지 않는다 → 전역 style (이름 앞에 camp- 를 붙여 겹치지 않게) -->
<style>
.camp-marker {
  min-width: 36px;
  height: 36px;
  padding: 0 9px;
  border: 2px solid var(--color-card);
  border-radius: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--color-card);
  font: 700 14px var(--font-base);
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
  cursor: pointer;
}
.camp-marker.fake-pos,
.fac-badge.camp-fac-pos {
  position: absolute;
  transform: translate(-50%, -50%); /* 좌표가 마커 가운데에 오게 */
}
.camp-marker.safe {
  background: var(--color-green);
}
.camp-marker.caution {
  background: var(--color-caution);
}
.camp-marker.danger {
  background: var(--color-danger);
}
.camp-marker.unknown {
  background: var(--color-text-sub);
}
/* 점수 앞 흰 텐트 (글씨와 같은 흰색 = currentColor) */
.camp-marker .camp-icon {
  display: inline-flex;
  width: 17px;
  height: 17px;
  margin-left: -2px;
}
.camp-marker .camp-icon svg {
  width: 100%;
  height: 100%;
}
/* 고른 캠핑장: 검은 이름표 + 왼쪽 등급 색 텐트 배지 (.fac-badge 는 styles/base.css) */
.camp-marker.selected {
  background: var(--color-text);
  padding: 0 12px 0 3px;
}
.camp-marker.selected .fac-badge {
  --fac-size: 28px;
  box-shadow: none;
}
.my-location {
  display: block;
  width: 16px;
  height: 16px;
  border: 3px solid var(--color-card);
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 6px rgba(79, 93, 51, 0.25);
}
</style>
