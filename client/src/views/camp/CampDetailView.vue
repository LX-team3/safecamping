<script setup>
// 캠핑장 상세: 경로 지도 위에 시트를 올린 한 화면
//  - 시트를 올리면 상세 (안전점수 · 캠핑장 정보 · 관광지 탭)
//  - "경로 보기"·손잡이로 내리면 경로 지도 (응급실·소방서·경찰서·편의점·약국까지 카카오 자동차 경로, 형광 파란 선)
//  - 안전점수 탭의 시설을 누르면 시설 상세, 관광지 탭의 관광지를 누르면 관광지 상세
// 데이터: GET /api/camps/{id}, GET /api/camps/{id}/tours?prefer=, GET /api/camps/{id}/routes
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import RouteMap from '@/components/map/RouteMap.vue'
import DetailTopBar from '@/components/detail/DetailTopBar.vue'
import PhotoBox from '@/components/detail/PhotoBox.vue'
import InfoRows from '@/components/detail/InfoRows.vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { api } from '@/utils/api'
import { vHscroll } from '@/utils/hscroll'
import { FACILITY_LABEL, shortSido } from '@/utils/place'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { useTripStore } from '@/stores/trip'
import { todayIso, nightsBetween } from '@/utils/date'
import { useFavoriteStore } from '@/stores/favorite'
import { useTourPreferenceStore } from '@/stores/tourPreference'

const props = defineProps({ id: { type: Number, required: true } })

const COLLAPSED = 290 // 내렸을 때 시트 높이(px): 이름 줄 + 시설 칩 + 설명 + 아래 버튼
const TABS = [
  { key: 'safety', label: '안전점수' },
  { key: 'info', label: '캠핑장 정보' },
  { key: 'tour', label: '관광지' },
]
const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '점수 없음' }
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' }

const route = useRoute()
const auth = useAuthStore()
const myCamping = useMyCampingStore()
const trip = useTripStore()
const favorite = useFavoriteStore()
const preference = useTourPreferenceStore()

const camp = ref(null)
const tours = ref([])
const error = ref('')
const sheetOpen = ref(true)
// "다른 관광지"로 돌아올 때 ?tab=tour 로 관광지 탭을 연다
const tab = ref(TABS.some((t) => t.key === route.query.tab) ? route.query.tab : 'safety')
const selectedId = ref(null) // 경로 지도에서 고른 시설
const chipsEl = ref(null) // 시설 칩 줄 (가로로 넘김)

// 지도 마커로 시설을 고르면 그 칩이 줄 밖에 있어도 보이게 옆으로 넘긴다
watch(selectedId, async (id) => {
  await nextTick() // 칩의 on 표시가 그려진 뒤에
  chipsEl.value?.querySelector(`[data-id="${id}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
})
const routes = ref([]) // 카카오 자동차 경로 [{ facilityId, type, ok, dummy, distanceKm, durationMin, path }]
const routeState = ref('loading') // loading | kakao | straight | error

// 여행 취향 답 (관광지 순서에 씀)
const prefer = computed(() => preference.codes)

// 경로는 상세와 따로 부른다: 카카오가 느리거나 실패해도 상세 화면은 먼저 보여야 하므로
async function loadRoutes(campId) {
  routes.value = []
  routeState.value = 'loading'
  try {
    const res = await api(`/api/camps/${campId}/routes`)
    if (campId !== props.id) return // 기다리는 사이 다른 캠핑장으로 바뀌었으면 버린다
    routes.value = res.routes
    routeState.value = res.source
  } catch {
    if (campId === props.id) routeState.value = 'error' // 직선으로 보여 준다
  }
}

async function load() {
  error.value = ''
  camp.value = null
  loadRoutes(props.id)
  try {
    await preference.load() // 회원이면 저장한 취향을 쓴다
  } catch {
    // 취향을 못 불러와도 캠핑장 상세는 보여 준다 (관광지는 인기순)
  }
  try {
    const [detail, tourList] = await Promise.all([
      api(`/api/camps/${props.id}`),
      // 쉼표로 이어 보낸다 → 서버가 목록으로 받는다
      api(`/api/camps/${props.id}/tours`, { params: { prefer: prefer.value.join(',') || undefined } }),
    ])
    camp.value = detail
    tours.value = tourList
    selectedId.value = detail.facilities[0]?.id ?? null
  } catch (e) {
    error.value = e.message
  }
}
watch(() => props.id, load, { immediate: true })

const facilities = computed(() => camp.value?.facilities || [])
const selected = computed(() => facilities.value.find((f) => f.id === selectedId.value))
const place = computed(() =>
  camp.value ? `${shortSido(camp.value.sido)} ${camp.value.sigungu} ${camp.value.eupmyeon || ''}`.trim() : '',
)
const subLine = computed(() => {
  if (!camp.value) return ''
  return [place.value, TYPE_LABEL[camp.value.campType]].join(' · ')
})

// 점수 칸 설명: 소방서 5분 · 응급실 17분
const summary = computed(() =>
  ['fire', 'er']
    .map((type) => facilities.value.find((f) => f.type === type))
    .filter(Boolean)
    .map((f) => `${FACILITY_LABEL[f.type]} ${f.driveMin}분`)
    .join(' · '),
)

const rowName = (f) => (f.type === 'er' ? `응급실 (${f.name})` : f.name)
// 경로 지도의 시간·거리: 카카오 경로를 받았으면 그 값, 아니면 DB 값
const routeOf = (f) => routes.value.find((r) => r.facilityId === f.id && r.ok)
const driveMinOf = (f) => routeOf(f)?.durationMin ?? f.driveMin
const distanceOf = (f) => routeOf(f)?.distanceKm ?? f.distanceKm
const shortLabel = (f) => `${FACILITY_LABEL[f.type]} ${driveMinOf(f)}분`

const hasRoutes = computed(() => routes.value.some((r) => r.ok))
const origin = computed(() => (camp.value ? { name: camp.value.name, lat: camp.value.lat, lng: camp.value.lng } : null))
const targets = computed(() =>
  facilities.value.map((f, i) => ({
    id: f.id,
    type: f.type, // 마커 아이콘 고르기 (utils/facilityIcon.js)
    name: FACILITY_LABEL[f.type], // 화면 읽기 프로그램용. 지도에는 쓰지 않음 (아이콘으로 알아봄)
    label: `${driveMinOf(f)}분`, // 지도 이름표는 시간만 (아래 칩·목록에는 이름이 그대로 있음)
    lat: f.lat,
    lng: f.lng,
    no: i + 1,
    path: routeOf(f)?.path,
    // 실제 경로를 그릴 때 경로를 받지 않는 시설(지금은 화장실)은 직선을 빼고 마커만 → 직선과 도로가 섞여 헷갈리지 않게
    noLine: hasRoutes.value && !routes.value.some((r) => r.facilityId === f.id),
  })),
)

// 경로 지도 위 안내 한 줄
const routeNote = computed(() => {
  if (routeState.value === 'loading') return '경로를 불러오는 중...'
  if (!hasRoutes.value) return '실제 경로를 불러오지 못해 직선으로 보여 드려요'
  const parts = ['카카오 길찾기 예상 시간 · 교통 상황에 따라 달라질 수 있어요']
  if (routes.value.some((r) => r.dummy)) parts.unshift('예시 위치 기준 경로')
  if (routes.value.some((r) => !r.ok)) parts.push('일부는 직선')
  return parts.join(' · ')
})

const isFavorite = computed(() => favorite.has(props.id))

// ---- 내 캠핑에 일정 등록 ----
// 등록하면 내 캠핑 탭에 카드로 뜬다 (서버 저장, stores/myCamping.js). 회원 전용이라 로그인이 필요하다
// 시트 단계: login(로그인 필요) → form(날짜 입력) → done(저장 끝)
const planOpen = ref(false)
const planStep = ref('form')
const planCheckIn = ref('') // "2026-11-14"
const planCheckOut = ref('')
const planMemo = ref('')
const planError = ref('')
const planSaving = ref(false)

function openPlan() {
  planError.value = ''
  planOpen.value = true
  if (!auth.isLoggedIn) {
    planStep.value = 'login'
    return
  }
  // 탐색에서 고른 날짜가 있으면 미리 채운다
  planCheckIn.value = trip.state.checkIn || ''
  planCheckOut.value = trip.state.checkOut || ''
  planMemo.value = ''
  planStep.value = 'form'
}

async function savePlan() {
  if (!planCheckIn.value || !planCheckOut.value) {
    planError.value = '체크인·체크아웃 날짜를 골라 주세요.'
    return
  }
  if (planCheckIn.value < todayIso()) {
    planError.value = '지난 날짜는 고를 수 없습니다.'
    return
  }
  if (nightsBetween(planCheckIn.value, planCheckOut.value) < 1) {
    planError.value = '체크아웃은 체크인 다음 날부터 고를 수 있습니다.'
    return
  }
  planError.value = ''
  planSaving.value = true
  try {
    await myCamping.add({
      campId: props.id,
      campName: camp.value.name,
      place: place.value,
      checkIn: planCheckIn.value,
      checkOut: planCheckOut.value,
      memo: planMemo.value.trim(),
    })
    planStep.value = 'done'
  } catch (e) {
    planError.value = e.message // 중복 등록 등 서버 문구를 그대로 보여 준다
  } finally {
    planSaving.value = false
  }
}
</script>

<template>
  <div class="detail">
    <DetailTopBar :favorite="isFavorite" @favorite="favorite.toggle(id)" />

    <div v-if="error" class="state">
      <p>{{ error }}</p>
      <button type="button" class="link" @click="load">다시 시도</button>
    </div>
    <p v-else-if="!camp" class="state">불러오는 중...</p>

    <template v-else>
      <RouteMap :origin="origin" :targets="targets" :grade="camp.grade" :selected-id="selectedId" :bottom-inset="COLLAPSED"
        @select="(fid) => (selectedId = fid)" />
      <p v-if="!sheetOpen" class="route-note">{{ routeNote }}</p>

      <section class="sheet" :style="{ top: sheetOpen ? '56px' : `calc(100% - ${COLLAPSED}px)` }">
        <button type="button" class="handle" :aria-expanded="sheetOpen" @click="sheetOpen = !sheetOpen">
          <span class="grip" />
          {{ sheetOpen ? '내리면 경로 지도' : '올려서 상세 보기' }}
        </button>

        <!-- 올린 상태: 상세 -->
        <div v-if="sheetOpen" class="scroll">
          <PhotoBox :src="camp.imageUrl || ''" :alt="camp.name" :caption="`${camp.name} 대표 사진`" />
          <h1>{{ camp.name }}</h1>
          <p class="sub">{{ subLine }}</p>

          <div class="score">
            <span class="circle" :class="camp.grade">
              <b>{{ camp.safetyScore ?? '-' }}</b>{{ GRADE_LABEL[camp.grade] }}
            </span>
            <div class="score-text">
              <b>{{ camp.headline }}</b>
              <span v-if="summary">{{ summary }}</span>
              <span v-if="camp.dummy" class="dummy">점수·시간은 더미 데이터</span>
            </div>
            <button type="button" class="to-route" :disabled="!facilities.length" @click="sheetOpen = false">
              경로 보기 ↓
            </button>
          </div>

          <div class="tabs" role="tablist">
            <button v-for="t in TABS" :key="t.key" type="button" role="tab" class="tab" :class="{ on: tab === t.key }"
              :aria-selected="tab === t.key" @click="tab = t.key">
              {{ t.label }}
            </button>
          </div>

          <!-- 안전점수 탭 -->
          <div v-if="tab === 'safety'">
            <template v-if="facilities.length">
              <p class="hint">가까운 순 · 누르면 시설 상세를 보여줘요</p>
              <ol class="list">
                <li v-for="(f, i) in facilities" :key="f.id">
                  <RouterLink class="row" :to="`/facilities/${f.id}?camp=${id}`">
                    <i class="no">{{ i + 1 }}</i>
                    <span class="name">{{ rowName(f) }}</span>
                    <span class="km">{{ f.distanceKm.toFixed(1) }}km</span>
                    <b class="time" :class="f.tone">차로 {{ f.driveMin }}분</b>
                    <span class="chev" aria-hidden="true">›</span>
                  </RouterLink>
                </li>
              </ol>
            </template>
            <p v-else class="empty">이 캠핑장의 시설 정보를 준비하고 있어요.</p>
          </div>

          <!-- 캠핑장 정보 탭 -->
          <div v-else-if="tab === 'info'">
            <InfoRows v-if="camp.infos.length" :infos="camp.infos" />
            <p v-else class="empty">캠핑장 정보를 준비하고 있어요.</p>
          </div>

          <!-- 관광지 탭 -->
          <div v-else>
            <RouterLink v-if="!prefer.length" class="banner" :to="`/tour-preference?camp=${id}`">
              <span><b>취향 알려주면 맞춤 추천해요</b><small>즐길 거리 · 자연환경 · 약 30초</small></span>
              <b>시작 ›</b>
            </RouterLink>
            <template v-if="tours.length">
              <p class="hint">
                {{ prefer.length ? '내 취향에 맞는 곳부터 보여 드려요' : '지금은 인기순 · 취향을 알려주면 내 취향순으로 바뀌어요' }}
                <!-- 배너는 취향이 없을 때만 보이므로, 저장한 뒤에는 여기서 바꾼다 -->
                <RouterLink v-if="prefer.length" class="change" :to="`/tour-preference?camp=${id}`">바꾸기</RouterLink>
              </p>
              <ol class="list">
                <li v-for="(t, i) in tours" :key="t.id">
                  <RouterLink class="row" :to="`/tours/${t.id}?camp=${id}`">
                    <b class="tour-no">{{ i + 1 }}</b>
                    <span class="name">{{ t.name }}</span>
                    <span class="km">차로 약 {{ t.driveMin }}분<template v-if="t.note"> · {{ t.note }}</template></span>
                    <span class="chev" aria-hidden="true">›</span>
                  </RouterLink>
                </li>
              </ol>
            </template>
            <p v-else class="empty">주변 관광지를 준비하고 있어요.</p>
          </div>
        </div>

        <!-- 내린 상태: 시설 칩 -->
        <div v-else class="compact">
          <div class="compact-head">
            <b>{{ camp.name }}</b>
            <span class="badge" :class="camp.grade"><i />{{ GRADE_LABEL[camp.grade] }} {{ camp.safetyScore ?? '' }}</span>
          </div>
          <!-- 칩이 많으면 옆으로 넘김: 손가락 밀기 · 마우스 휠(v-hscroll) · 마우스 기기에선 얇은 막대(.hscroll) -->
          <div ref="chipsEl" v-hscroll class="chips hscroll">
            <button v-for="f in facilities" :key="f.id" type="button" class="chip" :data-id="f.id" :class="{ on: f.id === selectedId }"
              :aria-pressed="f.id === selectedId" @click="selectedId = f.id">
              {{ shortLabel(f) }}
            </button>
          </div>
          <p v-if="selected" class="picked">
            <span>{{ selected.name }} · {{ distanceOf(selected).toFixed(1) }}km · 차로 {{ driveMinOf(selected) }}분</span>
            <RouterLink :to="`/facilities/${selected.id}?camp=${id}`">시설 상세 ›</RouterLink>
          </p>
          <p v-else class="picked">이 캠핑장의 시설 정보를 준비하고 있어요.</p>
        </div>

        <footer class="foot">
          <p>어디서 예약했든 날짜만 등록하면 캠핑 중 안전 알림을 보내드려요</p>
          <div class="buttons">
            <a v-if="camp.reservationUrl" class="reserve" :href="camp.reservationUrl" target="_blank"
              rel="noopener noreferrer">예약 사이트 ↗</a>
            <button v-else type="button" class="reserve" disabled title="예약 사이트 정보가 없어요">예약 사이트 ↗</button>
            <button type="button" class="plan" aria-haspopup="dialog" @click="openPlan">내 캠핑에 일정 등록</button>
          </div>
        </footer>
      </section>

      <!-- 일정 등록 시트 -->
      <BottomSheet v-if="planOpen" title="내 캠핑에 일정 등록" @close="planOpen = false">
        <div v-if="planStep === 'login'" class="plan-msg">
          <p>일정은 로그인하면 저장할 수 있어요.</p>
          <RouterLink to="/login" class="plan-go">로그인하기</RouterLink>
        </div>

        <div v-else-if="planStep === 'done'" class="plan-msg">
          <p><b>내 캠핑에 등록했어요.</b><br />내 캠핑 탭에서 확인할 수 있어요.</p>
          <RouterLink to="/my-camping" class="plan-go">내 캠핑에서 보기</RouterLink>
        </div>

        <form v-else class="plan-form" @submit.prevent="savePlan">
          <p class="plan-camp">{{ camp.name }}</p>
          <label>체크인
            <input v-model="planCheckIn" type="date" :min="todayIso()" />
          </label>
          <label>체크아웃
            <input v-model="planCheckOut" type="date" :min="planCheckIn || todayIso()" />
          </label>
          <label>메모 (선택)
            <input v-model.trim="planMemo" maxlength="100" placeholder="예: 예약번호, 준비물" />
          </label>
          <p v-if="planError" class="plan-error" role="alert">{{ planError }}</p>
          <button type="submit" class="plan-go" :disabled="planSaving">{{ planSaving ? '등록 중...' : '등록하기' }}</button>
        </form>
      </BottomSheet>
    </template>
  </div>
</template>

<style scoped>
.detail {
  position: relative;
  height: 100%;
  overflow: hidden;
}
.state {
  height: 100%;
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-text-sub);
}
.route-note {
  position: absolute;
  left: 16px;
  bottom: 300px;
  z-index: 10;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-sub);
}
.sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  border-radius: 20px 20px 0 0;
  background: var(--color-card);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  transition: top 0.25s ease;
}
.handle {
  flex: none;
  height: 40px;
  border: none;
  background: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.grip {
  width: 44px;
  height: 5px;
  border-radius: 3px;
  background: var(--color-border);
}
.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 16px 16px;
}
h1 {
  margin: 16px 0 4px;
  font-size: 23px;
  font-weight: 900;
}
.sub {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-sub);
}
.score {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 14px;
  background: var(--color-bg-soft);
}
.circle {
  flex: none;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-card);
  font-size: 11px;
  background: var(--color-text-sub);
}
.circle b {
  font-size: 22px;
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
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.score-text b {
  font-size: 15px;
  color: var(--color-text);
}
.dummy {
  color: var(--color-caution);
  font-size: 11px;
}
.to-route {
  flex: none;
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 18px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.to-route:disabled {
  opacity: 0.35;
  cursor: default;
}
.tabs {
  display: flex;
  margin-top: 16px;
  border-bottom: 1px solid var(--color-track);
}
.tab {
  flex: 1;
  height: 46px;
  border: none;
  border-bottom: 3px solid transparent;
  background: none;
  font-size: 15px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.tab.on {
  border-bottom-color: var(--color-text);
  color: var(--color-text);
  font-weight: 700;
}
.hint {
  margin: 12px 0 4px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.change {
  margin-left: 4px;
  color: var(--color-text);
  text-decoration: underline;
}
.list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-track);
}
.no {
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}
.tour-no {
  flex: none;
  width: 16px;
}
.name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
}
.km {
  flex: none;
  font-size: 13px;
  color: var(--color-text-sub);
}
.time {
  flex: none;
  font-size: 14px;
}
.time.safe {
  color: var(--color-green);
}
.time.caution {
  color: var(--color-caution);
}
.chev {
  flex: none;
  color: var(--color-text-sub);
}
.empty {
  margin: 28px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 14px;
  border-radius: 12px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 14px;
}
.banner span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.banner small {
  font-size: 12px;
}
.compact {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
}
.compact-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 13px;
  font-size: 13px;
  font-weight: 700;
  background: var(--color-bg-soft);
  color: var(--color-text-sub);
}
.badge i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}
.badge.safe {
  background: var(--color-safe-bg);
  color: var(--color-green);
}
.badge.caution {
  background: var(--color-caution-bg);
  color: var(--color-caution);
}
.badge.danger {
  background: var(--color-warn-bg);
  color: var(--color-danger);
}
.chips {
  display: flex;
  gap: 8px;
  margin: 10px -16px 0;
  padding: 0 16px 4px; /* 아래 4px: 마우스 기기에서 보이는 얇은 막대가 칩에 붙지 않게 */
  /* 가로 넘김·막대 모양은 전역 .hscroll (styles/base.css) */
}
.chip {
  flex: none;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-card);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.chip.on {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-card);
}
.picked {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin: 12px 0;
  font-size: 13px;
}
.picked a {
  flex: none;
  font-weight: 700;
}
.foot {
  flex: none;
  padding: 10px 16px 16px;
  border-top: 1px solid var(--color-track);
}
.foot p {
  margin: 0 0 8px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-sub);
}
.buttons {
  display: flex;
  gap: 10px;
}
.reserve,
.plan {
  height: 50px;
  border-radius: 25px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.reserve {
  flex: 0 0 42%;
  border: 1px solid var(--color-text);
  background: var(--color-card);
}
.plan {
  flex: 1;
  border: none;
  background: var(--color-primary);
  color: var(--color-card);
}
.reserve:disabled,
.plan:disabled {
  opacity: 0.5;
  cursor: default;
}
/* 일정 등록 시트 */
.plan-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.plan-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.plan-form input {
  height: 48px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font: inherit;
  font-size: 15px;
  color: var(--color-text);
}
.plan-camp {
  margin: 0;
  font-size: 17px;
  font-weight: 900;
}
.plan-error {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-danger);
}
.plan-go:disabled {
  opacity: 0.5;
  cursor: default;
}
.plan-msg {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0 4px;
  text-align: center;
}
.plan-msg p {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
}
.plan-go {
  height: 50px;
  border: none;
  border-radius: 25px;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>
