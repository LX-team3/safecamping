<script setup>
// 앱 예약 정보 확인 (웹 WebBookingCheckView 와 같은 흐름): 예약 사이트에서 예약하고 돌아온 뒤, 날짜 · 인원 · 동행 · 이동 수단을 확인해 내 캠핑에 저장한다
// 주소: /app/booking-check?camp=캠핑장id (내 캠핑 카드의 "예약 정보 확인")
// 저장: POST /api/member/camp-plans (캠핑장 · 날짜). 인원 · 동행 · 이동 수단은 일정에 저장되는 칸이 없어서 메모에 적어 둔다
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '@/utils/api'
import { nightsBetween, todayIso } from '@/utils/date'
import { shortSido } from '@/utils/place'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { useTripStore } from '@/stores/trip'

const WITH = ['혼자', '친구·연인', '아이 동반', '어르신 동반', '반려동물']
const MOVES = [
  { key: 'car', label: '자동차' }, { key: 'bus', label: '버스' }, { key: 'taxi', label: '택시' },
  { key: 'subway', label: '지하철' }, { key: 'walk', label: '도보' },
]
const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의' }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const myCamping = useMyCampingStore()
const trip = useTripStore()

const campId = Number(route.query.camp) || null
const camp = ref(null)
const error = ref('')

const reserved = ref(true)
const checkIn = ref(trip.state.checkIn || '')
const checkOut = ref(trip.state.checkOut || '')
const adults = ref(Math.max(1, trip.state.adults || 2))
const kids = ref(trip.state.kids || 0)
const withs = ref({ '친구·연인': true })
const move = ref('car')
const saved = ref(false)
const saving = ref(false)

onMounted(async () => {
  if (!campId) return
  try {
    camp.value = await api(`/api/camps/${campId}`)
  } catch (e) {
    error.value = e.message
  }
})

const nights = computed(() => (checkIn.value && checkOut.value ? nightsBetween(checkIn.value, checkOut.value) : 0))
const okDate = computed(() => nights.value > 0)
const md = (iso) => `${Number(iso.slice(5, 7))}월 ${Number(iso.slice(8, 10))}일`
const dateRange = computed(() => (checkIn.value && checkOut.value ? `${md(checkIn.value)} → ${md(checkOut.value)}` : '날짜를 골라 주세요'))
const stayText = computed(() => (okDate.value ? `${nights.value}박 ${nights.value + 1}일` : '확인 필요'))
const withList = computed(() => WITH.filter((w) => withs.value[w]))
const moveLabel = computed(() => MOVES.find((m) => m.key === move.value)?.label || '선택 안 함')
const peopleText = computed(() => `성인 ${adults.value}명 · 어린이 ${kids.value}명`)
const place = computed(() => (camp.value ? `${shortSido(camp.value.sido)} ${camp.value.sigungu} ${camp.value.eupmyeon || ''}`.trim() : ''))

// '혼자'는 다른 것과 같이 고를 수 없다
function toggleWith(label) {
  let w = { ...withs.value }
  if (label === '혼자') w = w['혼자'] ? {} : { 혼자: true }
  else {
    delete w['혼자']
    w[label] = !w[label]
    if (!w[label]) delete w[label]
  }
  withs.value = w
}
const bump = (r, d, min) => (r.value = Math.max(min, Math.min(10, r.value + d)))

async function save() {
  error.value = ''
  if (!auth.isLoggedIn) return (error.value = '저장하려면 로그인이 필요해요.')
  if (!camp.value) return (error.value = '캠핑장을 먼저 골라 주세요.')
  if (!okDate.value) return (error.value = '체크아웃은 체크인 다음 날부터 고를 수 있어요.')
  if (checkIn.value < todayIso()) return (error.value = '지난 날짜는 고를 수 없어요.')
  saving.value = true
  try {
    // 일정에는 인원 · 동행 · 이동 수단 칸이 없어서 메모에 적어 둔다 (내 캠핑 카드에 메모로 보인다)
    const memo = [peopleText.value, withList.value.join('·'), moveLabel.value].filter(Boolean).join(' · ')
    await myCamping.add({ campId, lat: null, lng: null, campName: camp.value.name, place: place.value, checkIn: checkIn.value, checkOut: checkOut.value, memo })
    trip.state.checkIn = checkIn.value
    trip.state.checkOut = checkOut.value
    trip.state.adults = adults.value
    trip.state.kids = kids.value
    saved.value = true
  } catch (e) {
    error.value = e.message // 중복 등록 등 서버 문구
  } finally {
    saving.value = false
  }
}

// 뒤로: 보통 내 캠핑에서 왔으므로 기록이 있으면 되돌아가고, 없으면(주소로 바로 들어옴) 내 캠핑으로
const goBack = () => (window.history.state?.back ? router.back() : router.push('/app/my-camping'))
</script>

<template>
  <div class="check">
    <header class="top">
      <button type="button" class="back" aria-label="뒤로" @click="goBack">‹</button>
      <h1>{{ saved ? '예약 정보를 저장했어요' : '예약을 마치셨나요?' }}</h1>
    </header>

    <main class="body">
      <!-- 저장한 뒤 -->
      <div v-if="saved" class="done">
        <p><b>{{ camp?.name }}</b></p>
        <p>{{ dateRange }}</p>
        <p class="sub">캠핑 당일에 맞춰 안전 정보와 기상특보를 알려 드릴게요.</p>
        <RouterLink to="/app/my-camping" class="primary">내 캠핑 보기</RouterLink>
        <RouterLink to="/app/explore" class="ghost">안전지도로 가기</RouterLink>
      </div>

      <template v-else>
        <p v-if="!campId" class="notice">내 캠핑 카드의 '예약 정보 확인'을 누르면 그 캠핑장으로 열려요. <RouterLink to="/app/explore">캠핑장 찾기 ›</RouterLink></p>

        <!-- 예약한 캠핑장 -->
        <section class="camp">
          <span class="cphoto" role="img" aria-label="예약한 캠핑장 사진">
            <img v-if="camp?.imageUrl" :src="camp.imageUrl" :alt="camp.name" />
          </span>
          <span class="cinfo"><small>예약한 캠핑장</small><b>{{ camp?.name || '캠핑장을 골라 주세요' }}</b><span>{{ place }}</span></span>
          <span v-if="camp?.safetyScore != null" class="cscore"><span>{{ GRADE_LABEL[camp.grade] }}</span><b>{{ camp.safetyScore }}<small>점</small></b></span>
        </section>

        <section class="box">
          <h2>예약 여부</h2>
          <div class="two">
            <button type="button" class="big" :class="{ on: reserved }" :aria-pressed="reserved" @click="reserved = true">예약했어요</button>
            <button type="button" class="big" :class="{ on: !reserved }" :aria-pressed="!reserved" @click="reserved = false">아직 안 했어요</button>
          </div>
        </section>

        <template v-if="reserved">
          <section class="box">
            <h2>날짜</h2>
            <div class="two">
              <label>체크인<input v-model="checkIn" type="date" :min="todayIso()" /></label>
              <label>체크아웃<input v-model="checkOut" type="date" :min="checkIn || todayIso()" /></label>
            </div>
            <p v-if="checkIn && checkOut && !okDate" class="bad">체크아웃 날짜가 체크인보다 늦어야 해요.</p>
          </section>

          <section class="box">
            <h2>인원수</h2>
            <div class="counter">
              <span>성인</span>
              <button type="button" aria-label="성인 줄이기" @click="bump(adults, -1, 1)">−</button><b>{{ adults }}명</b><button type="button" aria-label="성인 늘리기" @click="bump(adults, 1, 1)">+</button>
            </div>
            <div class="counter">
              <span>어린이</span>
              <button type="button" aria-label="어린이 줄이기" @click="bump(kids, -1, 0)">−</button><b>{{ kids }}명</b><button type="button" aria-label="어린이 늘리기" @click="bump(kids, 1, 0)">+</button>
            </div>
          </section>

          <section class="box">
            <h2>누구와 가나요? <small>여러 개 고를 수 있어요</small></h2>
            <div class="pills">
              <button v-for="w in WITH" :key="w" type="button" class="pill" :class="{ on: withs[w] }" :aria-pressed="!!withs[w]" @click="toggleWith(w)">{{ w }}</button>
            </div>
          </section>

          <section class="box">
            <h2>가는 수단</h2>
            <div class="pills">
              <button v-for="m in MOVES" :key="m.key" type="button" class="pill" :class="{ on: move === m.key }" :aria-pressed="move === m.key" @click="move = m.key">{{ m.label }}</button>
            </div>
          </section>

          <section class="sum">
            <h3>이대로 저장할까요?</h3>
            <dl>
              <dt>캠핑장</dt><dd>{{ camp ? `${camp.name} · ${camp.sigungu}` : '-' }}</dd>
              <dt>날짜</dt><dd>{{ okDate ? `${dateRange} (${stayText})` : dateRange }}</dd>
              <dt>인원</dt><dd>{{ peopleText }}</dd>
              <dt>동행</dt><dd>{{ withList.length ? withList.join(' · ') : '선택 안 함' }}</dd>
              <dt>가는 수단</dt><dd>{{ moveLabel }}</dd>
            </dl>
          </section>
        </template>
        <p v-else class="notice">예약을 마치면 이 화면에서 날짜와 인원을 저장해 주세요. 저장해 두면 캠핑 당일 안전 정보와 기상특보를 알려 드려요.</p>

        <p v-if="error" class="err" role="alert">{{ error }}</p>
        <button type="button" class="save" :disabled="!okDate || !reserved || saving" @click="save">
          {{ saving ? '저장 중...' : okDate ? '맞아요, 이대로 저장할게요' : '날짜를 확인해 주세요' }}
        </button>
        <RouterLink to="/app/my-camping" class="later">나중에 할게요</RouterLink>
      </template>
    </main>
  </div>
</template>

<style scoped>
.check {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.top {
  flex: none;
  height: 56px;
  padding: 0 12px 0 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--color-divider);
}
.top h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
.back {
  width: 44px;
  height: 44px;
  border: 0;
  background: none;
  font-size: 30px;
  line-height: 1;
  color: var(--color-text);
  cursor: pointer;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.notice {
  margin: 0;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-sub);
}
.notice a {
  color: var(--color-text);
  font-weight: 700;
}
.camp {
  padding: 14px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  gap: 12px;
}
.cphoto {
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: var(--color-photo);
  overflow: hidden;
}
.cphoto img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cinfo {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cinfo small,
.cinfo span {
  font-size: 12px;
  color: var(--color-text-sub);
}
.cinfo b {
  font-size: 16px;
}
.cscore {
  flex: none;
  min-width: 52px;
  padding: 6px 8px;
  border-radius: 12px;
  background: var(--color-safe-bg);
  color: var(--color-green);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.cscore span {
  font-size: 11px;
  font-weight: 700;
}
.cscore b {
  font-size: 18px;
  line-height: 1.1;
}
.cscore small {
  font-size: 11px;
}
.box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.box h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 900;
}
.box h2 small {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-sub);
}
.two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.big {
  height: 48px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}
.big.on {
  border: 2px solid var(--color-text);
  background: var(--color-selected-bg);
  font-weight: 700;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-sub);
}
input[type='date'] {
  height: 46px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  font: inherit;
  font-size: 15px;
  color: var(--color-text);
  background: var(--color-card);
}
.bad {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger);
}
.counter {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-track);
}
.counter span {
  flex: 1;
  font-size: 15px;
}
.counter b {
  min-width: 40px;
  text-align: center;
  font-size: 16px;
}
.counter button {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pill {
  height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: 21px;
  background: var(--color-card);
  color: var(--color-text);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.pill.on {
  border: 2px solid var(--color-text);
  background: var(--color-selected-bg);
  font-weight: 700;
}
.sum {
  padding: 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
}
.sum h3 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 900;
}
.sum dl {
  margin: 0;
  display: grid;
  grid-template-columns: 70px 1fr;
  row-gap: 8px;
  font-size: 13px;
}
.sum dt {
  color: var(--color-text-sub);
}
.sum dd {
  margin: 0;
  font-weight: 700;
}
.err {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger);
}
.save {
  height: 52px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.save:disabled {
  opacity: 0.4;
  cursor: default;
}
.later {
  height: 48px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: var(--color-text);
}
.done {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: center;
}
.done p {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
}
.done .sub {
  font-size: 13px;
  color: var(--color-text-sub);
  margin-bottom: 12px;
}
.primary,
.ghost {
  height: 50px;
  border-radius: var(--radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
}
.primary {
  background: var(--color-text);
  color: var(--color-card);
}
.ghost {
  border: 1px solid var(--color-border);
  color: var(--color-text);
}
.back:focus-visible, .big:focus-visible, .pill:focus-visible, .save:focus-visible, .counter button:focus-visible { outline: 2px solid var(--color-text); outline-offset: 2px; }
</style>
