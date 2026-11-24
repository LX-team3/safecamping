<script setup>
// 차박지 상세 (웹 차박지 찾기 왼쪽 패널): 지도 핀이나 목록에서 고른 차박 후보지 · 등록 야영장의 판정과 주변 안전
// 모양은 웹 캠핑장 상세(WebCampDetailPanel)와 같은 틀: 위 막대(목록 · 이름) → 사진 → 이름 → 점수 상자 → 내용 → 아래 버튼
// 데이터 (앱 차박 지도와 같은 API, 로그인 없이 됨)
//   GET /api/chabak/verdict?lat=&lng=     판정 · 사유 · 규제구역 · 필지 경계 · 근처 후보지 · 근처 야영장
//   GET /api/chabak/score?lat=&lng=       안전점수 (직선거리 기준 — 캠핑장 점수와 기준이 다름, server/CLAUDE.md)
//   GET /api/chabak/facilities?lat=&lng=  종류별 가까운 시설 3곳
// 저장(하트)은 없다: 차박 후보지는 캠핑장 표(camp)에 없어 저장 목록(saved_camp)에 담을 수 없음 (사용자 결정)
// 내 캠핑에 일정 등록: 웹 캠핑장 상세와 같은 시트. 등록 야영장은 campId 로, 후보지 · 찍은 자리는 위치(lat·lng)로 저장
//   POST /api/member/camp-plans (stores/myCamping.add) — 차박 자리 일정도 출발 준비 체크리스트가 된다
// parcel 이벤트: 필지 경계를 지도에 판정 색으로 칠하라고 부모(WebExploreView)에 알린다
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { useTripStore } from '@/stores/trip'
import { nightsBetween, todayIso } from '@/utils/date'
import { api } from '@/utils/api'
import { loadKakaoMap } from '@/utils/kakaoMap'
import { FACILITY_LABEL, directionsUrl } from '@/utils/place'
import { VERDICT, distanceText, driveMinOf, verdictColor } from '@/utils/chabak'

const props = defineProps({
  // 고른 자리 { kind: open·camp·point(지도에서 찍은 곳), name, area, lat, lng, imageUrl?, campId? (등록 야영장이면 camp.id) }
  spot: { type: Object, required: true },
})
// score: 안전점수를 받으면 부모에 알린다 → 지도에서 찍은 자리 핀에도 같은 점수가 보이게
const emit = defineEmits(['back', 'parcel', 'pick', 'score'])

const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의', unknown: '점수 없음' }
const TIER_BY_GRADE = { safe: 'safe', caution: 'mid', danger: 'warn' }

const verdict = ref(null) // ChabakVerdictResponse
const score = ref(null) // ChabakScoreResponse
const facilities = ref([]) // [{ id, type, name, lat, lng, distanceKm }]
const error = ref('')
let req = 0 // 다른 자리를 빨리 연달아 고르면 늦게 온 앞 응답이 덮지 않게

async function load() {
  const no = ++req
  error.value = ''
  verdict.value = null
  score.value = null
  facilities.value = []
  emit('parcel', null)
  const at = { lat: props.spot.lat, lng: props.spot.lng }
  // 셋을 동시에 부르고, 하나가 실패해도 나머지는 보여 준다
  const [v, s, f] = await Promise.allSettled([
    api('/api/chabak/verdict', { params: at }),
    api('/api/chabak/score', { params: at }),
    api('/api/chabak/facilities', { params: at }),
  ])
  if (no !== req) return
  if (v.status === 'fulfilled') {
    verdict.value = v.value
    if (v.value.parcel) emit('parcel', { geometry: v.value.parcel, color: verdictColor(v.value.verdict) })
  } else {
    error.value = v.reason?.message || '판정을 불러오지 못했어요.'
  }
  if (s.status === 'fulfilled') {
    score.value = s.value
    emit('score', { score: s.value.score, grade: s.value.grade })
  }
  if (f.status === 'fulfilled') facilities.value = f.value
}
watch(() => `${props.spot.lat},${props.spot.lng}`, load, { immediate: true })
onBeforeUnmount(() => emit('parcel', null)) // 패널을 닫으면 지도의 필지 칠도 지운다

const v = computed(() => verdict.value?.verdict)
const verdictInfo = computed(() => VERDICT[v.value] || null)
const tier = computed(() => TIER_BY_GRADE[score.value?.grade] || 'none')
const KIND_LABEL = { camp: '등록 야영장', open: '차박 후보지', point: '지도에서 찍은 자리' }
const kindLabel = computed(() => KIND_LABEL[props.spot.kind] || '차박 후보지')
const subLine = computed(() => [kindLabel.value, props.spot.area, verdict.value?.jibun && `지번 ${verdict.value.jibun}`].filter(Boolean).join(' · '))
// 땅 정보 줄 (값이 있는 것만)
const landRows = computed(() => {
  const r = verdict.value
  if (!r) return []
  return [
    ['지목', r.jimok], ['소유', r.owner], ['토지이용', r.landUse], ['지형', r.terrain], ['도로', r.roadSide],
  ].filter(([, val]) => val)
})
// 가까운 시설: 종류별 가장 가까운 한 곳만 (응급실 · 소방서 · 지구대 · 약국 · 편의점 순)
const FAC_ORDER = ['er', 'fire', 'police', 'pharmacy', 'store']
const nearest = computed(() =>
  FAC_ORDER.map((t) => facilities.value.filter((f) => f.type === t).sort((a, b) => a.distanceKm - b.distanceKm)[0]).filter(Boolean),
)

// ---- 사진: 등록 야영장은 캠핑장 사진, 후보지는 그 자리 스카이뷰(카카오 정적 지도) ----
const skyEl = ref(null)
const noSky = ref(false)
async function drawSky() {
  if (props.spot.imageUrl || !skyEl.value) return
  const kakao = await loadKakaoMap().catch(() => null)
  if (!kakao || !skyEl.value) {
    noSky.value = true
    return
  }
  skyEl.value.innerHTML = '' // 다른 자리를 고르면 새로 그린다
  new kakao.maps.StaticMap(skyEl.value, {
    center: new kakao.maps.LatLng(props.spot.lat, props.spot.lng),
    level: 3,
    mapTypeId: kakao.maps.MapTypeId.SKYVIEW,
  })
}
watch([skyEl, () => `${props.spot.lat},${props.spot.lng}`], drawSky)

const route = computed(() => directionsUrl({ name: props.spot.name, lat: props.spot.lat, lng: props.spot.lng }))

// ---- 내 캠핑에 일정 등록 (웹 캠핑장 상세와 같은 흐름: 로그인 안내 → 날짜·메모 → 완료) ----
const auth = useAuthStore()
const myCamping = useMyCampingStore()
const trip = useTripStore()
const planOpen = ref(false)
const planStep = ref('form') // form | login | done
const checkIn = ref('')
const checkOut = ref('')
const memo = ref('')
const planError = ref('')
const saving = ref(false)
// 일정에 남길 이름: 야영장은 이름, 후보지는 "475-3 잡종지", 찍은 자리는 판정의 지번 + 지목 (예: "산12-21 임야")
const planName = computed(() => {
  if (props.spot.kind !== 'point') return props.spot.name
  const r = verdict.value
  // 지번 끝의 지목 한 글자("475-3 잡")는 지목 이름으로 대신한다. 공백이 있을 때만 떼어서 "산12-21" 같은 지번은 그대로
  return r?.jibun ? [r.jibun.replace(/\s+\S$/, ''), r.jimok].filter(Boolean).join(' ') : props.spot.name
})
function openPlan() {
  planStep.value = auth.isLoggedIn ? 'form' : 'login'
  checkIn.value = trip.state.checkIn || ''
  checkOut.value = trip.state.checkOut || ''
  memo.value = ''
  planError.value = ''
  planOpen.value = true
}
// 서버와 같은 규칙을 먼저 화면에서 확인한다 (서버도 한 번 더 검사)
async function savePlan() {
  if (!checkIn.value || !checkOut.value) return (planError.value = '체크인·체크아웃 날짜를 골라 주세요.')
  if (checkIn.value < todayIso()) return (planError.value = '지난 날짜는 고를 수 없습니다.')
  if (nightsBetween(checkIn.value, checkOut.value) < 1) return (planError.value = '체크아웃은 체크인 다음 날부터 고를 수 있습니다.')
  planError.value = ''
  saving.value = true
  const isCamp = props.spot.kind === 'camp' && props.spot.campId != null
  try {
    await myCamping.add({
      campId: isCamp ? props.spot.campId : null,
      lat: isCamp ? null : props.spot.lat, // 캠핑장이 아닌 자리는 위치로 저장 (서버가 소수 6자리로 맞춤)
      lng: isCamp ? null : props.spot.lng,
      campName: planName.value,
      place: props.spot.area || '강원 인제군',
      checkIn: checkIn.value, checkOut: checkOut.value, memo: memo.value.trim(),
    })
    planStep.value = 'done'
  } catch (e) {
    planError.value = e.message // 중복 등록 등 서버 문구를 그대로 보여 준다
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="detail">
    <!-- 위 막대 -->
    <div class="bar">
      <button type="button" class="list-btn" @click="emit('back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>목록
      </button>
      <b class="bar-title">{{ spot.name }}</b>
      <span class="bar-gap" aria-hidden="true"></span>
    </div>

    <div class="panel">
      <!-- 사진 -->
      <span class="hero" role="img" :aria-label="`${spot.name} ${spot.imageUrl ? '사진' : '위성사진'}`">
        <img v-if="spot.imageUrl" :src="spot.imageUrl" :alt="spot.name" />
        <span v-else-if="!noSky" ref="skyEl" class="sky"></span>
        <span v-else class="nosky">위성사진을 불러오지 못했어요</span>
      </span>

      <!-- 이름 줄 -->
      <div class="title">
        <h2>{{ spot.name }}</h2>
        <span>{{ subLine }}</span>
      </div>

      <p v-if="error" class="state">{{ error }} <button type="button" class="link" @click="load">다시 시도</button></p>
      <p v-else-if="!verdict" class="state">판정을 불러오는 중...</p>

      <template v-else>
        <!-- 판정 + 안전점수 상자 (캠핑장 상세의 점수 상자와 같은 모양) -->
        <div class="why">
          <span class="circle" :class="tier">
            <b>{{ score?.score ?? '-' }}</b><span>{{ GRADE_LABEL[score?.grade] || '점수 없음' }}</span>
          </span>
          <span class="why-text">
            <span class="verdict" :style="{ background: verdictInfo ? `var(${verdictInfo.colorVar})` : 'var(--web-text-sub)' }">
              {{ v }}
            </span>
            <b>{{ verdictInfo?.title || '판정' }}</b>
            <span v-if="score?.erDriveMin != null">응급실 차로 약 {{ score.erDriveMin }}분 (직선거리 어림)</span>
          </span>
        </div>

        <!-- 판정 사유 -->
        <section v-if="verdict.reasons?.length" class="sec">
          <h3>판정 사유</h3>
          <ul class="reasons">
            <li v-for="r in verdict.reasons" :key="r">{{ r }}</li>
          </ul>
          <p v-if="verdict.nearBoundary" class="hint">필지 경계 가까이라 옆 필지일 수 있어요 (지적도 위치 오차)</p>
        </section>

        <!-- 걸린 규제구역 -->
        <section v-if="verdict.zones?.length" class="sec">
          <h3>걸린 규제구역</h3>
          <ul class="reasons">
            <li v-for="z in verdict.zones" :key="z">{{ z }}</li>
          </ul>
        </section>

        <!-- 땅 정보 -->
        <section v-if="landRows.length" class="sec">
          <h3>땅 정보</h3>
          <dl class="infos">
            <div v-for="[k, val] in landRows" :key="k" class="irow"><dt>{{ k }}</dt><dd>{{ val }}</dd></div>
          </dl>
        </section>

        <!-- 가까운 안전 시설 -->
        <section v-if="nearest.length" class="sec">
          <h3>가까운 안전 시설</h3>
          <ul class="facs">
            <li v-for="f in nearest" :key="f.id">
              <span class="ftype">{{ FACILITY_LABEL[f.type] || f.type }}</span>
              <span class="fname">{{ f.name }}</span>
              <span class="fdist">{{ f.distanceKm }}km · 약 {{ driveMinOf(f.distanceKm) }}분</span>
            </li>
          </ul>
          <p class="hint">거리 · 시간은 직선거리로 어림한 값이에요.</p>
        </section>

        <!-- 근처 차박 후보 (주의 · 불가일 때 서버가 보내 줌) -->
        <section v-if="verdict.alternatives?.length" class="sec">
          <h3>근처 차박 후보</h3>
          <ul class="facs">
            <li v-for="a in verdict.alternatives" :key="a.jibun">
              <button type="button" class="alt" @click="emit('pick', a)">
                <span class="fname">{{ a.jibun }}</span>
                <span class="fdist">{{ a.owner }} · {{ distanceText(a.distM) }}</span>
              </button>
            </li>
          </ul>
        </section>

        <!-- 근처 등록 야영장 -->
        <section v-if="verdict.nearbyCamps?.length" class="sec">
          <h3>근처 등록 야영장</h3>
          <ul class="facs">
            <li v-for="c in verdict.nearbyCamps" :key="c.name">
              <RouterLink v-if="c.id != null" :to="`/web/camps/${c.id}`" class="alt">
                <span class="fname">{{ c.name }}</span><span class="fdist">{{ distanceText(c.distM) }} ›</span>
              </RouterLink>
              <span v-else class="alt"><span class="fname">{{ c.name }}</span><span class="fdist">{{ distanceText(c.distM) }}</span></span>
            </li>
          </ul>
        </section>

        <p class="disclaimer">공공데이터 기준 참고용 판정이며 법적 경계는 지적측량으로 확정돼요. '제한 없음'도 합법을 보증하지 않아요.</p>
      </template>
    </div>

    <!-- 아래 버튼 -->
    <div class="foot">
      <a :href="route" target="_blank" rel="noopener" class="btn">카카오맵 길찾기 ↗</a>
      <RouterLink v-if="spot.kind === 'camp' && spot.campId != null" :to="`/web/camps/${spot.campId}`" class="btn">캠핑장 상세</RouterLink>
      <button type="button" class="btn primary" @click="openPlan">내 캠핑에 일정 등록</button>
    </div>

    <!-- 일정 등록 (웹 캠핑장 상세와 같은 시트) -->
    <div v-if="planOpen" class="plan-dim">
      <div class="plan-box">
        <div class="plan-head">
          <b>내 캠핑에 일정 등록</b>
          <button type="button" aria-label="일정 등록 닫기" @click="planOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
              stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
          </button>
        </div>
        <template v-if="planStep === 'login'">
          <p class="plan-msg">일정 등록은 로그인 후 이용할 수 있어요.</p>
          <RouterLink to="/web/login" class="plan-go">로그인</RouterLink>
        </template>
        <template v-else-if="planStep === 'form'">
          <span class="plan-camp">{{ planName }}<small v-if="spot.kind !== 'camp'"> · 차박 자리</small></span>
          <div class="plan-dates">
            <label>체크인<input v-model="checkIn" type="date" :min="todayIso()" /></label>
            <label>체크아웃<input v-model="checkOut" type="date" :min="checkIn || todayIso()" /></label>
          </div>
          <label class="plan-memo">메모 (선택)<input v-model="memo" type="text" maxlength="100" placeholder="예: 준비물, 같이 가는 사람" /></label>
          <p v-if="planError" class="plan-err" role="alert">{{ planError }}</p>
          <button type="button" class="plan-go" :disabled="saving" @click="savePlan">등록하기</button>
        </template>
        <template v-else>
          <p class="plan-msg"><b>내 캠핑에 등록했어요.</b><br />출발 준비 체크리스트도 내 캠핑에서 볼 수 있어요.</p>
          <RouterLink to="/web/my-booking" class="plan-go">내 캠핑에서 보기</RouterLink>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail {
  position: relative;
  flex: 1;
  min-height: 0;
  background: var(--web-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.bar {
  flex: none;
  height: 56px;
  padding: 0 12px 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--web-line-soft);
}
.list-btn {
  height: 40px;
  padding: 0 12px 0 6px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 2px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  color: var(--web-text);
  cursor: pointer;
}
.bar-title {
  flex: 1;
  text-align: center;
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bar-gap {
  width: 64px; /* 가운데 제목이 목록 버튼 폭만큼 가운데에 오게 */
}
.panel {
  flex: 1 1 auto;
  min-height: 0;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}
.panel > * {
  flex-shrink: 0;
}
.hero {
  height: 180px;
  border-radius: 14px;
  overflow: hidden;
  background: var(--web-photo-empty);
  color: var(--web-text-mute);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}
.hero img,
.sky {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title h2 {
  margin: 0;
  font-size: 22px;
  letter-spacing: -0.3px;
}
.title span {
  font-size: 13px;
  color: var(--web-text-sub);
}
.state {
  margin: 0;
  font-size: 14px;
  color: var(--web-text-sub);
}
.link {
  border: 0;
  background: none;
  font: inherit;
  color: var(--web-text);
  text-decoration: underline;
  cursor: pointer;
}
.why {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--web-bg-soft);
  display: flex;
  align-items: center;
  gap: 16px;
}
.circle {
  flex: 0 0 72px;
  height: 72px;
  border-radius: 999px;
  color: var(--web-white);
  background: var(--web-text-sub);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.05;
}
.circle b {
  font-size: 28px;
}
.circle span {
  font-size: 12px;
  font-weight: 700;
}
.circle.safe { background: var(--web-grade-safe); }
.circle.mid { background: var(--web-grade-caution); }
.circle.warn { background: var(--web-grade-danger); }
.why-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}
.why-text b {
  font-size: 16px;
}
.why-text > span:not(.verdict) {
  font-size: 13px;
  color: var(--web-text-sub);
}
.verdict {
  padding: 2px 10px;
  border-radius: 999px;
  color: var(--web-white);
  font-size: 12px;
  font-weight: 800;
}
.sec {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sec h3 {
  margin: 0;
  font-size: 15px;
}
.reasons {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--web-text-sub);
}
.infos {
  margin: 0;
}
.irow {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-top: 1px solid var(--web-line-soft);
  font-size: 14px;
}
.irow dt {
  flex: 0 0 72px;
  color: var(--web-text-sub);
}
.irow dd {
  margin: 0;
}
.facs {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.facs li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--web-bg-soft);
  font-size: 14px;
}
.facs li:has(.alt) {
  padding: 0;
}
.ftype {
  flex: none;
  font-weight: 700;
  min-width: 48px;
}
.fname {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fdist {
  flex: none;
  color: var(--web-text-sub);
  font-size: 13px;
}
.alt {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: var(--web-bg-soft);
  font: inherit;
  font-size: 14px;
  color: var(--web-text);
  text-align: left;
  cursor: pointer;
}
.disclaimer {
  margin: 0;
  font-size: 12px;
  color: var(--web-text-sub);
}
.foot {
  flex: none;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--web-line-soft);
  display: flex;
  gap: 8px;
}
.btn {
  flex: 1;
  height: 48px;
  border-radius: 12px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
}
.btn.primary {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
}
.plan-dim {
  position: absolute;
  inset: 0;
  background: var(--web-dim-plan);
  display: flex;
  align-items: flex-end;
  z-index: 40;
}
.plan-box {
  width: 100%;
  background: var(--web-bg);
  border-radius: 18px 18px 0 0;
  padding: 22px 28px 26px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.plan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plan-head b {
  font-size: 18px;
}
.plan-head button {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 0;
  background: var(--web-bg-close);
  color: var(--web-text-sub);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.plan-camp {
  font-size: 15px;
  font-weight: 700;
  color: var(--web-green);
}
.plan-camp small {
  font-weight: 400;
  color: var(--web-text-sub);
}
.plan-dates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.plan-box label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--web-text-sub);
}
.plan-box input {
  height: 46px;
  padding: 0 12px;
  border: 1px solid var(--web-line-input);
  border-radius: 10px;
  font-size: 15px;
  font-family: inherit;
  color: var(--web-text);
}
.plan-msg {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
}
.plan-err {
  margin: 0;
  font-size: 13px;
  color: var(--web-terracotta);
}
.plan-go {
  height: 50px;
  border-radius: 12px;
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.plan-go:disabled {
  opacity: 0.6;
}
button.btn {
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
</style>
