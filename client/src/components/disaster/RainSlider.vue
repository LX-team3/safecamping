<script setup>
// 3시간 강수량 슬라이더: 밀면 물이 차오르는 범위가 바뀜
// 눈금: 60·90mm 는 기상청 호우특보(주의보·경보) 기준, 120·150mm 는 범람 범위를 보여 주는 참고 단계(흐리게)
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
      <span class="src" :class="{ ai: state.stage.ai }">{{ state.stage.ai ? 'AI 판단' : '기준표' }}</span>
    </div>
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
  position: absolute; z-index: 6; left: 12px; right: 12px; top: calc(80px + env(safe-area-inset-top, 0px));
  background: rgba(20, 27, 33, 0.9); border: 1px solid var(--dm-line); border-radius: 12px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.4); padding: 6px 10px 4px; display: flex; flex-direction: column; backdrop-filter: blur(8px);
}
.top { display: flex; align-items: center; gap: 6px; }
.k { font-size: 11px; color: var(--dm-muted); }
.v { font-size: 11px; color: var(--dm-muted); margin-right: auto; }
.v b { font-family: var(--dm-font-num); font-size: 15px; color: var(--dm-text); margin-right: 1px; }
.stage { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--dm-card); }
.stage i { width: 8px; height: 8px; border-radius: 50%; }
.src { font-size: 10px; font-weight: 700; color: var(--dm-muted); border: 1px solid var(--dm-line); border-radius: 5px; padding: 1px 4px; }
.src.ai { color: var(--dm-water); border-color: var(--dm-water); }
input { -webkit-appearance: none; appearance: none; width: 100%; height: 26px; background: transparent; margin: 0; touch-action: pan-x; cursor: pointer; }
input::-webkit-slider-runnable-track { height: 6px; border-radius: 3px; background: var(--track); }
input::-moz-range-track { height: 6px; border-radius: 3px; background: var(--track); }
input::-webkit-slider-thumb { -webkit-appearance: none; width: 20px; height: 20px; margin-top: -7px; border-radius: 50%; background: #fff; border: 3px solid var(--dm-ground); box-shadow: 0 2px 6px rgba(0, 0, 0, .5); }
input::-moz-range-thumb { width: 16px; height: 16px; border-radius: 50%; background: #fff; border: 3px solid var(--dm-ground); }
input:focus-visible { outline: 2px solid var(--dm-water); outline-offset: 2px; border-radius: 6px; }
.ticks { position: relative; height: 12px; margin: -5px 10px 0; font-family: var(--dm-font-num); font-size: 9.5px; color: var(--dm-muted); }
.ticks span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
.ticks span b { font-weight: 700; color: var(--dm-text); margin-left: 2px; }
.ticks span.ref { opacity: 0.6; }
</style>
