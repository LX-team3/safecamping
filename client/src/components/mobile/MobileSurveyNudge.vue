<script setup>
// 1분 진단 안내 팝업 (웹 홈 팝업과 같은 디자인, 390px 틀에 맞게 줄임)
// 보일지 말지는 부르는 화면이 정한다. 이 부품은 모양과 버튼만 맡는다
//   start: 진단 시작하기 / hideToday: 오늘 하루 보지 않기 / close: 닫기 · 어두운 바탕 누르기
const emit = defineEmits(['start', 'hideToday', 'close'])
</script>

<template>
  <!-- 어두운 바탕을 누르면 닫힌다. 카드 안쪽을 눌렀을 때는 닫히지 않게 .self -->
  <div class="back" @click.self="emit('close')">
    <div class="nudge" role="dialog" aria-modal="true" aria-labelledby="nudge-title">
      <div class="main">
        <div class="head">
          <span class="eyebrow">CAMPINE · 1분 안전 진단</span>
          <h2 id="nudge-title">이번 캠핑,<br />나는 얼마나 대비됐을까?</h2>
        </div>

        <!-- 장식 그림이라 스크린리더가 읽지 않게 aria-hidden -->
        <div class="note note-l" aria-hidden="true">
          <div class="strip accent"></div>
          <div class="note-body">
            <span class="checkline"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10" /></svg><i></i></span>
            <span class="checkline"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10" /></svg><i></i></span>
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

        <div class="cta">
          <!-- 앱·웹 진단은 같은 8문항이다 (utils/webSurvey.js) -->
          <span class="bubble">8문항 · 약 1분 · 로그인 없이<i aria-hidden="true"></i></span>
          <button type="button" class="start" @click="emit('start')">진단 시작하기</button>
        </div>
      </div>

      <div class="foot">
        <button type="button" class="hide" @click="emit('hideToday')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></svg>오늘 하루 보지 않기
        </button>
        <button type="button" class="close" @click="emit('close')">닫기</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 화면 전체를 어둡게 덮고 가운데에 카드. 낮은 화면에서는 스크롤로 아래까지 볼 수 있다 */
.back {
  position: absolute;
  inset: 0;
  z-index: 100;
  padding: 16px;
  display: flex;
  overflow-y: auto;
  background: var(--color-nudge-dim);
}
.nudge {
  margin: auto; /* 가로·세로 정중앙. 화면보다 크면 위에서부터 스크롤 */
  flex: none;
  width: 100%;
  max-width: 344px;
  border-radius: 28px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.3);
}
.main {
  position: relative;
  height: 440px;
  background: var(--color-nudge-bg);
  color: var(--color-nudge-green);
  overflow: hidden;
}
.head {
  position: relative;
  z-index: 2;
  padding: 32px 20px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  word-break: keep-all;
}
.eyebrow {
  font-size: 14px;
  font-weight: 700;
}
.head h2 {
  margin: 8px 0 0;
  font-size: 30px;
  line-height: 1.24;
  font-weight: 700;
  letter-spacing: -1.2px;
}
.note {
  position: absolute;
  z-index: 2;
  width: 112px;
  padding: 0 0 10px;
  border-radius: 8px;
  background: var(--color-card);
  box-shadow: 0 6px 14px rgba(12, 31, 9, 0.15);
  overflow: hidden;
}
.note-l { left: 40px; top: 180px; transform: rotate(-10deg); }
.note-r { right: 40px; top: 194px; transform: rotate(8deg); }
.strip { height: 11px; }
.strip.accent { background: var(--color-nudge-accent); }
.strip.green { background: var(--color-nudge-green); }
.note-body {
  padding: 9px 10px 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.checkline { display: flex; align-items: center; gap: 6px; }
.checkline svg { stroke: var(--color-nudge-accent); }
.checkline i { flex: 1; height: 6px; border-radius: 3px; background: var(--color-nudge-bar); }
.checkline .box {
  width: 11px;
  height: 11px;
  margin: 0 1px;
  border: 2px solid var(--color-nudge-box);
  border-radius: 3px;
}
.score-body { padding: 8px 10px 0; display: flex; flex-direction: column; gap: 5px; }
.s-label { font-size: 10px; font-weight: 700; }
.s-num { font-size: 24px; line-height: 1; font-weight: 700; }
.s-num small { font-size: 13px; }
.track { display: block; height: 6px; border-radius: 3px; background: var(--color-nudge-track); overflow: hidden; }
.track em { display: block; width: 45%; height: 100%; background: var(--color-nudge-accent); }
.cta {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 28px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.bubble {
  position: relative;
  padding: 9px 18px;
  border-radius: 999px;
  background: var(--color-nudge-accent);
  color: var(--color-card);
  font-size: 13px;
  font-weight: 700;
}
.bubble i {
  position: absolute;
  left: 50%;
  bottom: -6px;
  width: 12px;
  height: 12px;
  background: var(--color-nudge-accent);
  transform: translateX(-50%) rotate(45deg);
}
.start {
  width: 232px;
  height: 56px;
  border: 0;
  border-radius: 999px;
  background: var(--color-nudge-green);
  color: var(--color-card);
  font: inherit;
  font-size: 17px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(12, 31, 9, 0.18);
  cursor: pointer;
}
.foot {
  background: var(--color-bg);
  padding: 14px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hide,
.close {
  background: none;
  border: 0;
  padding: 0;
  font: inherit;
  cursor: pointer;
}
.hide { display: flex; align-items: center; gap: 8px; color: var(--color-text-sub); font-size: 14px; }
.hide svg { color: var(--color-arrow); }
.close { color: var(--color-nudge-green); font-size: 15px; font-weight: 700; }
.start:focus-visible, .hide:focus-visible, .close:focus-visible { outline: 2px solid var(--color-nudge-green); outline-offset: 2px; }
</style>
