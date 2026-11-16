<script setup>
// 차박 지도: 지도에서 찍은 곳이 차박 불가 · 주의 · 제한 없음 · 야영 가능 중 어디인지 보여 준다
// 탐색 화면(캠핑장 지도)과 위쪽 "캠핑장 | 차박" 토글로 오간다. 아래 탭은 탐색으로 표시
// 판정은 서버 GET /api/chabak/verdict (DB 의 판정 함수가 계산). 지금은 인제군만 판정한다
import { computed, onMounted, ref } from 'vue'
import ChabakMap from '@/components/chabak/ChabakMap.vue'
import LayerSheet from '@/components/nearby/LayerSheet.vue'
import MapModeToggle from '@/components/common/MapModeToggle.vue'
import BottomNav from '@/components/common/BottomNav.vue'
import { api } from '@/utils/api'
import { INJE_CENTER, LAYER_CHIPS, VERDICT, distanceText } from '@/utils/chabak'
import { useDragSheet, useElementHeight } from '@/utils/dragSheet'

const mapRef = ref(null)
const picked = ref(null) // 누른 지점 { lat, lng }
const result = ref(null) // 판정 응답 (server ChabakVerdictResponse)
const loading = ref(false)
const error = ref(null) // { message, outside } — outside: 인제군 밖(404)

// ---- 아래 시트 높이: 3단계 (작게 · 카드 · 전체) — 끌기·애니메이션은 utils/dragSheet.js ----
// 작게 = 손잡이 + 판정 한 줄, 카드 = 내용 높이에 맞춤(화면의 60%까지), 전체 = 위쪽 지도만 조금 남김
const MIN_H = 84
const TOP_GAP = 56 // 전체일 때 위에 남기는 지도 높이
const stageEl = ref(null)
const contentEl = ref(null)
const stageH = useElementHeight(stageEl, 700) // 지도 칸 높이 (아래 탭 뺀 것)
const contentH = useElementHeight(contentEl) // 시트 안 내용의 실제 높이 (결과가 오거나 자세히 보기로 늘어나면 다시 잼)

const snapHeights = computed(() => ({
  min: MIN_H,
  card: Math.max(MIN_H, Math.min(28 + contentH.value, stageH.value * 0.6)),
  full: stageH.value - TOP_GAP,
}))
const { snap, dragging, style: sheetStyle, events: dragEvents } = useDragSheet(snapHeights, 'card')
// 카드·작게일 때 안쪽 스크롤 칸을 보이는 높이에 맞춘다 (단계가 정해진 뒤에만 바뀜 → 끄는 동안 다시 배치 없음)
const bodyMax = computed(() => snapHeights.value[snap.value] - 28)
const expanded = computed(() => snap.value === 'full') // 자세히 보기 (사유 전부 · 규제구역 · 근처 목록)

// ---- 판정 표시 레이어 (불가 구역 · 주의 구역 · 차박 후보지 · 등록 야영장) ----
// "지도 설정" 시트의 "판정 표시"에서 켜고 끈다 (지도 위를 가리지 않게 칩 줄 대신)
// 여러 개를 같이 켤 수 있다. 아래 시트의 ✕ 는 전부 끈다. 받은 목록은 기억해 두고 다시 켤 때 바로 쓴다
const verdictLayers = LAYER_CHIPS.map((c) => ({ ...c, colorVar: VERDICT[c.verdict].colorVar }))
const layerOn = ref({}) // { no: true, open: true, ... } 켜짐 여부
const layerData = ref({}) // type → { items, loading, error }
// 켜 둔 것만, 칩 순서대로 (아래 시트 제목 줄)
const activeLayers = computed(() => verdictLayers.filter((c) => layerOn.value[c.type]))
// 지도에 넘길 값: 다 받아 온 레이어만 [{ type, verdict, items }]
const mapLayers = computed(() =>
  activeLayers.value
    .filter((c) => layerData.value[c.type]?.items)
    .map((c) => ({ type: c.type, verdict: c.verdict, items: layerData.value[c.type].items })),
)

async function toggleLayer(type) {
  if (layerOn.value[type]) {
    layerOn.value = { ...layerOn.value, [type]: false }
    return
  }
  layerOn.value = { ...layerOn.value, [type]: true }
  // 켜면 판정 결과를 비우고, 시트를 작게 내려 지도가 넓게 보이게 한다 (한 줄 제목만)
  picked.value = null
  result.value = null
  error.value = null
  snap.value = 'min'
  if (layerData.value[type]?.items) return // 전에 받은 것
  layerData.value = { ...layerData.value, [type]: { items: null, loading: true, error: '' } }
  try {
    const items = await api('/api/chabak/layer', { params: { type } })
    layerData.value = { ...layerData.value, [type]: { items, loading: false, error: '' } }
  } catch (e) {
    layerData.value = { ...layerData.value, [type]: { items: null, loading: false, error: e.message } }
  }
}

function allLayersOff() {
  layerOn.value = {}
}

// ---- 지도 레이어 (안전 시설) — 탐색·내 주변과 같은 "지도 설정" 시트 ----
// 줄 목록·켜짐 기본값은 서버 GET /api/nearby/layers, 시설은 찍은 지점 근처 GET /api/chabak/facilities (종류별 3곳)
const layers = ref([]) // [{ code, icon, label, desc, ready, on }]
const facilityOn = ref({}) // { er: true, ... }
const layerSheetOpen = ref(false)
const nearFacilities = ref([]) // 찍은 지점 근처 시설

async function loadLayers() {
  try {
    const list = await api('/api/nearby/layers')
    layers.value = list
    facilityOn.value = Object.fromEntries(list.map((l) => [l.code, l.on]))
  } catch {
    // 못 불러오면 레이어 버튼을 눌렀을 때 다시 시도한다 (그동안 시설은 전부 보인다)
  }
}
onMounted(loadLayers)

async function openLayers() {
  if (!layers.value.length) await loadLayers()
  if (layers.value.length) layerSheetOpen.value = true
}
function toggleFacility(code) {
  facilityOn.value = { ...facilityOn.value, [code]: !facilityOn.value[code] }
}
// 켜 둔 종류만 지도에. 레이어 목록을 아직 못 받았으면(undefined) 보여 준다 (안전 정보라 기본은 숨기지 않음)
const mapFacilities = computed(() => nearFacilities.value.filter((f) => facilityOn.value[f.type] !== false))

// 시설은 판정과 따로 받는다 (시설을 못 받아도 판정은 보여 줌)
async function loadFacilities(lat, lng, no) {
  try {
    const list = await api('/api/chabak/facilities', { params: { lat, lng } })
    if (no === requestNo) nearFacilities.value = list
  } catch {
    if (no === requestNo) nearFacilities.value = []
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
  picked.value = null
  error.value = null
}

// 근처 차박 후보를 누르면 그 자리로 지도를 옮기고 바로 판정한다
function openAlternative(a) {
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
      <ChabakMap ref="mapRef" :picked="picked" :facilities="mapFacilities" :parcel="result?.parcel"
        :verdict="result?.verdict" :layers="mapLayers" @pick="pick" />
      <MapModeToggle active="chabak" class="toggle" />


      <!-- 오른쪽 위에 모은 지도 버튼: 지도 설정 · 내 위치 (탐색 지도와 같은 자리·모양, 전역 .map-btns · .map-btn) -->
      <div class="map-btns">
        <button type="button" class="map-btn" aria-label="지도 설정" aria-haspopup="dialog" @click="openLayers">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linejoin="round" aria-hidden="true">
            <path d="M12 3l9 5-9 5-9-5z" />
            <path d="M3 13l9 5 9-5" />
          </svg>
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
        </button>
      </div>
      <p v-if="locateMsg" class="locate-msg" role="status">{{ locateMsg }}</p>

      <section class="sheet drag-sheet" :class="{ dragging }" :style="sheetStyle">
        <!-- 손잡이: 끌어서 올리고 내림, 누르면 다음 단계 (작게 → 카드 → 전체) -->
        <button type="button" class="handle drag-handle" :aria-label="snap === 'full' ? '카드 내리기' : '시트 올리기'"
          :aria-expanded="expanded" v-on="dragEvents">
          <span />
        </button>

        <div class="body" :class="{ clip: snap === 'min' && !dragging }" :style="{ maxHeight: bodyMax + 'px' }">
          <div ref="contentEl" class="content">
          <!-- 1. 아직 안 누름: 안내 + 판정 색 범례 -->
          <!-- 판정 표시를 켰으면: 켠 것들을 한 줄로 (시트가 작게 내려가 지도가 넓게). 올리면 각각 짧은 설명 -->
          <template v-if="!picked && activeLayers.length">
            <div class="layer-title">
              <ul class="layer-list">
                <li v-for="c in activeLayers" :key="c.type">
                  <i :style="{ background: `var(${c.colorVar})` }" />{{ c.label }}
                  <span v-if="layerData[c.type]?.loading" class="count">…</span>
                  <!-- 개수는 점(후보지·야영장)만. 구역은 서버가 한 덩어리로 합쳐 보내서 개수가 의미 없음 -->
                  <span v-else-if="layerData[c.type]?.items && !c.zone" class="count">
                    {{ layerData[c.type].items.length }}
                  </span>
                </li>
              </ul>
              <button type="button" class="layer-off" aria-label="판정 표시 모두 끄기" @click="allLayersOff">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2"
                  stroke-linecap="round" aria-hidden="true">
                  <path d="M6 6l12 12M18 6L6 18" />
                </svg>
              </button>
            </div>
            <p v-for="c in activeLayers" :key="c.type" class="sub">
              <b>{{ c.label }}</b> · {{ layerData[c.type]?.error || c.note }}
            </p>
            <p class="sub">지도를 누르면 그 자리 판정을 볼 수 있어요</p>
          </template>

          <template v-else-if="!picked">
            <h1>차박할 곳을 지도에서 눌러 보세요</h1>
            <p class="sub">누른 땅이 차박해도 되는 곳인지 판정해 드려요</p>
            <ul class="legend">
              <li v-for="(v, name) in VERDICT" :key="name">
                <i :style="{ background: `var(${v.colorVar})` }" />{{ name }}
              </li>
            </ul>
          </template>

          <!-- 2. 판정 중 -->
          <p v-else-if="loading" class="state">판정하는 중...</p>

          <!-- 3. 인제군 밖 · 오류 -->
          <template v-else-if="error">
            <h1>{{ error.outside ? '판정 범위 밖이에요' : '판정하지 못했어요' }}</h1>
            <p class="sub">{{ error.message }}</p>
            <button v-if="error.outside" type="button" class="primary" @click="goInje">인제군으로 이동</button>
            <button v-else type="button" class="outline" @click="pick(picked.lat, picked.lng)">다시 시도</button>
          </template>

          <!-- 4. 판정 결과 -->
          <template v-else-if="result">
            <div class="head">
              <span class="badge" :style="{ background: `var(${verdict?.colorVar ?? '--color-text-sub'})` }">
                {{ result.verdict }}
              </span>
              <h1>{{ verdict?.title ?? result.verdict }}</h1>
            </div>
            <p class="sub">{{ parcelLine }}</p>

            <h2 v-if="expanded && shownReasons.length">판정 사유</h2>
            <ul v-if="shownReasons.length" class="reasons">
              <li v-for="r in shownReasons" :key="r">{{ r }}</li>
            </ul>

            <template v-if="expanded">
              <template v-if="result.zones?.length">
                <h2>걸린 규제구역</h2>
                <div class="zones">
                  <span v-for="z in result.zones" :key="z" class="zone">{{ z }}</span>
                </div>
              </template>

              <template v-if="result.alternatives?.length">
                <h2>근처 차박 후보</h2>
                <p class="note">공공데이터상 제한이 없는 국공유지예요</p>
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
                <ul class="rows">
                  <li v-for="c in result.nearbyCamps" :key="c.name" class="row static">
                    <i :style="{ background: 'var(--color-verdict-camp)' }" />
                    <span class="name">{{ c.name }}</span>
                    <span class="dist">{{ distanceText(c.distM) }}</span>
                  </li>
                </ul>
              </template>
            </template>

            <button v-if="!expanded" type="button" class="outline" @click="snap = 'full'">자세히 보기</button>
          </template>

          <p class="disclaimer">
            공공데이터 기준 참고용 판정이며 법적 경계는 지적측량으로 확정돼요 · 지금은 인제군만 판정해요
          </p>
          </div>
        </div>
      </section>
    </div>

    <BottomNav active="explore" />

    <LayerSheet v-if="layerSheetOpen" :layers="layers" :on="facilityOn" :verdict-layers="verdictLayers"
      :verdict-on="layerOn" @close="layerSheetOpen = false" @toggle="toggleFacility"
      @verdict-layer="toggleLayer" />
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
/* 고른 레이어 한 줄 제목 (시트가 작게일 때 이것만 보임) */
.layer-title {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 켠 레이어들: 한 줄에 이어서 (넘치면 잘림 — 시트를 올리면 아래에 하나씩 설명) */
.layer-list {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  gap: 12px;
  overflow: hidden;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 700;
}
.layer-list li {
  display: flex;
  align-items: center;
  gap: 5px;
}
.layer-list i {
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.layer-list .count {
  font-weight: 500;
  color: var(--color-text-sub);
}
/* 판정 표시 끄기 ✕ (줄 오른쪽 끝) */
.layer-off {
  margin-left: auto;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: var(--color-bg-soft);
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
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
