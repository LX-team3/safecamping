<script setup>
// 내 주변 탭 (2부 모바일 · 캠핑 시작 이후)
//  - 위: 지도 (내 위치 + 번호 붙은 안전 시설). 오른쪽 위 레이어 버튼 → "지도에 보일 것" 시트
//  - 아래 패널: 지금 이 위치 안전점수 · 119 신고 · 가족에게 내 위치 보내기 · 가까운 시설 목록
//  - 시설 시간은 "차로 n분"만 보여 준다. 길찾기는 카카오맵으로 넘긴다 (앱 안 경로 화면 없음)
// 데이터: GET /api/nearby?lat=&lng= (가장 가까운 캠핑장 기준 — 더미), /api/nearby/layers, /api/alerts?sido=
import { computed, onMounted, ref } from 'vue'
import NearbyMap from '@/components/nearby/NearbyMap.vue'
import LayerSheet from '@/components/nearby/LayerSheet.vue'
import BottomNav from '@/components/common/BottomNav.vue'
import { api } from '@/utils/api'
import { FACILITY_LABEL, directionsUrl, shortSido } from '@/utils/place'

const PANEL = 430 // 아래 패널 높이(px) — 지도가 마커를 이 위쪽에 모아 그린다

// 위치를 못 받았을 때 보여 줄 예시 위치 (더미 캠핑장 설하캠핑장 좌표, db/data_camp.sql)
const SAMPLE = { lat: 38.14, lng: 128.205 }

const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '-' }
const SHORT_LABEL = { er: '응급실', fire: '소방서', police: '지구대', store: '편의점' }

const me = ref(null) // { lat, lng }
const isSample = ref(false) // true: 내 위치가 아니라 예시 위치
const data = ref(null) // NearbyResponse
const layers = ref([])
const on = ref({}) // { er: true, ... }
const warnings = ref(null) // 발효 중 기상특보 수 (못 받으면 null)
const loading = ref(true)
const error = ref('')
const sheetOpen = ref(false)
const shareMsg = ref('')

// 브라우저 위치 → 실패하면 예시 위치
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

async function load() {
  loading.value = true
  error.value = ''
  try {
    const here = await locate()
    isSample.value = !here
    me.value = here || SAMPLE
    const [res, layerList] = await Promise.all([
      api('/api/nearby', { params: me.value }),
      layers.value.length ? layers.value : api('/api/nearby/layers'),
    ])
    data.value = res
    if (!layers.value.length) {
      layers.value = layerList
      on.value = Object.fromEntries(layerList.map((l) => [l.code, l.on]))
    }
    loadWarnings(res.basis?.sido)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

// 기상특보는 없어도 화면은 보여야 하므로 실패하면 조용히 넘어간다
async function loadWarnings(sido) {
  warnings.value = null
  if (!sido) return
  try {
    const res = await api('/api/alerts', { params: { sido: shortSido(sido) } })
    warnings.value = res.activeWarnings
  } catch {
    warnings.value = null
  }
}

onMounted(load)

const basis = computed(() => data.value?.basis)
const facilities = computed(() => data.value?.facilities || [])
// 켜 둔 레이어의 시설만 지도·목록에 보인다
const shown = computed(() => facilities.value.filter((f) => on.value[f.type]))

const regionLabel = computed(() => {
  const b = basis.value
  return b ? [shortSido(b.sido), b.sigungu, b.eupmyeon].filter(Boolean).join(' ') : '내 주변'
})
const warningLabel = computed(() => {
  if (warnings.value === null) return '기상특보 확인 못 함'
  return warnings.value ? `기상특보 ${warnings.value}건` : '기상특보 없음'
})

// 점수 아래 한 줄: 차로 몇 분만 (예: 소방서 5분 · 지구대 9분 · 응급실 17분)
const summary = computed(() =>
  facilities.value
    .filter((f) => f.type !== 'store')
    .map((f) => `${SHORT_LABEL[f.type] || FACILITY_LABEL[f.type]} ${f.driveMin}분`)
    .join(' · '),
)

// 기준 안내: 지금 위치 자체의 점수가 아니라 가까운 캠핑장 값을 쓰고 있다는 것을 숨기지 않는다
const basisNote = computed(() => {
  const b = basis.value
  if (!b) return ''
  const where = isSample.value ? '위치를 못 받아 예시 위치로 보여 드려요 · ' : ''
  const far = b.far ? `가장 가까운 데이터 ${b.campName}(${b.distanceKm}km) 기준` : `${b.campName} 기준`
  return `${where}${far}${data.value.dummy ? ' · 더미 데이터' : ''}`
})

// 시설 이름: 이름에 종류가 이미 들어 있으면 그대로, 아니면 "응급실 · 인제군보건의료원"
function titleOf(f) {
  const label = SHORT_LABEL[f.type] || FACILITY_LABEL[f.type] || ''
  return f.name.includes(label) ? f.name : `${label} · ${f.name}`
}

// 길찾기: 카카오맵으로만 넘긴다. 더미 시설은 좌표 대신 이름 검색 (utils/place.js)
function route(f) {
  const from = isSample.value ? null : { name: '내 위치', ...me.value }
  window.open(directionsUrl(f, from), '_blank', 'noopener')
}

function toggle(code) {
  on.value = { ...on.value, [code]: !on.value[code] }
}

// 가족에게 내 위치 보내기: 휴대폰 공유 창 → 없으면 링크 복사
// (가족방으로 바로 보내기는 2부 가족 위치 기능에서)
async function shareLocation() {
  shareMsg.value = ''
  if (isSample.value) {
    shareMsg.value = '위치 권한이 없어 내 위치를 보낼 수 없어요.'
    return
  }
  const url = `https://map.kakao.com/link/map/${encodeURIComponent('내 위치')},${me.value.lat},${me.value.lng}`
  try {
    if (navigator.share) {
      await navigator.share({ title: '내 위치', text: '지금 여기 있어요', url })
    } else {
      await navigator.clipboard.writeText(url)
      shareMsg.value = '내 위치 링크를 복사했어요. 가족 대화방에 붙여 넣어 주세요.'
    }
  } catch (e) {
    if (e?.name !== 'AbortError') shareMsg.value = '공유하지 못했어요. 다시 시도해 주세요.'
  }
}
</script>

<template>
  <div class="nearby">
    <div class="stage">
      <NearbyMap :me="me" :facilities="shown" :bottom-inset="PANEL" />

      <!-- 왼쪽 위: 지역 · 기상특보 -->
      <div class="status">
        <i :class="{ warn: warnings }" aria-hidden="true" />
        <div>
          <b>{{ regionLabel }} · {{ warningLabel }}</b>
          <span>날씨 정보는 준비 중이에요</span>
        </div>
      </div>

      <!-- 오른쪽 위: 레이어 -->
      <button type="button" class="layer-btn" aria-label="지도에 보일 것" aria-haspopup="dialog"
        :disabled="!layers.length" @click="sheetOpen = true">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3l9 5-9 5-9-5z" />
          <path d="M3 13l9 5 9-5" />
        </svg>
      </button>

      <!-- 아래 패널 -->
      <section class="panel" :style="{ height: PANEL + 'px' }">
        <span class="grip" aria-hidden="true" />

        <p v-if="loading" class="state">내 위치를 찾는 중...</p>
        <p v-else-if="error" class="state">
          {{ error }} <button type="button" class="link" @click="load">다시 시도</button>
        </p>
        <p v-else-if="!basis" class="state">주변 안전 데이터가 아직 없어요.</p>

        <template v-else>
          <div class="score">
            <div class="circle" :class="data.grade">
              <b>{{ data.safetyScore ?? '-' }}</b>{{ GRADE_LABEL[data.grade] }}
            </div>
            <div class="score-text">
              <h1>지금 이 위치 안전점수</h1>
              <p v-if="summary">차로 {{ summary }}</p>
              <p class="note">{{ basisNote }}</p>
            </div>
          </div>

          <div class="actions">
            <a class="call" href="tel:119">119 신고</a>
            <button type="button" class="share" @click="shareLocation">가족에게 내 위치 보내기</button>
          </div>
          <p v-if="shareMsg" class="share-msg" role="status">{{ shareMsg }}</p>

          <ol class="list">
            <li v-for="f in shown" :key="f.id" class="row">
              <span class="no">{{ f.no }}</span>
              <div class="name">
                <b>{{ titleOf(f) }}</b>
                <span>{{ f.distanceKm }}km</span>
              </div>
              <b class="time" :class="f.tone">차로 {{ f.driveMin }}분</b>
              <button type="button" class="go" @click="route(f)">길찾기</button>
            </li>
          </ol>
          <p v-if="!shown.length" class="state small">지도에 보일 시설을 켜 주세요.</p>
        </template>
      </section>
    </div>

    <BottomNav active="nearby" />

    <LayerSheet v-if="sheetOpen" :layers="layers" :on="on" @close="sheetOpen = false" @toggle="toggle" />
  </div>
</template>

<style scoped>
.nearby {
  position: relative; /* 레이어 시트가 이 화면을 덮는 기준 */
  height: 100%;
  display: flex;
  flex-direction: column;
}
.stage {
  position: relative;
  flex: 1;
  overflow: hidden;
}
.status {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 76px;
  z-index: 20;
  padding: 10px 14px;
  border-radius: 14px;
  background: var(--color-card);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  gap: 10px;
}
.status i {
  flex: none;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--color-green);
}
.status i.warn {
  background: var(--color-danger);
}
.status div {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.status b {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.status span {
  font-size: 12px;
  color: var(--color-text-sub);
}
.layer-btn {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 20;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  cursor: pointer;
}
.panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  max-height: calc(100% - 80px);
  display: flex;
  flex-direction: column;
  padding: 0 16px 16px;
  overflow-y: auto;
  border-radius: 20px 20px 0 0;
  background: var(--color-card);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
}
.grip {
  flex: none;
  align-self: center;
  width: 44px;
  height: 5px;
  margin: 10px 0 14px;
  border-radius: 3px;
  background: var(--color-border);
}
.score {
  display: flex;
  align-items: center;
  gap: 14px;
}
.circle {
  flex: none;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--color-text-sub);
  color: var(--color-card);
  font-size: 12px;
}
.circle b {
  font-size: 24px;
  line-height: 1.1;
}
.circle.safe {
  background: var(--color-green);
}
.circle.caution {
  background: var(--color-caution);
}
.circle.danger {
  background: var(--color-danger);
}
.score-text {
  min-width: 0;
}
h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 900;
}
.score-text p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-mid);
}
.score-text .note {
  font-size: 12px;
  color: var(--color-caution);
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.call,
.share {
  flex: 1;
  height: 48px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}
.call {
  background: var(--color-danger);
  color: var(--color-card);
}
.share {
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
}
.share-msg {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-text-sub);
}
.list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-line);
}
.no {
  flex: none;
  width: 26px;
  height: 26px;
  border: 2px solid var(--color-text);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}
.name {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.name b {
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.name span {
  font-size: 12px;
  color: var(--color-text-sub);
}
.time {
  flex: none;
  font-size: 13px;
}
.time.safe {
  color: var(--color-green);
}
.time.caution {
  color: var(--color-caution);
}
.go {
  flex: none;
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: 17px;
  background: var(--color-bg-soft);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.state {
  margin: 24px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.state.small {
  margin: 16px 0;
  font-size: 13px;
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
</style>
