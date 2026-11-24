<template>
  <div class="wrap">
    <!-- 3D 화면 -->
    <div ref="container" class="metaverse"></div>

    <!-- 캠핑 앱 화면으로 돌아가기. '/' 로 가면 화면 폭을 보고 PC(/web) · 모바일(/app/explore) 으로 보내 준다 -->
    <RouterLink to="/" class="back">← 캠핑 앱으로</RouterLink>

    <!-- 위쪽 정보줄: 지금 낮·밤, 푼 문제 수, 나가기 (점수는 없앴어요) -->
    <div v-if="phase === 'play'" class="hud">
      <!-- 누르면 낮↔밤이 바로 바뀌어요. 퀴즈를 푸는 중이나 침수 대피 중에는 못 눌러요 -->
      <button
        class="time"
        :disabled="!!quiz || !!flood || !!mini"
        :title="timeOfDay === 'night' ? '눌러서 낮으로 바꾸기' : '눌러서 밤으로 바꾸기'"
        @click="toggleTime"
      >
        {{ timeOfDay === 'night' ? '🌙 밤' : '☀️ 낮' }}
      </button>
      <span>✅ {{ doneCount }}문제</span>
      <button class="exit" @click="finish">나가기</button>
    </div>
    <!-- 미니게임: 마시멜로 굽기. 굽는 동안은 안내와 남은 시간 막대, 끝나면 결과 글자가 보여요 -->
    <div v-if="phase === 'play' && mini" class="mini">
      <template v-if="mini === 'play'">
        <b>🍡 마시멜로를 구워요!</b>
        <span v-if="isTouch"><b>화면을 터치</b>해서 뒤집어요. 한쪽만 오래 구우면 타요</span>
        <span v-else><b>Space</b> 또는 <b>마우스 클릭</b>으로 뒤집어요. 한쪽만 오래 구우면 타요</span>
        <div class="mini-bar"><i></i></div>
      </template>
      <template v-else>
        <b>{{ miniText }}</b>
        <span class="ouch">앗, 뜨거워! 뜨거운 꼬치에 손이 닿았어요</span>
      </template>
    </div>

    <!-- 조작 안내: 터치 화면이면 아래쪽에 조이스틱이 있어서 안내를 위쪽에 보여요 -->
    <div v-if="phase === 'play' && !quiz && !flood && !mini" class="help" :class="{ top: isTouch }">
      <template v-if="isTouch">조이스틱으로 이동 · 화면을 끌어 둘러보기 · 머리 위 <b>빨간 화살표</b>를 따라가요</template>
      <template v-else>WASD·방향키 이동 · Shift 달리기 · Space 점프 · 머리 위 <b>빨간 화살표</b>를 따라가요</template>
    </div>

    <!-- 터치 조작(휴대폰): 왼쪽 아래 조이스틱, 오른쪽 아래 달리기·점프 버튼. 퀴즈·미니게임·결과 창이 떠 있으면 숨겨요 -->
    <template v-if="showPad">
      <div
        ref="stick"
        class="stick"
        @pointerdown.prevent="stickDown"
        @pointermove.prevent="stickMove"
        @pointerup="stickUp"
        @pointercancel="stickUp"
      >
        <i :style="{ transform: `translate(${knob.x}px, ${knob.y}px)` }"></i>
      </div>
      <div class="pad-btns">
        <!-- 달리기는 누르고 있지 않아도 되게 켜고 끄는 방식이에요 (한 손으로 점프도 눌러야 해서) -->
        <button class="pad-btn" :class="{ on: running }" @pointerdown.prevent="toggleRun">🏃<small>달리기</small></button>
        <button class="pad-btn" @pointerdown.prevent="game.jump()">⬆️<small>점프</small></button>
      </div>
    </template>

    <!-- 돌발 미션: 비가 와서 물이 차오르는 동안 보이는 알림 -->
    <div v-if="phase === 'play' && flood === 'run'" class="alert">
      🌧️ 비가 많이 와요! 물이 차오르기 전에 <b>초록 화살표</b>를 따라 <b>출구</b>로 대피해요 ({{ isTouch ? '🏃 달리기 버튼' : 'Shift 달리기' }})
    </div>

    <!-- 시작 화면 -->
    <div v-if="phase === 'loading' || phase === 'ready'" class="dim">
      <div class="panel">
        <h1>⛺ 캠핑장 안전 탐험</h1>
        <p>인북천 물빛테마공원 캠핑장에 <b>위험 상황</b>이 하나씩 나타나요.</p>
        <ul>
          <li>머리 위 <b>빨간 화살표</b>가 위험 지점을 가리켜요.</li>
          <li>노란 고리 안으로 들어가면 퀴즈가 열려요. 풀면 다음 문제가 다른 곳에 나타나요.</li>
          <li>화면 위쪽 <b>☀️ 낮 / 🌙 밤</b> 버튼을 누르면 낮과 밤이 바뀌어요. 낮에는 낮 문제, 밤에는 밤 문제가 나와요.</li>
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
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { createScene } from '@/metaverse/scene.js'

const container = ref(null)
const phase = ref('loading') // loading → ready → play → end (불러오기 실패는 error)
const doneCount = ref(0)     // 푼 문제 수
const timeOfDay = ref('day') // 'day' 낮, 'night' 밤. 위쪽 버튼을 누르면 게임이 바꿔서 알려줘요
const audience = ref('child') // 'child' 아이, 'adult' 어른
// 돌발 미션 상태: '' 없음 / 'run' 대피 중 / 'safe' 대피 성공 / 'drowned' 물에 잠김
const flood = ref('')
// 미니게임(마시멜로 굽기) 상태: '' 없음 / 'play' 굽는 중 / 'ouch' 굽기 끝
const mini = ref('')
const miniText = ref('') // 굽기 결과 글자 (잘 구웠어요 / 탔어요 / 덜 익었어요)

// 아이·어른 고르기 ("처음으로"를 해도 고른 것이 그대로 남아요)
function pickAudience(who) {
  audience.value = who
  game?.setAudience(who)
}

// ---------- 터치 조작 (휴대폰·태블릿) ----------
// 터치 화면인지: 터치가 되는 기기면 처음부터 켜고, 아니어도 화면을 한 번 터치하면 켜요
const isTouch = ref(navigator.maxTouchPoints > 0)
const onFirstTouch = () => { isTouch.value = true }
// 조이스틱·버튼을 보여 줄 때: 탐험 중이고, 퀴즈·미니게임·대피 결과 창이 없을 때 (대피 중에는 보여요)
const showPad = computed(() =>
  isTouch.value && phase.value === 'play' && !quiz.value && !mini.value && flood.value !== 'safe' && flood.value !== 'drowned')

const STICK_R = 45                    // 조이스틱 손잡이가 움직일 수 있는 거리(px)
const stick = ref(null)               // 조이스틱 바탕 원
const knob = reactive({ x: 0, y: 0 }) // 손잡이가 가운데에서 움직인 거리(px)
const running = ref(false)            // 달리기 버튼이 켜져 있는지
let stickId = null                    // 조이스틱을 잡고 있는 손가락 번호

function stickDown(e) {
  stickId = e.pointerId
  // 손가락이 원 밖으로 나가도 계속 따라오게 붙잡아요. 붙잡기를 못 해도 조이스틱은 움직이게 오류는 넘어가요
  try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* 붙잡기 실패는 무시 */ }
  stickMove(e)
}
function stickMove(e) {
  if (e.pointerId !== stickId) return
  const box = stick.value.getBoundingClientRect()
  let dx = e.clientX - (box.left + box.width / 2)
  let dy = e.clientY - (box.top + box.height / 2)
  const len = Math.hypot(dx, dy)
  if (len > STICK_R) { dx *= STICK_R / len; dy *= STICK_R / len } // 원 밖으로는 못 나가요
  knob.x = dx
  knob.y = dy
  game?.setMove(dx / STICK_R, -dy / STICK_R) // 화면은 아래쪽이 +라서 위(앞으로)가 +가 되게 뒤집어요
}
function stickUp(e) {
  if (e && e.pointerId !== stickId) return
  stickId = null
  knob.x = 0
  knob.y = 0
  game?.setMove(0, 0)
}
function toggleRun() {
  running.value = !running.value
  game?.setRun(running.value)
}
// 조이스틱이 사라질 때(퀴즈가 열릴 때 등) 손잡이를 가운데로 돌려놔요. 안 그러면 다시 나왔을 때 혼자 걸어가요
watch(showPad, (on) => { if (!on) stickUp() })

// 위쪽 낮·밤 버튼: 바로 낮↔밤을 바꿔요 (글자는 게임이 onTime으로 알려 주면 바뀌어요)
function toggleTime(e) {
  game?.toggleTime()
  e.currentTarget.blur() // 버튼에 초점이 남으면 Space(점프)를 누를 때 버튼이 또 눌려서 초점을 빼요
}

// 퀴즈 상태
const quiz = ref(null)     // 지금 풀고 있는 문제
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
    // 미니게임: 'start' 시작 / 'ouch' 굽기 끝(text: 결과 글자) / 'end' 끝
    onMini: (state, text) => {
      if (state === 'start') mini.value = 'play'
      else if (state === 'ouch') { mini.value = 'ouch'; miniText.value = text }
      else mini.value = ''
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

onMounted(() => {
  mountGame()
  window.addEventListener('touchstart', onFirstTouch, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('touchstart', onFirstTouch)
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
/* touch-action: none → 손가락으로 끌 때 브라우저가 화면을 밀거나 확대하지 않게 해요
   100dvh → 휴대폰에서 주소창을 뺀 실제 화면 높이 (모르는 브라우저는 윗줄 100vh를 써요) */
.wrap { touch-action: none; user-select: none; -webkit-user-select: none; -webkit-touch-callout: none; }
.wrap { height: 100vh; height: 100dvh; }
.wrap { position: relative; width: 100vw; overflow: hidden; font-family: 'Pretendard', 'Noto Sans KR', sans-serif; }
.metaverse { width: 100%; height: 100%; background: #183c36; }

.hud {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%);
  display: flex; gap: 18px; padding: 10px 20px;
  background: rgba(20, 40, 30, 0.8); color: #fff; border-radius: 999px;
  font-size: 18px; font-weight: 700;
}
/* width: max-content → 좁은 화면에서 정보줄이 반으로 눌려 글자가 세로로 꺾이는 것을 막아요 */
.hud { align-items: center; width: max-content; white-space: nowrap; }
/* 낮·밤 버튼: 누를 수 있다는 것이 보이게 테두리를 둘러요 */
.hud .time {
  padding: 4px 14px; border: 2px solid rgba(255, 255, 255, 0.6); border-radius: 999px;
  background: transparent; color: #fff; font: inherit; cursor: pointer;
}
.hud .time:hover:not(:disabled) { background: rgba(255, 255, 255, 0.2); }
.hud .time:disabled { border-color: transparent; cursor: default; }
.hud .exit {
  padding: 4px 14px; border: 0; border-radius: 999px;
  background: #fff; color: #1f4d2b; font-size: 15px; font-weight: 700; cursor: pointer;
}
.back {
  position: absolute; top: 14px; left: 14px; z-index: 10;
  padding: 8px 14px; background: rgba(20, 40, 30, 0.8); color: #fff;
  border-radius: 999px; font-size: 14px; font-weight: 700; text-decoration: none;
}
/* 휴대폰처럼 좁은 화면에서는 돌아가기 버튼과 겹치지 않게 정보줄을 한 줄 아래로 */
@media (max-width: 560px) {
  .hud { top: 58px; gap: 12px; padding: 8px 16px; font-size: 16px; }
  /* 정보줄이 내려온 만큼, 그 아래 뜨는 안내·미니게임·비 알림도 한 줄 더 아래로 (겹치지 않게) */
  .wrap .help.top, .wrap .mini, .wrap .alert { top: 112px; }
}
.help {
  position: absolute; bottom: 14px; left: 50%; transform: translateX(-50%);
  padding: 8px 16px; background: rgba(255, 255, 255, 0.85); border-radius: 10px; font-size: 14px;
}

.help.top { top: 66px; bottom: auto; width: max-content; max-width: 92vw; text-align: center; font-size: 13px; }

/* 터치 조작: 왼쪽 아래 조이스틱 */
.stick {
  position: absolute; left: 28px; bottom: 32px; width: 130px; height: 130px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.28); border: 2px solid rgba(255, 255, 255, 0.7);
  display: flex; align-items: center; justify-content: center;
}
.stick i { width: 56px; height: 56px; border-radius: 50%; background: rgba(255, 255, 255, 0.9); box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3); }
/* 터치 조작: 오른쪽 아래 달리기·점프 버튼 */
.pad-btns { position: absolute; right: 24px; bottom: 32px; display: flex; gap: 14px; align-items: flex-end; }
.pad-btn {
  width: 76px; height: 76px; border-radius: 50%; border: 2px solid rgba(255, 255, 255, 0.7);
  background: rgba(20, 40, 30, 0.6); color: #fff; font-size: 26px; line-height: 1.1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.pad-btn small { font-size: 12px; font-weight: 700; }
.pad-btn.on { background: #e8862e; border-color: #fff; }
.pad-btn:active { transform: scale(0.94); }

/* 미니게임 안내: 3D 화면 클릭(뒤집기)을 막지 않게 pointer-events를 꺼요 */
.mini {
  position: absolute; top: 66px; left: 50%; transform: translateX(-50%);
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  width: max-content; max-width: 92vw; padding: 12px 20px;
  background: rgba(255, 253, 247, 0.92); border-radius: 14px;
  font-size: 15px; text-align: center; pointer-events: none;
}
.mini > b { font-size: 19px; }
.mini .ouch { color: #c0392b; font-weight: 700; }
/* 남은 시간 막대: 10초 동안 줄어들어요 (minigame.js의 PLAY_SECONDS와 같은 시간) */
.mini-bar { width: 260px; max-width: 80vw; height: 8px; background: #e2e6dc; border-radius: 999px; overflow: hidden; }
.mini-bar i { display: block; height: 100%; background: #e8862e; transform-origin: left; animation: mini-shrink 10s linear forwards; }
@keyframes mini-shrink { from { transform: scaleX(1); } to { transform: scaleX(0); } }

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
