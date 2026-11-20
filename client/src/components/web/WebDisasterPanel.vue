<script setup>
// 웹 3D 재난 대피 지도의 왼쪽 안내 패널: 제목 · 기상특보 · 비 슬라이더 · 대피 안내(기본 / 길안내)
// 앱은 이것들이 지도 위·아래에 흩어져 있고(MobileAlertPill · MobileRainSlider · MobileEvacSheet), 웹은 한 칸에 모았다
// 값은 전부 state(utils/disasterMap.js 가 채움)에서 오고, 버튼은 map 의 동작을 부른다
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
// 높낮이 그래프는 화면 배치가 없는 순수 그림이라 앱 것을 그대로 쓴다
import MobileRouteProfile from '@/components/mobile/MobileRouteProfile.vue'

const props = defineProps({
  state: { type: Object, required: true },
  map: { type: Object, default: null }, // createDisasterMap 이 돌려준 동작들
})
const emit = defineEmits(['rain'])

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
      <span class="loc">인북천 물빛테마공원 캠핑장 · 인제군 서화면</span>
    </header>

    <!-- 기상특보 -->
    <section class="alert" :class="{ warn: state.alert.warn }" aria-label="기상특보">
      <b>{{ state.alert.title }}</b><small>{{ state.alert.time }}</small>
      <p>{{ state.alert.area }}</p>
      <p>{{ state.alert.body }}</p>
      <p class="src">{{ state.alert.src }}</p>
    </section>

    <!-- 길안내 중에는 비 슬라이더를 숨긴다 (앱도 둘러보기 중에만 보임) -->
    <section v-show="!state.tourOn" class="box flood" aria-label="강수량에 따라 잠기는 범위">
      <div class="top">
        <span class="k">최근 3시간 비</span>
        <span class="v"><b>{{ state.rain }}</b>mm</span>
        <span class="stage"><i :style="{ background: state.stage.color }"></i>{{ state.stage.label }}</span>
        <span class="srcbadge" :class="{ ai: state.stage.ai }">{{ state.stage.ai ? 'AI 판단' : '기준표' }}</span>
      </div>
      <input type="range" min="0" :max="state.maxRain" step="5" :value="state.rain" aria-label="최근 3시간 동안 내린 비"
        :style="{ '--track': track }" @input="emit('rain', +$event.target.value)">
      <div class="ticks" aria-hidden="true">
        <span v-for="s in ticks" :key="s.from" :class="{ ref: s.ref }" :style="{ left: pct(s.from) + '%' }">
          {{ s.from }}<b v-if="s.alert">{{ s.alert }}</b>
        </span>
        <span style="left: 100%">mm</span>
      </div>
    </section>

    <!-- 기본 화면 -->
    <template v-if="state.mode === 'overview'">
      <section class="box">
        <div class="state-row">
          <span class="badge">{{ state.lv.badge }}</span>
          <div>
            <h2>{{ state.lv.head }}</h2>
            <p>{{ state.dest.sum }}</p>
          </div>
        </div>

        <div class="shelter">
          <span class="name">{{ state.dest.name }}</span>
          <span class="meta">{{ state.dest.meta }}</span>
          <button type="button" class="where" @click="map?.where()">위치 보기</button>
          <div class="dist">
            <b>{{ state.dest.distNum }}<span class="unit">{{ state.dest.distUnit }}</span></b>
            <small>{{ state.dest.min }}</small>
          </div>
        </div>

        <div class="seg" role="group" aria-label="경로 선택">
          <button type="button" :aria-pressed="state.routeKey === 'base'" :disabled="state.baseDisabled"
            @click="map?.selectRoute('base')">{{ state.baseLabel }}</button>
          <button type="button" :aria-pressed="state.routeKey === 'detour'" @click="map?.selectRoute('detour')">높은 곳 (참고)</button>
        </div>

        <div class="actions">
          <button type="button" class="btn primary" @click="map?.startNav()">{{ state.lv.cta }}</button>
          <button type="button" class="btn ghost" @click="map?.toggleTour()">{{ state.tourOn ? '■ 미리 보기 멈추기' : '▶ 대피 경로 따라가기' }}</button>
        </div>
      </section>

      <MobileRouteProfile :state="state" />

      <section class="todo-box">
        <h3>침수 시 행동요령</h3>
        <ul class="todo">
          <li v-for="(t, i) in state.todo" :key="i"><span>{{ i + 1 }}</span><span>{{ t }}</span></li>
        </ul>
      </section>

      <div class="emergency"><span>긴급 신고</span><b>119</b></div>
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
.srcbadge { font-size: 10px; font-weight: 700; color: var(--dm-muted); border: 1px solid var(--dm-line); border-radius: 5px; padding: 1px 4px; }
.srcbadge.ai { color: var(--dm-water); border-color: var(--dm-water); }
input[type='range'] { -webkit-appearance: none; appearance: none; width: 100%; height: 26px; background: transparent; margin: 0; cursor: pointer; }
input[type='range']::-webkit-slider-runnable-track { height: 6px; border-radius: 3px; background: var(--track); }
input[type='range']::-moz-range-track { height: 6px; border-radius: 3px; background: var(--track); }
input[type='range']::-webkit-slider-thumb { -webkit-appearance: none; width: 20px; height: 20px; margin-top: -7px; border-radius: 50%; background: var(--color-map-line); border: 3px solid var(--dm-ground); box-shadow: 0 2px 6px rgba(0, 0, 0, 0.5); }
input[type='range']::-moz-range-thumb { width: 16px; height: 16px; border-radius: 50%; background: var(--color-map-line); border: 3px solid var(--dm-ground); }
input[type='range']:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; border-radius: 6px; }
.ticks { position: relative; height: 14px; margin: -5px 10px 0; font-family: var(--dm-font-num); font-size: 10px; color: var(--dm-muted); }
.ticks span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.ticks span b { font-weight: 700; color: var(--dm-text); margin-left: 2px; }
.ticks span.ref { opacity: 0.6; }

.state-row { display: flex; gap: 12px; align-items: center; }
.badge { flex: none; font-size: 13px; font-weight: 700; padding: 5px 10px; border-radius: 8px; background: var(--lv); color: var(--color-dm-on-light); }
.state-row h2 { margin: 0; font-size: 18px; line-height: 1.4; font-weight: 700; text-wrap: balance; }
.state-row p { margin: 4px 0 0; font-size: 13px; color: var(--dm-muted); line-height: 1.5; }

.shelter { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; background: var(--dm-sheet); border-radius: 12px; padding: 12px; }
.shelter .name { font-size: 16px; font-weight: 700; }
.shelter .meta { font-size: 13px; color: var(--dm-muted); }
.shelter .dist { grid-row: 1 / span 3; grid-column: 2; align-self: center; text-align: right; }
.shelter .dist b { font-family: var(--dm-font-num); font-size: 22px; display: block; }
.shelter .dist .unit { font-size: 13px; }
.shelter .dist small { font-size: 11px; color: var(--dm-muted); }
.shelter .where { font: inherit; justify-self: start; margin-top: 4px; font-size: 12px; font-weight: 700; color: var(--dm-water); background: none; border: 1px solid var(--dm-water); border-radius: 999px; padding: 4px 10px; cursor: pointer; }

.btn { font: inherit; font-weight: 700; border: 0; border-radius: 12px; cursor: pointer; min-height: 48px; font-size: 15px; }
.btn.primary { background: var(--lv); color: var(--color-dm-on-light); }
.btn.ghost { background: transparent; color: var(--dm-text); border: 1px solid var(--dm-line); min-height: 44px; font-size: 14px; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
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
.emergency { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--dm-muted); border-top: 1px solid var(--dm-line); padding-top: 12px; }
.emergency b { font-family: var(--dm-font-num); font-size: 20px; color: var(--dm-text); user-select: all; }

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
