<script setup>
// 차박지 찾기 (설계 4-1-5): 왼쪽 패널. 서버의 차박 후보지(공공데이터상 제한 없는 곳)를 안전점수 높은 순으로 보여 준다
// 한 줄 팁은 설계의 예시라 서버 값(응급실까지 시간)으로 대신한다
// 설계의 "차박할 땐 이것만 챙겨요" 고정 문구 상자는 뺐다 (데이터와 무관한 고정 글이고, 출발 준비 체크리스트 · 진단 조심할 것과 겹침)
// 지도 아무 곳이나 누르면 그 자리를 판정한다 (WebExploreView → 차박지 상세)
// "지도에 보일 것" 필터 (앱 차박 지도의 필터 시트와 같은 내용, utils/chabak.js 의 LAYER_CHIPS · SAFETY_FILTERS)
//  - 갈 수 있는 곳(점): 차박 후보지 · 등록 야영장 — 목록과 지도 핀에 같이 적용
//  - 피해야 할 곳(면): 불가 구역 · 주의 구역 — 지도에 반투명 면으로 칠함 (처음엔 꺼 둠: 켜면 지도가 덮여서)
//  - 안전점수: 전체 · 위험 제외(40↑) · 안전만(70↑) — 점에만 적용
import { LAYER_CHIPS, SAFETY_FILTERS, VERDICT } from '@/utils/chabak'

defineProps({
  camps: { type: Array, default: () => [] }, // [{ id, kind: open·camp, name, safetyScore, grade, tier, area, erDriveMin }]
  selectedId: { type: [Number, String], default: null },
  layerOn: { type: Object, default: () => ({}) }, // { open, camp, no, caution } 켜짐
  safety: { type: String, default: 'all' }, // SAFETY_FILTERS 의 key
  zoneLoading: { type: Boolean, default: false }, // 구역을 처음 받는 중 (무거운 계산이라 몇 초 걸릴 수 있음)
})
const emit = defineEmits(['back', 'pick', 'toggle-layer', 'safety'])
const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의' }
</script>

<template>
  <div class="carpanel">
    <div class="bar">
      <button type="button" class="back" aria-label="검색으로 돌아가기" @click="emit('back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>
      </button>
      <b>🚙 차박지 찾기</b>
    </div>
    <div class="body">
      <!-- 지도에 보일 것 -->
      <div class="filter">
        <span class="flabel">갈 수 있는 곳</span>
        <div class="fchips">
          <button v-for="c in LAYER_CHIPS.filter((x) => x.section === 'go')" :key="c.type" type="button" class="fchip"
            :class="{ on: layerOn[c.type] }" :aria-pressed="!!layerOn[c.type]" :title="c.note" @click="emit('toggle-layer', c.type)">
            <i :style="{ background: `var(${VERDICT[c.verdict].colorVar})` }" />{{ c.label }}
          </button>
        </div>
        <span class="flabel">피해야 할 곳 <small>지도에 칠해져요</small></span>
        <div class="fchips">
          <button v-for="c in LAYER_CHIPS.filter((x) => x.section === 'avoid')" :key="c.type" type="button" class="fchip"
            :class="{ on: layerOn[c.type] }" :aria-pressed="!!layerOn[c.type]" :title="c.note" @click="emit('toggle-layer', c.type)">
            <i :style="{ background: `var(${VERDICT[c.verdict].colorVar})` }" />{{ c.label }}
          </button>
        </div>
        <p v-if="zoneLoading" class="fnote">구역을 불러오는 중이에요 (처음 한 번은 몇 초 걸려요)</p>
        <span class="flabel">안전점수</span>
        <div class="fchips">
          <button v-for="f in SAFETY_FILTERS" :key="f.key" type="button" class="fchip" :class="{ on: safety === f.key }"
            :aria-pressed="safety === f.key" @click="emit('safety', f.key)">{{ f.label }}</button>
        </div>
      </div>

      <div class="count-row">
        <b>차박지 {{ camps.length }}곳</b>
        <span>안전점수 높은 순</span>
      </div>
      <p v-if="!camps.length" class="state">조건에 맞는 곳이 없어요. 필터를 넓혀 보세요.</p>
      <div class="rows">
        <button v-for="c in camps" :key="c.id" type="button" class="row" :class="{ on: c.id === selectedId }"
          :aria-pressed="c.id === selectedId" @click="emit('pick', c.id)">
          <span class="score" :class="c.tier"><span class="gl">{{ GRADE_LABEL[c.grade] }}</span><span class="num">{{ c.safetyScore ?? '-' }}<small>점</small></span></span>
          <span class="info">
            <span class="line"><b>{{ c.name }}</b><span class="pill" :class="c.tier">{{ GRADE_LABEL[c.grade] }}</span></span>
            <span class="meta">{{ c.kind === 'camp' ? '등록 야영장' : '차박 후보지' }} · {{ c.area }}</span>
            <span v-if="c.erDriveMin != null" class="tip">응급실 차로 {{ c.erDriveMin }}분</span>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.carpanel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.bar {
  flex: none;
  height: 56px;
  padding: 0 12px 0 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--web-line-soft);
}
.bar b {
  font-size: 17px;
}
.back {
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.count-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.count-row b {
  font-size: 16px;
}
.count-row span {
  font-size: 13px;
  color: var(--web-text-sub);
}
.state {
  margin: 0;
  font-size: 14px;
  color: var(--web-text-sub);
}
.rows {
  display: flex;
  flex-direction: column;
  margin: -6px -8px 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: var(--web-text);
}
.row.on {
  background: var(--web-bg-selected);
}
.score {
  flex: 0 0 56px;
  height: 56px;
  border-radius: 14px;
  color: var(--web-white);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
}
.score.rec { background: linear-gradient(160deg, var(--web-rec-from) 0%, var(--web-rec-mid) 52%, var(--web-rec-to) 100%); }
.score.safe { background: var(--web-grade-safe); }
.score.mid { background: var(--web-grade-caution); }
.score.warn { background: var(--web-grade-danger); }
.gl {
  font-size: 11px;
  font-weight: 700;
}
.num {
  font-size: 19px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: -0.5px;
}
.num small {
  font-size: 10px;
  font-weight: 800;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.line {
  display: flex;
  align-items: center;
  gap: 6px;
}
.line b {
  font-size: 15px;
}
.pill {
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}
.pill.rec,
.pill.safe { background: var(--web-grade-safe-bg); color: var(--web-grade-safe); }
.pill.mid { background: var(--web-grade-caution-bg); color: var(--web-grade-caution-fg); }
.pill.warn { background: var(--web-grade-danger-bg); color: var(--web-grade-danger-fg); }
.meta {
  font-size: 13px;
  color: var(--web-text-sub);
}
.tip {
  font-size: 13px;
  color: var(--web-grade-safe);
  font-weight: 600;
}
.filter {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 0 4px;
}
.flabel {
  font-size: 13px;
  font-weight: 700;
}
.flabel small {
  font-weight: 400;
  color: var(--web-text-sub);
}
.fchips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.fchip {
  height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text-sub);
  display: flex;
  align-items: center;
  gap: 6px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.fchip i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  opacity: 0.35;
}
.fchip.on {
  border-color: var(--web-green);
  color: var(--web-text);
  font-weight: 700;
}
.fchip.on i {
  opacity: 1;
}
.fnote {
  margin: 0;
  font-size: 12px;
  color: var(--web-text-sub);
}
</style>
