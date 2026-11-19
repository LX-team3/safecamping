<script setup>
// 탐색 탭: 지도 위에 시트를 올린 한 화면
//  - 시트를 펼치면 목록 (내 유형 추천 TOP 5 · 전체 목록)
//  - 손잡이(가로 줄)·"지도" 버튼을 누르면 시트가 접혀 지도가 보인다
//  - 목록 한 줄을 누르면 캠핑장 상세. 지도 마커는 한 번 누르면 이름표, 이름표를 다시 누르면 상세
//  - 지역 칩 → RegionSheet(어디로), 날짜 칩 → TripSheet(누구와·언제). 고른 값은 trip store
//  - 지도 레이어 버튼 → 내 주변과 같은 LayerSheet(안전 시설 켜고 끄기). 처음엔 전부 꺼짐, 켠 종류는 지도 화면 안의 시설을 보여 준다
// 데이터: GET /api/camps (지도 마커·전체 목록), /api/camps/recommend (추천),
//         /api/nearby/layers (레이어 줄 목록), /api/facilities?types=&swLat=… (지도 화면 안의 켠 시설)
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CampMap from '@/components/map/CampMap.vue'
import CampRow from '@/components/explore/CampRow.vue'
import RegionSheet from '@/components/explore/RegionSheet.vue'
import TripSheet from '@/components/explore/TripSheet.vue'
import LayerSheet from '@/components/nearby/LayerSheet.vue'
import BottomNav from '@/components/common/BottomNav.vue'
import MapModeToggle from '@/components/common/MapModeToggle.vue'
import { useDragSheet, useElementHeight } from '@/utils/dragSheet'
import campingImg from '@/assets/illust/camping.svg' // 진단 안내 팝업 그림 (unDraw, 출처 표기 불필요)
import { api } from '@/utils/api'
import { facilityOn, initFacilityOn, onTypes, toggleFacilityOn } from '@/utils/facilityLayers'
import { vHscroll } from '@/utils/hscroll'
import { shortLabel } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'
import { useSurveyStore } from '@/stores/survey'
import { useFavoriteStore } from '@/stores/favorite'
import { useTripStore } from '@/stores/trip'

const COLLAPSED = 168 // 접었을 때 시트 높이(px): 손잡이 + 검색창 + 필터 줄

const router = useRouter()
const auth = useAuthStore()
const survey = useSurveyStore()
const favorite = useFavoriteStore()
const trip = useTripStore()

// ---- 화면 상태 ----
// 지도 위 시트: 목록(화면 전체) ↔ 지도(검색·필터만). 손잡이를 끌거나 눌러 오간다 (utils/dragSheet.js, 차박 지도와 같은 움직임)
const stageEl = ref(null)
const stageH = useElementHeight(stageEl, 700)
const sheetSnaps = computed(() => ({ map: COLLAPSED, list: stageH.value }))
const { snap: sheetSnap, dragging: sheetDragging, style: sheetStyle, events: sheetDrag } = useDragSheet(sheetSnaps, 'map') // 처음엔 지도 화면 (진단 안내 팝업도 지도 위에 뜬다)
// true: 목록 / false: 지도 (다른 곳에서 sheetOpen = true 처럼 바꾸면 그 단계로 움직인다)
const sheetOpen = computed({
  get: () => sheetSnap.value === 'list',
  set: (open) => (sheetSnap.value = open ? 'list' : 'map'),
})
const query = ref('') // 검색어 (이름·지역)
const matchMine = ref(true) // "내 유형 맞춤" 켜짐
const openSheet = ref(null) // null | 'region' | 'trip' | 'layers' — 열려 있는 아래 시트
const sort = ref('score') // score | er
const showExcluded = ref(false) // "따로 보기": 추천에서 뺀 위험 등급 보기
const selectedId = ref(null)

// ---- 서버 데이터 ----
const allCamps = ref([])
const recommend = ref({ camps: [], excluded: []})
const loading = ref(false)
const error = ref('')

// 1분 진단 답 (없으면 유형으로 거르지 않음)
const diagnosis = computed(() => survey.answers || {})
const campType = computed(() =>
  ({ auto: 'auto', glamping: 'glamping', wild: 'backpacking' })[diagnosis.value.campType] || '')

//const diagnosis = computed(() => survey.answers.diagnosis || {})
//const campType = computed(() => diagnosis.value.camp_type?.[0] || '')

async function load() {
  loading.value = true
  error.value = ''
  const params = { sido: trip.state.sido?.name, sigungu: trip.state.sigungu?.name, sort: sort.value }
  try {
    const [all, rec] = await Promise.all([
      api('/api/camps', { params }),
      api('/api/camps/recommend', { params: { ...params, campType: campType.value || undefined, limit: 5 } }),
    ])
    allCamps.value = all
    recommend.value = rec
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

// ---- 1분 진단 안내 팝업 ----
// 진단을 안 한 사람에게만 한 번 보여 준다. "나중에하기"를 누르면 이 브라우저 탭에서는 다시 안 뜬다
const PROMPT_KEY = 'survey-prompt-dismissed'
const surveyChecked = ref(false) // 서버의 내 진단 답까지 확인했는지 (확인 전에 띄우면 한 번 깜빡인다)
const promptDismissed = ref(readDismissed())
const showSurveyPrompt = computed(() => surveyChecked.value && !survey.done && !promptDismissed.value)

function readDismissed() {
  try {
    return sessionStorage.getItem(PROMPT_KEY) === '1'
  } catch {
    return false // 저장소를 못 쓰면 매번 보여 준다
  }
}
function later() {
  promptDismissed.value = true
  try {
    sessionStorage.setItem(PROMPT_KEY, '1')
  } catch {
    // 저장 못 해도 이번 화면에서는 닫힌다
  }
}
// 끝나면 탐색으로 돌아오게 from 을 붙여 보낸다
function startSurvey() {
  router.push({ path: '/survey', query: { from: 'explore' } })
}

onMounted(async () => {
  try {
    await survey.load(auth.isLoggedIn) // 로그인했으면 저장된 답을 가져온다 (이미 한 사람에게 팝업이 뜨면 안 된다)
  } catch {
    // 못 불러와도 탐색은 쓸 수 있게 둔다
  }
  surveyChecked.value = true
})

onMounted(load)
onMounted(loadLayers) // 켜짐 기본값을 미리 받아 둔다 (시트를 열기 전에도 지도에 맞게 보이도록)
watch([() => trip.state.sido, () => trip.state.sigungu, sort], load)
watch(matchMine, () => (showExcluded.value = false))

// 검색어로 거르기 (서버를 다시 부르지 않고 받은 목록 안에서)
function matches(camp) {
  const q = query.value.trim()
  if (!q) return true
  return [camp.name, camp.sigungu, camp.eupmyeon].some((s) => s?.includes(q))
}

const listCamps = computed(() => {
  let list
  if (showExcluded.value) list = recommend.value.excluded
  else if (matchMine.value) list = recommend.value.camps
  else list = allCamps.value
  return list.filter(matches)
})
// 지도에는 위험 등급까지 전부 보여 준다 (안전 정보라 숨기지 않음)
const mapCamps = computed(() => allCamps.value.filter(matches))

// ---- 지도 레이어 (안전 시설) ----
// 줄 목록은 내 주변과 같은 서버 목록(GET /api/nearby/layers)을 쓴다 → 두 화면의 시트가 늘 같다
const layers = ref([]) // [{ code, icon, label, desc, ready, on }]
// 켠 종류(facilityOn)는 utils/facilityLayers.js — 시설 상세에 갔다 와도 유지 (처음엔 전부 꺼짐)
const mapFacilities = ref([]) // 켠 종류의 시설 중 지도 화면 안에 있는 것 [{ id, type, name, lat, lng }]
let mapBounds = null // 지도가 알려 준 보이는 범위 { swLat, swLng, neLat, neLng }
let facilityReq = 0 // 늦게 온 앞 응답이 새 결과를 덮지 않게 요청 번호를 센다

async function loadLayers() {
  try {
    const list = await api('/api/nearby/layers')
    layers.value = list
    initFacilityOn(list)
  } catch {
    // 못 불러오면 지도 설정 버튼을 눌렀을 때 다시 시도한다
  }
}

async function openLayers() {
  if (!layers.value.length) await loadLayers()
  if (layers.value.length) openSheet.value = 'layers'
}

function toggleFacility(code) {
  toggleFacilityOn(code)
  loadFacilities()
}

// 지도를 움직이고 멈출 때마다 (CampMap 의 bounds) 켠 종류를 그 범위로 다시 받아 온다
function onBounds(b) {
  mapBounds = b
  loadFacilities()
}

async function loadFacilities() {
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
    // 못 받으면 시설 없이 캠핑장 마커만 보인다
  }
}

const isRanked = computed(() => matchMine.value && !showExcluded.value)
const title = computed(() => {
  if (showExcluded.value) return '추천에서 뺀 캠핑장'
  if (matchMine.value) return `내 유형 추천 TOP ${recommend.value.camps.length}`
  return '캠핑장 목록'
})

// 제목 아래 문구: 진단 답에서 무엇을 먼저 봤는지
const NEARBY_LABEL = { er: '응급실', fire: '소방서', police: '파출소', store: '편의점', toilet: '공중화장실', cctv: 'CCTV', light: '보안등', house: '인근 주택' }
const reason = computed(() => {
  if (showExcluded.value) return '안전점수가 낮아 추천하지 않는 곳이에요'
  if (!matchMine.value) return '' // 내 유형 맞춤을 끄면 안내 문구 없이 목록만
  const parts = []
  const heating = diagnosis.value.heating || []
  if (heating.includes('fuel') || heating.includes('wood')) parts.push('난방 안전')
  const first = diagnosis.value.facilities?.[0]
  if (first) parts.push(`${NEARBY_LABEL[first]} 가까운 곳`)
  return parts.length ? `${parts.join(' · ')}을 먼저 봤어요` : '안전점수가 높은 곳부터 보여 드려요'
})

const hasDummy = computed(() => allCamps.value.some((c) => c.dummy))

// 칩에 보일 글씨
const regionLabel = computed(() => trip.state.sigungu?.name || trip.state.sido?.name || '전국')
const dateLabel = computed(() =>
  trip.state.checkIn && trip.state.checkOut
    ? `${shortLabel(trip.state.checkIn)}–${shortLabel(trip.state.checkOut)}`
    : '인원',
)

// 시트에서 적용 → 목록을 펼쳐 결과를 보여 준다 (목록은 watch 가 다시 불러옴)
function applied() {
  openSheet.value = null
  showExcluded.value = false
  selectedId.value = null
  sheetOpen.value = true
}

function openDetail(id) {
  router.push(`/camps/${id}`)
}

function onMarker(id) {
  if (selectedId.value === id) openDetail(id)
  else selectedId.value = id
}
</script>

<template>
  <div class="explore">
    <div ref="stageEl" class="stage">
      <CampMap :camps="mapCamps" :facilities="mapFacilities" :selected-id="selectedId" :bottom-inset="COLLAPSED"
        @select="onMarker" @layers="openLayers" @bounds="onBounds" />
      <!-- 캠핑장 지도 ↔ 차박 지도 전환 (목록을 펼치면 시트가 덮는다) -->
      <MapModeToggle active="camp" class="mode-toggle" />

      <!-- 지도 위 시트 -->
      <section class="sheet drag-sheet" :class="{ open: sheetOpen, dragging: sheetDragging }"
        :style="sheetStyle">
        <!-- 손잡이를 끌어서 목록을 올리고 내린다. 누르면 목록 ↔ 지도 -->
        <button type="button" class="handle drag-handle" :aria-label="sheetOpen ? '지도 보기' : '목록 보기'"
          :aria-expanded="sheetOpen" v-on="sheetDrag">
          <span />
        </button>

        <label class="search">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="M21 21l-4.5-4.5" />
          </svg>
          <input v-model="query" type="search" placeholder="캠핑장 · 지역 검색" aria-label="캠핑장 · 지역 검색"
            @focus="sheetOpen = true" />
        </label>

        <!-- 필터 줄 (가로 스크롤: 손가락 밀기 · 마우스 휠(v-hscroll) · 마우스 기기에선 얇은 막대(.hscroll)) -->
        <div v-hscroll class="filters hscroll">
          <button type="button" class="chip icon" disabled title="상세 필터 (준비 중)" aria-label="상세 필터 (준비 중)">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" aria-hidden="true">
              <path d="M4 7h16M7 12h10M10 17h4" />
            </svg>
          </button>
          <button type="button" class="chip mine" :class="{ on: matchMine }" :aria-pressed="matchMine"
            @click="matchMine = !matchMine">
            <i />내 유형 맞춤
          </button>
          <button type="button" class="chip" aria-haspopup="dialog" @click="openSheet = 'region'">
            {{ regionLabel }} ▾
          </button>
          <button type="button" class="chip" aria-haspopup="dialog" @click="openSheet = 'trip'">
            {{ dateLabel }} ▾
          </button>
        </div>

        <!-- 목록 (시트를 펼쳤을 때만 보임) -->
        <div v-show="sheetOpen || sheetDragging" class="list-area">
          <div class="list-head">
            <div>
              <h1>{{ title }}</h1>
              <p v-if="reason" class="reason">{{ reason }}</p>
            </div>
            <label class="sort">
              <select v-model="sort" aria-label="정렬">
                <option value="score">안전점수순</option>
                <option value="er">응급실 가까운순</option>
              </select>
            </label>
          </div>
          <p v-if="hasDummy || (matchMine && recommend.criteria)" class="tag">
            
          </p>

          <p v-if="error" class="state">
            {{ error }} <button type="button" class="link" @click="load">다시 시도</button>
          </p>
          <p v-else-if="loading && !listCamps.length" class="state">불러오는 중...</p>
          <p v-else-if="!listCamps.length" class="state">조건에 맞는 캠핑장이 없어요.</p>

          <ol v-else class="list">
            <CampRow v-for="(camp, i) in listCamps" :key="camp.id" :camp="camp" :rank="isRanked ? i + 1 : 0"
              :favorite="favorite.has(camp.id)" @open="openDetail" @favorite="favorite.toggle" />
          </ol>

          <p v-if="isRanked && recommend.excluded.length" class="foot">
            주의 · 위험 등급 캠핑장 {{ recommend.excluded.length }}곳은 추천에서 뺐어요 ·
            <button type="button" class="link" @click="showExcluded = true">따로 보기</button>
          </p>
          <p v-if="showExcluded" class="foot">
            <button type="button" class="link" @click="showExcluded = false">추천 목록으로 돌아가기</button>
          </p>
        </div>
      </section>

      <!-- 목록일 때 떠 있는 "지도" 버튼 -->
      <button v-if="sheetOpen" type="button" class="to-map" @click="sheetOpen = false">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true">
          <path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z" />
          <path d="M9 4v14M15 6v14" />
        </svg>
        지도
      </button>
    </div>

    <BottomNav active="explore" />

    <!-- 1분 진단 안내 팝업 (진단 전에만) -->
    <div v-if="showSurveyPrompt" class="prompt-back">
      <div class="prompt" role="dialog" aria-modal="true" aria-labelledby="prompt-title">
        <!-- 장식 그림이라 alt 는 비워 둔다 (스크린리더가 읽지 않게) -->
        <img :src="campingImg" alt="" class="prompt-img" />
        <h2 id="prompt-title">내 유형 진단하기</h2>
        <p>결과에 따라 안전수칙과 캠핑장 추천이 달라집니다.</p>
        <div class="prompt-buttons">
          <button type="button" class="later" @click="later">나중에하기</button>
          <button type="button" class="now" @click="startSurvey">지금하기</button>
        </div>
      </div>
    </div>

    <!-- 아래 시트 (화면 전체를 어둡게 덮음) -->
    <RegionSheet v-if="openSheet === 'region'" @close="openSheet = null" @apply="applied" />
    <TripSheet v-if="openSheet === 'trip'" @close="openSheet = null" @apply="applied" />
    <LayerSheet v-if="openSheet === 'layers'" :layers="layers" :on="facilityOn" @close="openSheet = null"
      @toggle="toggleFacility" />
  </div>
</template>

<style scoped>
.explore {
  position: relative; /* 아래 시트가 이 화면을 덮는 기준 */
  height: 100%;
  display: flex;
  flex-direction: column;
}
.stage {
  position: relative;
  flex: 1;
  overflow: hidden; /* 접힌 시트·지도가 아래 탭을 넘지 않게 */
}
.mode-toggle {
  position: absolute;
  top: 14px;
  left: 16px; /* 왼쪽 위. 지도 버튼은 오른쪽 위에 세로로 (CampMap .map-btns) */
  z-index: 25; /* 지도 버튼(20) 위, 아래 시트(30) 밑 */
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
  /* 높이 애니메이션은 전역 .drag-sheet (styles/base.css) */
}
.sheet.open {
  box-shadow: none;
}
.handle {
  flex: none;
  height: 28px;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.handle span {
  width: 44px;
  height: 5px;
  border-radius: 3px;
  background: var(--color-border);
}
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
.chip:disabled {
  color: var(--color-text-sub);
  cursor: default;
}
.chip.icon {
  width: 42px;
  padding: 0;
  justify-content: center;
  color: var(--color-text);
}
.chip.mine {
  font-weight: 700;
}
.chip.mine i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-border);
}
.chip.mine.on {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-card);
}
.chip.mine.on i {
  background: var(--color-safe-bg);
}
.sort select {
  border: none;
  background: none;
  font: inherit;
  cursor: pointer;
  outline: none;
}
.list-area {
  flex: 1;
  overflow-y: auto;
  margin: 0 -16px;
  padding: 8px 16px 96px; /* 아래 "지도" 버튼에 마지막 줄이 가리지 않게 */
}
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
h1 {
  margin: 0;
  font-size: 21px;
  font-weight: 900;
}
.reason {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.sort {
  flex: none;
  height: 34px;
  padding: 0 12px;
  border-radius: 17px;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  font-size: 13px;
}
.tag {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--color-caution);
}
.list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.state {
  margin: 32px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.foot {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--color-text);
  text-decoration: underline;
  cursor: pointer;
}
/* 진단 안내 팝업: 화면 전체를 어둡게 덮고 가운데에 카드 */
.prompt-back {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.45);
}
.prompt {
  width: 100%;
  max-width: 344px;
  padding: 24px 16px 20px;
  text-align: center;
  border-radius: 20px;
  background: var(--color-card);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
/* 높이를 제한해서 버튼이 화면 아래로 밀리지 않게 한다 */
.prompt-img {
  align-self: center;
  height: 110px;
  width: auto;
  margin-bottom: 4px;
}
.prompt h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 900;
}
.prompt p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  letter-spacing: -0.03em;
  word-break: keep-all; /* 단어 중간에서 줄이 바뀌지 않게 */
  text-wrap: balance; /* 좁은 화면에서 두 줄이 되더라도 "다." 만 홀로 남지 않게 */
  color: var(--color-text);
}
.prompt-buttons {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.prompt-buttons button {
  height: 48px;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.prompt-buttons .later {
  flex: 1;
  border: 1.5px solid var(--color-text);
  background: var(--color-card);
  color: var(--color-text);
}
.prompt-buttons .now {
  flex: 1.4;
  border: 0;
  background: var(--color-text);
  color: var(--color-card);
}
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
</style>
