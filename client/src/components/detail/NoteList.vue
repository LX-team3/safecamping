<script setup>
// 서버 notes(place_note) 안내 상자 (시설 "위험할 땐" · 관광지 "가기 전에 알아두세요")
defineProps({
  notes: { type: Array, default: () => [] },
})

const LEVEL_LABEL = { danger: '위험할 땐', caution: '주의', safe: '안전' }
</script>

<template>
  <ul class="notes">
    <li v-for="(n, i) in notes" :key="i" class="note" :class="n.level">
      <b>{{ LEVEL_LABEL[n.level] }}</b>
      <p>{{ n.body }}</p>
    </li>
  </ul>
</template>

<style scoped>
.notes {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
}
.note b {
  flex: none;
  padding: 2px 10px;
  border-radius: 12px;
  color: var(--color-card);
  font-size: 12px;
}
.note p {
  margin: 0;
}
.note.danger {
  background: var(--color-warn-bg);
}
.note.danger b {
  background: var(--color-danger);
}
.note.caution {
  background: var(--color-caution-bg);
}
.note.caution b {
  background: var(--color-caution);
}
.note.safe {
  background: var(--color-safe-bg);
}
.note.safe b {
  background: var(--color-green);
}
</style>
