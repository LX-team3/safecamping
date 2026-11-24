<script setup>
// 차박지 필터 시트 — 차박 화면 칩 줄에서 연다 (only 로 한 부분만 보여 줌)
//  - only='avoid' (맨 왼쪽 필터 아이콘): 피해야 할 곳 — 불가 구역 · 주의 구역 (면, 처음엔 꺼짐 — 켜면 지도가 빨갛게 덮여서)
//  - only='safety' ("안전점수 ▾" 칩): 안전점수로 거르기 (전체 · 위험 제외 40↑ · 안전만 70↑, 캠핑장 등급과 같은 기준) — 점에만 적용
//  - only 없음: 전부 (갈 수 있는 곳 차박 후보지·등록 야영장 포함 — 지금은 칩으로 밖에 나와 있어 안 씀)
// 고르면 바로 지도에 반영된다 (적용 버튼 없음). 닫기는 ✕ · 바깥 · 손잡이 끌기 (공통 MobileBottomSheet)
import { computed } from 'vue'
import MobileBottomSheet from '@/components/mobile/MobileBottomSheet.vue'
import { SAFETY_FILTERS } from '@/utils/chabak'

const props = defineProps({
  spots: { type: Array, required: true }, // [{ type, section: go·avoid, label, note, colorVar }] (utils/chabak.js LAYER_CHIPS + 색)
  on: { type: Object, required: true }, // { open: true, camp: true } 켜짐 여부
  counts: { type: Object, required: true }, // { open: 12, camp: 18 } 필터를 거친 개수 (받기 전이면 null)
  safety: { type: String, required: true }, // SAFETY_FILTERS 의 key
  only: { type: String, default: null }, // null · 'avoid' · 'safety'
})
const emit = defineEmits(['close', 'toggle', 'safety'])

const SECTIONS = [
  { key: 'go', title: '갈 수 있는 곳' },
  { key: 'avoid', title: '피해야 할 곳' },
]
const groups = computed(() =>
  props.only === 'safety'
    ? []
    : SECTIONS.filter((s) => !props.only || s.key === props.only)
        .map((s) => ({ ...s, spots: props.spots.filter((x) => x.section === s.key) }))
        .filter((g) => g.spots.length),
)
const showSafety = computed(() => !props.only || props.only === 'safety')
const title = computed(() => ({ avoid: '지도에 구역 표시', safety: '안전점수' })[props.only] ?? '차박지 필터')
</script>

<template>
  <MobileBottomSheet :title="title" @close="emit('close')">
    <template v-for="g in groups" :key="g.key">
    <h3 v-if="!only" class="sub">{{ g.title }}</h3>
    <p v-else class="hint">켜면 지도에 구역을 칠해요 · 정확한 판정은 지도를 눌러서 봐요</p>
    <ul class="rows">
      <li v-for="s in g.spots" :key="s.type" class="row">
        <span class="icon" aria-hidden="true"><i class="dot" :style="{ background: `var(${s.colorVar})` }" /></span>
        <div class="text">
          <b>{{ s.label }} <span v-if="counts[s.type] != null" class="count">{{ counts[s.type] }}곳</span></b>
          <span>{{ s.note }}</span>
        </div>
        <button type="button" role="switch" class="switch" :class="{ on: on[s.type] }" :aria-checked="!!on[s.type]"
          :aria-label="`${s.label} 지도에 보이기`" @click="emit('toggle', s.type)">
          <i />
        </button>
      </li>
    </ul>
    </template>

    <template v-if="showSafety">
    <h3 v-if="!only" class="sub">안전점수</h3>
    <div class="seg" role="radiogroup" aria-label="안전점수 필터">
      <button v-for="f in SAFETY_FILTERS" :key="f.key" type="button" role="radio" :class="{ on: safety === f.key }"
        :aria-checked="safety === f.key" @click="emit('safety', f.key)">
        <i v-if="f.colorVar" class="sdot" :style="{ background: `var(${f.colorVar})` }" aria-hidden="true" />
        {{ f.label }}
      </button>
    </div>
    </template>
  </MobileBottomSheet>
</template>

<style scoped>
.sub {
  margin: 4px 0 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-sub);
}
.sub + .rows {
  margin-top: 4px;
}
.rows {
  margin: 0 0 16px;
  padding: 0;
  list-style: none;
}
.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-line);
}
.icon {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.text b {
  font-size: 15px;
}
.text .count {
  font-weight: 500;
  color: var(--color-text-sub);
}
.text > span {
  font-size: 12px;
  color: var(--color-text-sub);
}
.hint {
  margin: 2px 0 10px;
  font-size: 12px;
  color: var(--color-text-sub);
}
/* 스위치 · 고르기 모양은 "지도 설정" 시트(LayerSheet)와 같게 */
.switch {
  flex: none;
  width: 50px;
  height: 30px;
  padding: 3px;
  border: none;
  border-radius: 15px;
  background: var(--color-track);
  display: flex;
  cursor: pointer;
  transition: background 0.15s;
}
.switch i {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-card);
  transition: transform 0.15s;
}
.switch.on {
  background: var(--color-text);
}
.switch.on i {
  transform: translateX(20px);
}
.seg {
  display: flex;
  margin: 8px 0 4px;
  padding: 4px;
  border-radius: 24px;
  background: var(--color-bg-soft);
}
.seg button {
  flex: 1;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: none;
  border-radius: 20px;
  background: none;
  font-size: 15px;
  color: var(--color-text);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.sdot {
  flex: none;
  width: 9px;
  height: 9px;
  border-radius: 50%;
}
.seg button.on {
  background: var(--color-text);
  color: var(--color-card);
  font-weight: 700;
}
</style>
