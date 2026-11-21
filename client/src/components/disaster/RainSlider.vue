<script setup>
// 3시간 강수량 슬라이더: 밀면 단계와 물이 차오르는 범위가 바뀜
// 글자는 "3시간 강수량" (기상청 특보 기준과 같은 말. 실황이든 미리 보기든 맞는 표현이라 "내린 비" 대신 씀, 2026-11-21)
// 눈금: 60·90mm = 기상청 호우주의보·경보 기준, 114mm = 이 지역에 50년에 한 번 올 큰비 (criteria.json)
// 실황을 받으면 관측 시각과, 12·24시간 기준으로 단계가 정해졌으면 그 까닭을 한 줄로 보여 준다
// 2026-11-21 화면을 단순하게: "실시간"·"실시간 보기"·"시뮬레이션" 표시와 상류 수위 줄을 뺐다
//   (수위는 화면에만 안 보일 뿐 서버 단계 판단에는 그대로 쓴다. 막대를 만지면 새로고침 전까지 미리 보기로 남는다)
import { computed } from 'vue'

const props = defineProps({ state: { type: Object, required: true } })
const emit = defineEmits(['change'])

const pct = v => v / props.state.maxRain * 100
// 단계 구간별 색으로 칠한 막대
const track = computed(() => {
  const st = props.state.stages
  return 'linear-gradient(90deg,' + st.map((s, i) => `${s.color} ${pct(s.from)}% ${pct(st[i + 1] ? st[i + 1].from : props.state.maxRain)}%`).join(',') + ')'
})
const ticks = computed(() => props.state.stages.slice(1))
</script>

<template>
  <section class="flood" aria-label="강수량에 따라 잠기는 범위">
    <div class="top">
      <span class="k">3시간 강수량</span>
      <span class="v"><b>{{ state.rain }}</b>mm</span>
      <span class="stage"><i :style="{ background: state.stage.color }"></i>{{ state.stage.label }}</span>
    </div>
    <p v-if="state.live.on" class="note">
      {{ state.live.time }} · 기상청<template v-if="state.live.reasons.length"> · {{ state.live.reasons.join(', ') }}</template>
    </p>
    <input type="range" min="0" :max="state.maxRain" step="5" :value="state.rain" aria-label="3시간 강수량"
      :style="{ '--track': track }" @input="emit('change', +$event.target.value)">
    <div class="ticks" aria-hidden="true">
      <span v-for="s in ticks" :key="s.from" :class="{ ref: s.ref }" :style="{ left: pct(s.from) + '%' }">
        {{ s.from }}<b v-if="s.alert">{{ s.alert }}</b>
      </span>
      <span style="left: 100%">mm</span>
    </div>
  </section>
</template>

<style scoped>
.flood {
  /* 넓은 화면(PC)에서 막대가 끝없이 길어지지 않게 폭을 휴대폰 한 화면 정도로 둔다 */
  position: absolute; z-index: 6; left: 12px; width: min(440px, calc(100% - 24px)); box-sizing: border-box; top: calc(80px + env(safe-area-inset-top, 0px));
  background: var(--color-dm-shade); border: 1px solid var(--dm-line); border-radius: 12px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.4); padding: 6px 10px 4px; display: flex; flex-direction: column; backdrop-filter: blur(8px);
}
.top { display: flex; align-items: center; gap: 6px; }
.k { font-size: 11px; color: var(--dm-muted); }
.v { font-size: 11px; color: var(--dm-muted); margin-right: auto; }
.v b { font-family: var(--dm-font-num); font-size: 15px; color: var(--dm-text); margin-right: 1px; }
.stage { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--dm-card); }
.stage i { width: 8px; height: 8px; border-radius: 50%; }
.note { margin: 2px 0 0; font-size: 10.5px; color: var(--dm-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
input { -webkit-appearance: none; appearance: none; width: 100%; height: 26px; background: transparent; margin: 0; touch-action: pan-x; cursor: pointer; }
input::-webkit-slider-runnable-track { height: 6px; border-radius: 3px; background: var(--track); }
input::-moz-range-track { height: 6px; border-radius: 3px; background: var(--track); }
input::-webkit-slider-thumb { -webkit-appearance: none; width: 20px; height: 20px; margin-top: -7px; border-radius: 50%; background: var(--color-map-line); border: 3px solid var(--dm-ground); box-shadow: 0 2px 6px rgba(0, 0, 0, .5); }
input::-moz-range-thumb { width: 16px; height: 16px; border-radius: 50%; background: var(--color-map-line); border: 3px solid var(--dm-ground); }
input:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; border-radius: 6px; }
.ticks { position: relative; height: 12px; margin: -5px 10px 0; font-family: var(--dm-font-num); font-size: 9.5px; color: var(--dm-muted); }
.ticks span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.ticks span b { font-weight: 700; color: var(--dm-text); margin-left: 2px; }
.ticks span.ref { opacity: 0.6; }
</style>
