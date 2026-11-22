<template>
  <div class="wrap">
    <!-- 3D 화면 -->
    <div ref="container" class="metaverse"></div>

    <!-- client 로 돌아가기: 메타버스 라우터가 아니라 client 주소라서 RouterLink 가 아닌 일반 <a> -->
    <!-- '/' 로 가면 client 가 화면 폭을 보고 PC(/web) · 모바일(/app/explore) 으로 보내 준다 -->
    <a href="/" class="back">← 캠핑 앱으로</a>

    <!-- 위쪽 정보줄: 시간, 점수, 찾은 개수 -->
    <div v-if="phase === 'play'" class="hud">
      <span :class="{ hurry: timeLeft <= 30 }">⏱ {{ timeText }}</span>
      <span>⭐ {{ score }}점</span>
      <span>✅ {{ doneCount }} / {{ total }}</span>
    </div>
    <div v-if="phase === 'play' && !quiz" class="help">
      WASD·방향키 이동 · Shift 달리기 · 땅을 클릭해도 걸어가요 · 머리 위 <b>빨간 화살표</b>를 따라가요
    </div>

    <!-- 시작 화면 -->
    <div v-if="phase === 'loading' || phase === 'ready'" class="dim">
      <div class="panel">
        <h1>⛺ 캠핑장 안전 탐험</h1>
        <p>인북천 물빛테마공원 캠핑장 곳곳에 <b>위험 상황 {{ total || 4 }}곳</b>이 숨어 있어요.</p>
        <ul>
          <li>머리 위 <b>빨간 화살표</b>가 가장 가까운 위험 지점을 가리켜요.</li>
          <li>노란 고리 안으로 들어가면 퀴즈가 열려요.</li>
          <li>한 번에 맞히면 <b>25점</b>, 다시 풀어서 맞히면 <b>15점</b>이에요.</li>
          <li>제한 시간은 <b>5분</b>! 퀴즈를 푸는 동안에는 시간이 멈춰요.</li>
        </ul>
        <!-- 낮·밤 고르기: 누르면 뒤에 보이는 캠핑장이 바로 바뀌어요 -->
        <div class="time-pick">
          <button :class="{ on: timeOfDay === 'day' }" @click="pickTime('day')">☀️ 낮</button>
          <button :class="{ on: timeOfDay === 'night' }" @click="pickTime('night')">🌙 밤</button>
        </div>
        <button class="main" :disabled="phase === 'loading'" @click="start">
          {{ phase === 'loading' ? '캠핑장 불러오는 중…' : '탐험 시작!' }}
        </button>
      </div>
    </div>

    <!-- 불러오기 실패 -->
    <div v-if="phase === 'error'" class="dim">
      <div class="panel">
        <h1>😢 캠핑장을 불러오지 못했어요</h1>
        <p>잠시 뒤에 새로고침(F5)해 주세요.</p>
      </div>
    </div>

    <!-- 퀴즈 -->
    <div v-if="quiz" class="dim">
      <div class="panel quiz">
        <div class="q-title">{{ quiz.icon }} {{ quiz.title }}</div>
        <p class="situation">{{ quiz.situation }}</p>
        <p class="question">Q. {{ quiz.question }}</p>
        <button
          v-for="(c, i) in quiz.choices"
          :key="i"
          class="choice"
          :class="choiceClass(i)"
          :disabled="answered || wrongs.includes(i)"
          @click="answer(i)"
        >
          {{ i + 1 }}. {{ c }}
        </button>

        <div v-if="answered" class="feedback" :class="correct ? 'ok' : 'no'">
          <template v-if="correct">
            <b>정답이에요! +{{ gained }}점</b>
            <p>{{ quiz.explain }}</p>
            <button class="main" @click="closeQuiz">계속 탐험하기</button>
          </template>
          <template v-else>
            <b>아쉬워요! 다시 한 번 생각해 볼까요?</b>
            <button class="main" @click="retry">다시 풀기</button>
          </template>
        </div>
      </div>
    </div>

    <!-- 결과 화면 -->
    <div v-if="phase === 'end'" class="dim">
      <div class="panel">
        <h1>{{ resultTitle }}</h1>
        <p class="big">{{ score }}점</p>
        <p>위험 상황 {{ total }}곳 중 <b>{{ doneCount }}곳</b>을 해결했어요.</p>
        <p v-if="timeLeft > 0">남은 시간 {{ timeText }}</p>
        <p v-else>시간이 다 됐어요.</p>
        <button class="main" @click="restart">다시 하기 (새 문제)</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { createScene } from '../metaverse/scene.js'

const TIME_LIMIT = 300 // 제한 시간(초)

const container = ref(null)
const phase = ref('loading') // loading → ready → play → end (불러오기 실패는 error)
const total = ref(0)
const score = ref(0)
const doneCount = ref(0)
const timeLeft = ref(TIME_LIMIT)
const timeOfDay = ref('day') // 'day' 낮, 'night' 밤

// 낮·밤 고르기 ("다시 하기"를 해도 고른 것이 그대로 남아요)
function pickTime(mode) {
  timeOfDay.value = mode
  game?.setTime(mode)
}

// 퀴즈 상태
const quiz = ref(null)      // 지금 풀고 있는 문제
const answered = ref(false) // 답을 골랐는지
const correct = ref(false)  // 맞혔는지
const picked = ref(-1)      // 고른 번호
const wrongs = ref([])      // 틀린 번호들 (다시 풀 때 못 고르게)
const gained = ref(0)       // 이번 문제로 얻은 점수

let game = null
let timer = null

function mountGame() {
  game = createScene(container.value, {
    onReady: ({ total: n }) => {
      total.value = n
      phase.value = 'ready'
    },
    onMission: (m) => {
      quiz.value = m
      answered.value = false
      picked.value = -1
      wrongs.value = []
    },
    onError: () => {
      phase.value = 'error'
    },
  })
}

onMounted(mountGame)
onUnmounted(() => {
  clearInterval(timer)
  if (game) game.dispose()
})

function start() {
  score.value = 0
  doneCount.value = 0
  timeLeft.value = TIME_LIMIT
  phase.value = 'play'
  game.start()
  clearInterval(timer)
  timer = setInterval(() => {
    if (quiz.value) return // 퀴즈 중에는 시간이 멈춰요
    timeLeft.value--
    if (timeLeft.value <= 0) finish()
  }, 1000)
}

function answer(i) {
  picked.value = i
  answered.value = true
  correct.value = i === quiz.value.answer
  if (correct.value) {
    gained.value = wrongs.value.length ? 15 : 25
    score.value += gained.value
  } else {
    wrongs.value.push(i)
  }
}

function retry() {
  answered.value = false
  picked.value = -1
}

function closeQuiz() {
  game.completeMission(quiz.value.id)
  quiz.value = null
  doneCount.value++
  if (doneCount.value >= total.value) finish()
}

function finish() {
  clearInterval(timer)
  quiz.value = null
  phase.value = 'end'
  game.stop()
}

// 다시 하기: 3D 화면은 그대로 두고, 새 문제와 캐릭터 위치만 새로 준비해요
function restart() {
  total.value = game.restart()
  phase.value = 'ready'
}

function choiceClass(i) {
  if (answered.value && picked.value === i) return correct.value ? 'right' : 'wrong'
  if (wrongs.value.includes(i)) return 'wrong'
  return ''
}

const timeText = computed(() => {
  const t = Math.max(0, timeLeft.value)
  return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`
})

const resultTitle = computed(() => {
  if (score.value >= 100) return '🏅 캠핑 안전 박사!'
  if (score.value >= 70) return '👍 든든한 안전 지킴이'
  return '🌱 조금만 더 연습해요'
})
</script>

<style scoped>
.wrap { position: relative; width: 100vw; height: 100vh; overflow: hidden; font-family: 'Pretendard', 'Noto Sans KR', sans-serif; }
.metaverse { width: 100%; height: 100%; background: #183c36; }

.hud {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%);
  display: flex; gap: 18px; padding: 10px 20px;
  background: rgba(20, 40, 30, 0.8); color: #fff; border-radius: 999px;
  font-size: 18px; font-weight: 700;
}
.hud .hurry { color: #ff8a65; }
.back {
  position: absolute; top: 14px; left: 14px; z-index: 10;
  padding: 8px 14px; background: rgba(20, 40, 30, 0.8); color: #fff;
  border-radius: 999px; font-size: 14px; font-weight: 700; text-decoration: none;
}
/* 휴대폰처럼 좁은 화면에서는 돌아가기 버튼과 겹치지 않게 정보줄을 한 줄 아래로 */
@media (max-width: 560px) {
  .hud { top: 58px; }
}
.help {
  position: absolute; bottom: 14px; left: 50%; transform: translateX(-50%);
  padding: 8px 16px; background: rgba(255, 255, 255, 0.85); border-radius: 10px; font-size: 14px;
}

.dim {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, 0.35);
}
.panel {
  width: min(520px, 92vw); max-height: 90vh; overflow: auto;
  padding: 24px 26px; background: #fffdf7; border-radius: 18px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25); text-align: left;
}
.panel h1 { margin: 0 0 10px; font-size: 26px; }
.panel ul { padding-left: 20px; line-height: 1.7; }
.big { font-size: 44px; font-weight: 800; margin: 6px 0; color: #2f7d4f; }

.time-pick { display: flex; gap: 10px; margin-top: 12px; }
.time-pick button {
  flex: 1; padding: 10px; border: 2px solid #d9e2d4; border-radius: 12px;
  background: #fff; font-size: 16px; font-weight: 700; cursor: pointer;
}
.time-pick button.on { border-color: #2f7d4f; background: #e3f4e8; }

button.main {
  display: block; width: 100%; margin-top: 14px; padding: 12px;
  border: 0; border-radius: 12px; background: #2f7d4f; color: #fff;
  font-size: 17px; font-weight: 700; cursor: pointer;
}
button.main:disabled { background: #9bb8a6; cursor: default; }

.q-title { font-size: 22px; font-weight: 800; margin-bottom: 8px; }
.situation { background: #f1f6ee; padding: 10px 12px; border-radius: 10px; line-height: 1.6; }
.question { font-weight: 700; margin: 14px 0 8px; }
.choice {
  display: block; width: 100%; margin: 6px 0; padding: 10px 12px; text-align: left;
  border: 2px solid #d9e2d4; border-radius: 10px; background: #fff; font-size: 15px; cursor: pointer;
}
.choice:hover:not(:disabled) { border-color: #2f7d4f; }
.choice.right { border-color: #2f7d4f; background: #e3f4e8; }
.choice.wrong { border-color: #d9534f; background: #fbe9e8; color: #999; }
.feedback { margin-top: 12px; padding: 12px; border-radius: 10px; line-height: 1.6; }
.feedback.ok { background: #e3f4e8; }
.feedback.no { background: #fbe9e8; }
.feedback p { margin: 6px 0 0; }
</style>
