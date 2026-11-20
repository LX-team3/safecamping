<script setup>
// 웹 출발 준비 체크리스트 (내 예약 카드 → 안전 점검 체크리스트)
// 데이터: GET /api/member/camp-plans/{planId}/checklist  (모바일 ChecklistView 와 같은 API)
// 체크한 항목은 이 브라우저(localStorage)에만 저장한다 — 서버·DB 에 저장하지 않는다. 모바일과 같은 키를 써서 같은 기기에서는 같이 보인다
// 안전 판단 수치는 만들지 않는다: 특보는 기상청 발표 이름 그대로, 예보는 기상청 값 그대로 보여 준다
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import WebSubPage from '@/components/web/WebSubPage.vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { fromIso, labelWithDay } from '@/utils/date'

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

// ---- 체크 상태 ----
const checked = ref({})
const storeKey = () => `campin.checklist.${auth.member?.id ?? 0}.${planId.value}`
function readChecked() {
  try {
    const ids = JSON.parse(localStorage.getItem(storeKey()) || '[]')
    checked.value = Object.fromEntries(ids.map((id) => [id, true]))
  } catch {
    checked.value = {}
  }
}
onMounted(readChecked)
watch(planId, () => {
  readChecked()
  load()
})
function toggle(id) {
  checked.value = { ...checked.value, [id]: !checked.value[id] }
  try {
    localStorage.setItem(storeKey(), JSON.stringify(Object.keys(checked.value).filter((k) => checked.value[k])))
  } catch {
    // 저장은 못 해도 이번 화면에서는 체크가 된다
  }
}

// ---- 묶음 ----
const open = ref({ 특보: true, 날씨: true, 차박: true, 계절: true, 공통: true })
const GROUPS = ['특보', '날씨', '차박', '계절', '공통']
const groupTitle = (g) => ({ 특보: '특보 대응', 날씨: '날씨 대응', 차박: '차박 준비', 계절: `${data.value?.season ?? ''} 준비물`, 공통: '공통 준비물' })[g]
const groups = computed(() =>
  GROUPS.map((g) => ({ key: g, title: groupTitle(g), items: (data.value?.items ?? []).filter((i) => i.group === g) })).filter((g) => g.items.length),
)

// ---- 화면 글 ----
const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의', unknown: '점수 없음' }
const plan = computed(() => data.value?.plan)
const isSpot = computed(() => plan.value && plan.value.campId == null)
const range = computed(() => (plan.value ? `${labelWithDay(plan.value.checkIn)} – ${labelWithDay(plan.value.checkOut)}` : ''))
const openText = computed(() => {
  if (!data.value) return ''
  const d = fromIso(data.value.openDate)
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
})
const safetyLink = computed(() =>
  isSpot.value ? { to: '/web/chabak', text: '차박지 찾기 ›' } : { to: `/web/camps/${plan.value.campId}`, text: '캠핑장 상세 ›' },
)
const hasForecast = (d) => [d.minTemp, d.maxTemp, d.pop, d.maxWind, d.sky].some((v) => v != null)
const temp = (v) => (v == null ? '-' : `${Math.round(v)}°`)
const skyText = (d) => [d.sky, d.precip && d.precip !== '없음' ? d.precip : null].filter(Boolean).join(' · ') || '-'
const warnClass = (category) => (category.includes('경보') ? 'danger' : 'caution')
</script>

<template>
  <WebSubPage eyebrow="출발 준비" :title="plan ? plan.name : '안전 점검 체크리스트'" :desc="plan ? `${range}${isSpot ? ' · 차박지' : ''}` : ''" narrow>
    <template #actions>
      <RouterLink to="/web/my-booking" class="back">‹ 내 예약</RouterLink>
    </template>

    <div v-if="!auth.isLoggedIn" class="card center">
      <p>출발 준비는 로그인하면 쓸 수 있어요.</p>
      <RouterLink to="/web/login" class="primary">로그인</RouterLink>
    </div>
    <p v-else-if="error" class="state">{{ error }} <button type="button" class="retry" @click="load">다시 시도</button></p>
    <p v-else-if="!data" class="state">{{ loading ? '불러오는 중...' : '' }}</p>

    <template v-else>
      <!-- 이 곳의 안전 정보 -->
      <section v-if="data.safety" class="card info">
        <span class="grade" :class="data.safety.grade ?? 'unknown'">
          {{ GRADE_LABEL[data.safety.grade] ?? GRADE_LABEL.unknown }}<template v-if="data.safety.score != null"> {{ data.safety.score }}</template>
        </span>
        <span v-if="data.safety.erDriveMin != null" class="er">응급실 {{ data.safety.erApprox ? '약' : '차로' }} {{ data.safety.erDriveMin }}분</span>
        <RouterLink :to="safetyLink.to" class="link">{{ safetyLink.text }}</RouterLink>
      </section>

      <!-- 특보 · 날씨: 출발 3일 전부터 -->
      <div v-if="data.past" class="notice">지난 캠핑이에요. 준비물은 참고용으로만 보세요.</div>
      <template v-else-if="data.near">
        <div v-for="w in data.warnings" :key="w.category" class="alert" :class="warnClass(w.category)" role="alert">
          <b>{{ w.category }} 발효 중</b> {{ w.message }}
        </div>
        <div v-if="!data.warningsAvailable" class="notice"><b>특보 정보를 지금 받지 못했어요</b> 잠시 뒤 다시 확인하거나 기상청 날씨누리에서 확인해 주세요.</div>
        <div v-else-if="!data.warnings.length" class="notice"><b>지금 이 지역에 발효 중인 특보가 없어요</b> 시·도 단위 기상청 특보 현황 기준이에요.</div>

        <section v-if="data.forecast" class="card wx" aria-label="캠핑 기간 날씨">
          <h2>캠핑 기간 날씨</h2>
          <p v-if="!data.forecast.available" class="wx-empty">날씨 예보를 지금 받지 못했어요. 출발 전에 기상청 날씨누리에서 직접 확인해 주세요.</p>
          <ul v-else class="days">
            <li v-for="d in data.forecast.days" :key="d.date">
              <span class="wd">{{ labelWithDay(d.date) }}</span>
              <template v-if="hasForecast(d)">
                <span class="sky">{{ skyText(d) }}</span>
                <span class="tp">{{ temp(d.minTemp) }}<small> / {{ temp(d.maxTemp) }}</small></span>
                <span class="meta">
                  <template v-if="d.pop != null">강수확률 {{ d.pop }}%</template>
                  <template v-if="d.maxWind != null"> · 바람 {{ d.maxWind }}m/s</template>
                </span>
              </template>
              <span v-else class="none">예보 없음</span>
            </li>
          </ul>
          <p v-if="data.forecast.available" class="wx-foot">기상청 단기예보 · 캠핑장 위치 기준 · 아침 최저 / 낮 최고</p>
        </section>
      </template>
      <div v-else class="notice"><b>특보와 날씨는 {{ openText }}부터 보여요</b> 출발 3일 전부터 그 지역의 기상특보와 날씨 예보를 알려 드려요. 그때까지는 준비물을 미리 챙겨 두세요.</div>

      <!-- 항목 묶음 -->
      <section v-for="g in groups" :key="g.key" class="card grp">
        <button type="button" class="gt" :aria-expanded="!!open[g.key]" @click="open[g.key] = !open[g.key]">
          <span>{{ g.title }}<i v-if="g.key === '특보'" class="tag-warn">특보</i></span>
          <span class="chev" :class="{ shut: !open[g.key] }" aria-hidden="true"></span>
        </button>
        <ul v-show="open[g.key]" class="items">
          <li v-for="it in g.items" :key="it.id">
            <button type="button" class="item" :class="{ done: checked[it.id] }" role="checkbox" :aria-checked="!!checked[it.id]" @click="toggle(it.id)">
              <span class="box" aria-hidden="true">
                <svg v-if="checked[it.id]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10" /></svg>
              </span>
              <span class="tx">{{ it.text }}<small v-if="it.why">{{ it.why }}</small></span>
            </button>
          </li>
        </ul>
      </section>

      <p class="foot">체크한 항목은 이 기기(브라우저)에만 저장돼요 · 특보는 기상청 특보 현황 기준 참고용이에요</p>
    </template>
  </WebSubPage>
</template>

<style scoped>
.back {
  height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-white);
  color: var(--web-green);
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
}
.state {
  text-align: center;
  color: var(--web-text-sub);
  padding: 80px 0;
}
.retry {
  border: 0;
  background: none;
  color: var(--web-green);
  font-weight: 700;
  text-decoration: underline;
  cursor: pointer;
}
.card {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  padding: 22px 26px;
  margin-bottom: 14px;
}
.card.center {
  text-align: center;
  padding: 60px 26px;
}
.primary {
  display: inline-flex;
  height: 48px;
  padding: 0 32px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
  align-items: center;
}
.info {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 15px;
}
.grade {
  padding: 4px 14px;
  border-radius: 999px;
  font-weight: 800;
  background: var(--web-bg-chip);
}
.grade.safe {
  background: var(--web-grade-safe-bg);
  color: var(--web-grade-safe);
}
.grade.caution {
  background: var(--web-grade-caution-bg);
  color: var(--web-grade-caution-fg);
}
.grade.danger {
  background: var(--web-grade-danger-bg);
  color: var(--web-grade-danger-fg);
}
.link {
  margin-left: auto;
  color: var(--web-green);
  font-weight: 700;
}
.notice,
.alert {
  margin-bottom: 14px;
  padding: 14px 18px;
  border-radius: 12px;
  background: var(--web-bg-check);
  font-size: 14px;
  line-height: 1.6;
  color: var(--web-text-prep);
}
.notice b,
.alert b {
  margin-right: 6px;
  color: var(--web-green);
}
.alert.caution {
  background: var(--web-grade-danger-bg);
  color: var(--web-grade-danger-fg);
}
.alert.danger {
  background: var(--web-warn-bg);
  color: var(--web-text-warn);
}
.alert b {
  color: inherit;
}
.wx h2 {
  margin: 0 0 12px;
  font-size: 17px;
}
.days {
  margin: 0;
  padding: 0;
  list-style: none;
}
.days li {
  display: grid;
  grid-template-columns: 90px 1fr 110px 1.2fr;
  gap: 10px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--web-line-card);
  font-size: 14px;
}
.wd {
  font-weight: 700;
}
.tp {
  font-weight: 800;
  font-size: 16px;
}
.tp small {
  font-weight: 400;
  color: var(--web-text-sub);
}
.meta,
.none,
.wx-empty,
.wx-foot {
  color: var(--web-text-sub);
  font-size: 13px;
}
.wx-foot {
  margin: 10px 0 0;
}
.grp {
  padding: 8px 26px;
}
.gt {
  width: 100%;
  padding: 14px 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 17px;
  font-weight: 800;
  color: var(--web-text);
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}
.tag-warn {
  margin-left: 8px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--web-terracotta);
  color: var(--web-white);
  font-size: 12px;
  font-style: normal;
}
.chev {
  width: 9px;
  height: 9px;
  border-right: 2px solid var(--web-arrow);
  border-bottom: 2px solid var(--web-arrow);
  transform: rotate(-135deg);
  transition: transform 0.15s;
}
.chev.shut {
  transform: rotate(45deg);
}
.items {
  margin: 0 0 8px;
  padding: 0;
  list-style: none;
}
.item {
  width: 100%;
  padding: 13px 0;
  border: 0;
  border-top: 1px solid var(--web-line-card);
  background: none;
  font-family: inherit;
  text-align: left;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  cursor: pointer;
  color: var(--web-text);
}
.box {
  flex: none;
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border-radius: 6px;
  border: 2px solid var(--web-check-line);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--web-white);
}
.item.done .box {
  background: var(--web-green);
  border-color: var(--web-green);
}
.tx {
  font-size: 15px;
  line-height: 1.5;
  display: flex;
  flex-direction: column;
}
.tx small {
  font-size: 13px;
  color: var(--web-text-sub);
}
.item.done .tx {
  color: var(--web-text-mute);
  text-decoration: line-through;
}
.foot {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--web-text-mute);
  text-align: center;
}
</style>
