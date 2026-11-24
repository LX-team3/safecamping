// 재난 대피 3D 지도의 실시간 부분: 앱(MobileDisasterMapView)과 웹(WebDisasterMapView)이 함께 쓴다
// 실시간: GET /api/disaster/now (기상청 초단기실황 3·12·24시간 비 + 상류 반월촌교 수위 + 인제군 호우특보 → 서버가 셋 중 높은 단계로 판단). 10분마다 다시 받는다
//   호우특보(주의보 → 준비, 경보 → 대피)는 비가 오기 전에 내리는 예보 기반 경고라, 실제 비가 기준에 닿기 전에 단계가 오른다 (2026-11-23)
//   받지 못하면(키 없음·기상청 장애) 예시 값을 쓰지 않고 미리 보기(막대)만 보여 준다
// 쓰는 법: const live = useDisasterLive(state, map)
//   지도를 만들기 전에 live.start(), 지도를 만든 뒤 live.mapReady(), 화면을 떠날 때 live.stop()
//   특보 칸에는 live.alert 를 쓴다 (미리 보기 중에는 그 비에 맞는 예시 특보)
//   막대로 미리 보다가 "현상황 보기"를 누르면 live.backToLive() — 마지막으로 받은 실황으로 돌아간다
//   시연용 호우특보 예시 알림을 누르고 들어오면 live.showDemo(예시) — 특보 칸에 그 예시를 "예시"로 표시 (미리 보기 중에만)
import { computed, shallowRef } from 'vue'
import { api } from '@/utils/api'

const REFRESH_MS = 10 * 60 * 1000

const hhmm = d => `${d.getMonth() + 1}.${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`

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

/**
 * @param {object} state  initialState() 로 만든 화면 상태 (reactive)
 * @param {import('vue').ShallowRef} map  createDisasterMap 이 돌려준 동작들 (아직 없으면 null)
 */
export function useDisasterLive(state, map) {
  const now = shallowRef(null)  // 마지막으로 받은 /api/disaster/now 결과
  let alive = true
  let timer = null
  let first = null
  const demo = shallowRef(null) // 알림 예시(data/rainWarningDemo.js) 특보 칸. 실황으로 돌아가면 보이지 않는다

  // 기상특보 칸
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
    const f = w[0]
    state.alert = {
      title: f.category + (w.length > 1 ? ` 외 ${w.length - 1}건` : '') + ' 발효 중',
      time: (f.issuedAt ? hhmm(new Date(f.issuedAt)) + ' · ' : '') + '인제군',
      area: '대상: ' + w.map(x => x.area).join(' / '),
      body: w.map(x => x.message).join(' '),
      src: '출처: 기상청 기상특보 현황',
      warn: w.some(x => x.category.includes('경보')), calm: false,
    }
  }

  const alert = computed(() => {
    const preview = !state.live.on && state.live.status !== 'loading'
    if (preview && demo.value) return demo.value // 예시 알림으로 들어온 경우: 실제 특보보다 예시를 먼저 (제목에 "예시" 표시)
    if (!preview || !state.alert.calm) return state.alert
    return PREVIEW_ALERT[state.stage.level ?? 0] || PREVIEW_ALERT[0]
  })

  // 서버 단계(비·수위·호우특보 중 높은 쪽) → 지도 단계 (비를 못 받아도 수위나 특보로 단계가 정해지면 실시간으로 본다)
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
    // 막대로 미리 보기 중이면 단계는 그대로 두고, 실시간으로 돌아올 때 쓴다
    if (state.live.on || state.live.status === 'loading') showRain(now.value)
    else if (now.value?.level == null) state.live.status = 'none'
  }

  return {
    alert,
    start() { first = loadNow() },
    // 지도보다 실황이 먼저 왔으면 지금 반영하고, 10분마다 다시 받기 시작
    async mapReady() {
      await first
      if (alive && state.live.status !== 'none') showRain(now.value)
      if (alive && !timer) timer = setInterval(loadNow, REFRESH_MS)
    },
    stop() { alive = false; clearInterval(timer) },
    backToLive() { demo.value = null; if (now.value?.level != null) showRain(now.value) },
    // d: RAIN_DEMOS 의 한 항목. 비 양은 화면이 map.setRain 으로 따로 넣는다 (미리 보기 상태가 됨)
    showDemo(d) {
      demo.value = {
        title: d.category + ' 발효 (예시)', time: '시연용 예시 · ' + d.area, area: '대상: ' + d.area,
        body: d.lines.slice(0, 2).join(' ') + ' 실제 특보가 아니에요. 실제 특보는 기상청 날씨누리나 재난문자로 확인하세요.',
        src: '', warn: d.category.includes('경보'), calm: false,
      }
    },
  }
}
