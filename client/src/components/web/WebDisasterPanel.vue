<script setup>
// 웹 3D 재난 대피 지도의 왼쪽 안내 패널: 제목 · 기상특보 · 비 슬라이더 · 대피 안내(기본 / 길안내)
// 앱은 이것들이 지도 위·아래에 흩어져 있고(MobileAlertPill · MobileRainSlider · MobileEvacSheet), 웹은 한 칸에 모았다
// 값은 전부 state(utils/disasterMap.js 가 채움)에서 오고, 버튼은 map 의 동작을 부른다
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  state: { type: Object, required: true },
  map: { type: Object, default: null }, // createDisasterMap 이 돌려준 동작들
  alert: { type: Object, required: true }, // 특보 칸 (utils/disasterLive.js, 미리 보기 중에는 예시 특보)
})
const emit = defineEmits(['rain', 'live'])

// ---- 비 슬라이더: 단계 구간별 색으로 칠한 막대 (앱 MobileRainSlider 와 같은 계산)
const pct = (v) => (v / props.state.maxRain) * 100
const track = computed(() => {
  const st = props.state.stages
  return 'linear-gradient(90deg,' + st.map((s, i) =>
    `${s.color} ${pct(s.from)}% ${pct(st[i + 1] ? st[i + 1].from : props.state.maxRain)}%`).join(',') + ')'
})
const ticks = computed(() => props.state.stages.slice(1))
</script>

<template>
  <aside class="panel" aria-label="재난 대피 안내">
    <header class="head">
      <RouterLink to="/web" class="back">‹ CAMPIN 홈</RouterLink>
      <h1>호우 및 침수 시 대피경로</h1>
      <span class="loc">인북천 물빛테마공원 캠핑장</span>
    </header>

    <!-- 기상특보 -->
    <section class="alert" :class="{ warn: alert.warn, calm: alert.calm && !alert.off, off: alert.off }" aria-label="기상특보">
      <b>{{ alert.title }}</b><small>{{ alert.time }}</small>
      <p v-if="alert.area">{{ alert.area }}</p>
      <p>{{ alert.body }}</p>
      <p v-if="alert.src" class="src">{{ alert.src }}</p>
    </section>

    <section class="box flood" aria-label="강수량에 따라 잠기는 범위">
      <div class="top">
        <span class="k">3시간 강수량</span>
        <span class="v"><b>{{ state.rain }}</b>mm</span>
        <span class="stage"><i :style="{ background: state.stage.color }"></i>{{ state.stage.label }}</span>
      </div>
      <p v-if="state.live.on" class="note">
        {{ state.live.time }} · 기상청<template v-if="state.live.reasons.length"> · {{ state.live.reasons.join(', ') }}</template>
      </p>
      <!-- 막대로 미리 보는 중이고 받아 둔 실황이 있으면, 실제 상황으로 돌아가는 버튼 (앱과 같음) -->
      <p v-else-if="state.live.status === 'ok'" class="note preview">
        미리 보기 중 <button type="button" class="golive" @click="emit('live')">↺ 현상황 보기</button>
      </p>
      <input type="range" min="0" :max="state.maxRain" step="5" :value="state.rain" aria-label="3시간 강수량"
        :style="{ '--track': track }" @input="emit('rain', +$event.target.value)">
      <div class="ticks" aria-hidden="true">
        <span v-for="s in ticks" :key="s.from" :class="{ wide: (s.alert || '').length > 3 }" :style="{ left: pct(s.from) + '%' }">
          {{ s.from }}<b v-if="s.alert">{{ s.alert }}</b>
        </span>
        <span style="left: 100%">mm</span>
      </div>
      <p v-if="state.waterText" class="note water">{{ state.waterText }}</p>
    </section>

    <!-- 기본 화면 -->
    <template v-if="state.mode === 'overview'">
      <section class="box">
        <div class="state-row">
          <span class="badge">{{ state.lv.badge }}</span>
          <h2>{{ state.lv.head }}</h2>
        </div>

        <!-- 침수(114mm 이상)에는 경로·목적지·길안내를 빼고 할 일만 (앱 MobileEvacSheet 와 같음) -->
        <template v-if="!state.noRoute">
        <div class="shelter">
          <span class="name">{{ state.dest.name }}</span>
          <span class="meta">{{ state.dest.meta }}</span>
          <div class="links">
          <button type="button" class="where" @click="map?.where()">위치 보기</button>
        </div>
          <div class="dist">
            <b>{{ state.dest.distNum }}<span class="unit">{{ state.dest.distUnit }}</span></b>
            <small>{{ state.dest.min }}</small>
          </div>
        </div>

        <div class="seg" role="group" aria-label="경로 선택">
          <button type="button" :aria-pressed="state.routeKey === 'base'" :disabled="state.baseDisabled"
            @click="map?.selectRoute('base')">{{ state.baseLabel }}</button>
          <button type="button" :aria-pressed="state.routeKey === 'detour'" @click="map?.selectRoute('detour')">가까운 높은 곳</button>
        </div>

        <div class="actions">
          <button type="button" class="btn primary" @click="map?.startNav()">{{ state.lv.cta }}</button>
          <!-- 카카오맵 걷기 길찾기 (대피소 경로일 때만, 새 창) -->
          <a v-if="state.dest.navUrl" class="btn ghost link" :href="state.dest.navUrl" target="_blank" rel="noopener">카카오맵 길찾기</a>
        </div>
        </template>
      </section>

      <section class="todo-box">
        <h3>{{ state.lv.badge }} 단계 행동요령</h3>
        <ul class="todo">
          <li v-for="(t, i) in state.todo" :key="i"><span>{{ i + 1 }}</span><span>{{ t }}</span></li>
        </ul>
      </section>

      <!-- 휴대폰·전화 앱이 있는 PC 에서 누르면 바로 119 전화 걸기 (tel: 링크, 앱과 같음) -->
      <a class="emergency" href="tel:119" aria-label="119에 전화 걸기"><span>긴급 신고 · 눌러서 전화</span><b>119</b></a>
    </template>

    <!-- 길안내 화면 -->
    <section v-else class="box nav">
      <div class="nav-top"><span>{{ state.nav.left }}</span><span>{{ state.nav.count }}</span></div>
      <div class="progress" aria-hidden="true"><i :style="{ width: state.nav.bar + '%' }"></i></div>
      <div class="nav-step" aria-live="polite">
        <span class="no">{{ state.nav.no }}</span>
        <h2>{{ state.nav.head }}</h2>
        <p v-if="state.nav.caution" class="caution">{{ state.nav.caution }}</p>
        <p v-if="state.nav.sub" class="sub">{{ state.nav.sub }}</p>
      </div>
      <div class="nav-btns">
        <button type="button" class="btn ghost" :disabled="state.nav.prevDisabled" @click="map?.prev()">이전</button>
        <button type="button" class="btn primary" @click="map?.next()">{{ state.nav.nextLabel }}</button>
      </div>
      <button v-if="state.detourBtn" type="button" class="btn ghost detour" @click="map?.detour()">
        {{ state.baseLabel.replace(/^대피소 \((.*)\)$/, '$1') }}가 잠겼어요 · 높은 곳으로
      </button>
      <button type="button" class="btn ghost" @click="map?.endNav()">길안내 종료</button>
    </section>
  </aside>
</template>

<style scoped>
/* 색 변수(--dm-*, --lv)는 이 부품을 감싼 WebDisasterMapView 가 정한다 */
.panel {
  flex: 0 0 400px; height: 100%; overflow-y: auto; overscroll-behavior: contain;
  background: var(--dm-sheet); border-right: 1px solid var(--dm-line);
  padding: 20px 20px 28px; display: flex; flex-direction: column; gap: 14px;
  word-break: keep-all; overflow-wrap: break-word;
}
.head { display: flex; flex-direction: column; gap: 3px; }
.back { align-self: flex-start; font-size: 13px; font-weight: 700; color: var(--dm-water); margin-bottom: 6px; }
.head h1 { margin: 0; font-size: 20px; line-height: 1.35; font-weight: 700; }
.loc { font-size: 12px; color: var(--dm-muted); }

.alert {
  background: var(--dm-caution); color: var(--color-dm-on-light); border-radius: 12px; padding: 10px 14px;
  font-size: 13px; line-height: 1.6;
}
.alert.warn { background: var(--dm-severe); }
/* 앱 특보 알림(MobileAlertPill)과 같은 색: 특보 없음 = 안전 색, 못 받음 = 회색 */
.alert.calm { background: var(--dm-safe); }
.alert.off { background: var(--dm-card); color: var(--dm-text); }
.alert b { font-size: 14px; margin-right: 8px; }
.alert small { font-size: 11px; opacity: 0.85; }
.alert p { margin: 4px 0 0; }
.alert .src { font-size: 11px; opacity: 0.8; }

.box { background: var(--dm-card); border-radius: 14px; padding: 14px; display: flex; flex-direction: column; gap: 14px; }

.flood { gap: 4px; padding: 10px 14px 8px; }
.top { display: flex; align-items: center; gap: 8px; }
.k { font-size: 12px; color: var(--dm-muted); }
.v { font-size: 12px; color: var(--dm-muted); margin-right: auto; }
.v b { font-family: var(--dm-font-num); font-size: 18px; color: var(--dm-text); margin-right: 1px; }
.stage { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--dm-sheet); }
.stage i { width: 8px; height: 8px; border-radius: 50%; }
.note.water { margin-top: 6px; }
.note { margin: 2px 0 0; font-size: 11px; color: var(--dm-muted); }
.note.preview { display: flex; align-items: center; gap: 8px; }
.golive { font: inherit; font-size: 11px; font-weight: 700; color: var(--dm-water); background: none; border: 1px solid var(--dm-water); border-radius: 999px; padding: 2px 9px; cursor: pointer; }
.golive:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; }
input[type='range'] { -webkit-appearance: none; appearance: none; width: 100%; height: 26px; background: transparent; margin: 0; cursor: pointer; }
input[type='range']::-webkit-slider-runnable-track { height: 6px; border-radius: 3px; background: var(--track); }
input[type='range']::-moz-range-track { height: 6px; border-radius: 3px; background: var(--track); }
input[type='range']::-webkit-slider-thumb { -webkit-appearance: none; width: 20px; height: 20px; margin-top: -7px; border-radius: 50%; background: var(--color-map-line); border: 3px solid var(--dm-ground); box-shadow: 0 2px 6px rgba(0, 0, 0, 0.5); }
input[type='range']::-moz-range-thumb { width: 16px; height: 16px; border-radius: 50%; background: var(--color-map-line); border: 3px solid var(--dm-ground); }
input[type='range']:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; border-radius: 6px; }
.ticks { position: relative; height: 14px; margin: -5px 10px 0; font-family: var(--dm-font-num); font-size: 10px; color: var(--dm-muted); }
.ticks span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.ticks span b { font-weight: 700; color: var(--dm-text); margin-left: 2px; }
/* 글자가 긴 눈금(4글자 이상)은 왼쪽 끝을 눈금에 맞춰 옆 눈금과 겹치지 않게 (앱과 같음) */
.ticks span.wide { transform: translateX(-6px); }

.state-row { display: flex; gap: 12px; align-items: center; }
.badge { flex: none; font-size: 13px; font-weight: 700; padding: 5px 10px; border-radius: 8px; background: var(--lv); color: var(--color-dm-on-light); }
.state-row h2 { margin: 0; font-size: 18px; line-height: 1.4; font-weight: 700; text-wrap: balance; }

.shelter { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; background: var(--dm-sheet); border-radius: 12px; padding: 12px; }
.shelter .name { font-size: 16px; font-weight: 700; }
.shelter .meta { font-size: 13px; color: var(--dm-muted); }
.shelter .dist { grid-row: 1 / span 3; grid-column: 2; align-self: center; text-align: right; }
.shelter .dist b { font-family: var(--dm-font-num); font-size: 22px; display: block; }
.shelter .dist .unit { font-size: 13px; }
.shelter .dist small { font-size: 11px; color: var(--dm-muted); }
.shelter .links { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; }
.shelter .where { font: inherit; text-decoration: none; font-size: 12px; font-weight: 700; color: var(--dm-water); background: none; border: 1px solid var(--dm-water); border-radius: 999px; padding: 4px 10px; cursor: pointer; }

.btn { font: inherit; font-weight: 700; border: 0; border-radius: 12px; cursor: pointer; min-height: 48px; font-size: 15px; }
.btn.primary { background: var(--lv); color: var(--color-dm-on-light); }
.btn.ghost { background: transparent; color: var(--dm-text); border: 1px solid var(--dm-line); min-height: 44px; font-size: 14px; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn.link { display: flex; align-items: center; justify-content: center; text-decoration: none; }
.btn.detour { border-color: var(--dm-caution); color: var(--dm-caution); }
.btn:focus-visible, .seg button:focus-visible, .where:focus-visible, .back:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; }
.actions { display: grid; gap: 8px; }

.seg { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px; background: var(--dm-sheet); padding: 4px; border-radius: 12px; }
.seg button { font: inherit; font-size: 13px; font-weight: 700; color: var(--dm-muted); background: transparent; border: 0; border-radius: 9px; height: 38px; cursor: pointer; }
.seg button[aria-pressed='true'] { background: var(--dm-text); color: var(--dm-ground); }
.seg button:disabled { opacity: 0.38; cursor: not-allowed; text-decoration: line-through; }

.todo-box h3 { margin: 0 0 8px; font-size: 13px; color: var(--dm-muted); font-weight: 500; }
.todo { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.todo li { display: grid; grid-template-columns: 22px 1fr; gap: 10px; font-size: 14px; line-height: 1.5; background: var(--dm-card); border-radius: 12px; padding: 10px 12px; }
.todo li span:first-child { font-family: var(--dm-font-num); color: var(--lv); font-weight: 700; }
.emergency { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--dm-muted); border-top: 1px solid var(--dm-line); padding-top: 12px; text-decoration: none; min-height: 44px; }
.emergency b { font-family: var(--dm-font-num); font-size: 20px; color: var(--dm-text); }
.emergency:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; border-radius: 6px; }

.nav-top { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--dm-muted); }
.progress { height: 6px; background: var(--dm-sheet); border-radius: 3px; overflow: hidden; }
.progress i { display: block; height: 100%; background: var(--lv); transition: width 0.4s ease; }
.nav-step { display: flex; flex-direction: column; gap: 6px; }
.nav-step .no { font-family: var(--dm-font-num); color: var(--lv); font-size: 13px; font-weight: 700; }
.nav-step h2 { margin: 0; font-size: 22px; line-height: 1.35; text-wrap: balance; }
.nav-step .caution { margin: 0; font-size: 14px; color: var(--dm-caution); font-weight: 700; }
.nav-step .sub { margin: 0; font-size: 14px; color: var(--dm-muted); line-height: 1.5; }
.nav-btns { display: grid; grid-template-columns: 1fr 2fr; gap: 8px; }
@media (prefers-reduced-motion: reduce) { .progress i { transition: none; } }
</style>
