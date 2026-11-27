<script setup>
// 1분 진단 (앱). 웹(views/web/WebSurveyView.vue)과 같은 흐름 · 같은 디자인을 휴대폰 폭에 맞춘 것 (2026-11-27 웹 기준으로 통일)
//  - 설문: 한 화면에 질문 8개 (묶음 카드 2개) + 위 "n / 8 답했어요" 진행 막대 + 아래 "나의 안전 유형 보기"
//  - 결과: 유형 캐릭터 카드 → 탭 3개 (캠핑장 고르기 · 조심할 것 · 대비할 것) → 지도에서 확인 · 다시 진단
// 질문 · 유형 · 점수 계산은 utils/webSurvey.js 를 웹과 같이 쓴다 (웹 · 앱이 항상 같은 질문 · 같은 결과)
// 답은 stores/survey.js 에 저장 (탐색 지도의 "내 유형 점수"가 읽는다). 다시 진단할 때는 이전 답을 채워서 연다
// 웹의 큰 일러스트(WebIllust)는 휴대폰 폭에서 질문을 아래로 밀어내서 뺐다
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { goUp, pushBack } from '@/utils/backStack'
import { api } from '@/utils/api'
import { useAuthStore } from '@/stores/auth'
import { useSurveyStore } from '@/stores/survey'
import {
  ALLQ, KID_OPTS, PARTS, answeredCount, campTypeOf, computeResult, emptyAnswers, isReady,
  needsErBonus, pickAnswer, saveResult,
} from '@/utils/webSurvey'

const auth = useAuthStore()
const survey = useSurveyStore()
const router = useRouter()
const route = useRoute()
// 탐색 화면 팝업에서 왔으면(?from=explore) 나갈 때 탐색으로, 아니면 내 정보로
const backTo = computed(() => (route.query.from === 'explore' ? '/app/explore' : '/app/my-info'))

const scroller = ref(null) // 화면 안 스크롤 칸 (단계가 바뀌면 맨 위로)
const step = ref('survey') // survey | result
const A = ref(emptyAnswers())
const error = ref('')
const toast = ref('')
let toastTimer = null

onMounted(async () => {
  try {
    await survey.load(auth.isLoggedIn)
  } catch (e) {
    error.value = e.message
  }
  if (survey.answers) A.value = { ...emptyAnswers(), ...survey.answers, who: [...survey.answers.who] }
  // 내 정보의 "결과 다시 보기"(?view=result): 저장된 답으로 결과 화면부터 연다 (뒤로 → 내 정보)
  if (route.query.view === 'result' && isReady(A.value)) showResult(false)
})

const answered = computed(() => answeredCount(A.value))
const ready = computed(() => isReady(A.value))
const pick = (k, v) => (A.value = pickAnswer(A.value, k, v))
const isOn = (q, code) => (q.multi ? A.value[q.k].includes(code) : A.value[q.k] === code)
const qNo = (q) => ALLQ.indexOf(q) + 1
const showKid = (q) => q.k === 'who' && A.value.who.includes('child')

function toTop() {
  nextTick(() => scroller.value?.scrollTo(0, 0))
}

// ---- 결과 ----
const r = ref(null)
const tab = ref('recs') // recs | rules | check
const camps = ref([])
const campsLoaded = ref(false)
const more = ref(false)
const openRule = ref(null)
const checked = ref({})
let releaseResult = null // 결과로 넘어갈 때 쌓은 뒤로가기 칸 (휴대폰 뒤로가기 → 설문으로)

let openedAsResult = false // 내 정보에서 결과부터 열었는지 (그러면 뒤로 = 내 정보)
function submit() {
  if (!ready.value) return
  survey.save(A.value, auth.isLoggedIn).catch(() => {}) // 로그인했으면 이 브라우저에 사용자별로, 아니면 이 탭에만
  showResult(true)
}
// 결과 화면 열기. stacked: 설문에서 넘어온 경우 휴대폰 뒤로가기 칸을 쌓아 설문으로 돌아가게
function showResult(stacked) {
  r.value = computeResult(A.value)
  openRule.value = r.value.rules[0]?.ax ?? null
  checked.value = {}
  tab.value = 'recs'
  step.value = 'result'
  openedAsResult = !stacked
  if (stacked) {
    releaseResult = pushBack(() => {
      releaseResult = null
      backToSurvey()
    })
  }
  toTop()
  loadCamps(campTypeOf(A.value))
}
// 결과 → 설문 (고른 답은 그대로 두어 바꿀 것만 고치면 된다)
function backToSurvey() {
  camps.value = []
  campsLoaded.value = false
  more.value = false
  r.value = null
  step.value = 'survey'
  toTop()
}
function restart() {
  releaseResult?.()
  releaseResult = null
  openedAsResult = false
  backToSurvey()
}
onBeforeUnmount(() => {
  releaseResult?.()
  clearTimeout(toastTimer)
})

// 위 왼쪽 뒤로: 결과 → 설문, 설문 → 들어온 화면
function goBack() {
  if (step.value === 'result' && !openedAsResult) return restart()
  goUp(router, backTo.value)
}

// 추천 캠핑장 (웹과 같은 규칙): "어디서 주무세요?" 답에 맞는 유형만, 안전점수 높은 순 5곳 + 가까운 시설 시간
// 내 유형 점수(응급실 가산점)를 켜 뒀고 해당 답이 있으면 가산점을 더한 순 · 더한 점수
async function loadCamps(campType) {
  if (campsLoaded.value) return
  const useBonus = needsErBonus(A.value) && survey.bonusOn
  try {
    const list = await api('/api/camps', { params: { sort: useBonus ? 'bonus' : 'score', ...(campType ? { campType } : {}) } })
    const top = list
      .filter((c) => c.safetyScore != null)
      .slice(0, 5)
      .map((c) => (useBonus && c.erBonus ? { ...c, safetyScore: Math.min(100, c.safetyScore + c.erBonus) } : c))
    camps.value = await Promise.all(top.map(async (c) => {
      try {
        const d = await api(`/api/camps/${c.id}`)
        const min = (type) => d.facilities.filter((f) => f.type === type).reduce((m, f) => Math.min(m, f.driveMin), Infinity)
        const t = (v) => (Number.isFinite(v) ? v : null)
        return { ...c, headline: d.headline, fire: t(min('fire')), er: t(min('er')), police: t(min('police')) }
      } catch {
        return { ...c } // 상세를 못 받아도 카드는 보인다
      }
    }))
  } catch {
    camps.value = []
  }
  campsLoaded.value = true
}
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' }
const shownCamps = computed(() => camps.value.slice(0, more.value ? 5 : 3))

// 캐릭터: 그림을 못 불러오면 이모지 글자로. 누르면 다음 팁 (마지막 다음은 처음) + 젤리처럼 출렁
const imgFailed = ref(false)
const tipIdx = ref(0)
const tapped = ref(false)
const jumping = ref(false)
function nextTip() {
  tapped.value = true
  tipIdx.value = (tipIdx.value + 1) % r.value.type.tips.length
  jumping.value = false
  requestAnimationFrame(() => (jumping.value = true))
}
const onFaceAnimEnd = (e) => { if (e.animationName.includes('char-jump')) jumping.value = false }
watch(r, () => {
  imgFailed.value = false
  tipIdx.value = 0
  tapped.value = false
})

const toggleCheck = (id) => (checked.value = { ...checked.value, [id]: !checked.value[id] })
const mustFirst = (list) => [...list.filter((c) => c.must), ...list.filter((c) => !c.must)]
const TABS = [['recs', '캠핑장 고르기'], ['rules', '조심할 것'], ['check', '대비할 것']]

function say(m) {
  toast.value = m
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2600)
}
function save() {
  if (!auth.isLoggedIn) return say('로그인하면 결과를 저장할 수 있어요')
  say(saveResult(auth.member.id, A.value, r.value) ? '결과를 저장했어요 (이 기기에 저장돼요)' : '저장하지 못했어요')
}
</script>

<template>
  <div ref="scroller" class="survey">
    <!-- 위 줄: 뒤로 + (설문일 때) 진행 막대. 스크롤해도 늘 보이게 -->
    <div class="topbar">
      <button type="button" class="backbtn" :aria-label="step === 'result' ? '설문으로 돌아가기' : '뒤로'" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>
      </button>
      <div v-if="step === 'survey'" class="progress">
        <div class="prow"><span>{{ answered }} / {{ ALLQ.length }} 답했어요</span><span>약 1분</span></div>
        <div class="track"><i :style="{ width: (answered / ALLQ.length) * 100 + '%' }" /></div>
      </div>
      <b v-else class="toptitle">1분 진단 결과</b>
    </div>

    <!-- ===== 설문 ===== -->
    <template v-if="step === 'survey'">
      <div class="head">
        <span class="pill">1분 안전 진단</span>
        <h1>나의 캠핑 안전 유형 알아보기</h1>
      </div>
      <div class="body">
        <p v-if="error" class="warn" role="alert">{{ error }}</p>
        <section v-for="p in PARTS" :key="p.title" class="part">
          <h2>{{ p.title }}</h2>
          <p class="pdesc">{{ p.desc }}</p>
          <div v-for="q in p.qs" :key="q.k" class="q">
            <div class="qt"><span class="no">{{ qNo(q) }}</span>{{ q.t }}<small v-if="q.h">{{ q.h }}</small></div>
            <div class="opts" :class="{ quiz: q.quiz }">
              <button v-for="o in q.o" :key="o[0]" type="button" class="opt" :class="{ on: isOn(q, o[0]) }"
                :aria-pressed="isOn(q, o[0])" @click="pick(q.k, o[0])">
                {{ o[1] }}<small v-if="!q.quiz && o[2]">{{ o[2] }}</small>
              </button>
            </div>
            <div v-if="showKid(q)" class="kid">
              <b>가장 어린 아이는 몇 살이에요?</b>
              <div class="opts">
                <button v-for="o in KID_OPTS" :key="o[0]" type="button" class="opt small" :class="{ on: A.kid === o[0] }"
                  :aria-pressed="A.kid === o[0]" @click="pick('kid', o[0])">{{ o[1] }}</button>
              </div>
            </div>
          </div>
        </section>
        <RouterLink :to="backTo" class="later" @click.capture.prevent="goUp(router, backTo)">나중에 할게요</RouterLink>
      </div>
      <!-- 아래 고정: 다 답하면 결과 보기 -->
      <div class="foot">
        <button type="button" class="submit" :disabled="!ready" @click="submit">
          {{ ready ? '나의 안전 유형 보기' : '모든 질문에 답해 주세요' }}
        </button>
      </div>
    </template>

    <!-- ===== 결과 ===== -->
    <template v-else-if="r">
      <div class="head">
        <h1>나의 캠핑 안전 유형</h1>
        <p class="res"><b :class="`c-${r.type.color}`">{{ r.type.ax }}</b>를 가장 조심해야 하는 <b>‘{{ r.type.nick }}’</b> 유형이에요</p>
      </div>
      <div class="body">
        <!-- 유형 카드: 캐릭터 → 이름 → 성격 말풍선 → 행동 말풍선 → 주의 꼬리표 · 해시태그 (웹의 좁은 화면 배치와 같음) -->
        <div class="tcard">
          <button type="button" class="face" :class="{ jump: jumping }" :aria-label="`${r.type.nick} 누르면 다음 안전 팁`"
            @click="nextTip" @animationend="onFaceAnimEnd">
            <img v-if="!imgFailed" :src="r.type.img" alt="" width="150" height="150" @error="imgFailed = true" />
            <span v-else aria-hidden="true">{{ r.type.emoji }}</span>
          </button>
          <h2 class="cname" :class="`c-${r.type.color}`">{{ r.type.nick }}<small v-if="!tapped" class="tap-hint">캐릭터를 눌러 보세요</small></h2>
          <p class="bubble b-trait" :class="`tint-${r.type.color}`">{{ r.type.trait }}</p>
          <p :key="tipIdx" class="bubble b-act" :class="[`fill-${r.type.color}`, { again: tipIdx > 0 || tapped }]" aria-live="polite">{{ r.type.tips[tipIdx] }}</p>
          <div class="meta">
            <span class="risk" :class="`bg-${r.type.color}`">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true"><path d="M12 3 2 20h20zM12 10v4M12 17h.01" /></svg>{{ r.type.risk }}
            </span>
            <div class="kws"><span v-for="k in r.keywords" :key="k">{{ k }}</span></div>
          </div>
        </div>

        <!-- 탭 -->
        <div class="tabwrap">
          <div class="tabs" role="tablist">
            <button v-for="(t, i) in TABS" :key="t[0]" type="button" role="tab" :aria-selected="tab === t[0]"
              :class="{ on: tab === t[0] }" @click="tab = t[0]"><span class="tn">{{ i + 1 }}</span>{{ t[1] }}</button>
          </div>

          <!-- 조심할 것 -->
          <section v-if="tab === 'rules'" class="panel">
            <h3>이것부터 조심하세요</h3>
            <div v-for="ru in r.rules" :key="ru.ax" class="rule">
              <button type="button" class="rbtn" :aria-expanded="openRule === ru.ax" @click="openRule = openRule === ru.ax ? null : ru.ax">
                <span class="emoji" aria-hidden="true">{{ ru.emoji }}</span>
                <span class="rt"><span class="rtitle">{{ ru.title }}</span><span class="rtag">{{ ru.tag }}</span></span>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"
                  stroke-linejoin="round" aria-hidden="true" :style="{ transform: openRule === ru.ax ? 'rotate(180deg)' : 'none' }"><path d="m6 9 6 6 6-6" /></svg>
              </button>
              <p v-if="openRule === ru.ax" class="rbody">{{ ru.body }}</p>
            </div>
            <div class="call119">
              <span class="emoji" aria-hidden="true">📞</span>
              <span><b>위급하면 바로 119</b><span>캠핑장 이름 · 사이트 번호부터 말해요</span></span>
            </div>
          </section>

          <!-- 대비할 것 -->
          <section v-else-if="tab === 'check'" class="panel" :class="`tone-${r.type.color}`">
            <h3>이렇게 대비하세요</h3>
            <div v-for="g in [['출발 전에 챙겨요', r.checks], ['미리 알아둬요', r.know]]" :key="g[0]" class="cgroup">
              <b>{{ g[0] }}</b>
              <button v-for="c in mustFirst(g[1])" :key="c.id" type="button" role="checkbox" :aria-checked="!!checked[c.id]" class="ck"
                :class="{ on: checked[c.id], must: c.must }" @click="toggleCheck(c.id)">
                <span class="box"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2"
                  stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg></span>
                <span class="ct">{{ c.text }}<span v-if="c.must" class="sr-only"> (내 유형에 특히 중요)</span></span>
                <span v-if="c.must && !checked[c.id]" class="mark" aria-hidden="true">!</span>
              </button>
            </div>
          </section>

          <!-- 캠핑장 고르기 -->
          <section v-else class="panel">
            <h3>안전점수 높은 순으로 추천</h3>
            <p v-if="!campsLoaded" class="state">추천 캠핑장을 불러오는 중이에요...</p>
            <p v-else-if="!camps.length" class="state">고른 방식에 맞는 캠핑장을 찾지 못했어요</p>
            <RouterLink v-for="(c, i) in shownCamps" :key="c.id" :to="`/app/camps/${c.id}`" class="rec">
              <span class="rphoto">
                <img v-if="c.imageUrl" :src="c.imageUrl" :alt="`${c.name} 전경`" loading="lazy" />
                <span class="rrank">{{ i + 1 }}</span>
              </span>
              <span class="rinfo">
                <b class="rname">{{ c.name }}</b>
                <span class="rmeta">{{ [TYPE_LABEL[c.campType] || c.campType, c.eupmyeon].filter(Boolean).join(' · ') }}</span>
                <span class="rchips">
                  <span v-if="c.fire != null" class="c1">119 {{ c.fire }}분</span>
                  <span v-if="c.er != null" class="c2">응급실 {{ c.er }}분</span>
                </span>
              </span>
              <span class="rscore"><span>추천</span><b>{{ c.safetyScore }}</b></span>
            </RouterLink>
            <button v-if="camps.length > 3" type="button" class="morebtn" @click="more = !more">
              {{ more ? '접기' : `추천 캠핑장 ${camps.length - 3}곳 더보기` }}
            </button>
          </section>
        </div>

        <div class="actions">
          <button type="button" class="save" @click="save">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"
              aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>결과 저장
          </button>
          <RouterLink to="/app/explore" class="go">지도에서 확인하기
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"
              stroke-linejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
          </RouterLink>
        </div>
        <div class="links">
          <button type="button" @click="restart">다시 진단하기</button>
          <RouterLink to="/app/my-info">내 정보로</RouterLink>
        </div>
      </div>
    </template>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </div>
</template>

<style scoped>
/* 값은 웹 진단(WebSurveyView.vue)과 같고 크기만 휴대폰에 맞춤. 색은 tokens.css 의 --color-survey-* (웹과 같은 값) */
.survey {
  position: relative;
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  color: var(--color-text);
  word-break: keep-all;
  overflow-wrap: break-word;
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 8px 6px;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
}
.backbtn {
  flex: none;
  width: 44px;
  height: 44px;
  border: 0;
  background: transparent;
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.toptitle {
  font-size: 16px;
}
.progress {
  flex: 1;
}
.prow {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--color-text-sub);
}
.track {
  height: 6px;
  margin-top: 5px;
  border-radius: 3px;
  background: var(--color-survey-track);
  overflow: hidden;
}
.track i {
  display: block;
  height: 100%;
  background: var(--color-primary);
  transition: width 0.2s ease;
}
.head {
  padding: 22px 20px 18px;
  text-align: center;
}
.pill {
  display: inline-flex;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--color-survey-best);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 700;
}
.head h1 {
  margin: 8px 0 0;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 900;
  letter-spacing: -0.6px;
}
.head p {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-text-sub);
}
.head p.res {
  font-size: 15px;
  color: var(--color-text-mid);
}
.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 16px 24px;
  border-top: 2px solid var(--color-primary);
  background: var(--color-survey-quiet);
}
.warn {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
/* ---- 설문 ---- */
.part {
  padding: 18px 18px 6px;
  border: 1px solid var(--color-survey-track);
  border-radius: 18px;
  background: var(--color-card);
}
.part h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.4px;
}
.pdesc {
  margin: 6px 0 4px;
  font-size: 13.5px;
  color: var(--color-text-sub);
}
.q {
  padding: 14px 0 16px;
  border-top: 1px solid var(--color-line);
}
.part h2 + .pdesc + .q {
  border-top: 0;
}
.qt {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.3px;
}
.qt small {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-sub);
}
.no {
  margin-right: 6px;
  color: var(--color-primary);
}
.opts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.opt {
  min-height: 44px;
  padding: 9px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-text);
  font-family: inherit;
  font-size: 15px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
}
.opt small {
  margin-left: 4px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--color-text-sub);
}
/* 퀴즈 보기는 문장이라 한 줄씩 꽉 찬 카드 */
.opts.quiz {
  flex-direction: column;
}
.opts.quiz .opt {
  min-height: 52px;
  padding: 12px 16px;
  border-radius: 14px;
}
.opt.small {
  min-height: 40px;
  padding: 7px 13px;
  font-size: 14px;
}
.opt.on {
  border-color: var(--color-primary);
  background: var(--color-survey-best);
  font-weight: 700;
}
.kid {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-survey-best);
}
.kid b {
  font-size: 14px;
  color: var(--color-primary);
}
.kid .opts {
  margin-top: 8px;
}
.later {
  align-self: center;
  padding: 8px;
  font-size: 13.5px;
  color: var(--color-text-sub);
  text-decoration: underline;
}
.foot {
  position: sticky;
  bottom: 0;
  padding: 10px 16px 16px;
  background: var(--color-bg);
  border-top: 1px solid var(--color-divider);
}
.submit {
  width: 100%;
  height: 54px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.submit:disabled {
  opacity: 0.4;
  cursor: default;
}
/* ---- 결과: 유형 색 ---- */
.c-water { color: var(--color-survey-water); }
.c-fire { color: var(--color-survey-fire); }
.bg-water { background: var(--color-survey-water); }
.bg-fire { background: var(--color-survey-fire); }
.tcard {
  padding: 22px 18px;
  border: 1px solid var(--color-survey-track);
  border-radius: 20px;
  background: var(--color-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
button.face {
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  font-size: 110px;
  line-height: 1;
  filter: drop-shadow(0 10px 12px rgba(0, 0, 0, 0.12));
  opacity: 0;
  animation: char-rise 0.8s cubic-bezier(0.2, 1.5, 0.4, 1) 0.1s forwards;
}
.face img {
  display: block;
  width: 150px;
  height: 150px;
}
.face img,
.face > span {
  animation: char-float 3s ease-in-out 1s infinite;
}
button.face:active img,
button.face:active > span {
  transform: scale(1.06, 0.9);
  transition: transform 0.08s;
}
.face.jump img,
.face.jump > span {
  transform-origin: 50% 90%;
  animation: char-jump 0.6s ease-out;
}
.cname {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 900;
  letter-spacing: -1px;
  text-align: center;
  opacity: 0;
  animation: fade-up 0.5s ease-out 0.55s forwards;
}
.tap-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--color-text-sub);
  animation: hint-blink 1.6s ease-in-out 2s infinite;
}
.bubble {
  max-width: 300px;
  margin: 0;
  padding: 12px 16px;
  border-radius: 20px;
  background: var(--color-card);
  box-shadow: 0 6px 20px rgba(28, 29, 27, 0.08), 0 1px 3px rgba(28, 29, 27, 0.06);
  font-size: 14.5px;
  line-height: 1.55;
  text-align: center;
  text-wrap: balance;
  opacity: 0;
  animation: bubble-pop 0.45s cubic-bezier(0.2, 1.6, 0.4, 1) forwards;
}
.b-trait {
  font-weight: 600;
  animation-delay: 0.75s;
}
.b-trait.tint-fire {
  border: 1.5px solid var(--color-survey-fire);
  background: var(--color-survey-warn-bg);
  color: var(--color-survey-warn-ink);
}
.b-trait.tint-water {
  border: 1.5px solid var(--color-survey-water);
  background: var(--color-survey-sky);
  color: var(--color-survey-water);
}
.b-act {
  font-weight: 700;
  color: var(--color-card);
  background: var(--color-survey-fire);
  box-shadow: 0 8px 22px rgba(187, 105, 73, 0.28);
  animation-delay: 1.05s;
}
.b-act.fill-water {
  background: var(--color-survey-water);
  box-shadow: 0 8px 22px rgba(62, 90, 102, 0.28);
}
.b-act.again {
  animation-delay: 0s;
}
.meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  opacity: 0;
  animation: fade-up 0.5s ease-out 1.3s forwards;
}
.risk {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: 999px;
  color: var(--color-card);
  font-size: 13px;
  font-weight: 800;
}
.kws {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}
.kws span {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--color-survey-hash);
  font-size: 12.5px;
}
@keyframes char-jump {
  0% { transform: scale(1.06, 0.9); }
  30% { transform: scale(0.94, 1.08); }
  50% { transform: scale(1.04, 0.96); }
  70% { transform: scale(0.98, 1.02); }
  100% { transform: scale(1); }
}
@keyframes hint-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
@keyframes char-rise {
  0% { opacity: 0; transform: translateY(60px) scale(0.6); }
  60% { opacity: 1; transform: translateY(-10px) scale(1.06); }
  80% { transform: translateY(4px) scale(0.98); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes char-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
@keyframes bubble-pop {
  0% { opacity: 0; transform: scale(0.4); }
  70% { opacity: 1; transform: scale(1.06); }
  100% { opacity: 1; transform: scale(1); }
}
@keyframes fade-up {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  button.face, .face img, .face > span, .bubble, .cname, .meta, .tap-hint {
    animation: none;
    opacity: 1;
  }
}
/* ---- 결과: 탭 ---- */
.tabwrap {
  display: flex;
  flex-direction: column;
}
.tabs {
  display: flex;
  gap: 4px;
  padding: 0 2px;
}
/* 한 줄로: 글자 길이만큼 폭을 나누고(캠핑장 고르기가 가장 김) 줄바꿈 안 함 */
.tabs button {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0 6px;
  white-space: nowrap;
  height: 48px;
  border: 0;
  border-radius: 14px 14px 0 0;
  background: var(--color-survey-track);
  color: var(--color-text-sub);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.tabs button.on {
  background: var(--color-card);
  color: var(--color-text);
  font-weight: 800;
}
.tn {
  flex: none;
  width: 17px;
  height: 17px;
  border-radius: 999px;
  background: rgba(28, 29, 27, 0.12);
  color: var(--color-text-sub);
  font-size: 11px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tabs button.on .tn {
  background: var(--color-survey-fire);
  color: var(--color-card);
}
.panel {
  min-height: 320px;
  padding: 20px 16px;
  border: 1px solid var(--color-survey-track);
  border-radius: 18px;
  background: var(--color-card);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.4px;
}
.state {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-sub);
}
/* 조심할 것 */
.rule {
  border: 1px solid var(--color-survey-track);
  border-radius: 16px;
  background: var(--color-survey-card);
}
.rbtn {
  width: 100%;
  padding: 14px;
  border: 0;
  background: none;
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  font-family: inherit;
  color: var(--color-text-sub);
  cursor: pointer;
}
.emoji {
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: 999px;
  background: var(--color-survey-rule-icon);
  font-size: 21px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rtitle {
  font-size: 16px;
  font-weight: 800;
  color: var(--color-text);
}
.rtag {
  align-self: flex-start;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--color-survey-rule-tag);
  font-size: 12px;
  font-weight: 700;
}
.rbody {
  margin: 0;
  padding: 0 14px 16px 68px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-mid);
}
.call119 {
  padding: 14px;
  border: 2px solid var(--color-survey-prep);
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.call119 .emoji {
  background: var(--color-survey-report);
}
.call119 b {
  font-size: 16px;
  font-weight: 800;
}
.call119 span span {
  display: block;
  margin-top: 2px;
  font-size: 13.5px;
  color: var(--color-text-sub);
}
/* 대비할 것 */
.cgroup {
  padding: 16px 14px;
  border: 1px solid var(--color-survey-track);
  border-radius: 16px;
  background: var(--color-survey-card);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.cgroup > b {
  font-size: 16px;
  font-weight: 800;
}
.ck {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 8px 12px;
  border: 1px solid var(--color-survey-track);
  border-radius: 12px;
  background: var(--color-card);
  text-align: left;
  font-family: inherit;
  color: var(--color-text);
  cursor: pointer;
}
.box {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  border: 2px solid var(--color-text-light);
  color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ck.on .box {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-card);
}
.ct {
  flex: 1;
  min-width: 0;
  font-size: 14.5px;
}
.ck.on .ct {
  color: var(--color-arrow);
  text-decoration: line-through;
}
/* 내 유형에 특히 중요한 항목: 유형 색(위 캐릭터 카드와 같은 색)으로 테두리 · 옅은 바탕 · 오른쪽 작은 ! 표시
   (예전: 노랑 바탕 + 갈색 왼쪽 띠 + 갈색 굵은 글씨 → 경고 상자처럼 무거워 보여서 바꿈). 체크하면 강조를 거둔다 */
.tone-fire { --tone: var(--color-survey-fire); --tone-bg: var(--color-survey-warn-bg); }
.tone-water { --tone: var(--color-survey-water); --tone-bg: var(--color-survey-sky); }
.ck.must {
  border: 1.5px solid var(--tone);
  background: var(--tone-bg);
}
.ck.must .ct {
  font-weight: 700;
}
.ck.must .box {
  border-color: var(--tone);
  background: var(--color-card);
}
.mark {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--tone);
  color: var(--color-card);
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ck.must.on {
  border: 1px solid var(--color-survey-track);
  background: var(--color-card);
}
.ck.must.on .ct {
  font-weight: 400;
}
.ck.must.on .box {
  border-color: var(--color-primary);
  background: var(--color-primary);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
/* 캠핑장 고르기 */
.rec {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 1px solid var(--color-survey-track);
  border-radius: 16px;
  background: var(--color-survey-card);
  color: var(--color-text);
  text-decoration: none;
}
.rphoto {
  position: relative;
  flex: none;
  width: 76px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-photo);
}
.rphoto img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rrank {
  position: absolute;
  top: 5px;
  left: 5px;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-card);
  font-size: 12px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rinfo {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.rname {
  font-size: 15px;
  line-height: 1.3;
}
.rmeta {
  font-size: 12.5px;
  color: var(--color-text-sub);
}
.rchips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 2px;
  font-size: 11.5px;
  font-weight: 700;
}
.rchips span {
  padding: 2px 8px;
  border-radius: 999px;
}
.c1 { background: var(--color-survey-must-bg); color: var(--color-survey-must); }
.c2 { background: var(--color-survey-warn-bg); color: var(--color-survey-warn-ink); }
.rscore {
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(160deg, var(--color-survey-rec-from) 0%, var(--color-survey-rec-mid) 52%, var(--color-survey-rec-to) 100%);
  color: var(--color-card);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.rscore span {
  font-size: 11px;
  font-weight: 700;
}
.rscore b {
  font-size: 21px;
  line-height: 1;
  font-weight: 900;
}
.morebtn {
  height: 46px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-text);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
/* 아래 버튼 */
.actions {
  display: grid;
  grid-template-columns: 1fr 1.8fr;
  gap: 8px;
}
.save,
.go {
  height: 54px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}
.save {
  border: 1.5px solid var(--color-survey-fire);
  background: var(--color-card);
  color: var(--color-survey-warn-ink);
}
.go {
  background: var(--color-primary);
  color: var(--color-card);
}
.links {
  display: flex;
  justify-content: center;
  gap: 20px;
}
.links button,
.links a {
  padding: 6px 0;
  border: 0;
  background: none;
  color: var(--color-text-sub);
  font-family: inherit;
  font-size: 14px;
  text-decoration: underline;
  text-underline-offset: 4px;
  cursor: pointer;
}
.toast {
  position: absolute;
  left: 50%;
  bottom: 24px;
  z-index: 10;
  transform: translateX(-50%);
  padding: 10px 16px;
  border-radius: 10px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 13.5px;
  white-space: nowrap;
}
</style>
