<script setup>
// 웹 내 주변 (설계 4-2): 내 위치 + 반경(5·10·20km) 안 캠핑장 목록 → 캠핑장을 고르면 가까운 안전시설까지 경로
// 데이터: 캠핑장 GET /api/camps (내 위치에서 거리는 여기서 계산), 시설 GET /api/nearby?lat=&lng= (고른 캠핑장 좌표 기준),
//         칩 GET /api/nearby/layers, 기상특보 GET /api/alerts?sido=
// 경로: 카카오 도로 경로(GET /api/camps/{id}/routes)를 받으면 그 길을 따라 그리고, 못 받은 시설만 직선 · 서버 시간(차로 n분)을 쓴다
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import WebMapShell from '@/components/web/WebMapShell.vue'
import WebCampMap from '@/components/web/WebCampMap.vue'
import WebFacChips from '@/components/web/WebFacChips.vue'
import WebWeatherBar from '@/components/web/WebWeatherBar.vue'
import { api } from '@/utils/api'
import { FACILITY_LABEL, shortSido } from '@/utils/place'

const route = useRoute()

const RADII = [5, 10, 20]
// 위치를 못 받았을 때 보여 줄 예시 위치 (모바일 내 주변과 같은 인제 설하캠핑장 근처)
const SAMPLE = { lat: 38.14, lng: 128.205 }
const TIER_BY_GRADE = { safe: 'safe', caution: 'mid', danger: 'warn' }
const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의' }
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' }
const typeLabel = (t) => TYPE_LABEL[t] || t || ''

// ---- 내 위치 ----
const me = ref(null)
const isSample = ref(false)
const placeLabel = ref('')
const radius = ref(10)

function locate() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(null)
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 },
    )
  })
}
async function loadMe() {
  const here = await locate()
  isSample.value = !here
  me.value = here || SAMPLE
  // 주소 글씨와 기상특보 지역은 서버가 알려 주는 "가장 가까운 데이터 기준 지역"을 쓴다
  try {
    const res = await api('/api/nearby', { params: me.value })
    const b = res.basis
    placeLabel.value = b ? [shortSido(b.sido), b.sigungu, b.eupmyeon].filter(Boolean).join(' ') : ''
    loadWarnings(b?.sido)
  } catch {
    placeLabel.value = ''
  }
}

// ---- 기상특보 (없어도 화면은 보인다) ----
const warnings = ref(null)
const warnRegion = ref('')
async function loadWarnings(sido) {
  warnings.value = null
  if (!sido) return
  warnRegion.value = shortSido(sido)
  try {
    const res = await api('/api/alerts', { params: { sido: shortSido(sido) } })
    warnings.value = res.activeWarnings?.length ?? null // 발효 중 특보 목록(배열)의 개수
  } catch {
    warnings.value = null
  }
}

// ---- 캠핑장 + 거리 ----
const allCamps = ref([])
const loading = ref(true)
const error = ref('')
async function loadCamps() {
  try {
    allCamps.value = await api('/api/camps', { params: { sort: 'score' } })
  } catch (e) {
    error.value = e.message
  }
}
// 두 좌표 사이 직선거리(km) — 하버사인
function distanceKm(a, b) {
  const R = 6371
  const rad = (d) => (d * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}
const withDist = computed(() =>
  me.value
    ? allCamps.value
        .filter((c) => TIER_BY_GRADE[c.grade])
        .map((c) => ({ ...c, tier: TIER_BY_GRADE[c.grade], km: distanceKm(me.value, c) }))
        .sort((a, b) => a.km - b.km)
    : [],
)
const insideCamps = computed(() => withDist.value.filter((c) => c.km <= radius.value))
const fmtKm = (km) => `${km.toFixed(1)}km`

// ---- 고른 캠핑장 + 시설 경로 ----
const selectedId = ref(null)
const selected = computed(() => withDist.value.find((c) => c.id === selectedId.value) || null)
const nearby = ref(null) // 고른 캠핑장 기준 GET /api/nearby 응답
const roadRoutes = ref([]) // 카카오 자동차 경로 GET /api/camps/{id}/routes [{ facilityId, ok, durationMin, distanceKm, path }]
const focusKey = ref(null)
const layers = ref([])
const on = ref({}) // { er: true, ... } 지도·목록에 보일 시설 종류

async function pick(id) {
  selectedId.value = selectedId.value === id ? null : id
  focusKey.value = null
  nearby.value = null
  roadRoutes.value = []
  const c = selected.value
  if (!c) return
  // 경로는 따로 받는다: 카카오가 느리거나 실패해도 시설 목록은 먼저 보여야 해서 (못 받으면 직선)
  api(`/api/camps/${c.id}/routes`)
    .then((res) => { if (selectedId.value === c.id && res) roadRoutes.value = res.routes })
    .catch(() => {})
  try {
    nearby.value = await api('/api/nearby', { params: { lat: c.lat, lng: c.lng } })
  } catch (e) {
    error.value = e.message
  }
}
// 이 시설까지 받은 도로 경로 (없으면 null → 직선 · 서버 시간)
const roadOf = (f) => roadRoutes.value.find((r) => r.facilityId === f.id && r.ok) || null
const minOf = (f) => roadOf(f)?.durationMin ?? f.driveMin
const kmOf = (f) => roadOf(f)?.distanceKm ?? f.distanceKm
async function loadLayers() {
  try {
    const list = await api('/api/nearby/layers')
    layers.value = list.filter((l) => l.ready)
    on.value = Object.fromEntries(list.map((l) => [l.code, l.on]))
  } catch {
    // 못 받으면 칩이 안 보이고 시설은 전부 보인다
  }
}
const toggleChip = (code) => (on.value = { ...on.value, [code]: !on.value[code] })

const facs = computed(() => (nearby.value?.facilities || []).filter((f) => on.value[f.type] ?? true))
const CHIP_COLOR = { er: 'hospital', fire: 'fire', police: 'police', store: 'store', pharmacy: 'pharmacy', shelter: 'shelter' }
// 경로·행 색: 설계의 종류별 색 (CSS 변수 값을 읽어 지도에 칠한다)
const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()
const colorOf = (type) => cssVar(`--web-fac-${CHIP_COLOR[type] || 'shelter'}`)
const routes = computed(() =>
  facs.value.map((f) => ({ key: String(f.id), type: f.type, name: f.name, lat: f.lat, lng: f.lng, color: colorOf(f.type), label: `약 ${minOf(f)}분`, path: roadOf(f)?.path })),
)
const titleOf = (f) => {
  const label = FACILITY_LABEL[f.type] || ''
  return f.name.includes(label) ? f.name : `${label} · ${f.name}`
}

// 지도에는 목록 모드면 반경 안 캠핑장, 고른 뒤에는 그 캠핑장만
const mapCamps = computed(() => (selected.value ? [selected.value] : insideCamps.value))
const fitKey = computed(() => mapCamps.value.length * 100 + radius.value + (selected.value ? 1 : 0))

onMounted(async () => {
  await Promise.all([loadMe(), loadCamps(), loadLayers()])
  loading.value = false
  // 탐색의 정보 카드 "안전시설" 버튼으로 들어오면 그 캠핑장을 바로 고른다 (?camp=id)
  const id = Number(route.query.camp)
  if (id && withDist.value.some((c) => c.id === id)) pick(id)
})
watch(() => route.query.camp, (v) => {
  const id = Number(v)
  if (id && id !== selectedId.value && withDist.value.some((c) => c.id === id)) pick(id)
})
</script>

<template>
  <WebMapShell active="nearby">
    <template #side>
      <!-- 내 위치 + 반경 -->
      <div class="where">
        <div class="where-row">
          <div class="me">
            <span class="me-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" /></svg>
            </span>
            <span class="me-text">
              <span class="k">{{ isSample ? '예시 위치 (위치 권한 없음)' : '현재 내 위치' }}</span>
              <span class="v">{{ placeLabel || '위치를 찾는 중...' }}</span>
            </span>
          </div>
          <button type="button" class="refresh" aria-label="내 위치 다시 받기" @click="loadMe">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 11a8 8 0 1 0-2.3 5.7" /><path d="M20 4v7h-7" /></svg>
          </button>
        </div>
        <div class="radii">
          <span class="rk">반경</span>
          <button v-for="r in RADII" :key="r" type="button" class="r" :class="{ on: radius === r }" :aria-pressed="radius === r"
            @click="radius = r">{{ r }}km</button>
        </div>
      </div>

      <!-- 모드 1: 반경 안 캠핑장 목록 -->
      <template v-if="!selected">
        <div class="head2">
          <div class="t">반경 {{ radius }}km 안 캠핑장 <span class="cnt">{{ insideCamps.length }}</span>곳</div>
          <span class="s">가까운 순</span>
        </div>
        <div class="list">
          <p v-if="error" class="state">{{ error }}</p>
          <p v-else-if="loading" class="state">불러오는 중...</p>
          <p v-else-if="!insideCamps.length" class="state">반경 안에 캠핑장이 없어요. 반경을 넓혀 보세요.</p>
          <button v-for="c in insideCamps" :key="c.id" type="button" class="row" @click="pick(c.id)">
            <span class="box" :class="c.tier">{{ c.safetyScore ?? '-' }}</span>
            <span class="info">
              <span class="name">{{ c.name }}</span>
              <span class="meta">{{ typeLabel(c.campType) }} · 내 위치에서 {{ fmtKm(c.km) }}</span>
              <span class="grade" :class="c.tier">{{ GRADE_LABEL[c.grade] }}</span>
            </span>
            <span class="go">안전시설 ›</span>
          </button>
        </div>
      </template>

      <!-- 모드 2: 고른 캠핑장 주변 시설 + 경로 -->
      <template v-else>
        <div class="back-wrap">
          <button type="button" class="back" @click="pick(selected.id)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6" /></svg>반경 안 캠핑장 목록
          </button>
        </div>
        <div class="sel">
          <span class="box" :class="selected.tier">{{ selected.safetyScore ?? '-' }}</span>
          <span class="info">
            <span class="sel-name">{{ selected.name }}</span>
            <span class="meta">{{ typeLabel(selected.campType) }} · 내 위치에서 {{ fmtKm(selected.km) }}</span>
          </span>
        </div>
        <div class="head2 tight">
          <div class="t">안전시설 경로 <span class="cnt">{{ facs.length }}</span>곳</div>
          <span v-if="nearby?.dummy" class="dummy">더미 데이터</span>
        </div>
        <div class="list">
          <p v-if="!nearby" class="state">불러오는 중...</p>
          <p v-else-if="!facs.length" class="state">지도에 보일 시설을 켜 주세요.</p>
          <button v-for="f in facs" :key="f.id" type="button" class="frow" :class="{ focus: focusKey === String(f.id) }"
            :aria-pressed="focusKey === String(f.id)" @click="focusKey = focusKey === String(f.id) ? null : String(f.id)">
            <span class="ficon" :style="{ background: colorOf(f.type) }">{{ f.no }}</span>
            <span class="info">
              <span class="fname">{{ titleOf(f) }}</span>
              <span class="meta">{{ FACILITY_LABEL[f.type] }}</span>
            </span>
            <span class="ftime"><b>약 {{ minOf(f) }}분</b><span>{{ kmOf(f) }}km</span></span>
          </button>
        </div>
      </template>
    </template>

    <WebCampMap :camps="mapCamps" :selected-id="selectedId" :fit-key="fitKey" :me="me" :routes="routes" :focus-key="focusKey" @select="pick" />
    <WebFacChips :layers="layers" :on="on" @toggle="toggleChip" />
    <WebWeatherBar :region="warnRegion" :count="warnings" />
    <RouterLink v-if="selected" :to="`/web/camps/${selected.id}`" class="detail-link">캠핑장 상세 ›</RouterLink>
  </WebMapShell>
</template>

<style scoped>
.where {
  flex: none;
  padding: 18px 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-bottom: 1px solid var(--web-line-panel-soft);
}
.where-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.me {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.me-icon {
  flex: 0 0 36px;
  height: 36px;
  border-radius: 999px;
  background: var(--web-me-bg);
  color: var(--web-me);
  display: flex;
  align-items: center;
  justify-content: center;
}
.me-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.me-text .k {
  font-size: 12px;
  color: var(--web-text-sub);
}
.me-text .v {
  font-size: 15px;
  font-weight: 700;
}
.refresh {
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.radii {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rk {
  font-size: 13px;
  color: var(--web-text-sub);
  margin-right: 4px;
}
.r {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}
.r.on {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.head2 {
  flex: none;
  padding: 14px 20px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head2.tight {
  padding-top: 16px;
}
.head2 .t {
  font-size: 15px;
  font-weight: 700;
}
.cnt {
  color: var(--web-green);
}
.head2 .s {
  font-size: 12px;
  color: var(--web-text-sub);
}
.dummy {
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--web-bg-pale);
  font-size: 11px;
  color: var(--web-text-sub);
}
.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.state {
  margin: 24px 10px;
  font-size: 14px;
  color: var(--web-text-sub);
  text-align: center;
}
.row {
  display: flex;
  gap: 12px;
  width: 100%;
  text-align: left;
  padding: 14px 10px;
  border-radius: 12px;
  color: var(--web-text);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  font-family: inherit;
}
.row:hover {
  background: var(--web-row-selected);
}
.box {
  flex: 0 0 44px;
  height: 44px;
  border-radius: 12px;
  color: var(--web-white);
  font-weight: 700;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.box.safe { background: var(--web-grade-safe); }
.box.mid { background: var(--web-grade-caution); }
.box.warn { background: var(--web-grade-danger); }
.info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}
.name {
  font-size: 15px;
  font-weight: 700;
}
.meta {
  font-size: 12px;
  color: var(--web-text-sub);
}
.grade {
  font-size: 12px;
  font-weight: 700;
}
.grade.safe { color: var(--web-grade-safe); }
.grade.mid { color: var(--web-grade-caution-fg); }
.grade.warn { color: var(--web-grade-danger-fg); }
.go {
  align-self: center;
  font-size: 12px;
  color: var(--web-text-sub);
  white-space: nowrap;
}
.back-wrap {
  flex: none;
  padding: 12px 20px 0;
}
.back {
  height: 34px;
  padding: 0 10px 0 4px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: inherit;
  font-size: 13px;
  color: var(--web-text);
  cursor: pointer;
}
.sel {
  flex: none;
  margin: 6px 20px 0;
  padding: 14px;
  border-radius: 12px;
  background: var(--web-bg-row);
  display: flex;
  gap: 12px;
  align-items: center;
}
.sel-name {
  font-size: 16px;
  font-weight: 700;
}
.frow {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  text-align: left;
  padding: 12px 10px;
  border-radius: 12px;
  color: var(--web-text);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  font-family: inherit;
}
.frow.focus {
  background: var(--web-best);
  border-color: var(--web-best);
}
.ficon {
  flex: 0 0 36px;
  height: 36px;
  border-radius: 9px;
  color: var(--web-white);
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.fname {
  font-size: 14px;
  font-weight: 700;
}
.ftime {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  white-space: nowrap;
}
.ftime b {
  font-size: 14px;
}
.ftime span {
  font-size: 12px;
  color: var(--web-text-sub);
}
.detail-link {
  position: absolute;
  right: 80px;
  bottom: 28px;
  z-index: 3;
  height: 44px;
  padding: 0 18px;
  border-radius: 12px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
</style>
