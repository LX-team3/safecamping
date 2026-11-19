<script setup>
// 차박지 찾기 (설계 4-1-5): 왼쪽 패널. 서버의 차박 후보지(공공데이터상 제한 없는 곳)를 안전점수 높은 순으로 보여 준다
// 한 줄 팁은 설계의 예시라 서버 값(응급실까지 시간)으로 대신한다
defineProps({
  camps: { type: Array, default: () => [] }, // [{ id, name, safetyScore, grade, tier, area, erDriveMin }]
  selectedId: { type: [Number, String], default: null },
})
const emit = defineEmits(['back', 'pick'])
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
      <div class="prep">
        <b>차박할 땐 이것만 챙겨요</b>
        <span>🪟 창문 살짝 열고 자기 (일산화탄소)<br />🌧 비 예보 땐 물가에서 떨어져 주차<br />📶 휴대폰 신호 미리 확인</span>
      </div>
      <div class="count-row">
        <b>차박 후보지 {{ camps.length }}곳</b>
        <span>안전점수 높은 순</span>
      </div>
      <p v-if="!camps.length" class="state">차박 후보지가 아직 없어요.</p>
      <div class="rows">
        <button v-for="c in camps" :key="c.id" type="button" class="row" :class="{ on: c.id === selectedId }"
          :aria-pressed="c.id === selectedId" @click="emit('pick', c.id)">
          <span class="score" :class="c.tier"><span class="gl">{{ GRADE_LABEL[c.grade] }}</span><span class="num">{{ c.safetyScore ?? '-' }}<small>점</small></span></span>
          <span class="info">
            <span class="line"><b>{{ c.name }}</b><span class="pill" :class="c.tier">{{ GRADE_LABEL[c.grade] }}</span></span>
            <span class="meta">차박 후보지 · {{ c.area }}</span>
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
.prep {
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--web-bg-prep);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.prep b {
  font-size: 15px;
}
.prep span {
  font-size: 14px;
  color: var(--web-text-prep);
  line-height: 1.6;
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
</style>
