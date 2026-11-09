<script setup>
// "누구와, 언제 가세요?" 시트: 함께 가는 사람 · 어른/아이 인원 · 아이 나이대 · 달력(체크인→체크아웃)
// "적용"을 누르면 서버 POST /api/trips/check 로 검사 → 통과하면 trip store 에 넣는다
// 로그인 회원은 아이 나이대를 GET/PUT /api/member/children 로 불러오고 저장한다
import { computed, onMounted, ref, watch } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { api } from '@/utils/api'
import { useTripStore } from '@/stores/trip'
import { useAuthStore } from '@/stores/auth'
import { WEEK, fromIso, labelWithDay, monthCells, nightsBetween, todayIso } from '@/utils/date'

const emit = defineEmits(['close', 'apply'])
const trip = useTripStore()
const auth = useAuthStore()

const MAX = 10 // 서버 TripService.MAX_PEOPLE 과 같게
const AGES = [
  { code: 'infant', label: '영유아', sub: '0~3세' },
  { code: 'preschool', label: '미취학', sub: '4~7세' },
  { code: 'elementary', label: '초등', sub: '8~12세' },
]

// 시트 안에서 고치는 값 (적용 전까지 store 는 그대로)
const adults = ref(trip.state.adults)
const kids = ref(trip.state.kids)
const childAges = ref([...trip.state.childAges])
const checkIn = ref(trip.state.checkIn)
const checkOut = ref(trip.state.checkOut)
const loadedSaved = ref(false) // 회원 정보에서 나이대를 불러왔는지
const error = ref('')
const saving = ref(false)

// 아이 수가 바뀌면 나이대 칸 수를 맞춘다 (늘면 빈칸 null, 줄면 뒤에서 자름)
// flush: 'sync' — 아이 수를 바꾼 바로 그 순간 칸 수도 맞춘다 (아래 onMounted 에서 바로 채우기 위해)
watch(
  kids,
  (n) => {
    const next = childAges.value.slice(0, n)
    while (next.length < n) next.push(null)
    childAges.value = next
  },
  { flush: 'sync' },
)

onMounted(async () => {
  if (!auth.isLoggedIn) return
  try {
    const saved = await api('/api/member/children')
    if (!saved.length) return
    // 이번에 아직 고르지 않은 칸만 지난번 정보로 채운다
    if (kids.value === 0) kids.value = saved.length
    childAges.value = childAges.value.map((v, i) => v || saved[i] || null)
    loadedSaved.value = true
  } catch {
    /* 못 불러오면 빈 상태로 */
  }
})

// ---- 함께 가는 사람 칩: 인원 수에서 계산하고, 누르면 인원 수를 바꾼다 ----
const alone = computed(() => adults.value === 1 && kids.value === 0)
const withAdults = computed(() => adults.value >= 2)
const withKids = computed(() => kids.value >= 1)

function toggleAlone() {
  adults.value = 1
  kids.value = 0
}
function toggleAdults() {
  adults.value = withAdults.value ? 1 : Math.max(2, adults.value)
}
function toggleKids() {
  kids.value = withKids.value ? 0 : Math.max(1, kids.value)
}

// ---- 달력: 한 달씩 보여 주고 화살표로 넘긴다 (이번 달보다 앞으로는 못 간다) ----
const today = todayIso()
const now = new Date()

// 이미 고른 체크인이 있으면 그 달부터 보여 준다 (다시 열었을 때 고른 날짜가 바로 보이게)
function monthsFromNow(iso) {
  if (!iso) return 0
  const d = fromIso(iso)
  const diff = (d.getFullYear() - now.getFullYear()) * 12 + d.getMonth() - now.getMonth()
  return Math.max(0, diff)
}
const monthOffset = ref(monthsFromNow(checkIn.value)) // 0 = 이번 달

const month = computed(() => {
  const d = new Date(now.getFullYear(), now.getMonth() + monthOffset.value, 1)
  return { title: `${d.getFullYear()}년 ${d.getMonth() + 1}월`, cells: monthCells(d.getFullYear(), d.getMonth()) }
})

// 체크인 → 체크아웃 순서로 누른다. 둘 다 골랐거나 체크인보다 앞 날을 누르면 체크인부터 다시
function pickDay(iso) {
  if (iso < today) return
  if (!checkIn.value || checkOut.value || iso <= checkIn.value) {
    checkIn.value = iso
    checkOut.value = null
  } else {
    checkOut.value = iso
  }
}
function dayClass(iso, col) {
  return {
    past: iso < today,
    sun: col === 0,
    edge: iso === checkIn.value || iso === checkOut.value,
    between: checkIn.value && checkOut.value && iso > checkIn.value && iso < checkOut.value,
  }
}

// ---- 아래 요약 · 적용 ----
const summary = computed(() => {
  if (!checkIn.value) return '체크인 날짜를 눌러 주세요'
  if (!checkOut.value) return `${labelWithDay(checkIn.value)} 체크인 · 체크아웃 날짜를 눌러 주세요`
  const people = `어른 ${adults.value}${kids.value ? ` · 아이 ${kids.value}` : ''}`
  return `${labelWithDay(checkIn.value)} – ${labelWithDay(checkOut.value)} · ${nightsBetween(checkIn.value, checkOut.value)}박 · ${people}`
})
const canApply = computed(() => checkIn.value && checkOut.value && childAges.value.every(Boolean) && !saving.value)

async function apply() {
  error.value = ''
  saving.value = true
  const body = { adults: adults.value, kids: kids.value, childAges: childAges.value,
    checkIn: checkIn.value, checkOut: checkOut.value }
  try {
    const checked = await api('/api/trips/check', { method: 'post', body })
    if (auth.isLoggedIn && kids.value > 0) {
      await api('/api/member/children', { method: 'put', body: { ageGroups: childAges.value } })
    }
    trip.setWhoWhen(checked)
    emit('apply')
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BottomSheet title="누구와, 언제 가세요?" @close="emit('close')">
    <section>
      <div class="sec-head">
        <h3>함께 가는 사람</h3>
        <span class="hint">복수 선택 · 동행에 맞는 안전 정보를 챙겨요</span>
      </div>
      <div class="chips">
        <button type="button" class="chip" :class="{ on: alone }" :aria-pressed="alone" @click="toggleAlone">혼자</button>
        <button type="button" class="chip" :class="{ on: withAdults }" :aria-pressed="withAdults" @click="toggleAdults">
          성인 동행</button>
        <button type="button" class="chip" :class="{ on: withKids }" :aria-pressed="withKids" @click="toggleKids">
          아이 동반</button>
      </div>

      <div class="counter">
        <div><b>어른</b><small>만 13세 이상</small></div>
        <div class="step">
          <button type="button" class="minus" :disabled="adults <= 1" aria-label="어른 한 명 빼기" @click="adults--">−</button>
          <b aria-live="polite">{{ adults }}</b>
          <button type="button" class="plus" :disabled="adults >= MAX" aria-label="어른 한 명 더하기" @click="adults++">+</button>
        </div>
      </div>
      <div class="counter">
        <div><b>아이</b><small>만 13세 미만</small></div>
        <div class="step">
          <button type="button" class="minus" :disabled="kids <= 0" aria-label="아이 한 명 빼기" @click="kids--">−</button>
          <b aria-live="polite">{{ kids }}</b>
          <button type="button" class="plus" :disabled="kids >= MAX" aria-label="아이 한 명 더하기" @click="kids++">+</button>
        </div>
      </div>

      <!-- 아이가 있으면 나이대 -->
      <div v-if="kids > 0" class="ages">
        <h4>아이 나이대</h4>
        <p class="hint">나이에 맞는 안전 안내를 드려요<template v-if="loadedSaved"> · 지난번 정보를 불러왔어요</template></p>
        <div v-for="(age, i) in childAges" :key="i" class="age-row">
          <span class="who">아이 {{ i + 1 }}</span>
          <button v-for="a in AGES" :key="a.code" type="button" class="age" :class="{ on: age === a.code }"
            :aria-pressed="age === a.code" @click="childAges[i] = a.code">
            <b>{{ a.label }}</b><small>{{ a.sub }}</small>
          </button>
        </div>
        <p class="save-note">
          {{ auth.isLoggedIn ? '내 정보 › 함께 가는 가족에 저장돼요' : '로그인하면 다음에도 불러올 수 있어요' }}
        </p>
      </div>
    </section>

    <!-- 달력 -->
    <section class="month">
      <div class="month-head">
        <button type="button" class="arrow" :disabled="monthOffset === 0" aria-label="이전 달" @click="monthOffset--">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.4"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" />
          </svg>
        </button>
        <h3 aria-live="polite">{{ month.title }}</h3>
        <button type="button" class="arrow" aria-label="다음 달" @click="monthOffset++">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.4"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
      <p class="hint month-hint">체크인 → 체크아웃 순서로 눌러요</p>
      <div class="cal">
        <span v-for="(w, i) in WEEK" :key="w" class="wd" :class="{ sun: i === 0 }">{{ w }}</span>
        <template v-for="(iso, i) in month.cells" :key="iso || `blank-${i}`">
          <span v-if="!iso" />
          <button v-else type="button" class="day" :class="dayClass(iso, i % 7)" :disabled="iso < today"
            :aria-pressed="iso === checkIn || iso === checkOut" @click="pickDay(iso)">
            {{ Number(iso.slice(8)) }}
          </button>
        </template>
      </div>
    </section>

    <template #footer>
      <p class="foot-text">{{ summary }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="button" class="apply" :disabled="!canApply" @click="apply">적용하고 캠핑장 보기</button>
    </template>
  </BottomSheet>
</template>

<style scoped>
.sec-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin: 8px 0 12px;
}
h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 900;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-sub);
}
.chips {
  display: flex;
  gap: 8px;
}
.chip {
  height: 44px;
  padding: 0 18px;
  border: 1px solid var(--color-border);
  border-radius: 22px;
  background: var(--color-card);
  font-size: 15px;
  cursor: pointer;
}
.chip.on {
  border: 2px solid var(--color-text);
  background: var(--color-selected-bg);
  font-weight: 700;
}
.counter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-track);
}
.counter > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.counter small {
  font-size: 12px;
  color: var(--color-text-sub);
}
.step {
  display: flex;
  align-items: center;
  gap: 14px;
}
.step b {
  min-width: 20px;
  text-align: center;
  font-size: 18px;
}
.minus,
.plus {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.minus {
  border: 1px solid var(--color-border);
  background: var(--color-card);
}
.plus {
  border: none;
  background: var(--color-text);
  color: var(--color-card);
}
.minus:disabled,
.plus:disabled {
  opacity: 0.3;
  cursor: default;
}
.ages {
  margin-top: 14px;
  padding: 14px;
  border-radius: 14px;
  background: var(--color-bg-soft);
}
h4 {
  margin: 0 0 4px;
  font-size: 15px;
}
.age-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
}
.who {
  flex: 0 0 44px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.age {
  flex: 1;
  min-height: 48px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
}
.age b {
  font-size: 14px;
  font-weight: 500;
}
.age small {
  font-size: 11px;
  color: var(--color-text-sub);
}
.age.on {
  border: 2px solid var(--color-text);
}
.age.on b {
  font-weight: 700;
}
.save-note {
  margin: 12px 0 0;
  font-size: 12px;
  text-decoration: underline;
}
.month {
  margin-top: 24px;
}
.month-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.arrow {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.arrow:disabled {
  color: var(--color-border);
  cursor: default;
}
.month-hint {
  margin: 2px 0 12px;
  text-align: center;
}
.cal {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  row-gap: 6px;
  text-align: center;
}
.wd {
  font-size: 13px;
  color: var(--color-text-sub);
  padding-bottom: 6px;
}
.day {
  height: 44px;
  border: none;
  border-radius: 22px;
  background: none;
  font-size: 15px;
  cursor: pointer;
}
.sun {
  color: var(--color-danger);
}
.day.past {
  color: var(--color-border);
  cursor: default;
}
.day.between {
  border-radius: 0;
  background: var(--color-selected-bg);
}
.day.edge {
  background: var(--color-text);
  color: var(--color-card);
  font-weight: 700;
}
.foot-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.error {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-warn-text);
}
.apply {
  width: 100%;
  height: 54px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.apply:disabled {
  opacity: 0.35;
  cursor: default;
}
</style>
