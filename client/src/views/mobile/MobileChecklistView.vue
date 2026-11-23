<script setup>
// 출발 준비 체크리스트 (내 캠핑 카드 · 가족 캠핑 카드 → "출발 준비"). 서버 GET /api/member/camp-plans/{planId}/checklist (같은 가족방 일정도 됨)
//  - 이 곳의 안전 정보 · (출발 3일 전부터) 지금 발효 중인 기상특보 · 캠핑 기간 날씨 예보 · 항목 묶음(특보 대비 · 날씨 대비 · 차박 준비 · 계절 대비 · 공통)
//  - 묶음 제목을 누르면 접고 펼친다 (처음엔 공통 준비물만 접힘). "날씨 대비" 묶음은 예보(최저 -12° 이하 · 0° 이하 · 비/눈)에 따라 서버가 만든다 — 침낭·핫팩·일산화탄소 경보기·동파는 계절이 아니라 이 온도 기준
//  - 체크한 항목은 이 브라우저(localStorage)에만 저장한다 — 서버·DB 에 저장하지 않는다 (항목 id 는 서버가 고정값으로 줌)
// 안전 판단 수치(몇 도 이하 등)는 화면에도 없다: 특보는 기상청이 발표한 이름 그대로, 침낭은 "내 침낭의 쾌적온도 확인"으로 안내
// 날씨 카드의 기온·강수확률·풍속은 기상청 예보를 그대로 보여 줄 뿐, 우리가 기준을 정해 항목을 바꾸지 않는다 (침낭 항목에만 예보 최저기온을 덧붙임)
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MobileBottomNav from '@/components/mobile/MobileBottomNav.vue'
import WeatherIcon from '@/components/common/WeatherIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { fromIso, labelWithDay } from '@/utils/date'
import { weatherKind } from '@/utils/weather'

const route = useRoute()
const auth = useAuthStore()
const myCamping = useMyCampingStore()

const planId = computed(() => Number(route.params.planId))
const data = computed(() => myCamping.checklists[planId.value] ?? null)
const loading = ref(false)
const error = ref('')

async function load() {
  error.value = ''
  if (!auth.isLoggedIn) return
  loading.value = true
  try {
    await myCamping.loadChecklist(planId.value)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ---- 체크 상태: 브라우저에만 (사용자·일정마다 따로 — 다른 사람이 같은 브라우저를 써도 안 섞이게) ----
const checked = ref({}) // { 항목id: true }
const storeKey = () => `campin.checklist.${auth.member?.id ?? 0}.${planId.value}`

function readChecked() {
  try {
    const ids = JSON.parse(localStorage.getItem(storeKey()) || '[]')
    checked.value = Object.fromEntries(ids.map((id) => [id, true]))
  } catch {
    checked.value = {} // 저장소를 못 읽어도(시크릿 창 등) 체크 없이 계속 쓸 수 있다
  }
}
onMounted(readChecked)
watch(planId, readChecked)

function toggle(id) {
  checked.value = { ...checked.value, [id]: !checked.value[id] }
  try {
    const ids = Object.keys(checked.value).filter((k) => checked.value[k])
    localStorage.setItem(storeKey(), JSON.stringify(ids))
  } catch {
    // 저장은 못 해도 이번 화면에서는 체크가 된다
  }
}

// ---- 특보 배너 접기·펼치기 (특보 이름 → 펼쳤는지) ----
const openWarn = ref({})

// ---- 묶음 접기·펼치기 ----
const open = ref({ 특보: true, 날씨: true, 차박: true, 계절: true, 공통: false })
const GROUPS = ['특보', '날씨', '차박', '계절', '공통']
const groupTitle = (g) =>
  ({ 특보: '특보 대비', 날씨: '날씨 대비', 차박: '차박 준비', 계절: '계절 대비', 공통: '공통 준비물' })[g]
const groups = computed(() =>
  GROUPS.map((g) => ({ key: g, title: groupTitle(g), items: (data.value?.items ?? []).filter((i) => i.group === g) }))
    .filter((g) => g.items.length),
)

const doneIn = (items) => items.filter((i) => checked.value[i.id]).length

// ---- 화면에 보일 글 ----
const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '점수 없음' }
const plan = computed(() => data.value?.plan)
const isSpot = computed(() => plan.value && plan.value.campId == null)
const range = computed(() =>
  plan.value ? `${labelWithDay(plan.value.checkIn)} – ${labelWithDay(plan.value.checkOut)}` : '',
)
const openText = computed(() => {
  if (!data.value) return ''
  const d = fromIso(data.value.openDate)
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
})
const safetyLink = computed(() =>
  isSpot.value
    ? { to: { path: '/app/chabak', query: { lat: plan.value.lat, lng: plan.value.lng } }, text: '차박 판정 보기 ›' }
    : { to: `/app/camps/${plan.value.campId}`, text: '캠핑장 상세 ›' },
)
// ---- 날씨 예보 (서버 forecast.days: { date, minTemp, maxTemp, pop, maxWind, sky, precip }) ----
const hasForecast = (d) => [d.minTemp, d.maxTemp, d.pop, d.maxWind, d.sky].some((v) => v != null)
const temp = (v) => (v == null ? '-' : `${Math.round(v)}°`)
// 하늘 상태 + (비·눈이 있으면) 강수 형태: "구름많음 · 눈"
const skyText = (d) => [d.sky, d.precip && d.precip !== '없음' ? d.precip : null].filter(Boolean).join(' · ') || '-'
// 경보는 빨강, 주의보·그 밖은 주황 (기상청 특보 이름에 "경보"가 들어 있으면)
const warnClass = (category) => (category.includes('경보') ? 'danger' : 'caution')
// 날씨 종류(맑음·비·눈 …)별 카드 바탕색 클래스: wx-clear 등 (모르면 기본 회색)
const wxClass = (d) => {
  const k = weatherKind(d.sky, d.precip)
  return k ? `wx-${k}` : ''
}
</script>

<template>
  <div class="page">
    <div class="scroll">
      <RouterLink to="/app/my-camping" class="back">‹ 내 캠핑</RouterLink>

      <div v-if="!auth.isLoggedIn" class="empty">
        <p>출발 준비는 로그인하면 쓸 수 있습니다.</p>
        <RouterLink to="/app/login" class="primary">로그인</RouterLink>
      </div>

      <p v-else-if="error" class="state">
        {{ error }} <button type="button" class="retry" @click="load">다시 시도</button>
      </p>
      <p v-else-if="!data" class="state">{{ loading ? '불러오는 중...' : '' }}</p>

      <template v-else>
        <h1>{{ plan.name }}</h1>
        <p class="sub">{{ range }}<template v-if="isSpot"> · 차박지</template></p>

        <!-- 이 곳의 안전 정보: 캠핑장 목록·상세와 같은 값 (차박 자리는 찍은 자리의 안전점수 계산) -->
        <div v-if="data.safety" class="info">
          <div class="info-row">
            <span class="grade" :class="data.safety.grade ?? 'unknown'">
              <i />{{ GRADE_LABEL[data.safety.grade] ?? GRADE_LABEL.unknown }}
              <template v-if="data.safety.score != null">{{ data.safety.score }}</template>
            </span>
            <RouterLink :to="safetyLink.to" class="info-link">{{ safetyLink.text }}</RouterLink>
          </div>
        </div>

        <!-- 특보: 출발 3일 전부터(캠핑 중 포함) 그 지역에 지금 발효 중인 기상특보 -->
        <div v-if="data.past" class="notice">지난 캠핑입니다. 준비물은 참고용으로만 보십시오.</div>
        <template v-else-if="data.near">
          <!-- 특보 이름(제목)은 늘 보이고, 긴 지역 목록(메시지)은 제목을 눌러 펼친다 (처음엔 접힘) -->
          <div v-for="w in data.warnings" :key="w.category" class="alert" :class="warnClass(w.category)" role="alert">
            <button type="button" class="alert-head" :class="{ shut: !openWarn[w.category] }"
              :aria-expanded="!!openWarn[w.category]" @click="openWarn[w.category] = !openWarn[w.category]">
              <b>{{ w.category }} 발효 중</b>
              <span class="chev" aria-hidden="true" />
            </button>
            <p v-show="openWarn[w.category]" class="alert-msg">{{ w.message }}</p>
          </div>
          <div v-if="!data.warningsAvailable" class="notice">
            <b>특보 정보를 지금 받지 못했습니다</b>
            잠시 뒤 다시 확인하거나 기상청 날씨누리에서 확인해 주십시오.
          </div>
          <div v-else-if="!data.warnings.length" class="notice">
            <b>지금 이 지역에 발효 중인 특보가 없습니다</b>
            시·도 단위 기상청 특보 현황 기준입니다.
          </div>

          <!-- 날씨 예보: 캠핑 기간(오늘 이후) 하루씩. 기상청이 주지 않는 날은 "예보 없음" -->
          <section v-if="data.forecast" class="wx" aria-label="캠핑 기간 날씨">
            <h2 class="wx-title">캠핑 기간 날씨</h2>
            <p v-if="!data.forecast.available" class="wx-empty">
              날씨 예보를 지금 받지 못했습니다. 출발 전에 기상청 날씨누리에서 직접 확인해 주십시오.
            </p>
            <ul v-else class="wx-days">
              <li v-for="d in data.forecast.days" :key="d.date" class="wx-day">
                <span class="wx-date">{{ labelWithDay(d.date) }}</span>
                <template v-if="hasForecast(d)">
                  <!-- 날씨 색은 작은 동그란 아이콘 배경에만 (특보 배너의 빨강·주황과 겹쳐 보이지 않게) -->
                  <span class="wx-ic" :class="wxClass(d)"><WeatherIcon :sky="d.sky" :precip="d.precip" :size="22" /></span>
                  <span class="wx-sky">{{ skyText(d) }}</span>
                  <span class="wx-temp">{{ temp(d.minTemp) }}<small> / {{ temp(d.maxTemp) }}</small></span>
                  <span class="wx-meta">
                    <template v-if="d.pop != null">강수확률 {{ d.pop }}%</template>
                    <template v-if="d.maxWind != null"><br />바람 {{ d.maxWind }}m/s</template>
                  </span>
                </template>
                <span v-else class="wx-none">예보 없음</span>
              </li>
            </ul>
          </section>
        </template>
        <div v-else class="notice">
          <b>특보와 날씨는 {{ openText }}부터 보입니다</b>
          출발 3일 전부터 그 지역의 기상특보와 날씨 예보를 알려 드립니다. 그때까지는 준비물을 미리 챙겨 두십시오.
        </div>

        <!-- 항목 묶음 (제목을 누르면 접고 펼침) -->
        <section v-for="g in groups" :key="g.key" class="gcard">
          <button type="button" class="grp" :class="{ shut: !open[g.key] }" :aria-expanded="!!open[g.key]"
            @click="open[g.key] = !open[g.key]">
            {{ g.title }}
            <span class="cnt" :class="{ all: doneIn(g.items) === g.items.length }">{{ doneIn(g.items) }}/{{ g.items.length }}</span>
            <span class="chev" aria-hidden="true" />
          </button>
          <ul v-show="open[g.key]" class="items">
            <li v-for="it in g.items" :key="it.id">
              <button type="button" class="item" :class="{ done: checked[it.id] }" role="checkbox"
                :aria-checked="!!checked[it.id]" @click="toggle(it.id)">
                <span class="box" aria-hidden="true" />
                <span class="tx">
                  {{ it.text }}
                  <small v-if="it.why">{{ it.why }}</small>
                </span>
              </button>
            </li>
          </ul>
        </section>
      </template>
    </div>
    <MobileBottomNav active="mycamp" />
  </div>
</template>

<style scoped>
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 20px 24px;
}
.back {
  display: inline-block;
  min-height: 44px;
  line-height: 44px;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-sub);
}
h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.sub {
  margin: 4px 0 14px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.primary {
  height: 48px;
  padding: 0 28px;
  border-radius: var(--radius-input);
  background: var(--color-primary);
  color: var(--color-card);
  display: flex;
  align-items: center;
  font-weight: 700;
}
.state {
  margin: 48px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.retry {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
/* 이 곳의 안전 정보 (등급 배지는 캠핑장 목록과 같은 색) */
.info {
  margin: 0 0 12px;
  padding: 18px 18px;
  border-radius: 18px;
  background: var(--color-bg-soft);
}
.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: var(--color-text-sub);
}
.info-link {
  margin-left: auto;
  font-size: 15px;
  font-weight: 800;
  color: var(--color-text);
}
.grade {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border-radius: 16px;
  font-size: 16px;
  font-weight: 800;
  background: var(--color-bg-soft);
  color: var(--color-text-sub);
}
.grade i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: currentColor;
}
.grade.safe {
  background: var(--color-safe-bg);
  color: var(--color-green);
}
.grade.caution {
  background: var(--color-caution-bg);
  color: var(--color-caution);
}
.grade.danger {
  background: var(--color-warn-bg);
  color: var(--color-danger);
}
/* 특보 배너 · 안내 */
.alert,
.notice {
  margin: 0 0 10px;
  padding: 13px 16px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.55;
}
.alert b,
.notice b {
  display: block;
  margin-bottom: 2px;
  font-size: 14px;
}
/* 특보 배너 제목 줄 = 접고 펼치는 버튼 */
.alert-head {
  width: 100%;
  min-height: 24px;
  padding: 0;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.alert-head b {
  margin: 0;
}
.alert-head .chev {
  border-color: currentColor; /* 배너 글씨 색(빨강·주황)을 따라감 */
}
.alert-head.shut .chev {
  transform: rotate(-45deg);
}
.alert-msg {
  margin: 6px 0 0;
}
.alert.caution {
  background: var(--color-caution-bg);
  color: var(--color-caution);
}
.alert.danger {
  background: var(--color-warn-bg);
  color: var(--color-danger);
}
.notice {
  background: var(--color-bg-soft);
  color: var(--color-text-sub);
}
.notice b {
  color: var(--color-text);
}
/* 캠핑 기간 날씨 (색은 기존 변수만) */
.wx {
  margin: 16px 0 4px;
}
.wx-title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.wx-empty {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-sub);
}
.wx-days {
  margin: 0;
  padding: 0;
  list-style: none;
  /* 하루씩 가로 한 줄: 3일이면 한 줄에 꽉 차게, 4일 이상이면 옆으로 넘겨 본다 */
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(100px, 1fr);
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none; /* 스크롤바는 숨기고 손가락으로 넘긴다 */
}
.wx-days::-webkit-scrollbar {
  display: none;
}
.wx-day {
  min-width: 0;
  padding: 12px 10px;
  border-radius: 16px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.wx-date,
.wx-meta,
.wx-none {
  font-size: 12px;
  color: var(--color-text-sub);
}
.wx-sky {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
/* 날씨 종류별 동그란 아이콘 배경 (아이콘 색은 WeatherIcon). 카드 바탕은 모두 같은 연한 회색 */
.wx-ic {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-card);
}
.wx-clear { background: var(--color-wx-clear-bg); }
.wx-partly { background: var(--color-wx-partly-bg); }
.wx-cloud { background: var(--color-wx-cloud-bg); }
.wx-rain { background: var(--color-wx-rain-bg); }
.wx-snow { background: var(--color-wx-snow-bg); }
.wx-temp {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.wx-temp small {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-sub);
}
.wx-meta {
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
}
/* 항목 묶음 = 둥근 카드 */
.gcard {
  margin-top: 12px;
  border: 1.5px solid var(--color-border); /* 연한 줄(--color-line)은 흰 바탕에서 안 보여서 한 단계 짙게 */
  border-radius: 18px;
  background: var(--color-card);
  overflow: hidden;
}
/* 묶음 제목 = 접고 펼치는 버튼 */
.grp {
  width: 100%;
  min-height: 56px;
  padding: 0 16px;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  gap: 10px;
  font: inherit;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
  text-align: left;
  cursor: pointer;
}
/* 체크한 개수 "2/3" — 다 하면 초록 */
.cnt {
  padding: 2px 9px;
  border-radius: 10px;
  background: var(--color-bg-soft);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-sub);
  font-variant-numeric: tabular-nums;
}
.cnt.all {
  background: var(--color-safe-bg);
  color: var(--color-green);
}
.chev {
  margin: 0 2px 0 auto;
  width: 9px;
  height: 9px;
  border: solid var(--color-arrow);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  transition: transform 0.2s;
}
.grp.shut .chev {
  transform: rotate(-45deg);
}
.items {
  margin: 0;
  padding: 0;
  list-style: none;
}
.item {
  width: 100%;
  min-height: 60px;
  padding: 13px 16px;
  border: none;
  border-top: 1px solid var(--color-line);
  background: none;
  display: flex;
  align-items: center;
  gap: 14px;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
/* 동그란 체크칸 */
.box {
  flex: none;
  width: 26px;
  height: 26px;
  border: 2px solid var(--color-border);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, border-color 0.15s;
}
.item.done .box {
  border-color: var(--color-primary);
  background: var(--color-primary);
}
.item.done .box::after {
  content: '';
  width: 6px;
  height: 11px;
  border: solid var(--color-card);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg) translate(-1px, -1px);
}
.tx {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}
.tx small {
  display: block;
  margin-top: 2px;
  font-size: 12.5px;
  font-weight: 400;
  color: var(--color-text-sub);
}
.item.done .tx {
  color: var(--color-text-sub);
  text-decoration: line-through;
}
.item.done .tx small {
  text-decoration: none;
}
</style>
