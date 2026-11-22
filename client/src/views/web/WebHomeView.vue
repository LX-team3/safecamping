<script setup>
// 웹 메인 (설계 1-1 · 1-1' · 1-2 · 1-5): 히어로 검색 · 기상특보 띠 · 전국 안전 캠핑장 순위 · 메인 기능 · 공지사항
// 캠핑장 이름·점수·공지사항은 설계의 예시 데이터다 (더미) → 서버 데이터를 연결할 때 바꾼다
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import WebGuideModal from '@/components/web/WebGuideModal.vue'
import WebTripModal from '@/components/web/WebTripModal.vue'
import { useTripStore } from '@/stores/trip'
import { shortLabel } from '@/utils/date'
import { shortSido } from '@/utils/place'
import { loadSavedResult } from '@/utils/webSurvey'

const auth = useAuthStore()
// 검색칸(지역 · 날짜 · 인원): 누르면 지역·날짜 팝업이 열리고, 고른 값은 trip store 에 저장돼 탐색 화면에서도 그대로 쓴다
const trip = useTripStore()
const tripOpen = ref(false)
const tripTab = ref('region')
function openTrip(tab) {
  tripTab.value = tab
  tripOpen.value = true
}
const regionText = computed(() => (trip.state.sigungu ? `${shortSido(trip.state.sido?.name)} ${trip.state.sigungu.name}` : trip.state.sido ? shortSido(trip.state.sido.name) : '전국'))
const dateText = computed(() => (trip.state.checkIn && trip.state.checkOut ? `${shortLabel(trip.state.checkIn)} – ${shortLabel(trip.state.checkOut)}` : '날짜 선택'))
const peopleText = computed(() => `성인 ${trip.state.adults}${trip.state.kids ? ` · 아이 ${trip.state.kids}` : ''}`)
// 진단 결과를 저장한 사람만 "내 유형" 영역을 보여 준다 (결과는 이 브라우저에 저장, utils/webSurvey.js)
const savedType = computed(() => (auth.isLoggedIn && auth.member ? loadSavedResult(auth.member.id) : null))

// ---- 예시 데이터 (더미) ----
const MY_CAMPS = [
  { name: '설하캠핑장', score: 90, meta: '오토캠핑 · 인제읍' },
  { name: '북면 솔숲캠핑장', score: 88, meta: '글램핑 · 북면' },
  { name: '내린천 오토캠핑장', score: 85, meta: '오토캠핑 · 인제읍' },
  { name: '기린 계곡캠핑장', score: 82, meta: '카라반 · 기린면' },
]
const NATION = [
  { name: '화성시 향남 오토캠핑장', score: 94 },
  { name: '달궁자동차야영장', score: 92 },
  { name: '덕신야영장', score: 90 },
  { name: '초막골생태공원 느티나무야영장', score: 89 },
  { name: '자라섬 캠핑장', score: 87 },
  { name: '왕송호수 캠핑장', score: 86 },
  { name: '망상오토캠핑리조트', score: 85 },
  { name: '소백산국립공원 남천야영장', score: 84 },
]
const FEATURES = [
  { label: '01 고르고 · 안전점수 지도', title: '안전점수로 내 유형에 맞는 캠핑장 고르기', desc: '안전점수 지도에서 우리 가족 유형에 맞는 캠핑장을 골라요.', cta: '안전지도 보기', to: '/web/explore' },
  { label: '02 배우고 · 메타버스 안전 탐험', title: '메타버스로 캠핑장 위험 배우기', desc: '실제 캠핑장을 3D로 걸으며 위험한 곳을 퀴즈로 익혀요.', cta: '안전 탐험 하기', to: null },
  { label: '03 대비하세요 · 3D 재난 대피 지도', title: '3D 재난 대피 지도로 사고와 재난 대비하기', desc: '3D 재난 대피 지도로 위험을 먼저 알리고 대피소 길을 안내해요.', cta: '대피 지도 보기', to: null },
]
const NOTICES = [
  { title: '[안전안내] 주말 집중호우·강풍 예보에 따른 캠핑장 이용객 안전 안내', date: '2026.10.24', views: 412 },
  { title: '[안전안내] 텐트 안 난방용품 사용 시 일산화탄소 중독에 주의하세요', date: '2026.10.15', views: 1280 },
]

// ---- 히어로 영상: 두 개를 이어서 재생 ----
const vidA = ref(null)
const vidB = ref(null)
const cur = ref(0) // 0: 가족 숲, 1: 차박 호숫가
const paused = ref(false)

function play(el) {
  if (!el) return
  el.muted = true
  el.currentTime = 0
  el.play?.()?.catch(() => {}) // 자동재생이 막혀도 오류를 내지 않는다
}
function swapTo(i) {
  cur.value = i
  if (!paused.value) play(i === 0 ? vidA.value : vidB.value)
}
function toggleVid() {
  const v = cur.value === 0 ? vidA.value : vidB.value
  if (paused.value) v?.play?.()?.catch(() => {})
  else v?.pause()
  paused.value = !paused.value
}
onMounted(() => vidA.value?.play?.()?.catch(() => {}))

// ---- 전국 순위 가로 넘김 ----
const nationRow = ref(null)
const scrollRow = (dir) => nationRow.value?.scrollBy({ left: dir * 672, behavior: 'smooth' })

// ---- 찜 (예시: 화면 안에서만 기억) · 로그인 안내 · 알림 ----
const fav = ref({})
const guideOpen = ref(false)
const guideText = ref('')
const toast = ref('')
let toastTimer = null
onUnmounted(() => clearTimeout(toastTimer))

function toggleFav(i) {
  if (!auth.isLoggedIn) {
    guideText.value = '마음에 드는 캠핑장을 찜해 두면\n로그인 후 찜한 캠핑장에서 모아 볼 수 있어요.'
    guideOpen.value = true
    return
  }
  const liked = !!fav.value[i]
  fav.value = { ...fav.value, [i]: !liked }
  toast.value = liked ? '찜을 해제했어요' : '찜한 캠핑장에 담았어요'
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = '' }, 3000)
}

// ---- 1분 안전 진단 팝업: 처음 들어오면 뜨고, "오늘 하루 보지 않기"를 누르면 오늘은 안 뜬다 ----
const HIDE_KEY = 'web-nudge-hidden-date'
const today = () => new Date().toISOString().slice(0, 10)
function hiddenToday() {
  try { return localStorage.getItem(HIDE_KEY) === today() } catch { return false }
}
const nudgeOpen = ref(!hiddenToday())
const closeNudge = () => { nudgeOpen.value = false }
function hideToday() {
  try { localStorage.setItem(HIDE_KEY, today()) } catch { /* 저장이 막혀도 이번만 닫는다 */ }
  nudgeOpen.value = false
}
const showNudge = computed(() => nudgeOpen.value && !guideOpen.value)
</script>

<template>
  <div class="home">
    <!-- ② 검색 영역 (히어로) -->
    <section class="hero">
      <video ref="vidA" src="/web/hero-1.mp4" poster="/web/hero-1.jpg" muted playsinline preload="auto"
        aria-hidden="true" :style="{ opacity: cur === 0 ? 1 : 0 }" @ended="swapTo(1)"></video>
      <video ref="vidB" src="/web/hero-2.mp4" poster="/web/hero-2.jpg" muted playsinline preload="auto"
        aria-hidden="true" :style="{ opacity: cur === 1 ? 1 : 0 }" @ended="swapTo(0)"></video>
      <div class="shade" aria-hidden="true"></div>
      <div class="hero-text">
        <h1>떠나기 전에,<br />안전부터 확인하세요</h1>
        <form class="search" @submit.prevent>
          <button type="button" class="field" @click="openTrip('region')">
            <span class="k">지역</span><span class="v">{{ regionText }}</span><i class="chev"></i>
          </button>
          <button type="button" class="field divide" @click="openTrip('date')">
            <span class="k">날짜</span><span class="v">{{ dateText }}</span><i class="chev"></i>
          </button>
          <button type="button" class="field divide" @click="openTrip('date')">
            <span class="k">인원</span><span class="v">{{ peopleText }}</span><i class="chev"></i>
          </button>
          <RouterLink to="/web/explore" class="go">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>캠핑장 찾기
          </RouterLink>
        </form>
      </div>
      <button type="button" class="vid-toggle" :aria-label="paused ? '배경 영상 재생' : '배경 영상 일시정지'" @click="toggleVid">
        <svg v-if="!paused" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <rect x="6" y="5" width="4" height="14" rx="1" /><rect x="14" y="5" width="4" height="14" rx="1" />
        </svg>
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg>
      </button>
    </section>

    <!-- ③ 지금 안전 상황 -->
    <a href="#" class="alert" @click.prevent>
      <span class="tag">기상특보</span>
      <span class="msg">[지역] [특보 이름] 발효 중 · [기준 시각]</span>
      <span class="more">더미 데이터 · 알림 전체 보기 ›</span>
    </a>

    <!-- ④⑤ 로그인 후: 내 유형 + 내 유형 맞춤 TOP 5 (진단 결과가 서버에서 오면 실제 값으로 바꾼다) -->
    <template v-if="savedType">
      <section class="mytype">
        <div class="mytype-row">
          <span class="pill">내 유형</span>
          <b>{{ savedType.who }} · {{ savedType.siteName }}</b>
          <span class="muted">보완할 것: {{ savedType.weak.join(' · ') }}</span>
          <span class="mytype-actions">
            <RouterLink to="/web/survey" class="ghost">진단 결과 다시 보기</RouterLink>
            <RouterLink to="/web/survey" class="solid">다시 진단하기</RouterLink>
          </span>
        </div>
      </section>
      <section class="mytop">
        <div class="head">
          <h2>내 유형 맞춤 TOP 5</h2>
          <RouterLink to="/web/explore" class="link">안전지도 상세보기</RouterLink>
        </div>
        <div class="grid4">
          <RouterLink v-for="(c, i) in MY_CAMPS" :key="c.name" to="/web/explore" class="card nc">
            <span class="photo"></span>
            <span class="rank">{{ i + 1 }}위</span>
            <span class="info">
              <span class="name">{{ c.name }}</span>
              <span class="score-row">
                <span class="muted">안전점수</span><span class="score">{{ c.score }}</span>
                <span class="muted">/ 100 · {{ c.meta }}</span>
              </span>
            </span>
          </RouterLink>
        </div>
      </section>
    </template>

    <!-- ⑤-2 전국 안전 캠핑장 순위 -->
    <section class="nation">
      <div class="head pad">
        <h2>전국 안전 캠핑장 순위</h2>
        <RouterLink to="/web/explore" class="link">안전지도 상세보기</RouterLink>
        <div class="arrows">
          <button type="button" aria-label="이전 캠핑장" @click="scrollRow(-1)">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>
          </button>
          <button type="button" aria-label="다음 캠핑장" @click="scrollRow(1)">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
          </button>
        </div>
      </div>
      <div ref="nationRow" class="row">
        <article v-for="(n, i) in NATION" :key="n.name" class="card nc nation-card">
          <RouterLink to="/web/explore" class="photo" :aria-label="`${n.name} 전경`"></RouterLink>
          <span class="rank">{{ i + 1 }}위</span>
          <button type="button" class="heart" :aria-pressed="!!fav[i]" :aria-label="n.name + (fav[i] ? ' 찜 해제' : ' 찜하기')"
            @click="toggleFav(i)">
            <svg width="22" height="22" viewBox="0 0 24 24" :class="{ on: fav[i] }" stroke-width="2" stroke-linejoin="round"
              aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>
          </button>
          <RouterLink to="/web/explore" class="info">
            <span class="name ellipsis">{{ n.name }}</span>
            <span class="score-row">
              <span class="muted">안전점수</span><span class="score">{{ n.score }}</span><span class="muted">/ 100</span>
            </span>
          </RouterLink>
        </article>
      </div>
    </section>

    <!-- ⑥ 메인기능 1 · 2 · 3 -->
    <section class="features">
      <h2>고르고, 배우고, 대비하세요</h2>
      <div class="grid3">
        <component :is="f.to ? RouterLink : 'a'" v-for="f in FEATURES" :key="f.label" :to="f.to || undefined"
          :href="f.to ? undefined : '#'" class="mf" @click="f.to ? null : $event.preventDefault()">
          <span class="mf-photo"></span>
          <span class="mf-body">
            <span class="mf-title">{{ f.title }}</span>
            <span class="mf-label">{{ f.label }}</span>
            <span class="mf-desc">{{ f.desc }}</span>
            <span class="mf-cta"><span>{{ f.cta }}</span></span>
          </span>
        </component>
      </div>
    </section>

    <!-- ⑦ 공지사항 -->
    <section class="notices">
      <div class="head">
        <h2>공지사항</h2>
        <a href="#" class="link" @click.prevent>전체 보기</a>
      </div>
      <div class="grid2">
        <a v-for="t in NOTICES" :key="t.title" href="#" class="nt" @click.prevent>
          <span class="nt-head">
            <svg width="22" height="22" viewBox="0 0 24 24" stroke-width="1.2" stroke-linejoin="round" aria-hidden="true" class="leaf">
              <path d="M19 4C11 4 5 8.5 5 15c0 1.6.5 3 1.2 4.2C12.6 18.6 19 13 19 4Z" /><path d="M6.2 19.2C8 15 11 11.5 15 8.5" fill="none" />
            </svg>
            <span class="nt-title">{{ t.title }}</span>
          </span>
          <span class="nt-meta">
            <span>관리자</span><i></i><span>{{ t.date }}</span><i></i><span>조회 {{ t.views }}</span>
          </span>
        </a>
      </div>
    </section>

    <!-- 찜 알림 -->
    <div v-if="toast" class="toast" role="status">
      <span>{{ toast }}</span>
      <RouterLink to="/web/saved">찜한 캠핑장 보기</RouterLink>
    </div>

    <!-- ⑧ 1분 안전 진단 팝업 -->
    <!-- 어두운 바탕(.dim)을 누르면 닫힌다. 카드 안쪽을 눌렀을 때는 닫히지 않게 .self -->
    <div v-if="showNudge" class="dim" role="dialog" aria-modal="true" aria-labelledby="web-nudge-title" @click.self="closeNudge">
      <div class="nudge">
        <div class="nudge-main">
          <div class="nudge-head">
            <span class="eyebrow">CAMPINE · 1분 안전 진단</span>
            <h2 id="web-nudge-title">이번 캠핑,<br />나는 얼마나 대비됐을까?</h2>
          </div>

          <div class="note note-l" aria-hidden="true">
            <div class="strip accent"></div>
            <div class="note-body">
              <span class="checkline"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10" /></svg><i></i></span>
              <span class="checkline"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10" /></svg><i></i></span>
              <span class="checkline"><span class="box"></span><i></i></span>
            </div>
          </div>
          <div class="note note-r" aria-hidden="true">
            <div class="strip green"></div>
            <div class="score-body">
              <span class="s-label">내 대비도</span>
              <span class="s-num">?<small>점</small></span>
              <i class="track"><em></em></i>
            </div>
          </div>

          <div class="nudge-cta">
            <span class="bubble">10문항 · 약 1분 · 로그인 없이<i aria-hidden="true"></i></span>
            <RouterLink to="/web/survey" class="start">진단 시작하기</RouterLink>
          </div>
        </div>
        <div class="nudge-foot">
          <button type="button" class="hide" @click="hideToday">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></svg>오늘 하루 보지 않기
          </button>
          <button type="button" class="close" @click="closeNudge">닫기</button>
        </div>
      </div>
    </div>

    <WebGuideModal :open="guideOpen" :text="guideText" @close="guideOpen = false" />
    <WebTripModal :open="tripOpen" :tab="tripTab" @close="tripOpen = false" @apply="tripOpen = false" />
  </div>
</template>

<style scoped>
.home {
  position: relative;
}
h2 {
  margin: 0;
  font-size: 46px;
  font-weight: 900;
  letter-spacing: -1.8px;
  text-wrap: balance;
}
.muted {
  font-size: 14px;
  color: var(--web-text-sub);
}
.head {
  display: flex;
  align-items: baseline;
  gap: 18px;
}
.head.pad {
  padding: 0 80px;
}
.link {
  font-size: 18px;
  color: var(--web-text);
}

/* ② 히어로 */
.hero {
  position: relative;
  height: 760px;
  overflow: hidden;
  background: var(--web-text);
}
.hero video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: opacity 0.8s ease;
}
.shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.55) 0%, rgba(0, 0, 0, 0.25) 50%, rgba(0, 0, 0, 0.05) 75%);
}
.hero-text {
  position: absolute;
  left: 112px;
  right: 112px;
  bottom: 96px;
  color: var(--web-white);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hero-text h1 {
  margin: 0;
  font-size: 60px;
  line-height: 1.18;
  font-weight: 700;
  letter-spacing: -1.8px;
  text-wrap: balance;
}
.search {
  margin: 18px 0 0;
  width: 880px;
  max-width: 100%;
  height: 54px;
  padding: 5px 5px 5px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
}
.field {
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
  padding: 0 22px;
  border: 0;
  background: transparent;
  color: var(--web-white);
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  text-align: left;
}
.field.divide {
  border-left: 1px solid rgba(255, 255, 255, 0.35);
}
.field .k {
  font-size: 13px;
  opacity: 0.8;
  white-space: nowrap;
}
.field .v {
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
}
.chev {
  margin-left: auto;
  width: 7px;
  height: 7px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(45deg) translateY(-2px);
}
.go {
  flex: 0 0 auto;
  height: 100%;
  padding: 0 24px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 15px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}
.vid-toggle {
  position: absolute;
  right: 48px;
  bottom: 48px;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  border: 0;
  background: rgba(255, 255, 255, 0.9);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* ③ 기상특보 띠 */
.alert {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 22px 80px;
  background: var(--web-dark);
  color: var(--web-white);
}
.alert .tag {
  padding: 3px 10px;
  border-radius: 4px;
  background: var(--web-terracotta);
  font-size: 13px;
  font-weight: 700;
}
.alert .msg {
  font-size: 17px;
  font-weight: 700;
}
.alert .more {
  margin-left: auto;
  font-size: 15px;
  color: var(--web-text-light);
}

/* ④⑤ 내 유형 */
.mytype {
  padding: 72px 80px 0;
  background: var(--web-bg-beige);
}
.mytype-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 17px;
}
.pill {
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 14px;
  font-weight: 700;
}
.mytype-row .muted {
  font-size: 17px;
}
.mytype-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}
.ghost,
.solid {
  height: 46px;
  padding: 0 22px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
}
.ghost {
  border: 1.5px solid var(--web-green);
  background: var(--web-bg);
  color: var(--web-green);
}
.solid {
  background: var(--web-green);
  color: var(--web-white);
}
.mytop {
  padding: 36px 80px 96px;
  display: flex;
  flex-direction: column;
  gap: 32px;
  background: var(--web-bg-beige);
}
.grid4 {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

/* 캠핑장 카드 (맞춤 TOP · 전국 순위 공통) */
.card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--web-border);
  border-radius: 20px;
  overflow: hidden;
  background: var(--web-bg);
  color: var(--web-text);
}
.nc {
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}
.nc:hover {
  transform: translateY(-6px);
  box-shadow: 0 14px 30px rgba(31, 58, 30, 0.16);
  border-color: var(--web-green);
}
.nc:hover .name {
  color: var(--web-green);
}
.photo {
  display: block;
  height: 250px;
  background: var(--web-photo); /* 사진 자리 (사진은 데이터 연결 후) */
}
.rank {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 4px 13px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 14px;
  font-weight: 700;
}
.info {
  padding: 18px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--web-text);
}
.name {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.4px;
  transition: color 0.2s ease;
}
.ellipsis {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.score-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.score {
  font-size: 22px;
  font-weight: 700;
  color: var(--web-green);
}

/* ⑤-2 전국 순위 */
.nation {
  padding: 104px 0 0;
  display: flex;
  flex-direction: column;
  gap: 32px;
}
.arrows {
  margin-left: auto;
  align-self: center;
  display: flex;
  gap: 10px;
}
.arrows button {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.row {
  display: flex;
  gap: 24px;
  padding: 6px 80px 14px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scroll-padding-left: 80px;
  scrollbar-width: none;
}
.row::-webkit-scrollbar {
  display: none;
}
.nation-card {
  flex: 0 0 312px;
  scroll-snap-align: start;
}
.heart {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  border: 0;
  background: var(--web-bg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.heart svg {
  fill: none;
  stroke: var(--web-text);
}
.heart svg.on {
  fill: var(--web-terracotta);
  stroke: var(--web-terracotta);
}

/* ⑥ 메인기능 */
.features {
  padding: 104px 80px 0;
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.grid3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px;
}
.mf {
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
  background: var(--web-bg-soft);
  color: var(--web-text);
}
.mf:hover .mf-title {
  color: var(--web-green);
}
.mf-photo {
  display: block;
  height: 220px;
  background: var(--web-photo);
}
.mf-body {
  flex: 1;
  min-height: 270px;
  padding: 38px 40px 36px;
  display: flex;
  flex-direction: column;
}
.mf-title {
  font-family: var(--web-font-serif);
  font-size: 34px;
  line-height: 1.28;
  font-weight: 600;
  letter-spacing: -1px;
}
.mf-label {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 700;
}
.mf-desc {
  margin-top: 8px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--web-text-sub);
}
.mf-cta {
  margin-top: auto;
  padding-top: 26px;
}
.mf-cta span {
  display: inline-flex;
  align-items: center;
  height: 44px;
  padding: 0 22px;
  border-radius: 999px;
  background: var(--web-bg);
  font-size: 16px;
  font-weight: 500;
}

/* ⑦ 공지사항 */
.notices {
  padding: 120px 80px 0;
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.grid2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}
.nt {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 28px 30px;
  border: 1px solid var(--web-border);
  border-radius: 20px;
  background: var(--web-bg);
  color: var(--web-text);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.nt:hover {
  border-color: var(--web-green);
  box-shadow: 0 10px 24px rgba(31, 58, 30, 0.12);
}
.nt:hover .nt-title {
  text-decoration: underline;
  text-underline-offset: 4px;
}
.nt-head {
  display: flex;
  gap: 12px;
  font-size: 19px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: -0.3px;
}
.leaf {
  flex: 0 0 auto;
  margin-top: 3px;
  fill: var(--web-terracotta);
  stroke: var(--web-text);
}
.nt-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--web-text-sub);
}
.nt-meta i {
  width: 1px;
  height: 12px;
  background: var(--web-line-input);
}

/* 찜 알림 */
.toast {
  position: fixed;
  left: 50%;
  bottom: 48px;
  transform: translateX(-50%);
  z-index: 19;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 16px 14px 24px;
  border-radius: 999px;
  background: var(--web-text);
  color: var(--web-white);
  font-size: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
}
.toast a {
  height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  background: var(--web-white);
  color: var(--web-green);
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
}

/* ⑧ 1분 안전 진단 팝업 */
.dim {
  position: fixed;
  inset: 0;
  z-index: 20;
  padding: 16px 0;
  display: flex;
  overflow-y: auto; /* 화면이 낮으면 스크롤로 아래까지 볼 수 있게 (잘리지 않게) */
  background: var(--web-dim);
}
.nudge {
  margin: auto; /* 가로·세로 정중앙. 화면보다 크면 위에서부터 스크롤 */
  flex: none;
  width: 560px;
  max-width: calc(100% - 32px);
  border-radius: 32px;
  overflow: hidden;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.3);
}
.nudge-main {
  position: relative;
  height: 580px;
  background: var(--web-sky);
  color: var(--web-green);
  overflow: hidden;
}
.nudge-head {
  position: relative;
  z-index: 2;
  padding: 46px 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.eyebrow {
  font-size: 17px;
  font-weight: 700;
}
.nudge-head h2 {
  margin: 10px 0 0;
  font-size: 46px;
  line-height: 1.22;
  font-weight: 700;
  letter-spacing: -1.6px;
}
.note {
  position: absolute;
  z-index: 2;
  width: 150px;
  padding: 0 0 14px;
  border-radius: 10px;
  background: var(--web-white);
  box-shadow: 0 8px 18px rgba(12, 31, 9, 0.15);
  overflow: hidden;
}
.note-l {
  left: 112px;
  top: 250px;
  transform: rotate(-10deg);
}
.note-r {
  right: 112px;
  top: 268px;
  transform: rotate(8deg);
}
.strip {
  height: 14px;
}
.strip.accent {
  background: var(--web-terracotta);
}
.strip.green {
  background: var(--web-green);
}
.note-body {
  padding: 12px 14px 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.checkline {
  display: flex;
  align-items: center;
  gap: 8px;
}
.checkline svg {
  stroke: var(--web-terracotta);
}
.checkline i {
  flex: 1;
  height: 7px;
  border-radius: 4px;
  background: var(--web-sky-bar);
}
.checkline .box {
  width: 14px;
  height: 14px;
  margin: 0 1px;
  border: 2px solid var(--web-sky-box);
  border-radius: 4px;
}
.score-body {
  padding: 10px 14px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.s-label {
  font-size: 12px;
  font-weight: 700;
}
.s-num {
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
}
.s-num small {
  font-size: 16px;
}
.track {
  display: block;
  height: 7px;
  border-radius: 4px;
  background: var(--web-sky-track);
  overflow: hidden;
}
.track em {
  display: block;
  width: 45%;
  height: 100%;
  background: var(--web-terracotta);
}
.nudge-cta {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 40px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
}
.bubble {
  position: relative;
  padding: 11px 24px;
  border-radius: 999px;
  background: var(--web-terracotta);
  color: var(--web-white);
  font-size: 16px;
  font-weight: 700;
}
.bubble i {
  position: absolute;
  left: 50%;
  bottom: -7px;
  width: 14px;
  height: 14px;
  background: var(--web-terracotta);
  transform: translateX(-50%) rotate(45deg);
}
.start {
  width: 280px;
  height: 66px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 20px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(12, 31, 9, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
}
.nudge-foot {
  background: var(--web-bg);
  padding: 18px 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hide,
.close {
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
}
.hide {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--web-text-sub);
  font-size: 16px;
}
.hide svg {
  color: var(--web-arrow);
}
.close {
  color: var(--web-green);
  font-size: 17px;
  font-weight: 700;
}
</style>
