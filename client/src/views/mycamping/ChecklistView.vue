<script setup>
// 출발 준비 체크리스트 (내 캠핑 카드 → "출발 준비"). 서버 GET /api/member/camp-plans/{planId}/checklist
//  - 이 곳의 안전 정보 · (출발 3일 전부터) 지금 발효 중인 기상특보 · 항목 묶음(특보 대응 · 차박 준비 · 계절 · 공통)
//  - 묶음 제목을 누르면 접고 펼친다 (처음엔 공통 준비물만 접힘)
//  - 체크한 항목은 이 브라우저(localStorage)에만 저장한다 — 서버·DB 에 저장하지 않는다 (항목 id 는 서버가 고정값으로 줌)
// 안전 수치(몇 도 이하 등)는 화면에도 없다: 특보는 기상청이 발표한 이름 그대로, 침낭은 "내 침낭의 쾌적온도 확인"으로 안내
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BottomNav from '@/components/common/BottomNav.vue'
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

// ---- 묶음 접기·펼치기 ----
const open = ref({ 특보: true, 차박: true, 계절: true, 공통: false })
const GROUPS = ['특보', '차박', '계절', '공통']
const groupTitle = (g) => ({ 특보: '특보 대응', 차박: '차박 준비', 계절: `${data.value?.season ?? ''} 준비물`, 공통: '공통 준비물' })[g]
const groups = computed(() =>
  GROUPS.map((g) => ({ key: g, title: groupTitle(g), items: (data.value?.items ?? []).filter((i) => i.group === g) }))
    .filter((g) => g.items.length),
)

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
    ? { to: { path: '/chabak', query: { lat: plan.value.lat, lng: plan.value.lng } }, text: '차박 판정 보기 ›' }
    : { to: `/camps/${plan.value.campId}`, text: '캠핑장 상세 ›' },
)
// 경보는 빨강, 주의보·그 밖은 주황 (기상청 특보 이름에 "경보"가 들어 있으면)
const warnClass = (category) => (category.includes('경보') ? 'danger' : 'caution')
</script>

<template>
  <div class="page">
    <div class="scroll">
      <RouterLink to="/my-camping" class="back">‹ 내 캠핑</RouterLink>

      <div v-if="!auth.isLoggedIn" class="empty">
        <p>출발 준비는 로그인하면 쓸 수 있어요.</p>
        <RouterLink to="/login" class="primary">로그인</RouterLink>
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
            <span v-if="data.safety.erDriveMin != null" class="er">
              응급실 {{ data.safety.erApprox ? '약' : '차로' }} {{ data.safety.erDriveMin }}분
            </span>
            <RouterLink :to="safetyLink.to" class="info-link">{{ safetyLink.text }}</RouterLink>
          </div>
        </div>

        <!-- 특보: 출발 3일 전부터(캠핑 중 포함) 그 지역에 지금 발효 중인 기상특보 -->
        <div v-if="data.past" class="notice">지난 캠핑이에요. 준비물은 참고용으로만 보세요.</div>
        <template v-else-if="data.near">
          <div v-for="w in data.warnings" :key="w.category" class="alert" :class="warnClass(w.category)" role="alert">
            <b>{{ w.category }} 발효 중</b>
            {{ w.message }}
          </div>
          <div v-if="!data.warningsAvailable" class="notice">
            <b>특보 정보를 지금 받지 못했어요</b>
            잠시 뒤 다시 확인하거나 기상청 날씨누리에서 확인해 주세요.
          </div>
          <div v-else-if="!data.warnings.length" class="notice">
            <b>지금 이 지역에 발효 중인 특보가 없어요</b>
            시·도 단위 기상청 특보 현황 기준이에요.
          </div>
        </template>
        <div v-else class="notice">
          <b>특보는 {{ openText }}부터 보여요</b>
          출발 3일 전부터 그 지역의 기상특보를 알려 드려요. 그때까지는 준비물을 미리 챙겨 두세요.
        </div>

        <!-- 항목 묶음 (제목을 누르면 접고 펼침) -->
        <section v-for="g in groups" :key="g.key">
          <button type="button" class="grp" :class="{ shut: !open[g.key] }" :aria-expanded="!!open[g.key]"
            @click="open[g.key] = !open[g.key]">
            {{ g.title }}
            <i v-if="g.key === '특보'" class="tag-warn">특보</i>
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

        <p class="foot">
          체크한 항목은 이 기기(브라우저)에만 저장돼요 · 특보는 기상청 특보 현황 기준 참고용이에요
        </p>
      </template>
    </div>
    <BottomNav active="mycamp" />
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
  margin: 0 0 10px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
}
.info-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.info-link {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text);
}
.grade {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  background: var(--color-bg-soft);
  color: var(--color-text-sub);
}
.grade i {
  width: 7px;
  height: 7px;
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
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
}
.alert b,
.notice b {
  display: block;
  margin-bottom: 2px;
  font-size: 14px;
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
/* 묶음 제목 = 접고 펼치는 버튼 */
.grp {
  width: 100%;
  min-height: 48px;
  margin-top: 8px;
  padding: 0;
  border: none;
  border-bottom: 1px solid var(--color-line);
  background: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}
.tag-warn {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-danger);
  color: var(--color-card);
  font-size: 11px;
  font-style: normal;
}
.chev {
  margin: 0 4px 0 auto;
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
  min-height: 52px;
  padding: 10px 0;
  border: none;
  border-bottom: 1px solid var(--color-line);
  background: none;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.box {
  flex: none;
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border: 2px solid var(--color-border);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.item.done .box {
  border-color: var(--color-primary);
  background: var(--color-primary);
}
.item.done .box::after {
  content: '';
  width: 5px;
  height: 10px;
  border: solid var(--color-card);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg) translate(-1px, -1px);
}
.tx {
  flex: 1;
  min-width: 0;
  font-size: 15px;
}
.tx small {
  display: block;
  margin-top: 1px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.item.done .tx {
  color: var(--color-text-sub);
  text-decoration: line-through;
}
.item.done .tx small {
  text-decoration: none;
}
.foot {
  margin: 14px 0 0;
  font-size: 11px;
  color: var(--color-text-sub);
}
</style>
