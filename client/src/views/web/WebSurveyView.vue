<script setup>
// 웹 1분 진단 (설계 2-1 설문 · 2-2 결과표): 헤더·하단이 없는 한 화면. 질문 8개에 답하면 결과표가 같은 주소에서 바뀐다
// 질문·유형·점수 계산은 utils/webSurvey.js (설계서 그대로). 추천 캠핑장은 실제 캠핑장 안전점수 순
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import WebIllust from '@/components/web/WebIllust.vue'
import { api } from '@/utils/api'
import { useAuthStore } from '@/stores/auth'
import { ALLQ, KID_OPTS, PARTS, answeredCount, computeResult, emptyAnswers, isReady, loadSavedResult, pickAnswer, saveResult } from '@/utils/webSurvey'
import '@/styles/tokens-web.css'

const auth = useAuthStore()
const step = ref('survey') // survey | result
const A = ref(emptyAnswers())
const tab = ref('recs') // recs | rules | check
const toast = ref('')
let toastTimer = null

const answered = computed(() => answeredCount(A.value))
const ready = computed(() => isReady(A.value))
const pick = (k, v) => (A.value = pickAnswer(A.value, k, v))
const isOn = (q, code) => (q.multi ? A.value[q.k].includes(code) : A.value[q.k] === code)
const qNo = (q) => ALLQ.indexOf(q) + 1
const showKid = (q) => q.k === 'who' && A.value.who.includes('child')

// ---- 결과 ----
const r = ref(null)
const camps = ref([]) // 추천 캠핑장 (실제 데이터)
const more = ref(false)
const openRule = ref(null)
const checked = ref({})

async function submit() {
  if (!ready.value) return
  r.value = computeResult(A.value)
  openRule.value = r.value.rules[0]?.ax ?? null
  checked.value = {}
  tab.value = 'recs'
  step.value = 'result'
  window.scrollTo(0, 0)
  loadCamps()
}
function restart() {
  A.value = emptyAnswers()
  r.value = null
  step.value = 'survey'
  window.scrollTo(0, 0)
}

// 추천 캠핑장: 안전점수 높은 순 5곳 + 그 캠핑장의 가까운 시설 시간 (상세 API)
async function loadCamps() {
  if (camps.value.length) return
  try {
    const list = await api('/api/camps', { params: { sort: 'score' } })
    const top = list.filter((c) => c.safetyScore != null).slice(0, 5)
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
}
const TYPE_LABEL = { auto: '오토캠핑', glamping: '글램핑', backpacking: '노지·백패킹' }
const shownCamps = computed(() => camps.value.slice(0, more.value ? 5 : 3))

// ---- 체크리스트 ----
const toggleCheck = (id) => (checked.value = { ...checked.value, [id]: !checked.value[id] })
const checkTotal = computed(() => (r.value ? r.value.checks.length + r.value.know.length : 0))
const checkDone = computed(() => (r.value ? [...r.value.checks, ...r.value.know].filter((c) => checked.value[c.id]).length : 0))

// ---- 결과 저장 · 알림 ----
function say(m) {
  toast.value = m
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2600)
}
function save() {
  if (!auth.isLoggedIn) return say('로그인하면 결과를 저장할 수 있어요')
  say(saveResult(auth.member.id, A.value, r.value) ? '결과를 저장했어요 (이 브라우저에 저장돼요)' : '저장하지 못했어요')
}
const hadSaved = computed(() => auth.isLoggedIn && !!loadSavedResult(auth.member.id))
const TABS = [['recs', '캠핑장 고르기'], ['rules', '조심할 것'], ['check', '대비할 것']]
</script>

<template>
  <div class="survey">
    <!-- ===== 설문 ===== -->
    <template v-if="step === 'survey'">
      <div class="top">
        <div class="head">
          <span class="pill">1분 안전 진단</span>
          <h1>나의 캠핑 안전 유형 알아보기</h1>
          <p>{{ ALLQ.length }}문항 · 약 1분이면 내가 놓치기 쉬운 안전과 맞는 캠핑장을 알려 드려요</p>
          <p v-if="hadSaved" class="again">저장한 결과가 있어요 · 다시 진단하면 새 결과로 바뀌어요</p>
        </div>
        <WebIllust />
      </div>
      <div class="body">
        <div class="wrap">
          <div class="progress">
            <div class="prow"><span>{{ answered }} / {{ ALLQ.length }} 답했어요</span><span>약 1분</span></div>
            <div class="track"><i :style="{ width: (answered / ALLQ.length) * 100 + '%' }"></i></div>
          </div>
          <div class="parts">
            <section v-for="p in PARTS" :key="p.title" class="part">
              <h2>{{ p.title }}</h2>
              <p class="pdesc"><span class="chip" :class="p.chip">{{ p.feature }}</span>{{ p.desc }}</p>
              <div class="qs" :class="{ two: p.qs.every((q) => !q.quiz) }">
                <div v-for="q in p.qs" :key="q.k" class="q">
                  <div class="qt"><span class="no">{{ qNo(q) }}</span>{{ q.t }}<small v-if="q.h">{{ q.h }}</small></div>
                  <div class="opts" :class="{ quiz: q.quiz }">
                    <button v-for="o in q.o" :key="o[0]" type="button" class="opt" :class="{ on: isOn(q, o[0]) }" :aria-pressed="isOn(q, o[0])"
                      @click="pick(q.k, o[0])">{{ o[1] }}<small v-if="!q.quiz && o[2]">{{ o[2] }}</small></button>
                  </div>
                  <div v-if="showKid(q)" class="kid">
                    <b>가장 어린 아이는 몇 살이에요?</b>
                    <div class="kopts">
                      <button v-for="o in KID_OPTS" :key="o[0]" type="button" class="kopt" :class="{ on: A.kid === o[0] }" :aria-pressed="A.kid === o[0]" @click="pick('kid', o[0])">{{ o[1] }}</button>
                    </div>
                  </div>
                </div>
              </div>
            </section>
            <button type="button" class="submit" :disabled="!ready" @click="submit">{{ ready ? '나의 안전 유형 보기' : '모든 질문에 답해 주세요' }}</button>
            <RouterLink to="/web" class="later">나중에 할게요</RouterLink>
          </div>
        </div>
      </div>
    </template>

    <!-- ===== 결과표 ===== -->
    <template v-else-if="r">
      <div class="top">
        <div class="head">
          <span class="pill">1분 진단 결과</span>
          <h1>나의 캠핑 안전 유형</h1>
          <p class="res"><b :class="`c-${r.type.color}`">{{ r.type.lack }}</b>{{ r.lackTail }} <b>‘{{ r.type.nick }}’</b>이 나왔어요</p>
        </div>
        <WebIllust />
      </div>
      <div class="body">
        <div class="rwrap">
          <!-- 유형 카드 -->
          <div class="tcard">
            <div class="tinfo">
              <span class="risk" :class="`bg-${r.type.color}`">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3 2 20h20zM12 10v4M12 17h.01" /></svg>{{ r.type.risk }}
              </span>
              <h2 :class="`c-${r.type.color}`">{{ r.type.nick }}</h2>
              <p class="catch">{{ r.type.catch }}</p>
              <p class="who">{{ r.who }} · {{ r.siteName }}</p>
              <div class="kws"><span v-for="k in r.keywords" :key="k">{{ k }}</span></div>
            </div>
            <div class="bars">
              <div v-for="b in r.bars" :key="b.label" class="bar">
                <span class="bl">{{ b.label }}</span>
                <span class="bt">
                  <i class="grid"></i>
                  <i class="fill" :style="{ width: b.width + '%', backgroundSize: (10000 / b.width).toFixed(1) + '% 100%' }"></i>
                </span>
                <span class="bv">{{ b.v }}</span>
              </div>
            </div>
          </div>

          <!-- 탭 -->
          <div class="tabwrap">
            <div class="tabs" role="tablist">
              <button v-for="(t, i) in TABS" :key="t[0]" type="button" role="tab" :aria-selected="tab === t[0]" :class="{ on: tab === t[0] }" @click="tab = t[0]">
                <span class="tn">{{ i + 1 }}</span>{{ t[1] }}
              </button>
            </div>

            <!-- 조심할 것 -->
            <section v-if="tab === 'rules'" class="panel">
              <div class="ph"><h3>{{ r.type.nick }}은 이것부터 조심해요</h3><span>내 답변에서 위험이 큰 순서예요</span></div>
              <div v-for="ru in r.rules" :key="ru.ax" class="rule">
                <button type="button" class="rbtn" :aria-expanded="openRule === ru.ax" @click="openRule = openRule === ru.ax ? null : ru.ax">
                  <span class="emoji" aria-hidden="true">{{ ru.emoji }}</span>
                  <span class="rt"><span class="rtitle">{{ ru.title }}</span><span class="rtag">{{ ru.tag }}</span></span>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
                    :style="{ transform: openRule === ru.ax ? 'rotate(180deg)' : 'none' }"><path d="m6 9 6 6 6-6" /></svg>
                </button>
                <p v-if="openRule === ru.ax" class="rbody">{{ ru.body }}</p>
              </div>
              <div class="call119">
                <span class="emoji" aria-hidden="true">📞</span>
                <span><b>위급하면 바로 119</b><span>캠핑장 이름 · 사이트 번호부터 말해요</span></span>
              </div>
            </section>

            <!-- 대비할 것 -->
            <section v-else-if="tab === 'check'" class="panel">
              <div class="ph"><h3>이렇게 대비해요</h3><span>눌러서 체크해요 · {{ checkDone }}/{{ checkTotal }} 완료</span></div>
              <div class="cgrid">
                <div class="cgroup">
                  <b>출발 전에 챙겨요</b><span>내 유형에 필요한 용품이에요</span>
                  <button v-for="c in r.checks" :key="c.id" type="button" role="checkbox" :aria-checked="!!checked[c.id]" class="ck" :class="{ on: checked[c.id] }" @click="toggleCheck(c.id)">
                    <span class="box"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg></span>
                    <span class="ct">{{ c.text }}</span><span v-if="c.must" class="must">꼭</span>
                  </button>
                </div>
                <div class="cgroup">
                  <b>미리 알아둬요</b><span>떠나기 전에 한 번 읽어 두세요</span>
                  <button v-for="c in r.know" :key="c.id" type="button" role="checkbox" :aria-checked="!!checked[c.id]" class="ck" :class="{ on: checked[c.id] }" @click="toggleCheck(c.id)">
                    <span class="box"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg></span>
                    <span class="ct">{{ c.text }}</span><span v-if="c.must" class="must">꼭</span>
                  </button>
                </div>
              </div>
            </section>

            <!-- 캠핑장 고르기 -->
            <section v-else class="panel">
              <h3 class="rech">{{ r.type.recWhy }}으로 골랐어요</h3>
              <p v-if="!camps.length" class="state">추천 캠핑장을 불러오는 중이에요...</p>
              <RouterLink v-for="(c, i) in shownCamps" :key="c.id" :to="`/web/camps/${c.id}`" class="rec">
                <span class="rphoto">
                  <img v-if="c.imageUrl" :src="c.imageUrl" :alt="`${c.name} 전경`" />
                  <span class="rrank">{{ i + 1 }}</span>
                </span>
                <span class="rinfo">
                  <span class="rname"><b>{{ c.name }}</b><span>{{ [TYPE_LABEL[c.campType] || c.campType, c.eupmyeon].filter(Boolean).join(' · ') }}</span></span>
                  <span v-if="c.headline" class="rreason">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>{{ c.headline }}
                  </span>
                  <span class="rchips">
                    <span v-if="c.fire != null" class="c1">119 약 {{ c.fire }}분</span>
                    <span v-if="c.er != null" class="c2">응급실 약 {{ c.er }}분</span>
                    <span v-if="c.police != null" class="c3">파출소 약 {{ c.police }}분</span>
                  </span>
                </span>
                <span class="rscore"><span>추천</span><b>{{ c.safetyScore }}<small>점</small></b></span>
              </RouterLink>
              <button v-if="camps.length > 3" type="button" class="morebtn" @click="more = !more">{{ more ? '접기' : `추천 캠핑장 ${camps.length - 3}곳 더보기` }}</button>
            </section>
          </div>

          <div class="actions">
            <button type="button" class="save" @click="save">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>결과 저장
            </button>
            <RouterLink to="/web/explore" class="go">안전지도에서 확인하기
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
            </RouterLink>
          </div>
          <div class="links">
            <button type="button" @click="restart">다시 진단하기</button>
            <RouterLink to="/web">메인으로</RouterLink>
          </div>
        </div>
      </div>
    </template>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </div>
</template>

<style scoped>
.survey {
  min-height: 100dvh;
  background: var(--web-bg);
  color: var(--web-text);
  word-break: keep-all;
  overflow-wrap: break-word;
}
.top {
  background: var(--web-bg);
}
.head {
  max-width: 1040px;
  margin: 0 auto;
  padding: 56px 20px 0;
  text-align: center;
}
.pill {
  display: inline-flex;
  font-size: 15px;
  font-weight: 700;
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--web-best);
  color: var(--web-green);
}
.head h1 {
  margin: 8px 0 0;
  font-size: 52px;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: -2px;
}
.head p {
  margin: 14px auto 0;
  font-size: 20px;
  color: var(--web-text-sub);
}
.head p.res {
  font-size: 21px;
  color: var(--web-text-mid);
}
.head p.again {
  font-size: 15px;
  color: var(--web-green);
}
.top :deep(.art) {
  padding-top: 24px;
}
.body {
  border-top: 2px solid var(--web-green);
  background: var(--web-bg-quiet);
  min-height: 600px;
}
.wrap {
  max-width: 1040px;
  margin: 0 auto;
  padding: 18px 20px 0;
}
.progress {
  padding: 10px 0;
}
.prow {
  display: flex;
  justify-content: space-between;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--web-text-sub);
}
.track {
  height: 7px;
  border-radius: 4px;
  background: var(--web-track);
  overflow: hidden;
  margin-top: 6px;
}
.track i {
  display: block;
  height: 100%;
  background: var(--web-green);
  transition: width 0.2s ease;
}
.parts {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 0 40px;
}
.part {
  background: var(--web-bg);
  border: 1px solid var(--web-border);
  border-radius: 20px;
  padding: 24px 30px 10px;
}
.part h2 {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.6px;
}
.pdesc {
  margin: 8px 0 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  color: var(--web-text-sub);
}
.chip {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 700;
}
.chip.map { background: var(--web-green); color: var(--web-white); }
.chip.meta { background: var(--web-terracotta); color: var(--web-white); }
.chip.flood { background: var(--web-sky); color: var(--web-text); }
.qs {
  display: grid;
  grid-template-columns: 1fr;
  column-gap: 36px;
}
.qs.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.q {
  padding: 14px 0 16px;
  border-top: 1px solid var(--web-line-card);
}
.qs .q:first-child,
.qs.two .q:nth-child(2) {
  border-top: 0;
}
.qt {
  font-weight: 700;
  font-size: 18px;
  letter-spacing: -0.3px;
}
.qt small {
  font-weight: 500;
  color: var(--web-text-sub);
  font-size: 12.5px;
  margin-left: 6px;
}
.no {
  color: var(--web-green);
  margin-right: 6px;
}
.opts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}
.opt {
  padding: 10px 18px;
  border: 1.5px solid var(--web-line-input);
  border-radius: 999px;
  background: var(--web-bg);
  cursor: pointer;
  font-family: inherit;
  font-size: 16px;
  line-height: 1.45;
  text-align: left;
  color: var(--web-text);
}
.opt small {
  color: var(--web-text-sub);
  font-weight: 500;
  font-size: 12px;
  margin-left: 4px;
}
.opts.quiz .opt {
  flex: 1 1 0;
  min-height: 64px;
  padding: 14px 18px;
  border-radius: 14px;
}
.opt.on {
  border-color: var(--web-green);
  background: var(--web-best);
  font-weight: 700;
}
.kid {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--web-best);
}
.kid b {
  font-size: 15px;
  color: var(--web-green);
}
.kopts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.kopt {
  padding: 9px 14px;
  border: 1.5px solid var(--web-line-input);
  border-radius: 999px;
  background: var(--web-bg);
  cursor: pointer;
  font-family: inherit;
  font-size: 14.5px;
  color: var(--web-text);
}
.kopt.on {
  border-color: var(--web-green);
  background: var(--web-best);
  font-weight: 700;
}
.submit {
  width: 100%;
  padding: 14px 18px;
  border-radius: 10px;
  border: 1.5px solid var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.submit:disabled {
  opacity: 0.4;
  cursor: default;
}
.later {
  align-self: center;
  color: var(--web-text-sub);
  font-size: 14px;
  text-decoration: underline;
}

/* ---- 결과 ---- */
.rwrap {
  max-width: 880px;
  margin: 0 auto;
  padding: 32px 20px 64px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.c-water { color: var(--web-water); }
.c-fire { color: var(--web-terracotta); }
.c-prep { color: var(--web-prep); }
.c-report { color: var(--web-grade-danger); }
.c-safe { color: var(--web-green); }
.bg-water { background: var(--web-water); }
.bg-fire { background: var(--web-terracotta); }
.bg-prep { background: var(--web-prep); }
.bg-report { background: var(--web-grade-danger); }
.bg-safe { background: var(--web-green); }
.tcard {
  background: var(--web-bg);
  border: 1px solid var(--web-border);
  border-radius: 22px;
  padding: 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.risk {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 999px;
  color: var(--web-white);
  font-size: 15px;
  font-weight: 800;
}
.tinfo {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.tinfo h2 {
  margin: 8px 0 0;
  font-size: 54px;
  line-height: 1.1;
  font-weight: 900;
  letter-spacing: -2px;
}
.catch {
  margin: 10px 0 0;
  font-size: 18px;
}
.who {
  margin: 6px 0 0;
  font-size: 15px;
  color: var(--web-text-sub);
}
.kws {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kws span {
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--web-hash-bg);
  font-size: 14px;
}
.bars {
  padding-top: 20px;
  border-top: 1px solid var(--web-line);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.bar {
  display: grid;
  grid-template-columns: 80px 1fr 36px;
  align-items: center;
  gap: 12px;
}
.bl {
  font-size: 16px;
  font-weight: 500;
}
.bv {
  font-size: 16px;
  font-weight: 700;
  text-align: right;
}
.bt {
  position: relative;
  height: 16px;
  border-radius: 999px;
  background: var(--web-bar-bg);
  overflow: hidden;
}
.bt i {
  position: absolute;
}
.bt .grid {
  inset: 0;
  background-image: repeating-linear-gradient(90deg, transparent 0 calc(20% - 2px), var(--web-white) calc(20% - 2px) 20%);
}
.bt .fill {
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 999px;
  background-image: linear-gradient(90deg, var(--web-brand-1) 0%, var(--web-brand-2) 25%, var(--web-brand-3) 50%, var(--web-brand-4) 75%, var(--web-brand-5) 100%);
  background-repeat: no-repeat;
}
.tabwrap {
  display: flex;
  flex-direction: column;
}
.tabs {
  display: flex;
  gap: 8px;
  padding: 0 14px;
}
.tabs button {
  flex: 1;
  height: 62px;
  border: 0;
  border-radius: 18px 18px 0 0;
  cursor: pointer;
  font-family: inherit;
  background: var(--web-track);
  color: var(--web-text-sub);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 500;
}
.tabs button.on {
  background: var(--web-bg);
  color: var(--web-text);
  font-weight: 800;
}
.tn {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(28, 29, 27, 0.12);
  color: var(--web-text-sub);
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tabs button.on .tn {
  background: var(--web-terracotta);
  color: var(--web-white);
}
.panel {
  background: var(--web-bg);
  border: 1px solid var(--web-border);
  border-radius: 22px;
  padding: 34px 40px 30px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 560px;
}
.ph {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ph h3 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.6px;
}
.ph span {
  font-size: 15px;
  color: var(--web-text-sub);
}
.rule {
  border: 1px solid var(--web-border);
  border-radius: 20px;
  background: var(--web-bg-card);
}
.rbtn {
  width: 100%;
  padding: 20px 24px;
  border: 0;
  background: none;
  display: flex;
  align-items: center;
  gap: 18px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: var(--web-text-sub);
}
.emoji {
  flex: 0 0 auto;
  width: 56px;
  height: 56px;
  border-radius: 999px;
  background: var(--web-rule-icon-bg);
  font-size: 28px;
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
  gap: 6px;
}
.rtitle {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.4px;
  color: var(--web-text);
}
.rtag {
  align-self: flex-start;
  padding: 3px 12px;
  border-radius: 999px;
  background: var(--web-rule-tag-bg);
  color: var(--web-text-sub);
  font-size: 14px;
  font-weight: 700;
}
.rbody {
  margin: 0;
  padding: 0 24px 22px 98px;
  font-size: 16px;
  line-height: 1.7;
  color: var(--web-text-mid);
}
.call119 {
  border: 2px solid var(--web-prep);
  border-radius: 20px;
  background: var(--web-bg);
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 18px;
}
.call119 .emoji {
  background: var(--web-report-bg);
}
.call119 span span {
  display: block;
  font-size: 16px;
  color: var(--web-text-sub);
  margin-top: 4px;
}
.call119 b {
  font-size: 21px;
  font-weight: 800;
}
.cgrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}
.cgroup {
  border: 1px solid var(--web-border);
  border-radius: 20px;
  background: var(--web-bg-card);
  padding: 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cgroup > b {
  font-size: 21px;
  font-weight: 800;
}
.cgroup > span {
  margin-bottom: 4px;
  font-size: 15px;
  color: var(--web-text-sub);
}
.ck {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--web-border);
  border-radius: 12px;
  background: var(--web-bg);
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: var(--web-text);
}
.box {
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 2px solid var(--web-line-ghost);
  background: var(--web-bg);
  color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ck.on .box {
  border-color: var(--web-green);
  background: var(--web-green);
  color: var(--web-white);
}
.ct {
  flex: 1;
  min-width: 0;
  font-size: 17px;
}
.ck.on .ct {
  color: var(--web-arrow);
  text-decoration: line-through;
}
.must {
  flex: 0 0 auto;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--web-grade-danger-bg);
  color: var(--web-grade-danger-fg);
  font-size: 13px;
  font-weight: 700;
}
.rech {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
.state {
  margin: 0;
  font-size: 15px;
  color: var(--web-text-sub);
}
.rec {
  border: 1px solid var(--web-border);
  border-radius: 20px;
  padding: 16px 22px 16px 16px;
  background: var(--web-bg-card);
  display: flex;
  align-items: center;
  gap: 20px;
  color: var(--web-text);
}
.rphoto {
  position: relative;
  flex: 0 0 auto;
  width: 150px;
  height: 108px;
  border-radius: 14px;
  overflow: hidden;
  background: var(--web-photo);
}
.rphoto img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rrank {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 14px;
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
  gap: 8px;
}
.rname {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.rname b {
  font-size: 21px;
  letter-spacing: -0.4px;
}
.rname span {
  font-size: 15px;
  color: var(--web-text-sub);
}
.rreason {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 800;
  color: var(--web-green);
}
.rchips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 14px;
  font-weight: 700;
}
.rchips span {
  padding: 4px 10px;
  border-radius: 999px;
}
.c1 { background: var(--web-grade-danger-bg); color: var(--web-grade-danger-fg); }
.c2 { background: var(--web-warn-ink-bg); color: var(--web-warn-ink); }
.c3 { background: var(--web-best); color: var(--web-green); }
.rscore {
  flex: 0 0 auto;
  width: 92px;
  height: 92px;
  border-radius: 18px;
  background: linear-gradient(160deg, var(--web-rec-from) 0%, var(--web-rec-mid) 52%, var(--web-rec-to) 100%);
  color: var(--web-white);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.rscore span {
  font-size: 14px;
  font-weight: 700;
  opacity: 0.95;
}
.rscore b {
  font-size: 32px;
  line-height: 1;
  font-weight: 900;
}
.rscore small {
  font-size: 15px;
}
.morebtn {
  height: 54px;
  border-radius: 999px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.actions {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 12px;
}
.save {
  height: 64px;
  border-radius: 999px;
  border: 1.5px solid var(--web-terracotta);
  background: var(--web-bg);
  color: var(--web-warn-ink);
  font-family: inherit;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.go {
  height: 64px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 19px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.links {
  display: flex;
  justify-content: center;
  gap: 24px;
  font-size: 16px;
}
.links button,
.links a {
  border: 0;
  background: none;
  padding: 0;
  color: var(--web-text-sub);
  text-decoration: underline;
  text-underline-offset: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 16px;
}
.toast {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  background: var(--web-toast);
  color: var(--web-toast-fg);
  padding: 11px 18px;
  border-radius: 10px;
  font-size: 14px;
  z-index: 30;
}
</style>
