<script setup>
// 캠핑장 상세 (설계 4-1-1): 왼쪽 패널 안. 위 막대(목록 · 이름 · 공유 · 찜) → 사진 → 이름 → 탭 4개 → 아래 고정 버튼
// 탭: 캠핑장 정보 · 주변 안전시설(누르면 지도에 길을 그린다) · 관광지 (리뷰 탭은 기능이 없어 뺐다 — 후기는 내 캠핑의 지난 캠핑에서 쓴다)
// 데이터: GET /api/camps/{id}, /api/camps/{id}/tours, /api/camps/{id}/routes (카카오 도로 경로)
import { computed, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '@/utils/api'
import { FACILITY_LABEL, directionsUrl, shortSido } from '@/utils/place'
import { nightsBetween, todayIso } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'
import { useFavoriteStore } from '@/stores/favorite'
import { useMyCampingStore } from '@/stores/myCamping'
import { useTourPreferenceStore } from '@/stores/tourPreference'
import { useTripStore } from '@/stores/trip'
import { useReservationReturnStore } from '@/stores/reservationReturn'
import { useSurveyStore } from '@/stores/survey'
import { erBonusReasons } from '@/utils/webSurvey'

const visit = useReservationReturnStore() // 예약 사이트에서 돌아오면 "예약하셨나요?" 팝업

const props = defineProps({
  campId: { type: Number, required: true },
  focusKey: { type: String, default: null }, // 지도에서 강조 중인 시설
})
// camp: 불러온 상세 (지도가 이 캠핑장을 고르게) · routes: 지도에 그릴 경로 · focus: 강조할 시설 key
const emit = defineEmits(['back', 'camp', 'routes', 'focus'])

const TABS = [
  { key: 'info', label: '캠핑장 정보' },
  { key: 'near', label: '주변 안전시설' },
  { key: 'tour', label: '관광지' },
]
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' }
const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의', unknown: '점수 없음' }
const GRADE_CLASS = { safe: 'safe', caution: 'mid', danger: 'warn' }
const CHIP_COLOR = { er: 'hospital', fire: 'fire', police: 'police', store: 'store', pharmacy: 'pharmacy', shelter: 'shelter' }

const router = useRouter()
const auth = useAuthStore()
const favorite = useFavoriteStore()
const myCamping = useMyCampingStore()
const preference = useTourPreferenceStore()
const trip = useTripStore()
// 내 유형 점수 (응급실 가산점): 탐색 목록과 같은 켜짐 상태 · 같은 규칙. 켜져 있고 해당 답이 있으면 "71 +5" 로 따로 보여 준다
const survey = useSurveyStore()
const bonusReasons = computed(() => erBonusReasons(survey.answers))
// 가산점이 붙는 사람 · 캠핑장인지 (켜고 끄는 줄을 보일지). camp 는 아래에서 불러온 상세
const bonusApplies = computed(() => bonusReasons.value.length > 0 && !!camp.value?.erBonus)
const bonus = computed(() => (survey.bonusOn && bonusApplies.value ? camp.value.erBonus : 0))
const shownScore = computed(() => (camp.value?.safetyScore == null ? '-' : Math.min(100, camp.value.safetyScore + bonus.value)))

const camp = ref(null)
const tours = ref([])
const routes = ref([])
const error = ref('')
const tab = ref('info')
const tourSort = ref('pref') // pref(내 취향순) · near(가까운 순)

async function load() {
  error.value = ''
  camp.value = null
  routes.value = []
  tab.value = 'info'
  emit('routes', [])
  try {
    await preference.load() // 회원이면 저장한 여행 취향으로 관광지 순서를 정한다
  } catch {
    // 취향을 못 불러와도 상세는 보여 준다
  }
  try {
    const [detail, tourList] = await Promise.all([
      api(`/api/camps/${props.campId}`),
      api(`/api/camps/${props.campId}/tours`, { params: { prefer: preference.codes.join(',') || undefined } }),
    ])
    camp.value = detail
    tours.value = tourList
    emit('camp', detail)
  } catch (e) {
    error.value = e.message
  }
  // 경로는 따로 받는다: 카카오가 느리거나 실패해도 상세는 먼저 보여야 해서
  try {
    const res = await api(`/api/camps/${props.campId}/routes`)
    if (res) routes.value = res.routes
  } catch {
    // 못 받으면 직선으로 그린다
  }
}
watch(() => props.campId, load, { immediate: true })

const facilities = computed(() => camp.value?.facilities || [])
const place = computed(() => (camp.value ? `${shortSido(camp.value.sido)} ${camp.value.sigungu} ${camp.value.eupmyeon || ''}`.trim() : ''))
const subLine = computed(() => (camp.value ? [place.value, TYPE_LABEL[camp.value.campType]].filter(Boolean).join(' · ') : ''))
const tier = computed(() => GRADE_CLASS[camp.value?.grade] || 'safe')
const summary = computed(() =>
  ['fire', 'er']
    .map((type) => facilities.value.find((f) => f.type === type))
    .filter(Boolean)
    .map((f) => `${FACILITY_LABEL[f.type]} ${f.driveMin}분`)
    .join(' · '),
)
const infoRows = computed(() => (camp.value?.infos || []).map((i) => ({ label: i.label, value: i.value || '확인 필요', missing: !i.value })))

// ---- 지도에 그릴 경로 (주변 안전시설 탭에서만) ----
const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()
const colorOf = (type) => cssVar(`--web-fac-${CHIP_COLOR[type] || 'shelter'}`)
const routeOf = (f) => routes.value.find((r) => r.facilityId === f.id && r.ok)
const minOf = (f) => routeOf(f)?.durationMin ?? f.driveMin
const kmOf = (f) => routeOf(f)?.distanceKm ?? f.distanceKm
const mapRoutes = computed(() =>
  tab.value === 'near'
    ? facilities.value.map((f) => ({ key: String(f.id), type: f.type, name: f.name, lat: f.lat, lng: f.lng, color: colorOf(f.type), label: `${minOf(f)}분`, path: routeOf(f)?.path }))
    : [],
)
watch(mapRoutes, (v) => emit('routes', v), { immediate: true })
watch(tab, () => emit('focus', null))
const nameOf = (f) => (f.type === 'er' ? `응급실 (${f.name})` : f.name)

const sortedTours = computed(() =>
  tourSort.value === 'near' ? [...tours.value].sort((a, b) => a.driveMin - b.driveMin) : tours.value,
)

// ---- 공유 · 찜 알림 ----
const shareOpen = ref(false)
const toast = ref('') // '' | copied | login | saved
async function copyLink() {
  shareOpen.value = false
  try {
    await navigator.clipboard.writeText(`${location.origin}/web/camps/${props.campId}`)
    toast.value = 'copied'
  } catch {
    // 복사가 막힌 브라우저에서는 알리지 않는다
  }
}
async function toggleFav() {
  if (!auth.isLoggedIn) {
    toast.value = 'login'
    return
  }
  const was = favorite.has(props.campId)
  await favorite.toggle(props.campId)
  toast.value = was ? '' : 'saved'
}

// ---- 일정 등록 ----
const planOpen = ref(false)
const planStep = ref('form') // form | login | done
const checkIn = ref('')
const checkOut = ref('')
const memo = ref('')
const planError = ref('')
const saving = ref(false)
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
  try {
    await myCamping.add({
      campId: props.campId, lat: null, lng: null, campName: camp.value.name, place: place.value,
      checkIn: checkIn.value, checkOut: checkOut.value, memo: memo.value.trim(),
    })
    planStep.value = 'done'
  } catch (e) {
    planError.value = e.message // 중복 등록 등 서버 문구를 그대로 보여 준다
  } finally {
    saving.value = false
  }
}

const openFacility = (f) => router.push(`/web/facilities/${f.id}?camp=${props.campId}`)
</script>

<template>
  <div class="detail" role="dialog" :aria-label="`${camp?.name || '캠핑장'} 상세`">
    <!-- 위 막대 -->
    <div class="bar">
      <button type="button" class="list-btn" @click="emit('back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>목록
      </button>
      <b class="bar-title">{{ camp?.name }}</b>
      <span class="rel">
        <button type="button" class="icon" :class="{ on: shareOpen }" aria-label="공유하기" :aria-expanded="shareOpen" @click="shareOpen = !shareOpen">
          <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 15V3" /><path d="m7 8 5-5 5 5" /><path d="M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7" /></svg>
        </button>
        <div v-if="shareOpen" class="menu" role="menu" aria-label="공유하기">
          <span class="menu-title">{{ camp?.name }} 공유하기</span>
          <button type="button" role="menuitem" @click="copyLink">
            <span class="mi link"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" /></svg></span>링크 복사
          </button>
        </div>
      </span>
      <button type="button" class="icon" :aria-label="favorite.has(campId) ? '관심 캠핑장 해제' : '관심 캠핑장 저장'" :aria-pressed="favorite.has(campId)" @click="toggleFav">
        <svg width="22" height="22" viewBox="0 0 24 24" class="hrt" :class="{ on: favorite.has(campId) }" stroke-width="2" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" />
        </svg>
      </button>
    </div>

    <p v-if="error" class="state">{{ error }} <button type="button" class="link" @click="load">다시 시도</button></p>
    <p v-else-if="!camp" class="state">불러오는 중...</p>

    <template v-else>
      <!-- 대표 사진 -->
      <span class="hero" role="img" :aria-label="`${camp.name} 대표 사진`">
        <img v-if="camp.imageUrl" :src="camp.imageUrl" :alt="camp.name" />
        <template v-else>
          <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="8.5" cy="10" r="1.5" /><path d="m21 16-5-5-8 8" /></svg>
          <span>{{ camp.name }} 대표 사진</span>
        </template>
      </span>

      <!-- 이름 줄 -->
      <div class="title">
        <h2>{{ camp.name }}</h2>
        <span>{{ subLine }}</span>
      </div>

      <!-- 탭 -->
      <div class="tabs" role="tablist">
        <button v-for="t in TABS" :key="t.key" type="button" role="tab" :aria-selected="tab === t.key" :class="{ on: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
      </div>

      <div class="panel">
        <!-- 탭 1: 캠핑장 정보 -->
        <template v-if="tab === 'info'">
          <div class="why">
            <!-- 내 유형 점수가 붙는 사람 · 캠핑장이면 원이 켜고 끄는 버튼 (켜면 76, 끄면 71 — 숫자 자체가 바뀜). 색 · 등급 글자는 저장된 점수 그대로 -->
            <span v-if="bonusApplies" class="circle-wrap">
              <button type="button" class="circle tap" :class="tier" :aria-pressed="survey.bonusOn"
                :aria-label="survey.bonusOn ? `내 유형 점수 ${shownScore}점 (안전점수 ${camp.safetyScore} + 응급실 ${bonus}). 누르면 끄기` : `안전점수 ${camp.safetyScore}점. 누르면 내 유형 점수 켜기`"
                @click="survey.bonusOn = !survey.bonusOn">
                <b>{{ shownScore }}</b>
                <span>{{ GRADE_LABEL[camp.grade] }}</span>
              </button>
              <span class="tap-tag" aria-hidden="true">내 유형 {{ survey.bonusOn ? 'ON' : 'OFF' }}</span>
            </span>
            <span v-else class="circle" :class="tier"><b>{{ camp.safetyScore ?? '-' }}</b><span>{{ GRADE_LABEL[camp.grade] }}</span></span>
            <span class="why-text">
              <b>{{ camp.headline || '안전점수' }}</b>
              <span v-if="summary">차로 {{ summary }}</span>
              <!-- 켜고 끄기는 왼쪽 원을 누른다 -->
              <span v-if="bonusApplies" class="bonus-line">
                <template v-if="bonus">내 유형 점수예요 · {{ bonusReasons.join(' · ') }}에 해당해 응급실 거리를 더 따졌어요 (안전점수 {{ camp.safetyScore }})</template>
                <template v-else>내 유형 점수 꺼짐 · 원을 누르면 켜져요</template>
              </span>
            </span>
          </div>
          <dl class="infos">
            <div v-for="r in infoRows" :key="r.label" class="irow">
              <dt>{{ r.label }}</dt><dd :class="{ missing: r.missing }">{{ r.value }}</dd>
            </div>
          </dl>
          <p v-if="camp.dummy" class="dummy">더미 데이터예요 · 실제 값이 아니에요</p>
        </template>

        <!-- 탭 2: 주변 안전시설 -->
        <template v-else-if="tab === 'near'">
          <span class="basis">가까운 안전시설이에요 · 시간은 차로 가는 예상 시간이에요</span>
          <div class="facs">
            <div v-for="f in facilities" :key="f.id" class="fac" :class="{ on: focusKey === String(f.id) }">
              <button type="button" class="fhead" :aria-pressed="focusKey === String(f.id)" @click="emit('focus', focusKey === String(f.id) ? null : String(f.id))">
                <span class="ficon" :style="{ background: colorOf(f.type) }">{{ facilities.indexOf(f) + 1 }}</span>
                <span class="fname"><b>{{ nameOf(f) }}</b><span>{{ FACILITY_LABEL[f.type] }} · {{ kmOf(f) }}km</span></span>
                <span class="ftime"><b :class="f.tone">약 {{ minOf(f) }}분</b><span v-if="f.tone === 'safe'" class="golden">골든타임 안</span></span>
              </button>
              <div v-if="focusKey === String(f.id)" class="fbtns">
                <a href="tel:119" class="call">119 신고</a>
                <button type="button" @click="openFacility(f)">시설 상세 ›</button>
                <a :href="directionsUrl(f, { name: camp.name, lat: camp.lat, lng: camp.lng })" target="_blank" rel="noopener" class="dir">길찾기</a>
              </div>
            </div>
          </div>
        </template>

        <!-- 탭 3: 관광지 -->
        <template v-else-if="tab === 'tour'">
          <div class="sorts" role="tablist" aria-label="관광지 정렬">
            <button type="button" role="tab" :aria-selected="tourSort === 'pref'" :class="{ on: tourSort === 'pref' }" @click="tourSort = 'pref'">내 취향순</button>
            <button type="button" role="tab" :aria-selected="tourSort === 'near'" :class="{ on: tourSort === 'near' }" @click="tourSort = 'near'">가까운 순</button>
          </div>
          <p v-if="!tours.length" class="state small">주변 관광지 정보가 아직 없어요.</p>
          <div class="tours">
            <RouterLink v-for="t in sortedTours" :key="t.id" :to="`/web/tours/${t.id}?camp=${campId}`" class="tour">
              <span class="tphoto" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                  stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="8.5" cy="10" r="1.5" /><path d="m21 16-5-5-8 8" /></svg>
              </span>
              <span class="tinfo">
                <span class="tline"><b>{{ t.name }}</b><span v-if="t.matched" class="match">내 취향</span></span>
                <span v-if="t.note" class="tnote">{{ t.note }}</span>
                <span class="tmin">차로 약 {{ t.driveMin }}분</span>
              </span>
            </RouterLink>
          </div>
        </template>

      </div>

      <!-- 알림 -->
      <div v-if="toast" class="toast" role="status">
        <svg v-if="toast === 'saved'" width="18" height="18" viewBox="0 0 24 24" class="hrt on" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>
        <span class="tmsg">{{ toast === 'copied' ? '링크를 복사했어요. 원하는 곳에 붙여 넣어 공유하세요' : toast === 'login' ? '찜하려면 로그인이 필요해요' : '찜한 캠핑장에 담았어요' }}</span>
        <RouterLink v-if="toast === 'login'" to="/web/login" class="tlink">로그인하기 ›</RouterLink>
        <RouterLink v-if="toast === 'saved'" to="/web/saved" class="tlink">찜한 캠핑장 보기 ›</RouterLink>
        <button type="button" aria-label="알림 닫기" @click="toast = ''">✕</button>
      </div>

      <!-- 아래 고정 -->
      <div class="foot">
        <a v-if="camp.reservationUrl" :href="camp.reservationUrl" target="_blank" rel="noopener" class="resv" @click="visit.mark({ campId: props.campId, campName: camp.name })">예약 사이트 ↗</a>
        <span v-else class="resv off" aria-disabled="true">예약 사이트 없음</span>
        <button type="button" class="plan" @click="openPlan">내 캠핑에 일정 등록</button>
      </div>

      <!-- 일정 등록 -->
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
            <span class="plan-camp">{{ camp.name }}</span>
            <div class="plan-dates">
              <label>체크인<input v-model="checkIn" type="date" :min="todayIso()" /></label>
              <label>체크아웃<input v-model="checkOut" type="date" :min="checkIn || todayIso()" /></label>
            </div>
            <label class="plan-memo">메모 (선택)<input v-model="memo" type="text" maxlength="100" placeholder="예: 예약번호, 준비물" /></label>
            <p v-if="planError" class="plan-err" role="alert">{{ planError }}</p>
            <button type="button" class="plan-go" :disabled="saving" @click="savePlan">등록하기</button>
          </template>
          <template v-else>
            <p class="plan-msg"><b>내 캠핑에 등록했어요.</b><br />내 캠핑에서 확인할 수 있어요.</p>
            <RouterLink to="/web/my-booking" class="plan-go">내 캠핑에서 보기</RouterLink>
          </template>
        </div>
      </div>
    </template>
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
.detail > * {
  flex-shrink: 0;
}
.bar {
  height: 56px;
  padding: 0 12px 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
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
.rel {
  position: relative;
}
.icon {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 0;
  background: transparent;
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.icon.on {
  background: var(--web-bg-chip);
}
.hrt {
  fill: none;
  stroke: var(--web-text);
}
.hrt.on {
  fill: var(--web-terracotta);
  stroke: var(--web-terracotta);
}
.menu {
  position: absolute;
  right: -44px;
  top: 46px;
  z-index: 30;
  width: 220px;
  padding: 8px;
  border-radius: 14px;
  background: var(--web-bg);
  box-shadow: 0 8px 24px rgba(28, 29, 27, 0.18);
  display: flex;
  flex-direction: column;
}
.menu-title {
  padding: 6px 10px 8px;
  font-size: 13px;
  color: var(--web-text-sub);
}
.menu button {
  height: 44px;
  padding: 0 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--web-text);
  cursor: pointer;
}
.mi {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  background: var(--web-bg-chip);
  color: var(--web-green);
  display: flex;
  align-items: center;
  justify-content: center;
}
.state {
  margin: 24px 20px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.state.small {
  margin: 8px 0;
}
.link {
  border: 0;
  background: none;
  color: var(--web-link-blue);
  font: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.hero {
  height: 168px;
  background: var(--web-bg-hero);
  color: var(--web-text-photo);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  overflow: hidden;
}
.hero img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.title {
  padding: 16px 20px 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.4px;
}
.title span {
  font-size: 14px;
  color: var(--web-text-sub);
}
.tabs {
  margin-top: 12px;
  padding: 0 12px;
  display: flex;
  border-bottom: 1px solid var(--web-line-soft);
}
.tabs button {
  flex: 1 1 auto;
  white-space: nowrap;
  padding: 0 6px;
  height: 46px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  font-family: inherit;
  font-size: 15px;
  color: var(--web-text-sub);
  cursor: pointer;
}
.tabs button.on {
  border-bottom-color: var(--web-green);
  font-weight: 800;
  color: var(--web-green);
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
  gap: 4px;
  min-width: 0;
}
.why-text b {
  font-size: 16px;
}
.why-text span {
  font-size: 13px;
  color: var(--web-text-sub);
}
.infos {
  margin: 0;
  display: flex;
  flex-direction: column;
}
.irow {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--web-line-info);
  font-size: 14px;
}
.irow dt {
  color: var(--web-text-sub);
}
.irow dd {
  margin: 0;
}
.irow dd.missing {
  color: var(--web-text-mute);
}
.dummy {
  margin: 0;
  font-size: 12px;
  color: var(--web-text-sub);
}
.basis {
  font-size: 13px;
  color: var(--web-text-sub);
}
.facs {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.fac {
  border: 1px solid var(--web-line-info);
  border-radius: 12px;
  background: var(--web-bg);
}
.fac.on {
  border-color: var(--web-best);
  background: var(--web-best);
}
.fhead {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: var(--web-text);
  font-family: inherit;
}
.ficon {
  flex: 0 0 30px;
  height: 30px;
  border-radius: 999px;
  color: var(--web-white);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.fname {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.fname b {
  font-size: 15px;
}
.fname span {
  font-size: 12px;
  color: var(--web-text-sub);
}
.ftime {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.ftime b {
  font-size: 15px;
}
.ftime b.safe { color: var(--web-grade-safe); }
.ftime b.caution { color: var(--web-grade-danger-fg); }
.golden {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--web-grade-safe-bg);
  font-size: 11px;
  font-weight: 700;
  color: var(--web-grade-safe);
}
.fbtns {
  padding: 0 12px 12px 54px;
  display: grid;
  grid-template-columns: 1fr 1fr 1.2fr;
  gap: 8px;
}
.fbtns > * {
  height: 38px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.fbtns .call {
  border: 1px solid var(--web-terracotta);
  color: var(--web-terracotta);
}
.fbtns button {
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
}
.fbtns .dir {
  background: var(--web-green);
  color: var(--web-white);
}
.sorts {
  align-self: flex-start;
  display: flex;
  padding: 3px;
  border-radius: 10px;
  background: var(--web-bg-chip);
}
.sorts button {
  height: 30px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--web-text-sub);
  font-family: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
.sorts button.on {
  background: var(--web-bg);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  color: var(--web-text);
  font-weight: 700;
}
.tours {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tour {
  display: flex;
  gap: 14px;
  padding: 10px;
  border: 1px solid var(--web-line);
  border-radius: 14px;
  background: var(--web-bg);
  color: var(--web-text);
}
.tphoto {
  flex: 0 0 84px;
  height: 68px;
  border-radius: 10px;
  background: var(--web-photo-empty);
  color: var(--web-text-mute);
  display: flex;
  align-items: center;
  justify-content: center;
}
.tinfo {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  justify-content: center;
}
.tline {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tline b {
  font-size: 15px;
}
.match {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--web-bg-chip);
  font-size: 11px;
  color: var(--web-text-sub);
}
.tnote {
  font-size: 13px;
  color: var(--web-text-sub);
}
.tmin {
  font-size: 13px;
  font-weight: 700;
  color: var(--web-grade-safe);
}
.toast {
  margin: 0 16px 10px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--web-green);
  color: var(--web-white);
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}
.tmsg {
  flex: 1;
}
.tlink {
  color: var(--web-rec-from);
  font-weight: 800;
  white-space: nowrap;
}
.toast button {
  width: 24px;
  height: 24px;
  border: 0;
  background: none;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
}
.foot {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--web-line-soft);
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 8px;
}
.resv,
.plan {
  height: 48px;
  border-radius: 12px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
/* 흰 패널 위 흰 버튼이라 테두리가 연하면 버튼으로 안 보임 → 메인 초록 테두리 (앱 .reserve 도 진한 테두리) */
.resv {
  border: 1.5px solid var(--web-green);
  background: var(--web-bg);
  color: var(--web-text);
}
.resv.off {
  border-color: var(--web-line-input); /* 예약 사이트 없음: 누를 수 없으니 연한 테두리 그대로 */
  color: var(--web-text-mute);
  cursor: default;
}
.plan {
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
.bonus-line {
  font-size: 13px;
  color: var(--web-green);
}
.bonus-line b {
  font-weight: 800;
}
.circle-wrap {
  flex: 0 0 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.circle.tap {
  width: 72px;
  border: 0;
  font: inherit;
  cursor: pointer;
  box-shadow: 0 0 0 3px var(--web-bg-soft), 0 0 0 4px var(--web-green); /* 누를 수 있는 원이라는 표시 (바깥 테두리) */
}
.tap-tag {
  font-size: 11px;
  font-weight: 800;
  color: var(--web-green);
}
</style>
