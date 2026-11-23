<script setup>
// 차박 지도: 지도에서 찍은 곳이 차박 불가 · 주의 · 제한 없음 · 야영 가능 중 어디인지 보여 준다
// 탐색 화면(캠핑장 지도)과 위쪽 "캠핑장 | 차박" 토글로 오간다. 아래 탭은 탐색으로 표시
// 판정은 서버 GET /api/chabak/verdict (DB 의 판정 함수가 계산). 지금은 인제군만 판정한다
// 아래 시트는 두 가지 (탐색 화면과 같은 틀):
//  - 둘러보기(아직 안 누름): 검색 · 칩(필터 · 차박 후보지 · 등록 야영장 · 안전점수) · 차박지 목록
//  - 판정 카드(지도나 목록에서 고름): 판정 · 안전점수 · 자세히 보기 · (등록 야영장이면) 내 캠핑에 등록
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import MobileChabakMap from '@/components/mobile/MobileChabakMap.vue'
import MobileLayerSheet from '@/components/mobile/MobileLayerSheet.vue'
import MobileFilterSheet from '@/components/mobile/MobileFilterSheet.vue'
import MobileSpotRow from '@/components/mobile/MobileSpotRow.vue'
import MobilePlanSheet from '@/components/mobile/MobilePlanSheet.vue'
import MobileMapModeToggle from '@/components/mobile/MobileMapModeToggle.vue'
import MobileBottomNav from '@/components/mobile/MobileBottomNav.vue'
import { api } from '@/utils/api'
import { facilityOn, initFacilityOn, onTypes, toggleFacilityOn } from '@/utils/facilityLayers'
import { GRADE_LABEL, INJE_CENTER, LAYER_CHIPS, SAFETY_FILTERS, VERDICT, distanceText, driveMinOf } from '@/utils/chabak'
import { vHscroll } from '@/utils/hscroll'
import { useDragSheet, useElementHeight } from '@/utils/dragSheet'

const mapRef = ref(null)
const picked = ref(null) // 누른 지점 { lat, lng }
const result = ref(null) // 판정 응답 (server ChabakVerdictResponse)
const loading = ref(false)
const error = ref(null) // { message, outside } — outside: 인제군 밖(404)

// ---- 아래 시트 높이: 3단계 (작게 · 카드 · 전체) — 끌기·애니메이션은 utils/dragSheet.js ----
// 판정 카드: 작게 = 손잡이 + 판정 한 줄, 카드 = 내용 높이에 맞춤(화면의 60%까지), 전체 = 위쪽 지도만 조금 남김
// 둘러보기: 작게 = 손잡이 + 검색 + 칩 줄 (탐색과 같음), 카드 = 화면의 55%, 전체 = 목록
const MIN_H = 84
const BROWSE_MIN = 168 // 손잡이 + 검색창 + 칩 줄 (탐색 COLLAPSED 와 같음)
const TOP_GAP = 56 // 전체일 때 위에 남기는 지도 높이
const stageEl = ref(null)
const contentEl = ref(null)
const stageH = useElementHeight(stageEl, 700) // 지도 칸 높이 (아래 탭 뺀 것)
const contentH = useElementHeight(contentEl) // 시트 안 내용의 실제 높이 (결과가 오거나 자세히 보기로 늘어나면 다시 잼)

const browsing = computed(() => !picked.value) // 둘러보기(목록) 상태인지
const snapHeights = computed(() =>
  browsing.value
    ? { min: BROWSE_MIN, card: Math.round(stageH.value * 0.55), full: stageH.value - TOP_GAP }
    : {
        min: MIN_H,
        card: Math.max(MIN_H, Math.min(28 + contentH.value, stageH.value * 0.6)),
        full: stageH.value - TOP_GAP,
      },
)
// 처음엔 지도가 보이게 작게 (탐색과 같음) — 시트를 올리면 목록
const { snap, dragging, style: sheetStyle, events: dragEvents } = useDragSheet(snapHeights, 'min')
// 카드·작게일 때 안쪽 스크롤 칸을 보이는 높이에 맞춘다 (단계가 정해진 뒤에만 바뀜 → 끄는 동안 다시 배치 없음)
// 둘러보기에서는 검색·칩 줄(손잡이 포함 BROWSE_MIN)을 뺀 높이가 목록 칸
const bodyMax = computed(() => snapHeights.value[snap.value] - (browsing.value ? BROWSE_MIN : 28))
const expanded = computed(() => snap.value === 'full') // 자세히 보기 (사유 전부 · 규제구역 · 근처 목록)

// ---- 갈 수 있는 곳 레이어 (차박 후보지 · 등록 야영장) ----
// 화면에 들어오면 둘 다 켜 둔다. "지도 설정" 시트의 "지도에 표시"에서 끄고 켠다
// 받은 목록은 기억해 두고 다시 켤 때 바로 쓴다
const verdictLayers = LAYER_CHIPS.map((c) => ({ ...c, colorVar: VERDICT[c.verdict].colorVar }))
const layerOn = ref({}) // { no: true, open: true, ... } 켜짐 여부
const layerData = ref({}) // type → { items, loading, error }
// 켜 둔 것만, 칩 순서대로 (아래 시트 제목 줄)
const activeLayers = computed(() => verdictLayers.filter((c) => layerOn.value[c.type]))
// "안전점수" 필터: 안전점수가 기준 이상인 곳만 (기준은 캠핑장 등급과 같음)
const safetyFilter = ref('all') // SAFETY_FILTERS 의 key
const safetyMin = computed(() => SAFETY_FILTERS.find((f) => f.key === safetyFilter.value)?.min ?? null)
// 필터를 거친 목록 (받기 전이면 null)
function shownItems(type) {
  const items = layerData.value[type]?.items
  if (!items) return null
  // 구역(불가·주의)은 안전점수가 없어서 거르지 않는다 — "안전점수" 필터는 점(후보지·야영장)에만
  if (LAYER_CHIPS.find((c) => c.type === type)?.zone) return items
  return safetyMin.value == null ? items : items.filter((it) => it.score != null && it.score >= safetyMin.value)
}
// ---- 차박지 필터 시트 (칩 줄: 맨 왼쪽 필터 아이콘 = 구역 표시, "안전점수 ▾" = 안전점수 거르기) ----
const filterOpen = ref(null) // null · 'avoid' · 'safety'
const safetyLabel = computed(() => SAFETY_FILTERS.find((f) => f.key === safetyFilter.value)?.label ?? '전체')
// 필터 시트에 보여 줄 개수 (필터를 거친 것, 받기 전·구역이면 null — 구역은 한 덩어리라 개수가 의미 없음)
const filterCounts = computed(() =>
  Object.fromEntries(verdictLayers.map((c) => [c.type, c.zone ? null : (shownItems(c.type)?.length ?? null)])),
)
// 구역(불가·주의)을 하나라도 켜면 필터 아이콘에 점 표시
const zonesOn = computed(() => verdictLayers.some((c) => c.zone && layerOn.value[c.type]))
// 칩 줄에 보일 "갈 수 있는 곳" 두 개 (차박 후보지 · 등록 야영장)
const spotChips = verdictLayers.filter((c) => !c.zone)

// 지도에 넘길 값: 다 받아 온 레이어만, 필터를 거친 것 [{ type, verdict, items }]
const mapLayers = computed(() =>
  activeLayers.value
    .filter((c) => layerData.value[c.type]?.items)
    .map((c) => ({ type: c.type, verdict: c.verdict, items: shownItems(c.type) })),
)

async function toggleLayer(type) {
  if (layerOn.value[type]) {
    layerOn.value = { ...layerOn.value, [type]: false }
    return
  }
  await showLayer(type)
}

// 켜고 목록을 받아 온다 (처음 들어올 때도 이걸로 둘 다 켬)
async function showLayer(type) {
  layerOn.value = { ...layerOn.value, [type]: true }
  if (layerData.value[type]?.items) return // 전에 받은 것
  layerData.value = { ...layerData.value, [type]: { items: null, loading: true, error: '' } }
  try {
    const items = await api('/api/chabak/layer', { params: { type } })
    layerData.value = { ...layerData.value, [type]: { items, loading: false, error: '' } }
  } catch (e) {
    layerData.value = { ...layerData.value, [type]: { items: null, loading: false, error: e.message } }
  }
}

// 처음 들어오면 "갈 수 있는 곳"(후보지·야영장)만 보여 준다. 피해야 할 곳(구역)은 필터 아이콘에서 켤 때 받아 옴
onMounted(() => LAYER_CHIPS.filter((c) => c.on).forEach((c) => showLayer(c.type))) // 기본 켜짐인 것만 (갈 수 있는 곳)

// ---- 지도 레이어 (안전 시설) — 탐색·내 주변과 같은 "지도 설정" 시트 ----
// 줄 목록은 서버 GET /api/nearby/layers. 처음엔 전부 꺼짐 (탐색과 같음)
// 켠 종류는 지도 화면 안의 시설을 GET /api/facilities 로 받아 지도에 (찍은 지점과 상관없이)
// 판정 카드의 "가까운 시설" 목록은 따로 — 찍은 지점 근처 GET /api/chabak/facilities (종류별 3곳)
const layers = ref([]) // [{ code, icon, label, desc, ready, on }]
// 켠 종류(facilityOn)는 utils/facilityLayers.js — 시설 상세에 갔다 와도 유지 (처음엔 전부 꺼짐)
const layerSheetOpen = ref(false)
const nearFacilities = ref([]) // 찍은 지점 근처 시설 (판정 카드용)
const mapFacilities = ref([]) // 켠 종류의 시설 중 지도 화면 안에 있는 것 (지도용)
let mapBounds = null // 지도가 알려 준 보이는 범위
let facilityReq = 0 // 늦게 온 앞 응답이 새 결과를 덮지 않게

async function loadLayers() {
  try {
    const list = await api('/api/nearby/layers')
    layers.value = list
    initFacilityOn(list)
  } catch {
    // 못 불러오면 지도 설정 버튼을 눌렀을 때 다시 시도한다
  }
}
onMounted(loadLayers)

async function openLayers() {
  if (!layers.value.length) await loadLayers()
  if (layers.value.length) layerSheetOpen.value = true
}
function toggleFacility(code) {
  toggleFacilityOn(code)
  loadMapFacilities()
}
// 지도를 움직이고 멈출 때마다 (MobileChabakMap 의 bounds) 켠 종류를 그 범위로 다시 받는다
function onBounds(b) {
  mapBounds = b
  loadMapFacilities()
}
async function loadMapFacilities() {
  const no = ++facilityReq
  const types = onTypes()
  if (!types.length || !mapBounds) {
    mapFacilities.value = []
    return
  }
  try {
    const list = await api('/api/facilities', { params: { types: types.join(','), ...mapBounds } })
    if (no === facilityReq) mapFacilities.value = list
  } catch {
    // 못 받으면 시설 없이 지도만
  }
}

// 판정 카드 "가까운 시설": 종류마다 가장 가까운 1곳, 가까운 순 (서버는 종류별 3곳을 가까운 순으로 줌)
const nearestByType = computed(() => {
  const seen = new Set()
  return nearFacilities.value.filter((f) => !seen.has(f.type) && seen.add(f.type))
})

// 점수 카드 아래 줄: "소방서 약 4분 · 응급실 약 17분" (캠핑장 상세와 같은 순서, 차박은 직선거리 어림이라 "약")
const scoreSummary = computed(() =>
  [['fire', '소방서'], ['er', '응급실']]
    .map(([type, label]) => {
      const f = nearestByType.value.find((x) => x.type === type)
      return f ? `${label} 약 ${driveMinOf(f.distanceKm)}분` : null
    })
    .filter(Boolean)
    .join(' · '),
)

// 시설은 판정과 따로 받는다 (시설을 못 받아도 판정은 보여 줌)
async function loadFacilities(lat, lng, no) {
  try {
    const list = await api('/api/chabak/facilities', { params: { lat, lng } })
    if (no === requestNo) nearFacilities.value = list
  } catch {
    if (no === requestNo) nearFacilities.value = []
  }
}

// ---- 찍은 지점의 안전점수 (캠핑장 안전점수와 같은 계산, 서버 GET /api/chabak/score) ----
// 판정과 따로 받는다 (점수를 못 받아도 판정은 보여 줌)
const scoreData = ref(null) // { score, grade, erDriveMin, items: [{ type, distM, weight, part }] }
async function loadScore(lat, lng, no) {
  scoreData.value = null
  try {
    const data = await api('/api/chabak/score', { params: { lat, lng } })
    if (no === requestNo) scoreData.value = data
  } catch {
    // 점수 칸만 안 보이고 판정은 그대로
  }
}

// ---- 내 위치: 브라우저 위치 → 지도에 점 + 그 자리 바로 판정 ----
const locating = ref(false)
const locateMsg = ref('')

function locate() {
  locateMsg.value = ''
  if (!navigator.geolocation) {
    locateMsg.value = '이 브라우저는 위치 찾기를 지원하지 않아요.'
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (p) => {
      locating.value = false
      const { latitude: lat, longitude: lng } = p.coords
      mapRef.value?.showMe(lat, lng)
      if (browsing.value) browseSnap = snap.value
      selectedCamp.value = null
      pick(lat, lng) // 인제군 밖이면 시트에 "판정 범위 밖이에요"
    },
    () => {
      locating.value = false
      locateMsg.value = '위치 권한이 없어 내 위치를 찾지 못했어요.'
    },
    { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 },
  )
}

// 빠르게 여러 번 누르면 늦게 온 옛 응답이 새 응답을 덮을 수 있다 → 마지막 요청 번호만 받는다
let requestNo = 0

async function pick(lat, lng) {
  const no = ++requestNo
  picked.value = { lat, lng }
  result.value = null
  error.value = null
  loading.value = true
  locateMsg.value = ''
  snap.value = 'card' // 새로 누르면 카드 높이로 보여 준다
  loadFacilities(lat, lng, no)
  loadScore(lat, lng, no)
  try {
    const data = await api('/api/chabak/verdict', { params: { lat, lng } })
    if (no !== requestNo) return
    result.value = data
  } catch (e) {
    if (no !== requestNo) return
    error.value = { message: e.message, outside: e.status === 404 }
  } finally {
    if (no === requestNo) loading.value = false
  }
}

function goInje() {
  mapRef.value?.moveTo(INJE_CENTER.lat, INJE_CENTER.lng, 10)
  backToBrowse()
}

// 판정 카드 닫기 → 처음 화면(갈 수 있는 곳 · 안전점수 필터)으로
// 지도의 핀·칠한 필지·근처 시설도 같이 지운다. 아직 오는 중인 응답은 버린다 (요청 번호를 올림)
function closeVerdict() {
  requestNo++
  picked.value = null
  result.value = null
  error.value = null
  loading.value = false
  scoreData.value = null
  nearFacilities.value = []
  selectedCamp.value = null
}

// ---- 둘러보기 목록: 켜 둔 차박 후보지 · 등록 야영장을 한 목록으로 (탐색 목록과 같은 모양, MobileSpotRow) ----
// 안전점수 필터 · 검색어를 거쳐 안전점수 높은 순으로 정렬 (응급실 거리는 점수에 이미 들어 있어 정렬 선택은 두지 않음). 누르면 지도를 그 자리로 옮기고 판정 카드
const query = ref('')
const listLoading = computed(() => spotChips.some((c) => layerOn.value[c.type] && layerData.value[c.type]?.loading))
const listItems = computed(() => {
  const q = query.value.trim()
  const all = spotChips
    .filter((c) => layerOn.value[c.type])
    .flatMap((c) => (shownItems(c.type) ?? []).map((it) => ({ ...it, kind: c.type })))
    .filter((it) => !q || [it.name, it.area].some((t) => t?.includes(q)))
  const er = (it) => it.erDriveMin ?? Infinity // 모르는 곳은 뒤로
  const sc = (it) => it.score ?? -1
  return all.sort((a, b) => sc(b) - sc(a) || er(a) - er(b)) // 점수 같으면 응급실 가까운 곳 먼저
})

const fromList = ref(false) // 지금 판정이 목록에서 고른 것인지 (판정 카드에 "‹ 목록으로")
let browseSnap = 'min' // 판정 카드로 가기 전 둘러보기 시트 높이 → 목록으로 돌아올 때 그대로
function pickFromList(item) {
  fromList.value = true
  browseSnap = snap.value
  selectedCamp.value = item.kind === 'camp' && item.id != null ? item : null
  mapRef.value?.moveTo(item.lat, item.lng, 4)
  pick(item.lat, item.lng)
}
// 판정을 닫고 둘러보기(목록)로 — 판정 카드로 가기 전 시트 높이 그대로 (목록을 보던 중이면 목록이 다시 보임)
function backToBrowse() {
  fromList.value = false
  closeVerdict()
  snap.value = browseSnap
}
// 지도를 직접 누름 (점을 눌렀으면 item 이 같이 옴 — 등록 야영장이면 "내 캠핑에 등록"을 보여 주려고)
function onMapPick(lat, lng, item) {
  if (browsing.value) browseSnap = snap.value
  fromList.value = false
  selectedCamp.value = item?.id != null ? item : null
  pick(lat, lng)
}

// ---- 내 캠핑에 등록 (판정 결과가 나온 모든 자리) ----
// 등록 야영장이면 camp.id 로, 그 밖(차박 후보지 · 지도에서 찍은 자리)은 지번 이름 + 좌표로 등록한다 (불가·주의 자리도 — 고르는 건 사람 마음)
// 시트는 캠핑장 상세와 같은 MobilePlanSheet. 내 캠핑 카드에서 누르면 /chabak?lat=&lng= 로 이 자리가 다시 열린다
const selectedCamp = ref(null) // 고른 등록 야영장 { id, name, area, ... }
const planOpen = ref(false)
const PLACE = '강원 인제군' // 판정 범위가 인제군뿐이라 (읍·면은 데이터에 없음)
const planTarget = computed(() => {
  if (!result.value || !picked.value) return null
  if (selectedCamp.value) {
    return { campId: selectedCamp.value.id, name: selectedCamp.value.name, place: selectedCamp.value.area ?? '' }
  }
  // "475-3 잡" → "475-3 잡종지" (지번 끝 지목 한 글자를 빼고 지목 이름을 붙임)
  const base = (result.value.jibun ?? '').replace(/\s*[가-힣]$/, '')
  const name = [base, result.value.jimok].filter(Boolean).join(' ') || '차박지'
  return { campId: null, name, place: PLACE, lat: picked.value.lat, lng: picked.value.lng }
})

// 내 캠핑 카드에서 들어온 경우 (/chabak?lat=&lng=): 지도가 준비되면 그 자리로 가서 판정
const route = useRoute()
function onMapReady() {
  const lat = Number(route.query.lat)
  const lng = Number(route.query.lng)
  if (route.query.lat == null || !Number.isFinite(lat) || !Number.isFinite(lng)) return
  fromList.value = false
  selectedCamp.value = null
  mapRef.value?.moveTo(lat, lng, 4)
  pick(lat, lng)
}

// 근처 차박 후보를 누르면 그 자리로 지도를 옮기고 바로 판정한다
function openAlternative(a) {
  selectedCamp.value = null
  mapRef.value?.moveTo(a.lat, a.lng, 4)
  pick(a.lat, a.lng)
}

const verdict = computed(() => (result.value ? VERDICT[result.value.verdict] : null))
// 접힌 카드에서는 사유를 2줄만
const shownReasons = computed(() => {
  const reasons = result.value?.reasons ?? []
  return expanded.value ? reasons : reasons.slice(0, 2)
})
// "산12-21 임 · 임야 · 국유지" (빈 값은 빼고)
const parcelLine = computed(() =>
  [result.value?.jibun, result.value?.jimok, result.value?.owner].filter(Boolean).join(' · '),
)
</script>

<template>
  <div class="chabak">
    <div ref="stageEl" class="stage">
      <MobileChabakMap ref="mapRef" :picked="picked" :facilities="mapFacilities" :parcel="result?.parcel"
        :verdict="result?.verdict" :layers="mapLayers" @pick="onMapPick" @bounds="onBounds" @ready="onMapReady" />
      <MobileMapModeToggle active="chabak" class="toggle" />


      <!-- 오른쪽 위에 모은 지도 버튼: 지도 설정 · 내 위치 (탐색 지도와 같은 자리·모양, 전역 .map-btns · .map-btn)
           필터는 아래 시트의 칩 줄로 옮김 (탐색과 같은 자리) -->
      <div class="map-btns">
        <button type="button" class="map-btn" aria-label="지도 설정" aria-haspopup="dialog" @click="openLayers">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linejoin="round" aria-hidden="true">
            <path d="M12 3l9 5-9 5-9-5z" />
            <path d="M3 13l9 5 9-5" />
          </svg>
          <small aria-hidden="true">지도</small>
        </button>
        <!-- 내 위치: 지도 앱에서 흔히 쓰는 조준점 모양. 누르면 그 자리 바로 판정 -->
        <button type="button" class="map-btn" :disabled="locating" :aria-busy="locating"
          aria-label="내 위치에서 판정" @click="locate">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="7" />
            <circle cx="12" cy="12" r="2.5" fill="currentColor" />
            <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
          </svg>
          <small aria-hidden="true">내 위치</small>
        </button>
      </div>
      <p v-if="locateMsg" class="locate-msg" role="status">{{ locateMsg }}</p>

      <section class="sheet drag-sheet" :class="{ dragging }" :style="sheetStyle">
        <!-- 손잡이: 끌어서 올리고 내림, 누르면 다음 단계 (작게 → 카드 → 전체) -->
        <button type="button" class="handle drag-handle" :aria-label="snap === 'full' ? '카드 내리기' : '시트 올리기'"
          :aria-expanded="expanded" v-on="dragEvents">
          <span />
        </button>

        <!-- 둘러보기: 검색 + 칩 줄 (탐색과 같은 자리·모양). 시트를 내려도 이 줄은 보인다 -->
        <template v-if="browsing">
          <label class="search">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.5-4.5" />
            </svg>
            <input v-model="query" type="search" placeholder="지번 · 캠핑장 이름 검색" aria-label="지번 · 캠핑장 이름 검색"
              @focus="snap = 'full'" />
          </label>
          <div v-hscroll class="filters hscroll">
            <!-- 구역 표시 (불가 · 주의 구역을 지도에 칠하기). 켜 둔 구역이 있으면 점 -->
            <button type="button" class="chip icon" aria-label="지도에 구역 표시" aria-haspopup="dialog"
              @click="filterOpen = 'avoid'">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" aria-hidden="true">
                <path d="M4 7h16M7 12h10M10 17h4" />
              </svg>
              <i v-if="zonesOn" class="filter-dot" aria-hidden="true" />
            </button>
            <!-- 차박 후보지 · 등록 야영장: 누르면 지도 점과 목록에서 같이 켜고 끔. 동그라미 색 = 지도 점 색 -->
            <button v-for="c in spotChips" :key="c.type" type="button" class="chip spot-chip"
              :class="{ on: layerOn[c.type] }" :aria-pressed="!!layerOn[c.type]" @click="toggleLayer(c.type)">
              <i :style="{ background: `var(${c.colorVar})` }" />{{ c.label }}
            </button>
            <button type="button" class="chip" :class="{ on: safetyFilter !== 'all' }" aria-haspopup="dialog"
              @click="filterOpen = 'safety'">
              안전점수{{ safetyFilter !== 'all' ? ` · ${safetyLabel}` : '' }} ▾
            </button>
          </div>
        </template>

        <div class="body" :class="{ clip: snap === 'min' && !dragging }" :style="{ maxHeight: bodyMax + 'px' }">
          <div ref="contentEl" class="content">
          <!-- 1. 둘러보기 목록: 켜 둔 차박 후보지 · 등록 야영장 (탐색 목록과 같은 모양). 누르면 지도에서 그 자리 판정 -->
          <template v-if="browsing">
            <div class="list-head">
              <h1>차박지 {{ listItems.length }}곳</h1>
            </div>
            <p v-if="listLoading && !listItems.length" class="state">불러오는 중...</p>
            <p v-else-if="!spotChips.some((c) => layerOn[c.type])" class="state">
              차박 후보지 · 등록 야영장을 눌러 목록 확인
            </p>
            <p v-else-if="!listItems.length" class="state">조건에 맞는 곳이 없어요. 안전점수 기준을 넓혀 보세요</p>
            <ol v-else class="spot-list">
              <MobileSpotRow v-for="(it, i) in listItems" :key="`${it.kind}-${it.id ?? it.name}-${it.lat}`" :item="it"
                :rank="i + 1" @open="pickFromList" />
            </ol>
          </template>

          <!-- 2. 판정 중 -->
          <p v-else-if="loading" class="state">판정하는 중...</p>

          <!-- 3. 인제군 밖 · 오류 -->
          <template v-else-if="error">
            <button type="button" class="back" @click="backToBrowse">‹ 목록으로</button>
            <h1>{{ error.outside ? '판정 범위 밖이에요' : '판정하지 못했어요' }}</h1>
            <p class="sub">{{ error.message }}</p>
            <button v-if="error.outside" type="button" class="primary" @click="goInje">인제군으로 이동</button>
            <button v-else type="button" class="outline" @click="pick(picked.lat, picked.lng)">다시 시도</button>
          </template>

          <!-- 4. 판정 결과 -->
          <template v-else-if="result">
            <!-- 돌아가기: 둘러보기(차박지 목록)로. 목록에서 골랐으면 그때 시트 높이 그대로 -->
            <button type="button" class="back" @click="backToBrowse">‹ 목록으로</button>
            <div class="head">
              <span class="badge" :style="{ background: `var(${verdict?.colorVar ?? '--color-text-sub'})` }">
                {{ result.verdict }}
              </span>
              <h1>{{ verdict?.title ?? result.verdict }}</h1>
            </div>
            <p class="sub">{{ parcelLine }}</p>

            <!-- 판정(여기서 자도 되나)은 위의 딱지와 큰 제목이 이미 말하므로 따로 한 줄을 더 두지 않는다
                 안전점수(도움받기 쉬운가)는 캠핑장과 같은 기준·등급 (안전 70↑ · 주의 40↑ · 위험) -->
            <dl class="facts">
              <div v-if="scoreData?.score != null" class="fact">
                <dt>안전점수</dt>
                <dd>
                  <!-- 누르면 시트를 올려 "가까운 시설" 목록 -->
                  <button type="button" class="fact-link" :disabled="!nearestByType.length" @click="snap = 'full'">
                    <i class="grade" :class="scoreData.grade" />
                    <b>{{ scoreData.score }}점 {{ GRADE_LABEL[scoreData.grade] }}</b>
                    <span v-if="scoreSummary" class="fact-sub">· {{ scoreSummary }}</span>
                    <span class="arrow" aria-hidden="true">›</span>
                  </button>
                </dd>
              </div>
            </dl>

            <h2 v-if="expanded && shownReasons.length">판정 사유</h2>
            <ul v-if="shownReasons.length" class="reasons">
              <li v-for="r in shownReasons" :key="r">{{ r }}</li>
            </ul>

            <template v-if="expanded">
              <!-- 가까운 시설: 캠핑장 상세의 목록과 같은 모양 (번호 · 이름 · 거리 · 차로 몇 분). 누르면 시설 상세 -->
              <template v-if="nearestByType.length">
                <h2>가까운 시설</h2>
                <ol class="rows facility-rows">
                  <li v-for="(f, i) in nearestByType" :key="f.id">
                    <RouterLink :to="`/app/facilities/${f.id}`" class="row">
                      <b class="no">{{ i + 1 }}</b>
                      <span class="name">{{ f.type === 'er' ? `응급실 (${f.name})` : f.name }}</span>
                      <span class="dist">{{ f.distanceKm }}km</span>
                      <span class="drive">차로 약 {{ driveMinOf(f.distanceKm) }}분</span>
                      <span class="arrow" aria-hidden="true">›</span>
                    </RouterLink>
                  </li>
                </ol>
              </template>

              <template v-if="result.zones?.length">
                <h2>걸린 규제구역</h2>
                <div class="zones">
                  <span v-for="z in result.zones" :key="z" class="zone">{{ z }}</span>
                </div>
              </template>

              <template v-if="result.alternatives?.length">
                <h2>근처 차박 후보</h2>
                <p class="note">공공데이터상 제한 없는 국공유지</p>
                <ul class="rows">
                  <li v-for="a in result.alternatives" :key="a.jibun">
                    <button type="button" class="row" @click="openAlternative(a)">
                      <i :style="{ background: 'var(--color-verdict-open)' }" />
                      <span class="name">{{ a.jibun }}<template v-if="a.owner"> · {{ a.owner }}</template></span>
                      <span class="dist">{{ distanceText(a.distM) }}</span>
                      <span class="arrow" aria-hidden="true">›</span>
                    </button>
                  </li>
                </ul>
              </template>

              <template v-if="result.nearbyCamps?.length">
                <h2>근처 등록 야영장</h2>
                <!-- 누르면 캠핑장 상세로 (서버가 이름으로 camp.id 를 찾아 붙임. 못 찾은 곳은 누를 수 없게 이름만) -->
                <ul class="rows">
                  <li v-for="c in result.nearbyCamps" :key="c.name">
                    <RouterLink v-if="c.id" :to="`/app/camps/${c.id}`" class="row">
                      <i :style="{ background: 'var(--color-verdict-camp)' }" />
                      <span class="name">{{ c.name }}</span>
                      <span class="dist">{{ distanceText(c.distM) }}</span>
                      <span class="arrow" aria-hidden="true">›</span>
                    </RouterLink>
                    <div v-else class="row static">
                      <i :style="{ background: 'var(--color-verdict-camp)' }" />
                      <span class="name">{{ c.name }}</span>
                      <span class="dist">{{ distanceText(c.distM) }}</span>
                    </div>
                  </li>
                </ul>
              </template>
            </template>

            <button v-if="!expanded" type="button" class="outline" @click="snap = 'full'">자세히 보기</button>
            <!-- 내 캠핑에 일정 등록 (캠핑장 상세와 같은 시트). 등록 야영장도 후보지도 찍은 자리도 가능 -->
            <button v-if="planTarget" type="button" class="primary" aria-haspopup="dialog" @click="planOpen = true">
              내 캠핑에 등록하기
            </button>
          </template>

          <p v-if="!browsing" class="disclaimer">
            공공데이터 기준 참고용 판정이며 법적 경계는 지적측량으로 확정
          </p>
          </div>
        </div>
      </section>

      <!-- 목록을 끝까지 올렸을 때 떠 있는 "지도" 버튼 (탐색과 같음) -->
      <button v-if="browsing && snap === 'full'" type="button" class="to-map" @click="snap = 'min'">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true">
          <path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z" />
          <path d="M9 4v14M15 6v14" />
        </svg>
        지도
      </button>
    </div>

    <MobileBottomNav active="explore" />

    <!-- 지도 설정: 지도 종류 · 안전 시설 (탐색·내 주변과 같은 시트) -->
    <MobileLayerSheet v-if="layerSheetOpen" :layers="layers" :on="facilityOn" @close="layerSheetOpen = false"
      @toggle="toggleFacility" />
    <!-- 차박지 필터: 구역 표시(필터 아이콘) · 안전점수(칩) -->
    <MobileFilterSheet v-if="filterOpen" :only="filterOpen" :spots="verdictLayers" :on="layerOn" :counts="filterCounts"
      :safety="safetyFilter" @close="filterOpen = null" @toggle="toggleLayer" @safety="safetyFilter = $event" />
    <!-- 내 캠핑에 일정 등록 (등록 야영장) -->
    <MobilePlanSheet v-if="planOpen && planTarget" :camp-id="planTarget.campId" :camp-name="planTarget.name"
      :place="planTarget.place" :lat="planTarget.lat ?? null" :lng="planTarget.lng ?? null" @close="planOpen = false" />
  </div>
</template>

<style scoped>
.chabak {
  position: relative; /* "지도 설정" 시트(BottomSheet)가 이 화면 안만 덮게 (없으면 브라우저 전체를 덮음) */
  height: 100%;
  display: flex;
  flex-direction: column;
}
.stage {
  position: relative;
  flex: 1;
  overflow: hidden; /* 시트·지도가 아래 탭을 넘지 않게 */
}
.toggle {
  position: absolute;
  top: 14px;
  left: 16px; /* 왼쪽 위. 지도 버튼은 오른쪽 위에 세로로 (.map-btns) */
  z-index: 20;
}
/* 목록 화면 */
.back {
  align-self: flex-start;
  height: 32px;
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-sub);
  cursor: pointer;
}
/* ---- 둘러보기: 검색 · 칩 줄 · 목록 머리 (탐색 화면과 같은 모양) ---- */
.search {
  flex: none;
  height: 48px;
  padding: 0 16px;
  border-radius: 24px;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  gap: 10px;
}
.search input {
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  font-size: 15px;
  outline: none;
}
.filters {
  flex: none;
  display: flex;
  gap: 8px;
  margin: 12px -16px 0;
  padding: 0 16px 12px;
  /* 가로 넘김·막대 모양은 전역 .hscroll (styles/base.css) */
}
.chip {
  position: relative;
  flex: none;
  height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: 21px;
  background: var(--color-card);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  white-space: nowrap;
  cursor: pointer;
}
.chip.icon {
  width: 42px;
  padding: 0;
  justify-content: center;
}
/* 켜짐: 탐색의 "내 유형 맞춤" 켜짐과 같은 검은 칩 */
.chip.on {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-card);
  font-weight: 700;
}
/* 차박 후보지 · 등록 야영장 칩의 동그라미 = 지도 점 색 */
.spot-chip i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}
.spot-chip.on i {
  box-shadow: 0 0 0 2px var(--color-card);
}
/* 구역을 켜 두면 필터 아이콘 오른쪽 위 작은 점 */
.filter-dot {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 8px;
  height: 8px;
  border: 2px solid var(--color-card);
  border-radius: 50%;
  background: var(--color-verdict-no);
}
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.spot-list {
  margin: 0;
  padding: 0 0 72px; /* 아래 떠 있는 "지도" 버튼에 마지막 줄이 가리지 않게 */
  list-style: none;
}
/* 목록을 끝까지 올렸을 때 떠 있는 "지도" 버튼 (탐색과 같음) */
.to-map {
  position: absolute;
  left: 50%;
  bottom: 20px;
  z-index: 40;
  transform: translateX(-50%);
  height: 48px;
  padding: 0 24px;
  border: none;
  border-radius: 24px;
  background: var(--color-primary);
  color: var(--color-card);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  cursor: pointer;
}
.sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  padding: 0 16px;
  border-radius: 20px 20px 0 0;
  background: var(--color-card);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  /* 높이 애니메이션·끄는 동안 멈춤은 전역 .drag-sheet (styles/base.css) */
}
.handle {
  flex: none;
  height: 28px;
  margin: 0 -16px;
  padding: 0;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 위치 권한 안내: 위쪽 버튼들 아래 */
.locate-msg {
  position: absolute;
  top: 124px;
  left: 16px;
  right: 16px;
  z-index: 20;
  margin: 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 13px;
}
.handle span {
  width: 44px;
  height: 5px;
  border-radius: 3px;
  background: var(--color-border);
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
/* 작게일 때는 판정 한 줄만 보이고 스크롤도 막는다 */
.body.clip {
  overflow: hidden;
}
.content {
  padding-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
h1 {
  margin: 0;
  font-size: 21px;
  font-weight: 900;
}
h2 {
  margin: 8px 0 0;
  font-size: 15px;
  font-weight: 700;
}
.sub {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.badge {
  flex: none;
  height: 30px;
  padding: 0 12px;
  border-radius: 15px;
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-card);
}
.legend {
  margin: 0;
  padding: 12px 14px;
  list-style: none;
  border-radius: 12px;
  background: var(--color-bg-soft);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  font-size: 13px;
}
.legend li,
.row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.legend i,
.row i {
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.reasons {
  margin: 0;
  padding: 12px 14px 12px 30px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  line-height: 20px;
}
.zones {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.zone {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  font-size: 13px;
}
.note {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--color-text-sub);
}
.rows {
  margin: 0;
  padding: 0;
  list-style: none;
}
.row {
  width: 100%;
  height: 48px;
  padding: 0;
  border: none;
  border-bottom: 1px solid var(--color-line);
  background: none;
  gap: 10px;
  text-align: left;
  cursor: pointer;
}
.row.static {
  cursor: default;
}
.row .name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row .dist {
  font-size: 13px;
  color: var(--color-text-sub);
}
/* 가까운 시설 목록 (캠핑장 상세 목록 모양: 검은 동그라미 번호 · 거리 · 차로 몇 분) */
.facility-rows .row {
  height: 56px;
}
.facility-rows .no {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.facility-rows .drive {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-caution);
}
/* 이름표 붙은 줄: 안전점수 (판정은 위의 딱지·제목이 말함) */
.facts {
  margin: 0;
  padding: 4px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
}
.fact {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.fact + .fact {
  border-top: 1px solid var(--color-line);
}
.fact dt {
  flex: none;
  width: 60px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.fact dd {
  flex: 1;
  min-width: 0;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
}
.fact i {
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
/* 안전 등급 점 색: 캠핑장 등급과 같은 변수 (안전 초록 · 주의 주황 · 위험 빨강) */
.fact i.grade {
  background: var(--color-text-sub);
}
.fact i.grade.safe {
  background: var(--color-green);
}
.fact i.grade.caution {
  background: var(--color-caution);
}
.fact i.grade.danger {
  background: var(--color-danger);
}
.fact-link {
  flex: 1;
  min-width: 0;
  min-height: 44px;
  padding: 0;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  gap: 6px;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.fact-link:disabled {
  cursor: default;
}
.fact-link .fact-sub {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fact-link .arrow {
  color: var(--color-arrow);
}
.row .arrow {
  color: var(--color-arrow);
}
.primary,
.outline {
  flex: none;
  height: 48px;
  border-radius: var(--radius-input);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.primary {
  border: none;
  background: var(--color-primary);
  color: var(--color-card);
}
.outline {
  border: 1px solid var(--color-border);
  background: var(--color-card);
}
.state {
  margin: 16px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.disclaimer {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--color-text-sub);
}
</style>
