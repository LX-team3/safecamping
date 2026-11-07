<script setup>
// 캠핑장 지도: 점수 마커(안전·주의·위험 색) + 고른 캠핑장 이름표 + 범례 · 레이어 · 내 위치 버튼
// 카카오 키(VITE_KAKAO_MAP_KEY)가 있으면 카카오맵, 없으면 좌표 비율로 마커만 놓은 임시 그림을 보여 준다
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'

const props = defineProps({
  camps: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  bottomInset: { type: Number, default: 0 }, // 아래 시트가 가리는 높이(px) → 버튼·마커를 그 위로 올린다
})
const emit = defineEmits(['select'])

const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '점수 없음' }
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
  draw()
  fit()
})

onBeforeUnmount(() => {
  stopSizing?.()
  overlays.forEach((o) => o.setMap(null))
  myOverlay?.setMap(null)
})

// ---- 카카오 지도 마커 ----
// 마커는 카카오 CustomOverlay 에 직접 만든 버튼을 붙인다 (모양은 아래 전역 style 의 .camp-marker)
function markerEl(camp) {
  const selected = camp.id === props.selectedId
  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = `camp-marker ${camp.grade}${selected ? ' selected' : ''}`
  btn.setAttribute('aria-label', `${camp.name} ${GRADE_LABEL[camp.grade]} ${camp.safetyScore ?? ''}`)
  if (selected) {
    btn.appendChild(document.createElement('i')) // 등급 색 점
    btn.appendChild(document.createTextNode(`${camp.name} ${camp.safetyScore ?? '-'}`))
  } else {
    btn.textContent = camp.safetyScore ?? '-'
  }
  btn.addEventListener('click', () => emit('select', camp.id))
  return btn
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
// 캠핑장 좌표의 최소·최대를 0~100% 로 바꿔 마커 위치를 잡는다 (실제 지도 축척 아님)
const fakeMarkers = computed(() => {
  const list = props.camps
  if (!list.length) return []
  const lats = list.map((c) => c.lat)
  const lngs = list.map((c) => c.lng)
  const [minLat, maxLat, minLng, maxLng] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)]
  return list.map((c) => ({
    camp: c,
    left: maxLng === minLng ? 50 : ((c.lng - minLng) / (maxLng - minLng)) * 100,
    top: maxLat === minLat ? 50 : ((maxLat - c.lat) / (maxLat - minLat)) * 100, // 위도는 위가 큼 → 뒤집음
  }))
})
</script>

<template>
  <div class="camp-map">
    <!-- 카카오 지도가 그려질 자리 -->
    <div ref="el" class="canvas" :class="{ fake: mode === 'fake' }">
      <template v-if="mode === 'fake'">
        <span class="fake-note">임시 지도 · {{ message ? '카카오 지도 불러오기 실패' : '카카오 키 없음' }} (위치는 좌표 비율)</span>
        <div class="fake-area" :style="{ bottom: bottomInset + 60 + 'px' }">
          <button v-for="m in fakeMarkers" :key="m.camp.id" type="button" class="camp-marker fake-pos"
            :class="[m.camp.grade, { selected: m.camp.id === selectedId }]"
            :style="{ left: m.left + '%', top: m.top + '%', zIndex: m.camp.id === selectedId ? 10 : 1 }"
            :aria-label="`${m.camp.name} ${GRADE_LABEL[m.camp.grade]} ${m.camp.safetyScore ?? ''}`"
            @click="emit('select', m.camp.id)">
            <template v-if="m.camp.id === selectedId"><i />{{ m.camp.name }} {{ m.camp.safetyScore ?? '-' }}</template>
            <template v-else>{{ m.camp.safetyScore ?? '-' }}</template>
          </button>
        </div>
      </template>
    </div>

    <!-- 오른쪽 위: 레이어(재난 위험 레이어는 2부 예정) + 범례 -->
    <div class="top-right">
      <button type="button" class="round" disabled title="재난 위험 레이어 (준비 중)" aria-label="지도 레이어 (준비 중)">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3l9 5-9 5-9-5z" />
          <path d="M3 13l9 5 9-5" />
        </svg>
      </button>
      <ul class="legend">
        <li><i class="safe" />안전</li>
        <li><i class="caution" />주의</li>
        <li><i class="danger" />위험</li>
      </ul>
    </div>

    <!-- 왼쪽 아래: 내 위치 (카카오 지도일 때만) -->
    <button v-if="mode === 'kakao'" type="button" class="round locate" :style="{ bottom: bottomInset + 16 + 'px' }"
      aria-label="내 위치로" @click="locate">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linejoin="round" aria-hidden="true">
        <path d="M21 3L3 10.5l7.5 3L13.5 21z" />
      </svg>
    </button>

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
  top: 20px;
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
.top-right {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  z-index: 20;
}
.round {
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.round:disabled {
  cursor: default;
}
.legend {
  margin: 0;
  padding: 10px 12px;
  list-style: none;
  border-radius: 10px;
  background: var(--color-card);
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.legend li {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.legend .safe {
  background: var(--color-green);
}
.legend .caution {
  background: var(--color-caution);
}
.legend .danger {
  background: var(--color-danger);
}
.locate {
  position: absolute;
  left: 16px;
  z-index: 20;
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
.camp-marker.fake-pos {
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
/* 고른 캠핑장: 검은 이름표 + 등급 색 점 */
.camp-marker.selected {
  background: var(--color-text);
  padding: 0 12px;
}
.camp-marker i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}
.camp-marker.safe i {
  background: var(--color-green);
}
.camp-marker.caution i {
  background: var(--color-caution);
}
.camp-marker.danger i {
  background: var(--color-danger);
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
