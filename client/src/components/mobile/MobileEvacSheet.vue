<script setup>
// 아래 대피 안내 시트: 기본(대피 장소·경로 고르기·행동요령) / 길안내(단계별) 두 모양
// 지도를 만지면 접히고(지도가 가려지지 않게), 손잡이를 누르면 펼쳐진다
defineProps({
  state: { type: Object, required: true },
  map: { type: Object, default: null },   // utils/disasterMap.js 가 돌려준 동작들
})
</script>

<template>
  <section class="sheet" :class="{ min: !state.sheetOpen }" aria-label="대피 안내">
    <button type="button" class="grab" aria-label="안내 접기·펼치기" :aria-expanded="state.sheetOpen"
      @click="state.sheetOpen = !state.sheetOpen"></button>

    <!-- 기본 화면 -->
    <div v-if="state.mode === 'overview'" class="sec">
      <div class="state-row">
        <span class="badge">{{ state.lv.badge }}</span>
        <h2>{{ state.lv.head }}</h2>
      </div>

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

      <div class="sec small">
        <h3>{{ state.lv.badge }} 단계 행동요령</h3>
        <ul class="todo">
          <li v-for="(t, i) in state.todo" :key="i"><span>{{ i + 1 }}</span><span>{{ t }}</span></li>
        </ul>
      </div>

      <!-- 휴대폰에서 누르면 바로 119 전화 걸기 화면이 열린다 (tel: 링크) -->
      <a class="emergency" href="tel:119" aria-label="119에 전화 걸기"><span>긴급 신고 · 눌러서 전화</span><b>119</b></a>
    </div>

    <!-- 길안내 화면 -->
    <div v-else class="sec">
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
    </div>
  </section>
</template>

<style scoped>
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; z-index: 7; background: var(--dm-sheet); border-radius: 20px 20px 0 0;
  box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.45); max-height: 40%; overflow-y: auto; overscroll-behavior: contain;
  padding: 6px 16px calc(18px + env(safe-area-inset-bottom, 0px));
  display: flex; flex-direction: column; gap: 14px; transition: max-height 0.25s ease;
}
.sheet.min { max-height: 168px; }
.grab { font: inherit; align-self: center; width: 64px; height: 22px; border: 0; background: transparent; cursor: pointer; display: grid; place-items: center; flex: none; }
.grab::before { content: ""; width: 40px; height: 5px; border-radius: 3px; background: var(--color-dm-grab); }
.grab:focus-visible { outline: 2px solid var(--dm-water); border-radius: 6px; }

.sec { display: flex; flex-direction: column; gap: 14px; }
.sec.small { gap: 8px; }
.sec h3 { margin: 0; font-size: 13px; color: var(--dm-muted); font-weight: 500; }

.state-row { display: flex; gap: 12px; align-items: center; }
.badge { flex: none; font-size: 13px; font-weight: 700; padding: 5px 10px; border-radius: 8px; background: var(--lv); color: var(--color-dm-on-light); }
.state-row h2 { margin: 0; font-size: 19px; line-height: 1.4; font-weight: 700; text-wrap: balance; word-break: keep-all; }

.shelter { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; background: var(--dm-card); border-radius: 14px; padding: 14px; }
.shelter .name { font-size: 17px; font-weight: 700; }
.shelter .meta { font-size: 13px; color: var(--dm-muted); }
.shelter .dist { grid-row: 1 / span 3; grid-column: 2; align-self: center; text-align: right; }
.shelter .dist b { font-family: var(--dm-font-num); font-size: 22px; display: block; }
.shelter .dist .unit { font-size: 13px; }
.shelter .dist small { font-size: 11px; color: var(--dm-muted); }
.shelter .links { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; }
.shelter .where { font: inherit; text-decoration: none; font-size: 12px; font-weight: 700; color: var(--dm-water); background: none; border: 1px solid var(--dm-water); border-radius: 999px; padding: 4px 10px; cursor: pointer; }

.btn { font: inherit; font-weight: 700; border: 0; border-radius: 14px; cursor: pointer; min-height: 52px; font-size: 16px; }
.btn.primary { background: var(--lv); color: var(--color-dm-on-light); }
.btn.ghost { background: transparent; color: var(--dm-text); border: 1px solid var(--dm-line); min-height: 46px; font-size: 14px; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn:focus-visible, .seg button:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; }
.btn.link { display: flex; align-items: center; justify-content: center; text-decoration: none; }
.btn.detour { border-color: var(--dm-caution); color: var(--dm-caution); }
.actions { display: grid; gap: 8px; }

.seg { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px; background: var(--dm-card); padding: 4px; border-radius: 12px; margin-top: -4px; }
.seg button { font: inherit; font-size: 13px; font-weight: 700; color: var(--dm-muted); background: transparent; border: 0; border-radius: 9px; height: 38px; cursor: pointer; }
.seg button[aria-pressed="true"] { background: var(--dm-text); color: var(--dm-ground); }
.seg button:disabled { opacity: 0.38; cursor: not-allowed; text-decoration: line-through; }

.todo { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.todo li { display: grid; grid-template-columns: 22px 1fr; gap: 10px; font-size: 14px; line-height: 1.5; background: var(--dm-card); border-radius: 12px; padding: 10px 12px; }
.todo li span:first-child { font-family: var(--dm-font-num); color: var(--lv); font-weight: 700; }
.emergency { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--dm-muted); border-top: 1px solid var(--dm-line); padding-top: 12px; text-decoration: none; min-height: 44px; }
.emergency b { font-family: var(--dm-font-num); font-size: 20px; color: var(--dm-text); }
.emergency:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; border-radius: 6px; }

.nav-top { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--dm-muted); }
.progress { height: 6px; background: var(--dm-card); border-radius: 3px; overflow: hidden; }
.progress i { display: block; height: 100%; background: var(--lv); transition: width 0.4s ease; }
.nav-step { display: flex; flex-direction: column; gap: 6px; }
.nav-step .no { font-family: var(--dm-font-num); color: var(--lv); font-size: 13px; font-weight: 700; }
.nav-step h2 { margin: 0; font-size: 22px; line-height: 1.35; text-wrap: balance; }
.nav-step .caution { margin: 0; font-size: 14px; color: var(--dm-caution); font-weight: 700; }
.nav-step .sub { margin: 0; font-size: 14px; color: var(--dm-muted); line-height: 1.5; }
.nav-btns { display: grid; grid-template-columns: 1fr 2fr; gap: 8px; }
@media (prefers-reduced-motion: reduce) { .sheet, .progress i { transition: none; } }
</style>
