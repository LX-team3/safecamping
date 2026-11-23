<template>
  <div class="wrap">
    <!-- 3D 화면 -->
    <div ref="container" class="metaverse"></div>

    <!-- 위쪽 정보줄: 지금 낮·밤, 푼 문제 수, 나가기 (점수는 없앴어요) -->
    <div v-if="phase === 'play'" class="hud">
      <span>{{ timeOfDay === 'night' ? '🌙 밤' : '☀️ 낮' }}</span>
      <span>✅ {{ doneCount }}문제</span>
      <button class="exit" @click="finish">나가기</button>
    </div>
    <div v-if="phase === 'play' && !quiz && !flood" class="help">
      WASD·방향키 이동 · Shift 달리기 · Space 점프 · 머리 위 <b>빨간 화살표</b>를 따라가요
    </div>

    <!-- 돌발 미션: 비가 와서 물이 차오르는 동안 보이는 알림 -->
    <div v-if="phase === 'play' && flood === 'run'" class="alert">
      🌧️ 비가 많이 와요! 물이 차오르기 전에 <b>초록 화살표</b>를 따라 <b>출구</b>로 대피해요 (Shift 달리기)
    </div>

    <!-- 시작 화면 -->
    <div v-if="phase === 'loading' || phase === 'ready'" class="dim">
      <div class="panel">
        <h1>⛺ 캠핑장 안전 탐험</h1>
        <p>인북천 물빛테마공원 캠핑장에 <b>위험 상황</b>이 하나씩 나타나요.</p>
        <ul>
          <li>머리 위 <b>빨간 화살표</b>가 위험 지점을 가리켜요.</li>
          <li>노란 고리 안으로 들어가면 퀴즈가 열려요. 풀면 다음 문제가 다른 곳에 나타나요.</li>
          <li>낮과 밤이 <b>2분마다</b> 바뀌어요. 낮에는 낮 문제, 밤에는 밤 문제가 나와요.</li>
          <li>갑자기 <b>비</b>가 오면 물이 차오르기 전에 <b>출구</b>로 대피해요.</li>
          <li>제한 시간은 없어요. 그만하고 싶을 때 <b>나가기</b>를 눌러요.</li>
        </ul>
        <!-- 아이·어른 고르기: 고른 쪽에 맞는 문제가 나와요 -->
        <div class="who-pick">
          <button :class="{ on: audience === 'child' }" @click="pickAudience('child')">🧒 아이</button>
          <button :class="{ on: audience === 'adult' }" @click="pickAudience('adult')">🧑 어른</button>
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
            <b>정답이에요!</b>
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

    <!-- 돌발 미션 결과: 대피 성공 / 물에 잠김 -->
    <div v-if="phase === 'play' && (flood === 'safe' || flood === 'drowned')" class="dim">
      <div class="panel">
        <template v-if="flood === 'safe'">
          <h1>🎉 대피 성공!</h1>
          <p class="situation">
            비가 많이 오면 강물은 아주 빨리 불어나요. 짐을 챙기지 말고 바로 출구나 높은 곳으로 피해요.
            우리 캠핑장의 출구가 어디인지 도착했을 때 미리 봐 두면 좋아요.
          </p>
          <button class="main" @click="floodContinue">계속 탐험하기</button>
        </template>
        <template v-else>
          <h1>🌊 물에 잠겼어요</h1>
          <p class="situation">
            물이 차오르기 시작하면 걸어서는 늦을 수 있어요. 비가 오면 바로 출구 쪽으로 달려요.
            얕아 보여도 물살에 넘어질 수 있어서 물속을 걸어가면 안 돼요.
          </p>
          <button class="main" @click="floodRetry">다시 대피하기</button>
        </template>
      </div>
    </div>

    <!-- 결과 화면 -->
    <div v-if="phase === 'end'" class="dim">
      <div class="panel">
        <h1>👏 탐험을 마쳤어요</h1>
        <p class="big">{{ doneCount }}문제</p>
        <p>위험 상황을 이만큼 해결했어요.</p>
        <button class="main" @click="restart">처음으로</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { createScene } from '../metaverse/scene.js'

const container = ref(null)
const phase = ref('loading') // loading → ready → play → end (불러오기 실패는 error)
const doneCount = ref(0)     // 푼 문제 수
const timeOfDay = ref('day') // 'day' 낮, 'night' 밤. 게임이 2분마다 바꿔서 알려줘요
const audience = ref('child') // 'child' 아이, 'adult' 어른
// 돌발 미션 상태: '' 없음 / 'run' 대피 중 / 'safe' 대피 성공 / 'drowned' 물에 잠김
const flood = ref('')

// 아이·어른 고르기 ("처음으로"를 해도 고른 것이 그대로 남아요)
function pickAudience(who) {
  audience.value = who
  game?.setAudience(who)
}

// 퀴즈 상태
const quiz = ref(null)      // 지금 풀고 있는 문제
const answered = ref(false) // 답을 골랐는지
const correct = ref(false)  // 맞혔는지
const picked = ref(-1)      // 고른 번호
const wrongs = ref([])      // 틀린 번호들 (다시 풀 때 못 고르게)

let game = null

function mountGame() {
  game = createScene(container.value, {
    onReady: () => {
      game.setAudience(audience.value) // 불러오는 동안 고른 대상(아이/어른)을 맞춰요
      phase.value = 'ready'
    },
    onTime: (mode) => {
      timeOfDay.value = mode
    },
    // 침수 미션: 'start' 비 시작 / 'safe' 출구 도착 / 'drowned' 물에 잠김 / 'end' 끝
    onFlood: (state) => {
      if (state === 'start') flood.value = 'run'
      else if (state === 'end') flood.value = ''
      else flood.value = state
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
  if (game) game.dispose()
})

function start() {
  doneCount.value = 0
  phase.value = 'play'
  game.start()
}

function answer(i) {
  picked.value = i
  answered.value = true
  correct.value = i === quiz.value.answer
  if (!correct.value) wrongs.value.push(i)
}

function retry() {
  answered.value = false
  picked.value = -1
}

// 문제를 맞히고 "계속 탐험하기"를 눌렀을 때: 다음 문제가 다른 곳에 나타나요
function closeQuiz() {
  game.completeMission(quiz.value.id)
  quiz.value = null
  doneCount.value++
}

// 대피 성공 뒤 계속 탐험하기 / 물에 잠긴 뒤 다시 대피하기
function floodContinue() {
  flood.value = ''
  game.floodContinue()
}
function floodRetry() {
  game.floodRetry()
}

// 나가기: 제한 시간이 없어서, 그만하고 싶을 때 직접 눌러서 끝내요
function finish() {
  quiz.value = null
  flood.value = ''
  phase.value = 'end'
  game.stop()
}

// 처음으로: 3D 화면은 그대로 두고, 새 문제와 캐릭터 위치만 새로 준비해요
function restart() {
  game.restart()
  phase.value = 'ready'
}

function choiceClass(i) {
  if (answered.value && picked.value === i) return correct.value ? 'right' : 'wrong'
  if (wrongs.value.includes(i)) return 'wrong'
  return ''
}
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
.hud { align-items: center; }
.hud .exit {
  padding: 4px 14px; border: 0; border-radius: 999px;
  background: #fff; color: #1f4d2b; font-size: 15px; font-weight: 700; cursor: pointer;
}
.help {
  position: absolute; bottom: 14px; left: 50%; transform: translateX(-50%);
  padding: 8px 16px; background: rgba(255, 255, 255, 0.85); border-radius: 10px; font-size: 14px;
}

.alert {
  position: absolute; top: 66px; left: 50%; transform: translateX(-50%);
  width: max-content; max-width: 92vw; padding: 10px 18px;
  background: rgba(190, 40, 40, 0.92); color: #fff; border-radius: 12px;
  font-size: 16px; font-weight: 700; text-align: center;
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

.who-pick { display: flex; gap: 10px; margin-top: 12px; }
.who-pick button {
  flex: 1; padding: 10px; border: 2px solid #d9e2d4; border-radius: 12px;
  background: #fff; font-size: 16px; font-weight: 700; cursor: pointer;
}
.who-pick button.on { border-color: #2f7d4f; background: #e3f4e8; }

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
