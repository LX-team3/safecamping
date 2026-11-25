<script setup>
// 웹 탐색 · 검색 (설계 4-1): 왼쪽 패널(검색 · 지도에 보일 캠핑장 등급 · 결과 목록) + 지도(시설 칩 · 핀 · 정보 카드)
// 데이터는 모바일 탐색과 같은 서버 API를 쓴다:
//   GET /api/camps (지도 핀·전체), /api/camps/recommend (추천 TOP 5), /api/nearby/layers (시설 칩), /api/facilities (켠 시설)
// 같은 화면이 주소에 따라 왼쪽 패널만 바뀐다 (설계 4-1 · 4-1-1 · 4-1-2 · 4-1-5):
//   /web/explore → 검색 목록 · /web/chabak → 차박지 찾기 · /web/camps/:id → 캠핑장 상세 · /web/facilities/:id?camp= → 시설 상세
// 설계와 다른 점: 지역·날짜 칩은 값만 보여 주고(고르는 창은 아직 없음), 리뷰는 서버 기능이 없어 준비 중
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import WebMapShell from '@/components/web/WebMapShell.vue'
import WebCampMap from '@/components/web/WebCampMap.vue'
import WebChabakToggle from '@/components/web/WebChabakToggle.vue'
import WebCarPanel from '@/components/web/WebCarPanel.vue'
import WebChabakDetailPanel from '@/components/web/WebChabakDetailPanel.vue'
import WebCampDetailPanel from '@/components/web/WebCampDetailPanel.vue'
import WebFacilityPanel from '@/components/web/WebFacilityPanel.vue'
import WebTripModal from '@/components/web/WebTripModal.vue'
import { goUp } from '@/utils/backStack'
import { api } from '@/utils/api'
import { facilityOn, initFacilityOn, onTypes, toggleFacilityOn } from '@/utils/facilityLayers'
import { directionsUrl } from '@/utils/place'
import { shortLabel } from '@/utils/date'
import { LAYER_CHIPS, SAFETY_FILTERS, verdictColor } from '@/utils/chabak'
import { useFavoriteStore } from '@/stores/favorite'
import { useTripStore } from '@/stores/trip'
import { useAuthStore } from '@/stores/auth'
import { useSurveyStore } from '@/stores/survey'
import { erBonusReasons } from '@/utils/webSurvey'

const props = defineProps({
  mode: { type: String, default: 'browse' }, // browse | car
  campId: { type: Number, default: null }, // 있으면 캠핑장 상세
  facId: { type: Number, default: null }, // 있으면 시설 상세 (campId 는 어느 캠핑장에서 왔는지)
})
const view = computed(() => (props.facId ? 'facility' : props.campId ? 'detail' : props.mode === 'car' ? 'car' : 'browse'))

const router = useRouter()
const favorite = useFavoriteStore()
const trip = useTripStore()

// 등급 묶음: 추천(내 유형 TOP 5) · 안심 · 보통 · 유의 (서버 grade: safe · caution · danger)
const TIERS = [
  { key: 'rec', label: '추천', sub: '안전점수 TOP 5' },
  { key: 'safe', label: '안심', sub: '안전한 곳' },
  { key: 'mid', label: '보통', sub: '주의가 필요한 곳' },
  { key: 'warn', label: '유의', sub: '더 대비해요' },
]
const TIER_BY_GRADE = { safe: 'safe', caution: 'mid', danger: 'warn' }
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' } // 서버 campType → 화면 글씨
const typeLabel = (t) => TYPE_LABEL[t] || t || ''

// ---- 서버 데이터 ----
const allCamps = ref([])
const recommend = ref({ camps: [], excluded: [] })
const loading = ref(false)
const error = ref('')

// ---- 내 유형 점수 (응급실 가산점) — 앱 탐색과 같은 규칙 ----
// 진단에서 아이·어르신·차박·겨울·걸어서 중 하나를 골랐으면 버튼이 보이고, 켜면 점수 옆에 "+5" 를 따로 붙이고
// 추천 · 목록을 "안전점수 + 가산점" 순(서버 sort=bonus)으로 받는다. 등급(색 · 묶음)은 저장된 안전점수 그대로
const auth = useAuthStore()
const survey = useSurveyStore()
onMounted(() => survey.load(auth.isLoggedIn).catch(() => {})) // 로그인했으면 이 브라우저에 저장된 내 진단 답
const bonusReasons = computed(() => erBonusReasons(survey.answers))
const bonusActive = computed(() => bonusReasons.value.length > 0 && survey.bonusOn)
// 웹은 "+5" 를 따로 붙이지 않고 점수 숫자 자체를 바꿔 보여 준다 (사용자 결정). 등급(grade · 색 · 묶음)은 저장된 점수 그대로
// baseScore 에 저장된 점수를 남겨 둔다 (정보 카드 · 상세의 설명용)
const withBonus = (c) =>
  bonusActive.value && c.erBonus && c.safetyScore != null
    ? { ...c, baseScore: c.safetyScore, safetyScore: Math.min(100, c.safetyScore + c.erBonus) }
    : c

async function load() {
  loading.value = true
  error.value = ''
  const params = { sido: trip.state.sido?.name, sigungu: trip.state.sigungu?.name, sort: bonusActive.value ? 'bonus' : 'score' }
  try {
    const [all, rec] = await Promise.all([
      api('/api/camps', { params }),
      api('/api/camps/recommend', { params: { ...params, limit: 5 } }),
    ])
    allCamps.value = all
    recommend.value = rec
    if (selectedId.value == null) selectedId.value = rec.camps[0]?.id ?? null // 설계처럼 1위가 처음부터 골라져 있다
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch([() => trip.state.sido, () => trip.state.sigungu, bonusActive], load)

const topIds = computed(() => new Set(recommend.value.camps.map((c) => c.id)))
const tierOf = (c) => (topIds.value.has(c.id) ? 'rec' : TIER_BY_GRADE[c.grade] || null)
// 지도·목록에 쓰는 캠핑장 (등급 묶음 정보를 붙임)
const camps = computed(() =>
  allCamps.value
    .map((c) => ({ ...withBonus(c), tier: tierOf(c) }))
    .filter((c) => c.tier),
)

// ---- 검색 · 등급 칩 ----
const query = ref('')
const tiers = ref({ rec: true }) // 처음엔 추천만 지도에 올린다
const selectedId = ref(null)
const more = ref({})

const matches = (c) => {
  const q = query.value.trim()
  return !q || [c.name, c.sigungu, c.eupmyeon].some((s) => s?.includes(q))
}
const tierChips = computed(() =>
  TIERS.map((t) => ({ ...t, count: camps.value.filter((c) => c.tier === t.key).length, on: !!tiers.value[t.key] })),
)
const toggleTier = (key) => (tiers.value = { ...tiers.value, [key]: !tiers.value[key] })

const onKeys = computed(() => TIERS.filter((t) => tiers.value[t.key]).map((t) => t.key))
const onlyRec = computed(() => onKeys.value.length === 1 && onKeys.value[0] === 'rec')
const listTitle = computed(() =>
  !onKeys.value.length
    ? '지도에 보일 캠핑장을 골라 주세요'
    : onlyRec.value
      ? '추천 캠핑장'
      : onKeys.value.map((k) => TIERS.find((t) => t.key === k).label).join(' · ') + ' 캠핑장',
)
const visibleCamps = computed(() => camps.value.filter((c) => tiers.value[c.tier] && matches(c)))
const listCount = computed(() => (!visibleCamps.value.length ? '' : onlyRec.value ? 'TOP 5' : `${visibleCamps.value.length}곳`))

// 목록: 등급별로 묶는다. 추천은 전부, 나머지는 3곳까지 + 더보기
const sections = computed(() =>
  onKeys.value.map((k, i) => {
    const t = TIERS.find((x) => x.key === k)
    const all = visibleCamps.value.filter((c) => c.tier === k)
    const open = !!more.value[k]
    const cut = k === 'rec' ? all.length : 3
    return {
      ...t,
      first: i === 0,
      count: all.length,
      items: open ? all : all.slice(0, cut),
      hasMore: all.length > cut,
      open,
      moreLabel: open ? '접기 ▴' : `${all.length - cut}곳 더보기 ▾`,
    }
  }),
)
const toggleMore = (key) => (more.value = { ...more.value, [key]: !more.value[key] })

// ---- 차박지 찾기 (앱 차박 지도와 같은 레이어 · 필터) ----
// 점: 차박 후보지(GET /api/chabak/layer?type=open) · 등록 야영장(type=camp)
// 면: 불가 구역(type=no) · 주의 구역(type=caution) — 켤 때 처음 한 번 받는다 (서버도 처음 한 번만 계산해 기억)
// 지도 핀을 누르면 왼쪽에 차박지 상세(WebChabakDetailPanel: 판정 · 안전점수 · 가까운 시설)
// 키: 후보지는 캠핑장 id 가 없어 'open-0' …, 야영장은 'camp-12' (검색 탭 캠핑장 id 와 겹치지 않게)
const spots = ref([]) // 후보지 + 야영장 [{ id, kind, name, lat, lng, safetyScore, grade, tier, area, erDriveMin, imageUrl, campId }]
const layerOn = ref(Object.fromEntries(LAYER_CHIPS.map((c) => [c.type, c.on]))) // 처음: 점은 켬, 구역은 끔
const safety = ref('all')
const zoneData = ref({}) // { no: geometry, caution: geometry }
const zoneLoading = ref(false)
const toSpot = (kind) => (s, i) => ({
  id: kind === 'camp' ? `camp-${s.id}` : `open-${i}`,
  kind, name: s.name, lat: s.lat, lng: s.lng, safetyScore: s.score, grade: s.grade,
  tier: TIER_BY_GRADE[s.grade] || 'warn', area: s.area, erDriveMin: s.erDriveMin, imageUrl: s.imageUrl,
  campId: kind === 'camp' ? s.id : null,
})
async function loadSpots() {
  if (spots.value.length) return
  try {
    const [open, campList] = await Promise.all([
      api('/api/chabak/layer', { params: { type: 'open' } }),
      api('/api/chabak/layer', { params: { type: 'camp' } }),
    ])
    spots.value = [...open.map(toSpot('open')), ...campList.map(toSpot('camp'))]
  } catch (e) {
    error.value = e.message
  }
}
async function toggleLayer(type) {
  layerOn.value = { ...layerOn.value, [type]: !layerOn.value[type] }
  const chip = LAYER_CHIPS.find((c) => c.type === type)
  if (!chip?.zone || !layerOn.value[type] || zoneData.value[type]) return
  zoneLoading.value = true
  try {
    const list = await api('/api/chabak/layer', { params: { type } })
    zoneData.value = { ...zoneData.value, [type]: list[0]?.geometry ?? null } // 한 덩어리로 합친 구역 하나
  } catch (e) {
    error.value = e.message
  } finally {
    zoneLoading.value = false
  }
}
watch(view, (v) => { if (v === 'car') { selectedId.value = null; loadSpots() } }, { immediate: true })
// 목록 · 지도 핀: 켠 종류 + 안전점수 기준 (점수가 없는 곳은 '전체'에서만), 안전점수 높은 순
const carCamps = computed(() => {
  const min = SAFETY_FILTERS.find((f) => f.key === safety.value)?.min
  return spots.value
    .filter((s) => layerOn.value[s.kind])
    .filter((s) => min == null || (s.safetyScore != null && s.safetyScore >= min))
    .sort((a, b) => (b.safetyScore ?? -1) - (a.safetyScore ?? -1))
})
// 지도에 칠할 구역 (켠 것 중 받아 온 것만). 주의 → 불가 순서로 그려서 불가가 위에 보이게
const carZones = computed(() =>
  ['caution', 'no']
    .filter((t) => layerOn.value[t] && zoneData.value[t])
    .map((t) => ({ key: t, geometry: zoneData.value[t], color: verdictColor(LAYER_CHIPS.find((c) => c.type === t).verdict) })),
)
const carSpot = computed(() => (view.value === 'car' ? spots.value.find((s) => s.id === selectedId.value) || pickedAlt.value : null))
const carParcel = ref(null) // 고른 자리의 필지 경계 (상세 패널이 판정을 받으면 알려 줌)
// 상세의 "근처 차박 후보"를 누르면 그 자리로 (후보지 목록에 없을 수도 있어 따로 기억)
const pickedAlt = ref(null)
function pickAlt(a) {
  pickedAlt.value = { id: `alt-${a.jibun}`, kind: 'open', name: a.jibun, lat: a.lat, lng: a.lng, area: a.owner }
  selectedId.value = pickedAlt.value.id
}
// 지도 아무 곳이나 누름 → 그 자리를 판정 (앱 차박 지도와 같음). 검색 탭에서는 아무 일 없음
// 핀 이름은 "찍은 자리", 지번은 판정 결과가 오면 상세 이름 줄에 나온다
// 상세가 점수를 받으면 찍은 자리 · 근처 후보 핀에도 붙인다 (목록의 후보지 · 야영장은 이미 점수가 있음)
function onSpotScore({ score, grade }) {
  if (!pickedAlt.value || pickedAlt.value.id !== selectedId.value) return
  pickedAlt.value = { ...pickedAlt.value, safetyScore: score, grade, tier: TIER_BY_GRADE[grade] || 'warn' }
}
let pointNo = 0
function onMapClick({ lat, lng }) {
  if (view.value !== 'car') return
  pickedAlt.value = { id: `pt-${++pointNo}`, kind: 'point', name: '찍은 자리', lat, lng, area: '' }
  selectedId.value = pickedAlt.value.id
}
// 지도에는 켠 등급 + 고른 캠핑장 (차박지 찾기는 차박·노지만, 상세·시설 상세는 그 캠핑장 하나만)
const mapCamps = computed(() => {
  if (view.value === 'detail' || view.value === 'facility') {
    const c = detailCamp.value
    return c ? [{ ...withBonus(c), tier: TIER_BY_GRADE[c.grade] || 'safe' }] : [] // 상세 핀도 내 유형 점수
  }
  if (view.value === 'car') return pickedAlt.value ? [...carCamps.value, { ...pickedAlt.value, tier: pickedAlt.value.tier || 'safe' }] : carCamps.value
  return camps.value.filter((c) => (tiers.value[c.tier] && matches(c)) || c.id === selectedId.value)
})
const fitKey = computed(() => mapCamps.value.length + tierChips.value.filter((t) => t.on).length * 1000 + (view.value === 'browse' ? 0 : 7))

const selected = computed(() => (view.value === 'car' ? null : camps.value.find((x) => x.id === selectedId.value) || null))
const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의' }
const tierLabel = (c) => (c.tier === 'rec' ? '추천' : GRADE_LABEL[c.grade])

// 목록·핀을 누르면 정보 카드, 같은 곳을 한 번 더 누르면 상세
function pick(id) {
  if (view.value === 'car') { // 차박지는 고르면 바로 왼쪽에 상세가 뜬다
    pickedAlt.value = null
    selectedId.value = id
    return
  }
  if (selectedId.value === id) openDetail(id)
  else selectedId.value = id
}
const openDetail = (id) => router.push(`/web/camps/${id}`)

// ---- 상세 · 시설 패널과 지도 ----
const detailCamp = ref(null) // 상세 패널이 불러온 캠핑장 (지도에 이 캠핑장 하나만 보인다)
const mapRoutes = ref([]) // 패널이 알려 주는 지도 경로
const focusKey = ref(null)
const onCamp = (d) => (detailCamp.value = d)
watch(() => [props.campId, props.facId], async ([id, fid]) => {
  if (id) selectedId.value = id
  detailCamp.value = null
  mapRoutes.value = []
  focusKey.value = null
  // 시설 상세에는 캠핑장 상세 패널이 없어서 지도에 놓을 캠핑장을 여기서 받는다 (상세 패널은 스스로 받아 알려 준다)
  if (fid && id) {
    try {
      detailCamp.value = await api(`/api/camps/${id}`)
    } catch {
      // 못 받으면 지도에 캠핑장 핀 없이 시설 경로만 보인다
    }
  }
}, { immediate: true })
// 시설 상세 → 그 캠핑장. 캠핑장에서 왔으면 뒤로 가서 보던 탭 그대로
const backFromFacility = () => goUp(router, `/web/camps/${props.campId}`)
// 캠핑장 상세의 뒤로: 들어온 화면(홈·진단 결과·내 캠핑 …)으로. 주소로 바로 들어왔으면 탐색으로
const backFromDetail = () => (window.history.state?.back ? router.back() : router.push('/web/explore'))
const toBrowse = () => router.push('/web/explore')

// ---- 지도 위 시설 칩 ----
const layers = ref([])
const mapFacilities = ref([])
let mapBounds = null
let facilityReq = 0

async function loadLayers() {
  try {
    const list = await api('/api/nearby/layers')
    layers.value = list.filter((l) => l.ready)
    initFacilityOn(list)
  } catch {
    // 못 불러오면 칩이 안 보일 뿐 캠핑장 탐색은 된다
  }
}
onMounted(loadLayers)

function toggleChip(code) {
  toggleFacilityOn(code)
  loadFacilities()
}
function onBounds(b) {
  mapBounds = b
  loadFacilities()
}
async function loadFacilities() {
  const no = ++facilityReq // 늦게 온 앞 응답이 새 결과를 덮지 않게
  const types = onTypes()
  if (!types.length || !mapBounds) {
    mapFacilities.value = []
    return
  }
  try {
    const list = await api('/api/facilities', { params: { types: types.join(','), ...mapBounds } })
    if (no === facilityReq) mapFacilities.value = list
  } catch {
    // 못 받으면 시설 없이 캠핑장 핀만 보인다
  }
}
// 칩 색: 설계의 종류별 색 (서버 시설 코드 → 색 이름)
const CHIP_COLOR = { er: 'hospital', fire: 'fire', police: 'police', store: 'store', pharmacy: 'pharmacy', shelter: 'shelter' }

// ---- 칩 글씨 ----
const tripOpen = ref(false)
const tripTab = ref('region')
function openTrip(tab) {
  tripTab.value = tab
  tripOpen.value = true
}
const regionLabel = computed(() => trip.state.sigungu?.name || trip.state.sido?.name || '전국')
const dateLabel = computed(() =>
  trip.state.checkIn && trip.state.checkOut ? `${shortLabel(trip.state.checkIn)}–${shortLabel(trip.state.checkOut)}` : '날짜',
)

// ---- 정보 카드 ----
const copied = ref(false)
async function share() {
  try {
    await navigator.clipboard.writeText(`${location.origin}/web/camps/${selected.value.id}`)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    // 복사가 막힌 브라우저에서는 알리지 않는다
  }
}
const directions = computed(() => (selected.value ? directionsUrl(selected.value) : '#'))
</script>

<template>
  <WebMapShell active="search">
    <template #side>
      <template v-if="view === 'browse'">
      <!-- 검색 + 필터 -->
      <div class="filters">
        <div class="searchbox">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
          <input v-model="query" type="search" placeholder="캠핑장 · 지역 검색" aria-label="캠핑장 · 지역 검색" />
        </div>
        <div class="chips">
          <button type="button" class="chip icon" aria-label="상세 필터 (준비 중)" disabled>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" aria-hidden="true"><path d="M4 6h16M7 12h10M10 18h4" /></svg>
          </button>
          <button type="button" class="chip" @click="openTrip('region')">{{ regionLabel }} ▾</button>
          <button type="button" class="chip" @click="openTrip('date')">{{ dateLabel }} ▾</button>
        </div>
        <div class="tierbox">
          <span class="tierlabel">지도에 보일 캠핑장</span>
          <div class="tiers">
            <button v-for="t in tierChips" :key="t.key" type="button" class="tier" :class="[t.key, { on: t.on }]"
              :aria-pressed="t.on" @click="toggleTier(t.key)">
              <svg width="14" height="18" viewBox="0 0 24 30" aria-hidden="true">
                <path d="M12 1C5.9 1 1.5 5.6 1.5 11.4c0 7.2 8.4 15.6 9.6 16.8a1.3 1.3 0 0 0 1.8 0c1.2-1.2 9.6-9.6 9.6-16.8C22.5 5.6 18.1 1 12 1z" class="pin" />
                <circle cx="12" cy="11.2" r="3.8" class="dot" />
              </svg>{{ t.label }} <span class="n">{{ t.count }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 내 유형 점수: 가산점에 해당하는 진단 답이 있을 때만 -->
      <div v-if="bonusReasons.length" class="bonusbar">
        <button type="button" class="chip bonus-chip" :class="{ on: survey.bonusOn }" :aria-pressed="survey.bonusOn"
          @click="survey.bonusOn = !survey.bonusOn">내 유형 점수 {{ survey.bonusOn ? '켜짐' : '꺼짐' }}</button>
      </div>

      <!-- 결과 헤더 -->
      <div class="result-head">
        <div class="title">{{ listTitle }} <span class="count">{{ listCount }}</span></div>
        <span class="sort">{{ bonusActive ? '안전점수 + 내 유형 점수순' : '안전점수순' }}</span>
      </div>

      <!-- 결과 목록 -->
      <div class="list">
        <p v-if="error" class="state">{{ error }} <button type="button" class="link" @click="load">다시 시도</button></p>
        <p v-else-if="loading && !camps.length" class="state">불러오는 중...</p>
        <p v-else-if="!sections.length" class="state">지도에 보일 등급을 골라 주세요.</p>
        <p v-else-if="!visibleCamps.length" class="state">조건에 맞는 캠핑장이 없어요.</p>

        <div v-for="g in sections" :key="g.key" class="group">
          <div v-if="!onlyRec" class="group-head" :class="{ first: g.first }">
            <svg width="14" height="18" viewBox="0 0 24 30" aria-hidden="true" :class="['gpin', g.key]">
              <path d="M12 1C5.9 1 1.5 5.6 1.5 11.4c0 7.2 8.4 15.6 9.6 16.8a1.3 1.3 0 0 0 1.8 0c1.2-1.2 9.6-9.6 9.6-16.8C22.5 5.6 18.1 1 12 1z" />
            </svg>
            <b>{{ g.label }}</b><span class="gcount">{{ g.count }}곳</span><span class="gsub">{{ g.sub }}</span>
          </div>
          <button v-for="(c, i) in g.items" :key="c.id" type="button" class="row" :class="{ selected: c.id === selectedId }"
            :aria-pressed="c.id === selectedId" @click="pick(c.id)">
            <span class="score" :class="c.tier"><span class="gl">{{ tierLabel(c) }}</span><span class="num">{{ c.safetyScore ?? '-' }}<small>점</small></span></span>
            <span class="info">
              <span class="name"><span v-if="c.tier === 'rec'" class="rank">{{ i + 1 }}위</span>{{ c.name }}</span>
              <span class="meta">{{ [typeLabel(c.campType), [c.sigungu, c.eupmyeon].filter(Boolean).join(' ')].filter(Boolean).join(' · ') }}</span>
              <span v-if="c.erDriveMin != null" class="status" :class="c.tier">응급실 차로 {{ c.erDriveMin }}분</span>
            </span>
          </button>
          <button v-if="g.hasMore" type="button" class="more" :aria-expanded="g.open" @click="toggleMore(g.key)">{{ g.moreLabel }}</button>
        </div>
      </div>
      </template>

      <WebChabakDetailPanel v-else-if="view === 'car' && carSpot" :spot="carSpot"
        @back="selectedId = null; pickedAlt = null" @parcel="(p) => (carParcel = p)" @pick="pickAlt" @score="onSpotScore" />
      <WebCarPanel v-else-if="view === 'car'" :camps="carCamps" :selected-id="selectedId" :layer-on="layerOn" :safety="safety"
        :zone-loading="zoneLoading" @back="toBrowse" @pick="pick" @toggle-layer="toggleLayer" @safety="(k) => (safety = k)" />
      <WebFacilityPanel v-else-if="view === 'facility'" :fac-id="facId" :camp-id="campId" @back="backFromFacility" @close="toBrowse" @routes="(r) => (mapRoutes = r)" />
      <WebCampDetailPanel v-else :camp-id="campId" :focus-key="focusKey" @back="backFromDetail" @camp="onCamp" @routes="(r) => (mapRoutes = r)" @focus="(k) => (focusKey = k)" />
    </template>

    <!-- 지도 -->
    <WebCampMap :camps="mapCamps" :facilities="view === 'browse' || view === 'car' ? mapFacilities : []" :selected-id="selectedId" :fit-key="fitKey"
      :routes="mapRoutes" :focus-key="focusKey" :zones="view === 'car' ? carZones : []" :hide-names="view === 'car'" :parcel="view === 'car' && carSpot ? carParcel : null"
      @select="pick" @bounds="onBounds" @mapclick="onMapClick">
      <template #top-right>
        <WebChabakToggle :on="view === 'car'" />
      </template>
    </WebCampMap>

    <!-- 지도 위 시설 칩 -->
    <div v-if="layers.length && (view === 'browse' || view === 'car')" class="faclist">
      <button v-for="l in layers" :key="l.code" type="button" class="fac" :class="[CHIP_COLOR[l.code] || 'etc', { on: facilityOn[l.code] }]"
        :aria-pressed="!!facilityOn[l.code]" @click="toggleChip(l.code)">{{ l.label }}</button>
    </div>

    <!-- 고른 캠핑장 정보 카드 -->
 <div v-if="selected && view === 'browse'" class="card" role="dialog" :aria-label="`${selected.name} 정보`">
      <button type="button" class="x" aria-label="닫기" @click="selectedId = null">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" aria-hidden="true"><path d="M5 5l14 14M19 5 5 19" /></svg>
      </button>
      <div class="card-top">
        <div class="card-info">
          <button type="button" class="card-name" @click="openDetail(selected.id)">
            <span>{{ selected.name }}</span>
            <span class="go"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h13M13 6l6 6-6 6" /></svg></span>
          </button>
          <span class="card-line">
            <b class="badge" :class="selected.tier">{{ tierLabel(selected) }} · {{ selected.safetyScore ?? '-' }}점</b>
            <span v-if="selected.erDriveMin != null">응급실 차로 약 {{ selected.erDriveMin }}분</span>
          </span>
          <span class="addr">강원 {{ selected.sigungu }} {{ selected.eupmyeon }}</span>
          <span v-if="selected.campType" class="addr2">{{ typeLabel(selected.campType) }}</span>
          <span class="card-links">
            <button type="button" @click="openDetail(selected.id)">상세보기</button>
          </span>
        </div>
        <span class="photo" role="img" :aria-label="`${selected.name} 사진`">
          <img v-if="selected.imageUrl" :src="selected.imageUrl" :alt="selected.name" />
          <template v-else>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="8.5" cy="10" r="1.5" /><path d="m21 16-5-5-8 8" /></svg>사진
          </template>
        </span>
      </div>
      <div class="card-actions">
        <div class="triple">
          <button type="button" :aria-label="favorite.has(selected.id) ? '찜 취소' : '찜하기'" @click="favorite.toggle(selected.id)">
            <svg width="22" height="22" viewBox="0 0 24 24" :fill="favorite.has(selected.id) ? 'currentColor' : 'none'" stroke="currentColor"
              stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>
          </button>
          <RouterLink :to="`/web/nearby?camp=${selected.id}`" aria-label="안전시설">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3 4 6v6c0 4.4 3.4 8.2 8 9 4.6-.8 8-4.6 8-9V6z" /><path d="M9 12l2 2 4-4" /></svg>
          </RouterLink>
          <button type="button" aria-label="링크 복사" @click="share">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 5h5v5M19 5l-8 8M18 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h4" /></svg>
          </button>
        </div>
        <a :href="directions" target="_blank" rel="noopener" class="dir">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 21V9a4 4 0 0 1 4-4h9M15 1l4 4-4 4" /></svg>길찾기
        </a>
      </div>
      <p v-if="copied" class="copied" role="status">링크를 복사했어요</p>
    </div>
    <WebTripModal :open="tripOpen" :tab="tripTab" @close="tripOpen = false" @apply="tripOpen = false" />
  </WebMapShell>
</template>

<style scoped>
/* ---- 왼쪽 패널 ---- */
.filters {
  flex: none;
  padding: 18px 20px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-bottom: 1px solid var(--web-line-panel-soft);
}
.searchbox {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 14px;
  border: 2px solid var(--web-green);
  border-radius: 12px;
  color: var(--web-text-sub);
}
.searchbox input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  font-size: 15px;
  background: transparent;
  color: var(--web-text);
}
.chips {
  display: flex;
  gap: 6px;
  align-items: center;
}
.chip {
  flex: 0 0 auto;
  white-space: nowrap;
  height: 36px;
  padding: 0 10px;
  border-radius: 10px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  font-size: 13px;
  color: var(--web-text);
}
.chip.icon {
  width: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tierbox {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tierlabel {
  font-size: 12px;
  font-weight: 700;
  color: var(--web-text-sub);
}
.tiers {
  display: flex;
  gap: 6px;
}
.tier {
  flex: 1;
  justify-content: center;
  height: 36px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1.5px solid var(--web-line-panel);
  background: var(--web-bg);
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  color: var(--web-text);
}
.tier .n {
  font-weight: 500;
  color: var(--web-text-sub);
}
.tier .dot {
  fill: var(--web-white);
}
.tier.on {
  font-weight: 800;
}
/* 등급별 색 (핀 · 켠 칩의 테두리/바탕) */
.tier.rec .pin { fill: url(#web-rec-grad); }
.tier.safe .pin { fill: var(--web-grade-safe); }
.tier.mid .pin { fill: var(--web-grade-caution); }
.tier.warn .pin { fill: var(--web-grade-danger); }
.tier.rec.on { border-color: var(--web-rec-mid); background: var(--web-rec-tint); }
.tier.safe.on { border-color: var(--web-grade-safe); background: var(--web-grade-safe-bg); }
.tier.mid.on { border-color: var(--web-grade-caution); background: var(--web-grade-caution-bg); }
.tier.warn.on { border-color: var(--web-grade-danger); background: var(--web-grade-danger-bg); }

.result-head {
  flex: none;
  padding: 14px 20px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  font-size: 15px;
  font-weight: 700;
}
.count {
  color: var(--web-grade-safe);
}
.bonusbar {
  padding: 0 20px 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.bonus-chip.on {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
}
.sort {
  font-size: 13px;
  color: var(--web-text-sub);
}
.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 12px 16px;
}
.state {
  margin: 24px 8px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.link {
  border: 0;
  background: none;
  color: var(--web-link-blue);
  font: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 8px;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 8px 6px;
  border-top: 1px solid var(--web-line);
}
.group-head.first {
  border-top: 0;
}
.group-head b {
  font-size: 14px;
}
.gcount {
  font-size: 13px;
  color: var(--web-text-sub);
}
.gsub {
  margin-left: auto;
  font-size: 12px;
  color: var(--web-text-mute);
}
.gpin.rec path { fill: url(#web-rec-grad); }
.gpin.safe path { fill: var(--web-grade-safe); }
.gpin.mid path { fill: var(--web-grade-caution); }
.gpin.warn path { fill: var(--web-grade-danger); }
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
.row.selected {
  background: var(--web-row-selected);
  border-color: var(--web-border);
}
.score {
  flex: 0 0 56px;
  height: 56px;
  border-radius: 14px;
  color: var(--web-white);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
}
.score.rec { background: linear-gradient(160deg, var(--web-rec-from) 0%, var(--web-rec-mid) 52%, var(--web-rec-to) 100%); }
.score.safe { background: var(--web-grade-safe); }
.score.mid { background: var(--web-grade-caution); }
.score.warn { background: var(--web-grade-danger); }
.score .gl {
  font-size: 11px;
  font-weight: 700;
}
.score .num {
  font-size: 19px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: -0.5px;
}
.score small {
  font-size: 10px;
  font-weight: 800;
}
.info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.name {
  font-size: 15px;
  font-weight: 700;
}
.rank {
  margin-right: 6px;
  color: var(--web-green);
}
.meta {
  font-size: 12px;
  color: var(--web-text-sub);
}
.status {
  font-size: 12px;
  font-weight: 700;
}
.status.rec,
.status.safe { color: var(--web-grade-safe); }
.status.mid { color: var(--web-grade-caution-fg); }
.status.warn { color: var(--web-grade-danger-fg); }
.more {
  margin: 2px 8px 0;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--web-border);
  background: var(--web-bg);
  font-size: 13px;
  font-weight: 700;
  color: var(--web-text-sub);
  cursor: pointer;
  font-family: inherit;
}

/* ---- 지도 위 시설 칩 ---- */
.faclist {
  position: absolute;
  top: 16px;
  left: 16px;
  right: 80px;
  z-index: 3;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.fac {
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 14px;
  color: var(--web-text);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  cursor: pointer;
}
.fac.on {
  font-weight: 800;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
.fac.shelter.on { border: 1.5px solid var(--web-fac-shelter); background: var(--web-fac-shelter-tint); }
.fac.hospital.on { border: 1.5px solid var(--web-fac-hospital); background: var(--web-fac-hospital-tint); }
.fac.pharmacy.on { border: 1.5px solid var(--web-fac-pharmacy); background: var(--web-fac-pharmacy-tint); }
.fac.fire.on { border: 1.5px solid var(--web-fac-fire); background: var(--web-fac-fire-tint); }
.fac.police.on { border: 1.5px solid var(--web-fac-police); background: var(--web-fac-police-tint); }
.fac.store.on { border: 1.5px solid var(--web-fac-store); background: var(--web-fac-store-tint); }
.fac.etc.on { border: 1.5px solid var(--web-green); background: var(--web-bg-soft); }

/* 차박지 찾기 버튼 (지도 오른쪽 위, 지도 종류 버튼 아래) */

/* ---- 정보 카드 ---- */
.card {
  position: absolute;
  left: 50%;
  bottom: 72px;
  transform: translateX(-50%);
  width: 440px;
  max-width: calc(100% - 32px);
  background: var(--web-bg);
  border: 1px solid var(--web-line-panel);
  border-radius: 6px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
  padding: 22px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  z-index: 6;
}
.x {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: 0;
  background: transparent;
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.card-top {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-name {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 36px 0 0;
  border: 0;
  background: none;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: var(--web-text);
}
.go {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.card-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 13px;
}
.badge.rec { background: var(--web-rec-selected); color: var(--web-rec-badge-fg); }
.badge.safe { background: var(--web-grade-safe-bg); color: var(--web-grade-safe); }
.badge.mid { background: var(--web-grade-caution-bg); color: var(--web-grade-caution-fg); }
.badge.warn { background: var(--web-grade-danger-bg); color: var(--web-grade-danger-fg); }
.addr {
  margin-top: 8px;
  font-size: 15px;
}
.addr2 {
  font-size: 13px;
  color: var(--web-text-mute);
}
.card-links {
  margin-top: 2px;
  font-size: 14px;
  color: var(--web-link-blue);
}
.card-links button {
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.photo {
  margin-top: 40px;
  flex: 0 0 112px;
  height: 112px;
  background: var(--web-photo-empty);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--web-text-sub);
  font-size: 11px;
  overflow: hidden;
}
.photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-actions {
  display: flex;
  gap: 12px;
}
.triple {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border: 1px solid var(--web-line-panel);
  border-radius: 4px;
  overflow: hidden;
}
.triple > * {
  height: 52px;
  border: 0;
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.triple > * + * {
  border-left: 1px solid var(--web-line-panel);
}
.dir {
  flex: 0 0 148px;
  height: 54px;
  border-radius: 4px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.copied {
  position: absolute;
  top: -44px;
  left: 50%;
  transform: translateX(-50%);
  margin: 0;
  padding: 8px 16px;
  border-radius: 999px;
  background: var(--web-text);
  color: var(--web-white);
  font-size: 13px;
  white-space: nowrap;
}
</style>
