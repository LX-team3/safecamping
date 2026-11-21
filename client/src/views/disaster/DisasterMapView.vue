<script setup>
// 재난 대피 3D 지도 (인북천 물빛테마공원 캠핑장)
// 지도 자체는 utils/disasterMap.js (CesiumJS) 가 그리고, 이 화면은 위·아래 안내 부품을 배치한다
// 화면을 떠나면 지도를 정리한다 (Cesium 은 그래픽 메모리를 많이 써서 꼭 destroy 해야 함)
// 실시간: GET /api/disaster/now (기상청 초단기실황 3·12·24시간 비 + 상류 반월촌교 수위 + 인제군 기상특보 → 서버가 단계 판단). 10분마다 다시 받는다
//   받지 못하면(키 없음·기상청 장애) 예시 값을 쓰지 않고 시뮬레이션(슬라이더)만 보여 준다
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { createDisasterMap, initialState } from '@/utils/disasterMap'
import { api } from '@/utils/api'
import AlertPill from '@/components/disaster/AlertPill.vue'
import RainSlider from '@/components/disaster/RainSlider.vue'
import EvacSheet from '@/components/disaster/EvacSheet.vue'

const REFRESH_MS = 10 * 60 * 1000

const stage = ref(null)     // 지도가 들어갈 칸
const tagLayer = ref(null)  // 지도 위 이름표 칸
const credits = ref(null)   // 지도 출처 표시 칸
const state = reactive(initialState())
const map = shallowRef(null)
const now = shallowRef(null)  // 마지막으로 받은 /api/disaster/now 결과
let alive = true
let timer = null

const hhmm = d => `${d.getMonth() + 1}.${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`

// 기상특보 칩
function showWarnings(res) {
  if (!res || !res.warningAvailable) {
    state.alert = { title: '특보 정보 없음', time: '받지 못했어요', area: '', body: '기상청 특보를 지금 받을 수 없어요. 기상청 날씨누리나 재난문자를 함께 확인하세요.', src: '', warn: false, calm: true, off: true }
    return
  }
  const w = res.warnings
  if (!w.length) {
    state.alert = { title: '발효 중인 특보 없음', time: '인제군', area: '', body: '지금 인제군에 발효 중인 기상특보가 없어요.', src: '출처: 기상청 기상특보 현황', warn: false, calm: true }
    return
  }
  const first = w[0]
  state.alert = {
    title: first.category + (w.length > 1 ? ` 외 ${w.length - 1}건` : '') + ' 발효 중',
    time: (first.issuedAt ? hhmm(new Date(first.issuedAt)) + ' · ' : '') + '인제군',
    area: '대상: ' + w.map(x => x.area).join(' / '),
    body: w.map(x => x.message).join(' '),
    src: '출처: 기상청 기상특보 현황',
    warn: w.some(x => x.category.includes('경보')), calm: false,
  }
}

// 슬라이더로 미리 보는 중에는 실제 특보가 없을 때, 그 비에 맞는 특보를 "예시"로 보여 준다
// (큰비를 미리 보는데 "특보 없음"이 함께 떠서 헷갈렸음. 60mm=호우주의보, 90mm 이상=호우경보 기준이라 단계에 맞춰 고름.
//  114mm 이상도 특보는 호우경보가 가장 높다. 실제 특보가 발효 중이면 미리 보기 중에도 실제 특보를 그대로 보여 준다)
const PREVIEW_BODY = '비 막대로 미리 보는 화면이에요. 이만큼 비가 오면 보통 이 특보가 내려져요. 실제 특보는 기상청 날씨누리나 재난문자로 확인하세요.'
const PREVIEW_ALERT = [
  { title: '특보 없음', time: '미리 보기 · 예시', warn: false, calm: true },
  { title: '호우주의보', time: '미리 보기 · 예시', warn: false, calm: false },
  { title: '호우경보', time: '미리 보기 · 예시', warn: true, calm: false },
  { title: '호우경보', time: '미리 보기 · 예시', warn: true, calm: false },
].map(a => ({ ...a, area: '', body: PREVIEW_BODY, src: '' }))
const pillAlert = computed(() => {
  const preview = !state.live.on && state.live.status !== 'loading'
  if (!preview || !state.alert.calm) return state.alert
  return PREVIEW_ALERT[state.stage.level ?? 0] || PREVIEW_ALERT[0]
})

// 실황 비·수위 → 지도 단계 (비를 못 받아도 수위로 단계가 정해지면 실시간으로 본다)
function showRain(res) {
  if (!res || res.level == null) {
    state.live.status = 'none'; state.live.on = false
    return
  }
  state.live.status = 'ok'
  const at = res.observedAt || (res.water && res.water.observedAt)
  state.live.time = at ? hhmm(new Date(at)) + ' 관측' : ''
  state.live.reasons = res.reasons || []
  map.value?.setLive(res.level, res.rain3h ?? 0)
}

async function loadNow() {
  try {
    now.value = await api('/api/disaster/now')
  } catch {
    now.value = null  // 서버가 꺼져 있거나 오류 → 실시간 없음
  }
  if (!alive) return
  showWarnings(now.value)
  // 슬라이더로 시뮬레이션 중이면 단계는 그대로 두고, 실시간으로 돌아올 때 쓴다
  if (state.live.on || state.live.status === 'loading') showRain(now.value)
  else if (now.value?.level == null) state.live.status = 'none'
}

onMounted(async () => {
  const first = loadNow()
  try {
    const m = await createDisasterMap({
      container: stage.value, tagLayer: tagLayer.value, credits: credits.value, state,
      base: import.meta.env.BASE_URL + 'disaster-map/',
    })
    if (!alive) { m.destroy(); return }   // 불러오는 사이에 화면을 떠났으면 바로 정리
    map.value = m
    await first
    if (alive && state.live.status !== 'none') showRain(now.value)  // 지도보다 실황이 먼저 왔으면 지금 반영
  } catch (e) {
    state.error = '3D 지도를 불러오지 못했어요: ' + e.message
    console.error(e)
  }
  timer = setInterval(loadNow, REFRESH_MS)
})
onBeforeUnmount(() => {
  alive = false
  clearInterval(timer)
  map.value?.destroy()
})
</script>

<template>
  <div class="dm" :style="{ '--lv': state.lv.color }">
    <div ref="stage" class="stage"></div>
    <div ref="tagLayer" class="tags"></div>
    <div ref="credits" class="credits"></div>

    <header class="topbar">
      <div class="place">
        <span class="app">CAMPIN</span>
        <h1>호우 및 침수 시 대피경로</h1>
        <span class="loc">인북천 물빛테마공원 캠핑장 · 인제군 서화면</span>
      </div>
      <AlertPill :alert="pillAlert" />
    </header>

    <RainSlider v-show="!state.tourOn" :state="state" @change="mm => map?.setRain(mm)" />
    <EvacSheet :state="state" :map="map" />

    <div v-if="state.loading || state.error" class="loading">
      <div v-if="!state.error" class="spin"></div>
      {{ state.error || '3D 지도를 불러오는 중…' }}
    </div>
  </div>
</template>

<style scoped>
/* 이 화면의 어두운 색 (야간 대피 상황을 고려). 지도 위에 겹치는 부품들이 함께 쓴다
   색 값은 tokens.css 의 --color-dm-* · --color-stage-* (팀 규칙: 색은 tokens.css 변수만) */
.dm {
  --dm-ground: var(--color-dm-ground);
  --dm-sheet: var(--color-dm-sheet);
  --dm-card: var(--color-dm-card);
  --dm-line: var(--color-dm-line);
  --dm-text: var(--color-dm-text);
  --dm-muted: var(--color-dm-muted);
  --dm-water: var(--color-dm-water);
  --dm-safe: var(--color-stage-0);
  --dm-caution: var(--color-stage-1);
  --dm-severe: var(--color-stage-3);
  --dm-font-num: 'IBM Plex Mono', ui-monospace, monospace;
  position: relative;
  height: 100%;
  overflow: hidden;
  background: var(--dm-ground);
  color: var(--dm-text);
  font-size: 15px;
}
.stage { position: absolute; inset: 0; touch-action: none; }
.stage :deep(.cesium-widget-credits) { display: none !important; }
.tags { position: absolute; inset: 0; pointer-events: none; z-index: 3; }
.credits { position: absolute; right: 8px; bottom: 174px; z-index: 4; font-size: 9px; color: var(--color-dm-muted); pointer-events: none; text-align: right; }
.credits :deep(a) { color: inherit; pointer-events: auto; }

.topbar {
  position: absolute; left: 0; right: 0; top: 0; z-index: 5;
  display: flex; justify-content: space-between; align-items: flex-start; gap: 10px;
  padding: calc(14px + env(safe-area-inset-top, 0px)) 14px 28px;
  background: linear-gradient(var(--color-dm-shade), transparent);
  pointer-events: none;
}
.place { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.place .app { font-size: 12px; font-weight: 700; color: var(--dm-water); letter-spacing: 0.04em; }
.place h1 { margin: 0; font-size: 18px; font-weight: 700; line-height: 1.3; }
.place .loc { font-size: 12px; color: var(--dm-muted); }

/* 지도 위 이름표 (utils/disasterMap.js 가 만들어 붙임) */
.tags :deep(.tag) {
  position: absolute; transform: translate(-50%, -100%); white-space: nowrap;
  font-size: 12px; font-weight: 700; padding: 4px 9px; border-radius: 7px; margin-top: -8px;
}
.tags :deep(.tag.me) { background: var(--dm-water); color: var(--dm-ground); margin-top: -58px; }   /* 캐릭터 머리 위 */
.tags :deep(.tag.shelter) { background: var(--dm-safe); color: var(--dm-ground); }

.loading {
  position: absolute; inset: 0; z-index: 30; background: var(--dm-ground); color: var(--dm-muted);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; font-size: 14px;
  padding: 0 24px; text-align: center;
}
.spin { width: 32px; height: 32px; border: 3px solid var(--dm-line); border-top-color: var(--dm-water); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spin { animation: none; } }
</style>
