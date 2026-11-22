<script setup>
// 웹 지역·날짜 선택 팝업: 위 탭으로 "지역" ↔ "날짜·인원" 를 오간다 (모바일 RegionSheet · TripSheet 와 같은 API · 같은 trip store)
//   지역: 시·도 → 시·군·구 (GET /api/regions/sidos, /sidos/{id}/sigungus, /sigungus?q=)
//   날짜·인원: 달력(체크인→체크아웃) + 어른·아이 인원 + 아이 나이대 → "적용" 때 POST /api/trips/check 로 서버가 검사
// 사용: <WebTripModal :open="…" tab="region|date" @close="…" @apply="…" />
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '@/utils/api'
import { shortSido } from '@/utils/place'
import { useAuthStore } from '@/stores/auth'
import { useTripStore } from '@/stores/trip'
import { WEEK, fromIso, labelWithDay, monthCells, nightsBetween, todayIso } from '@/utils/date'

const props = defineProps({
  open: { type: Boolean, default: false },
  tab: { type: String, default: 'region' }, // 처음 열 탭
})
const emit = defineEmits(['close', 'apply'])

const trip = useTripStore()
const auth = useAuthStore()
const cur = ref('region')
const error = ref('')

// ---------- 지역 ----------
const sidos = ref([])
const sido = ref(null)
const sigungus = ref([])
const picked = ref(null)
const query = ref('')
const results = ref([])

async function openSido(s) {
  error.value = ''
  sido.value = s
  query.value = ''
  results.value = []
  try {
    sigungus.value = await api(`/api/regions/sidos/${s.id}/sigungus`)
  } catch (e) {
    error.value = e.message
  }
}
function toNation() {
  sido.value = null
  picked.value = null
  sigungus.value = []
}
let timer = null
watch(query, (q) => {
  clearTimeout(timer)
  if (!q.trim()) return (results.value = [])
  timer = setTimeout(async () => {
    try {
      results.value = await api('/api/regions/sigungus', { params: { q } })
    } catch (e) {
      error.value = e.message
    }
  }, 300)
})
function pickFromSearch(g) {
  const s = sidos.value.find((x) => x.name === g.sido)
  if (s) openSido(s)
  picked.value = g
}
const regionFoot = computed(() => {
  if (picked.value) return `${shortSido(sido.value?.name)} ${picked.value.name} · ${picked.value.safetyReady ? '안심 점수를 바로 계산할 수 있어요' : '안심 데이터를 준비하고 있어요'}`
  if (sido.value) return `${sido.value.name} 전체 캠핑장을 보여 드려요`
  return '전국 캠핑장을 보여 드려요'
})

// ---------- 날짜 · 인원 ----------
const MAX = 10 // 서버 TripService.MAX_PEOPLE 과 같게
const AGES = [
  { code: 'infant', label: '영유아', sub: '0~3세' },
  { code: 'preschool', label: '미취학', sub: '4~7세' },
  { code: 'elementary', label: '초등', sub: '8~12세' },
]
const adults = ref(1)
const kids = ref(0)
const childAges = ref([])
const checkIn = ref(null)
const checkOut = ref(null)
const saving = ref(false)
const monthOffset = ref(0)
const today = todayIso()
const now = new Date()

watch(kids, (n) => {
  const next = childAges.value.slice(0, n)
  while (next.length < n) next.push(null)
  childAges.value = next
}, { flush: 'sync' })

// 열릴 때마다 store 값으로 되돌린다 (적용 전까지 store 는 그대로)
async function reset() {
  error.value = ''
  cur.value = props.tab
  const t = trip.state
  adults.value = t.adults
  kids.value = t.kids
  childAges.value = [...t.childAges]
  checkIn.value = t.checkIn
  checkOut.value = t.checkOut
  picked.value = t.sigungu
  query.value = ''
  results.value = []
  if (t.checkIn) {
    const d = fromIso(t.checkIn)
    monthOffset.value = Math.max(0, (d.getFullYear() - now.getFullYear()) * 12 + d.getMonth() - now.getMonth())
  } else monthOffset.value = 0
  try {
    if (!sidos.value.length) sidos.value = await api('/api/regions/sidos')
    if (t.sido) await openSido(t.sido)
    else toNation()
    picked.value = t.sigungu
  } catch (e) {
    error.value = e.message
  }
  if (auth.isLoggedIn && t.kids === 0) {
    try {
      const saved = await api('/api/member/children') // 지난번에 적은 아이 나이대
      if (saved.length && kids.value === 0) {
        kids.value = saved.length
        childAges.value = saved.slice()
      }
    } catch {
      /* 못 불러오면 빈 상태로 */
    }
  }
}
watch(() => props.open, (o) => o && reset(), { immediate: true })

function monthOf(offset) {
  const d = new Date(now.getFullYear(), now.getMonth() + monthOffset.value + offset, 1)
  return { title: `${d.getFullYear()}년 ${d.getMonth() + 1}월`, cells: monthCells(d.getFullYear(), d.getMonth()) }
}
const months = computed(() => [monthOf(0), monthOf(1)])

function pickDay(iso) {
  if (iso < today) return
  if (!checkIn.value || checkOut.value || iso <= checkIn.value) {
    checkIn.value = iso
    checkOut.value = null
  } else checkOut.value = iso
}
function dayClass(iso, col) {
  return {
    past: iso < today,
    sun: col === 0,
    edge: iso === checkIn.value || iso === checkOut.value,
    between: checkIn.value && checkOut.value && iso > checkIn.value && iso < checkOut.value,
  }
}
const bump = (r, d, min) => (r.value = Math.max(min, Math.min(MAX - (r === adults ? kids.value : adults.value), r.value + d)))

const dateFoot = computed(() => {
  if (!checkIn.value) return '체크인 날짜를 눌러 주세요'
  if (!checkOut.value) return `${labelWithDay(checkIn.value)} 체크인 · 체크아웃 날짜를 눌러 주세요`
  return `${labelWithDay(checkIn.value)} – ${labelWithDay(checkOut.value)} · ${nightsBetween(checkIn.value, checkOut.value)}박 · 어른 ${adults.value}${kids.value ? ` · 아이 ${kids.value}` : ''}`
})
const canApplyDate = computed(() => checkIn.value && checkOut.value && childAges.value.every(Boolean) && !saving.value)

async function apply() {
  error.value = ''
  // 지역은 바로 store 에 넣는다 (날짜 서버 검사가 실패해도 지역은 적용)
  trip.setRegion(sido.value, picked.value)
  if (cur.value === 'region') return emit('apply') // 지역 탭에서는 지역만 적용하고 닫는다
  if (!canApplyDate.value) return (error.value = '체크인·체크아웃 날짜와 아이 나이대를 모두 골라 주세요.')
  saving.value = true
  try {
    const body = { adults: adults.value, kids: kids.value, childAges: childAges.value, checkIn: checkIn.value, checkOut: checkOut.value }
    const checked = await api('/api/trips/check', { method: 'post', body })
    if (auth.isLoggedIn && kids.value > 0) await api('/api/member/children', { method: 'put', body: { ageGroups: childAges.value } })
    trip.setWhoWhen(checked)
    emit('apply')
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

const onKey = (e) => props.open && e.key === 'Escape' && emit('close')
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div v-if="open" class="dim" role="dialog" aria-modal="true" aria-label="지역과 날짜 선택" @click.self="emit('close')">
    <div class="box">
      <header>
        <div class="tabs" role="tablist">
          <button type="button" role="tab" :aria-selected="cur === 'region'" :class="{ on: cur === 'region' }" @click="cur = 'region'">지역</button>
          <button type="button" role="tab" :aria-selected="cur === 'date'" :class="{ on: cur === 'date' }" @click="cur = 'date'">날짜 · 인원</button>
        </div>
        <button type="button" class="x" aria-label="닫기" @click="emit('close')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>
      </header>

      <!-- 지역 -->
      <div v-if="cur === 'region'" class="body">
        <h2>어디로 캠핑을 떠나시나요?</h2>
        <input v-model="query" class="search" placeholder="시·군·구 이름으로 찾기 (예: 인제)" />
        <ul v-if="query.trim()" class="chips">
          <li v-for="g in results" :key="g.id"><button type="button" class="chip" :class="{ on: picked?.id === g.id }" @click="pickFromSearch(g)">{{ shortSido(g.sido) }} {{ g.name }}</button></li>
          <li v-if="!results.length" class="none">찾는 지역이 없어요.</li>
        </ul>
        <template v-else>
          <div class="crumb">
            <button type="button" :class="{ cur: !sido }" @click="toNation">전국</button>
            <template v-if="sido"><span>›</span><b>{{ sido.name }}</b></template>
          </div>
          <ul v-if="!sido" class="chips">
            <li v-for="s in sidos" :key="s.id"><button type="button" class="chip" @click="openSido(s)">{{ shortSido(s.name) }}</button></li>
          </ul>
          <ul v-else class="chips">
            <li v-for="g in sigungus" :key="g.id"><button type="button" class="chip" :class="{ on: picked?.id === g.id }" @click="picked = picked?.id === g.id ? null : g">{{ g.name }}</button></li>
          </ul>
        </template>
        <p class="foot">{{ regionFoot }}</p>
      </div>

      <!-- 날짜 · 인원 -->
      <div v-else class="body">
        <h2>누구와, 언제 가세요?</h2>
        <div class="people">
          <div class="pcol">
            <span>어른</span>
            <div class="step"><button type="button" aria-label="어른 줄이기" :disabled="adults <= 1" @click="bump(adults, -1, 1)">−</button><b>{{ adults }}</b><button type="button" aria-label="어른 늘리기" @click="bump(adults, 1, 1)">+</button></div>
          </div>
          <div class="pcol">
            <span>아이</span>
            <div class="step"><button type="button" aria-label="아이 줄이기" :disabled="kids <= 0" @click="bump(kids, -1, 0)">−</button><b>{{ kids }}</b><button type="button" aria-label="아이 늘리기" @click="bump(kids, 1, 0)">+</button></div>
          </div>
        </div>
        <div v-if="kids" class="ages">
          <div v-for="(_, i) in childAges" :key="i" class="age">
            <span>아이 {{ i + 1 }}</span>
            <button v-for="a in AGES" :key="a.code" type="button" :class="{ on: childAges[i] === a.code }" @click="childAges[i] = a.code">{{ a.label }}<small>{{ a.sub }}</small></button>
          </div>
        </div>

        <div class="cal">
          <button type="button" class="nav" aria-label="이전 달" :disabled="monthOffset === 0" @click="monthOffset--">‹</button>
          <div v-for="(m, mi) in months" :key="m.title" class="month">
            <h3>{{ m.title }}</h3>
            <div class="grid wk"><span v-for="(w, i) in WEEK" :key="w" :class="{ sun: i === 0 }">{{ w }}</span></div>
            <div class="grid">
              <template v-for="(iso, i) in m.cells" :key="mi + '-' + i">
                <i v-if="!iso"></i>
                <button v-else type="button" class="day" :class="dayClass(iso, i % 7)" :disabled="iso < today" @click="pickDay(iso)">{{ Number(iso.slice(8)) }}</button>
              </template>
            </div>
          </div>
          <button type="button" class="nav" aria-label="다음 달" :disabled="monthOffset >= 11" @click="monthOffset++">›</button>
        </div>
        <p class="foot">{{ dateFoot }}</p>
      </div>

      <footer>
        <p v-if="error" class="err" role="alert">{{ error }}</p>
        <button type="button" class="ghost" @click="emit('close')">취소</button>
        <button type="button" class="ok" :disabled="cur === 'date' && !canApplyDate" @click="apply">{{ cur === 'region' ? '이 지역으로 보기' : '적용' }}</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  z-index: 60;
  padding-top: 90px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: var(--web-dim);
}
.box {
  width: 720px;
  max-width: calc(100% - 32px);
  max-height: calc(100vh - 130px);
  border-radius: 20px;
  background: var(--web-bg);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
header {
  flex: none;
  padding: 16px 20px 0 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--web-line);
}
.tabs {
  display: flex;
  gap: 24px;
}
.tabs button {
  padding: 12px 0;
  border: 0;
  border-bottom: 3px solid transparent;
  background: none;
  font-family: inherit;
  font-size: 16px;
  color: var(--web-text-sub);
  cursor: pointer;
}
.tabs button.on {
  border-bottom-color: var(--web-green);
  color: var(--web-green);
  font-weight: 700;
}
.x {
  width: 38px;
  height: 38px;
  margin-bottom: 6px;
  border: 0;
  border-radius: 50%;
  background: var(--web-bg-close);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px 8px;
}
h2 {
  margin: 0 0 16px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
.search {
  width: 100%;
  height: 46px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--web-line-input);
  font-family: inherit;
  font-size: 15px;
  box-sizing: border-box;
}
.crumb {
  margin: 16px 0 10px;
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  color: var(--web-text-sub);
}
.crumb button {
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 14px;
  color: var(--web-green);
  cursor: pointer;
  padding: 0;
}
.crumb button.cur {
  color: var(--web-text);
  font-weight: 700;
  cursor: default;
}
.crumb b {
  color: var(--web-text);
}
.chips {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 15px;
  cursor: pointer;
}
.chip.on {
  background: var(--web-green);
  border-color: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.none {
  color: var(--web-text-sub);
  font-size: 14px;
}
.foot {
  margin: 18px 0 8px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.people {
  display: flex;
  gap: 40px;
  margin-bottom: 14px;
}
.pcol {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 15px;
  font-weight: 700;
}
.step {
  display: flex;
  align-items: center;
  gap: 12px;
}
.step button {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  font-size: 18px;
  cursor: pointer;
}
.step button:disabled {
  opacity: 0.35;
  cursor: default;
}
.step b {
  min-width: 18px;
  text-align: center;
  font-size: 17px;
}
.ages {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.age {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
}
.age > span {
  width: 54px;
  color: var(--web-text-sub);
}
.age button {
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}
.age button small {
  margin-left: 6px;
  color: var(--web-text-mute);
}
.age button.on {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
}
.age button.on small {
  color: var(--web-text-light);
}
.cal {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.nav {
  flex: none;
  width: 34px;
  height: 34px;
  margin-top: 2px;
  border-radius: 50%;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  font-size: 18px;
  cursor: pointer;
}
.nav:disabled {
  opacity: 0.3;
  cursor: default;
}
.month {
  flex: 1;
  min-width: 0;
}
.month h3 {
  margin: 6px 0 10px;
  text-align: center;
  font-size: 16px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  row-gap: 2px;
}
.wk span {
  text-align: center;
  font-size: 12px;
  color: var(--web-text-mute);
  padding-bottom: 4px;
}
.sun {
  color: var(--web-terracotta);
}
.day {
  height: 36px;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  border-radius: 8px;
}
.day.past {
  color: var(--web-check-line);
  cursor: default;
}
.day.sun {
  color: var(--web-terracotta);
}
.day.between {
  background: var(--web-best);
  border-radius: 0;
}
.day.edge {
  background: var(--web-green);
  color: var(--web-white);
}
footer {
  flex: none;
  padding: 16px 28px 22px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}
.err {
  margin: 0 auto 0 0;
  font-size: 13px;
  color: var(--web-text-warn);
}
.ghost,
.ok {
  height: 48px;
  padding: 0 28px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 16px;
  cursor: pointer;
}
.ghost {
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  color: var(--web-text);
}
.ok {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.ok:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
