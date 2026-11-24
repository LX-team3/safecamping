<script setup>
// 웹 예약 정보 확인 (설계 5-1): 예약 사이트에서 예약하고 돌아온 뒤, 날짜 · 인원 · 동행 · 이동 수단을 확인해 내 캠핑에 저장한다
// 주소: /web/booking-check?camp=캠핑장id (캠핑장 상세의 "예약 사이트"에서 돌아올 때)
// 저장: 새 일정은 POST, 내 예약 카드에서 들어왔으면(?plan=) PUT /api/member/camp-plans/{id} 로 고친다. 인원 · 동행 · 이동 수단은 칸이 없어서 메모 글자로 (utils/bookingMemo.js)
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '@/utils/api'
import { nightsBetween, todayIso } from '@/utils/date'
import { shortSido } from '@/utils/place'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { useTripStore } from '@/stores/trip'
import { MOVES, WITH, makeBookingMemo, parseBookingMemo } from '@/utils/bookingMemo'

const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의' }

const route = useRoute()
const auth = useAuthStore()
const myCamping = useMyCampingStore()
const trip = useTripStore()

const campId = Number(route.query.camp) || null
// ?plan=일정id: 내 예약 카드의 "예약 정보 확인"에서 들어오면 저장해 둔 날짜 · 인원 · 동행 · 이동 수단을 채우고, 저장하면 그 일정을 고친다
const planId = Number(route.query.plan) || null
const plan = ref(null) // 고칠 일정 (없으면 새로 등록)
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
  if (planId && auth.isLoggedIn) fillFromPlan()
  if (!campId) return
  try {
    camp.value = await api(`/api/camps/${campId}`)
  } catch (e) {
    error.value = e.message
  }
})

// 저장해 둔 일정으로 칸을 채운다. 메모에서 못 찾은 값(직접 쓴 메모 등)은 기본값 그대로
async function fillFromPlan() {
  try {
    if (!myCamping.plans.length) await myCamping.load()
  } catch (e) {
    return (error.value = e.message)
  }
  const p = myCamping.plans.find((x) => x.id === planId)
  if (!p) return
  plan.value = p
  checkIn.value = p.checkIn
  checkOut.value = p.checkOut
  const m = parseBookingMemo(p.memo)
  if (m.adults != null) adults.value = Math.max(1, m.adults)
  if (m.kids != null) kids.value = m.kids
  if (m.withs) withs.value = m.withs
  if (m.move) move.value = m.move
}

const nights = computed(() => (checkIn.value && checkOut.value ? nightsBetween(checkIn.value, checkOut.value) : 0))
const okDate = computed(() => nights.value > 0)
const md = (iso) => `${Number(iso.slice(5, 7))}월 ${Number(iso.slice(8, 10))}일`
const dateRange = computed(() => (checkIn.value && checkOut.value ? `${md(checkIn.value)} → ${md(checkOut.value)}` : '날짜를 골라 주세요'))
const stayText = computed(() => (okDate.value ? `${nights.value}박 ${nights.value + 1}일` : '확인 필요'))
const withList = computed(() => WITH.filter((w) => withs.value[w]))
const moveLabel = computed(() => MOVES.find((m) => m.key === move.value)?.label || '선택 안 함')
const peopleText = computed(() => `성인 ${adults.value}명 · 어린이 ${kids.value}명`)
const place = computed(() => (camp.value ? `${shortSido(camp.value.sido)} ${camp.value.sigungu} ${camp.value.eupmyeon || ''}`.trim() : ''))

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
// 인원 +/− (0~10명, 성인은 1명부터). 템플릿에서 ref 를 넘기면 숫자로 풀려서 값이 안 바뀌므로 칸 이름으로 받는다
const COUNT = { adults: [adults, 1], kids: [kids, 0] }
const bump = (key, d) => {
  const [r, min] = COUNT[key]
  r.value = Math.max(min, Math.min(10, r.value + d))
}

async function save() {
  error.value = ''
  if (!auth.isLoggedIn) return (error.value = '저장하려면 로그인이 필요해요.')
  if (!camp.value) return (error.value = '캠핑장을 먼저 골라 주세요.')
  if (!okDate.value) return (error.value = '체크아웃은 체크인 다음 날부터 고를 수 있어요.')
  // 고칠 때 체크인을 그대로 두면 지난 날짜여도 된다 (이미 시작한 캠핑의 인원만 고치는 경우)
  if (checkIn.value < todayIso() && checkIn.value !== plan.value?.checkIn) return (error.value = '지난 날짜는 고를 수 없어요.')
  saving.value = true
  try {
    // 일정에는 인원 · 동행 · 이동 수단 칸이 없어서 메모에 적어 둔다 (내 예약에서 메모로 보인다)
    const memo = makeBookingMemo({ adults: adults.value, kids: kids.value, withs: withs.value, move: move.value })
    if (plan.value) await myCamping.update(plan.value.id, { checkIn: checkIn.value, checkOut: checkOut.value, memo })
    else await myCamping.add({ campId, lat: null, lng: null, campName: camp.value.name, place: place.value, checkIn: checkIn.value, checkOut: checkOut.value, memo })
    trip.state.checkIn = checkIn.value
    trip.state.checkOut = checkOut.value
    trip.state.adults = adults.value
    trip.state.kids = kids.value
    saved.value = true
    window.scrollTo(0, 0)
  } catch (e) {
    error.value = e.message // 중복 등록 등 서버 문구
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="check">
    <section class="banner">
      <img src="/web/banner-night.png" alt="" aria-hidden="true" />
      <span class="shade" aria-hidden="true"></span>
      <div class="inner">
        <span class="step">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>예약 확인 단계
        </span>
        <h1>{{ saved ? '예약 정보를 저장했어요' : plan ? '예약 정보 확인' : '예약을 마치셨나요?' }}</h1>
      </div>
    </section>

    <main class="main">
      <!-- 저장한 뒤 -->
      <div v-if="saved" class="done">
        <p><b>{{ camp?.name }}</b> · {{ dateRange }}</p>
        <p>캠핑 당일에 맞춰 안전 정보와 기상특보를 알려 드릴게요.</p>
        <div class="dbtns">
          <RouterLink to="/web/my-booking" class="primary">내 캠핑 보기</RouterLink>
          <RouterLink to="/web/explore" class="ghost">안전지도로 가기</RouterLink>
        </div>
      </div>

      <div v-else class="grid">
        <div class="left">
          <p v-if="!campId" class="notice">캠핑장 상세에서 '예약 사이트'를 누른 뒤 돌아오면 그 캠핑장으로 열려요. <RouterLink to="/web/explore">캠핑장 찾기 ›</RouterLink></p>

          <section class="box">
            <h2>예약 여부</h2>
            <div class="two">
              <button type="button" class="big" :class="{ on: reserved }" :aria-pressed="reserved" @click="reserved = true">
                <svg v-if="reserved" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>예약했어요
              </button>
              <button type="button" class="big" :class="{ on: !reserved }" :aria-pressed="!reserved" @click="reserved = false">
                <svg v-if="!reserved" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>아직 안 했어요
              </button>
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
              <div class="two">
                <div class="counter">
                  <span>성인</span>
                  <button type="button" aria-label="성인 줄이기" @click="bump('adults', -1)">−</button><b>{{ adults }}명</b><button type="button" class="plus" aria-label="성인 늘리기" @click="bump('adults', 1)">+</button>
                </div>
                <div class="counter">
                  <span>어린이 <small class="age">※13세 이하</small></span>
                  <button type="button" aria-label="어린이 줄이기" @click="bump('kids', -1)">−</button><b>{{ kids }}명</b><button type="button" class="plus" aria-label="어린이 늘리기" @click="bump('kids', 1)">+</button>
                </div>
              </div>
            </section>

            <section class="box">
              <h2>누구와 가나요? <small>여러 개 고를 수 있어요</small></h2>
              <div class="pills">
                <button v-for="w in WITH" :key="w" type="button" class="pill" :class="{ on: withs[w] }" :aria-pressed="!!withs[w]" @click="toggleWith(w)">
                  <svg v-if="withs[w]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>{{ w }}
                </button>
              </div>
            </section>

            <section class="box">
              <h2>가는 수단</h2>
              <div class="pills">
                <button v-for="m in MOVES" :key="m.key" type="button" class="pill" :class="{ on: move === m.key }" :aria-pressed="move === m.key" @click="move = m.key">{{ m.label }}</button>
              </div>
            </section>
          </template>
          <p v-else class="notice">예약을 마치면 이 화면에서 날짜와 인원을 저장해 주세요. 저장해 두면 캠핑 당일 안전 정보와 기상특보를 알려 드려요.</p>
        </div>

        <aside class="right">
          <div class="camp">
            <div class="ctop">
              <span class="cphoto" role="img" aria-label="예약한 캠핑장 사진">
                <img v-if="camp?.imageUrl" :src="camp.imageUrl" :alt="camp.name" />
                <template v-else>사진</template>
              </span>
              <span class="cinfo"><small>예약한 캠핑장</small><b>{{ camp?.name || '캠핑장을 골라 주세요' }}</b><span>{{ place }}</span></span>
              <span v-if="camp?.safetyScore != null" class="cscore"><span>{{ GRADE_LABEL[camp.grade] }}</span><b>{{ camp.safetyScore }}<small>점</small></b></span>
            </div>
            <div class="cdate"><span>{{ dateRange }}</span><span class="stay">{{ stayText }}</span></div>
          </div>

          <div class="sum">
            <h3>이대로 저장할까요?</h3>
            <dl>
              <dt>캠핑장</dt><dd>{{ camp ? `${camp.name} · ${camp.sigungu}` : '-' }}</dd>
              <dt>날짜</dt><dd>{{ okDate ? `${dateRange} (${stayText})` : dateRange }}</dd>
              <dt>인원</dt><dd>{{ peopleText }}</dd>
              <dt>동행</dt><dd>{{ withList.length ? withList.join(' · ') : '선택 안 함' }}</dd>
              <dt>가는 수단</dt><dd>{{ moveLabel }}</dd>
            </dl>
          </div>
          <p v-if="error" class="err" role="alert">{{ error }}</p>
          <button type="button" class="save" :disabled="!okDate || !reserved || saving" @click="save">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>{{ okDate ? '맞아요, 이대로 저장할게요' : '날짜를 확인해 주세요' }}
          </button>
          <RouterLink to="/web/my-booking" class="later">나중에 할게요</RouterLink>
        </aside>
      </div>
    </main>
  </div>
</template>

<style scoped>
.banner {
  position: relative;
  background: var(--web-green);
  overflow: hidden;
}
.banner img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 60%;
}
.shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(12, 31, 9, 0.82) 0%, rgba(12, 31, 9, 0.55) 45%, rgba(12, 31, 9, 0.25) 100%);
}
.inner {
  position: relative;
  max-width: 1120px;
  margin: 0 auto;
  padding: 48px 80px 40px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--web-white);
  color: var(--web-green);
  font-size: 13px;
  font-weight: 700;
}
.inner h1 {
  margin: 0;
  color: var(--web-white);
  font-size: 40px;
  font-weight: 800;
  letter-spacing: -1px;
}
.main {
  background: var(--web-bg-booking);
  padding: 40px 20px 80px;
}
.grid {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 28px;
  align-items: start;
}
.left {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.box {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.box h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}
.box h2 small {
  font-size: 13px;
  font-weight: 400;
  color: var(--web-text-sub);
  margin-left: 6px;
}
.two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.big {
  height: 54px;
  border-radius: 10px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.big.on {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--web-text-sub);
}
input[type='date'] {
  height: 50px;
  padding: 0 14px;
  border: 1px solid var(--web-line-input);
  border-radius: 10px;
  font-family: inherit;
  font-size: 16px;
  color: var(--web-text);
}
.bad {
  margin: 0;
  font-size: 13px;
  color: var(--web-terracotta);
}
.counter {
  height: 68px;
  padding: 0 16px;
  border-radius: 12px;
  border: 1px solid var(--web-line);
  background: var(--web-bg-booking);
  display: flex;
  align-items: center;
  gap: 12px;
}
.counter span {
  flex: 1;
  font-size: 15px;
}
/* 어린이 기준 나이 (팀 결정: 13세 이하) */
.counter .age {
  margin-left: 4px;
  font-size: 12px;
  color: var(--web-text-sub);
}
.counter b {
  min-width: 36px;
  text-align: center;
  font-size: 17px;
}
.counter button {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
  font-size: 22px;
  cursor: pointer;
}
.counter button.plus {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pill {
  height: 46px;
  padding: 0 20px;
  border-radius: 999px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
  font-family: inherit;
  font-size: 15px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pill.on {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.notice {
  margin: 0;
  padding: 16px 20px;
  border-radius: 12px;
  background: var(--web-bg-chip);
  font-size: 14px;
  line-height: 1.6;
  color: var(--web-text-sub);
}
.notice a {
  color: var(--web-green);
  font-weight: 700;
}
.right {
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.camp {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  overflow: hidden;
}
.ctop {
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.cphoto {
  flex: 0 0 72px;
  height: 72px;
  border-radius: 10px;
  background: var(--web-photo-empty);
  color: var(--web-text-faint);
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
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
.cinfo small {
  font-size: 11px;
  color: var(--web-text-sub);
}
.cinfo b {
  font-size: 18px;
}
.cinfo span {
  font-size: 12px;
  color: var(--web-text-sub);
}
.cscore {
  flex: 0 0 auto;
  width: 64px;
  height: 64px;
  border-radius: 14px;
  background: linear-gradient(160deg, var(--web-rec-from) 0%, var(--web-rec-mid) 52%, var(--web-rec-to) 100%);
  color: var(--web-white);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.cscore span {
  font-size: 11px;
  font-weight: 700;
}
.cscore b {
  font-size: 22px;
  line-height: 1;
  font-weight: 900;
}
.cscore small {
  font-size: 11px;
}
.cdate {
  padding: 14px 18px;
  border-top: 1px dashed var(--web-line-input);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 700;
}
.stay {
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 12px;
}
.sum {
  padding: 22px 24px;
  border-radius: 16px;
  background: var(--web-best);
}
.sum h3 {
  margin: 0 0 12px;
  font-size: 17px;
  font-weight: 800;
}
.sum dl {
  margin: 0;
  display: grid;
  grid-template-columns: 76px 1fr;
  row-gap: 10px;
  font-size: 14px;
}
.sum dt {
  color: var(--web-text-sub);
}
.sum dd {
  margin: 0;
  font-weight: 700;
}
.err {
  margin: 0;
  font-size: 13px;
  color: var(--web-terracotta);
}
.save {
  height: 58px;
  border: 0;
  border-radius: 12px;
  background: var(--web-green);
  color: var(--web-white);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(31, 58, 30, 0.2);
}
.save:disabled {
  opacity: 0.45;
  cursor: default;
}
.later {
  height: 56px;
  border-radius: 12px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
  font-size: 15px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}
.done {
  max-width: 560px;
  margin: 24px auto;
  padding: 40px;
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.done p {
  margin: 0;
  font-size: 16px;
  line-height: 1.6;
}
.dbtns {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.primary,
.ghost {
  height: 52px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.primary {
  background: var(--web-green);
  color: var(--web-white);
}
.ghost {
  border: 1px solid var(--web-line-input);
  color: var(--web-text);
}
</style>
