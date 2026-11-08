<script setup>
// 탐색 지도 "지도에 보일 것" 시트 (지도 오른쪽 위 레이어 버튼)
// 안전·주의·위험 등급별로 캠핑장 마커를 켜고 끈다. 목록(추천)은 그대로이고 지도 마커만 바뀐다
import BottomSheet from '@/components/common/BottomSheet.vue'

defineProps({
  on: { type: Object, required: true }, // { safe: true, caution: true, danger: true }
})
const emit = defineEmits(['close', 'toggle'])

const ROWS = [
  { code: 'safe', label: '안전', desc: '안전 등급 캠핑장' },
  { code: 'caution', label: '주의', desc: '주의 등급 캠핑장' },
  { code: 'danger', label: '위험', desc: '위험 등급 캠핑장' },
]
</script>

<template>
  <BottomSheet title="지도에 보일 것" @close="emit('close')">
    <ul class="rows">
      <li v-for="r in ROWS" :key="r.code" class="row">
        <i class="dot" :class="r.code" aria-hidden="true" />
        <div class="text">
          <b>{{ r.label }}</b>
          <span>{{ r.desc }}</span>
        </div>
        <button type="button" role="switch" class="switch" :class="{ on: on[r.code] }" :aria-checked="!!on[r.code]"
          :aria-label="`${r.label} 캠핑장 지도에 보이기`" @click="emit('toggle', r.code)">
          <i />
        </button>
      </li>
    </ul>
  </BottomSheet>
</template>

<style scoped>
.rows {
  margin: 0;
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
.dot {
  flex: none;
  width: 14px;
  height: 14px;
  margin: 0 11px; /* 내 주변 시트의 아이콘 칸(36px)과 줄 맞춤 */
  border-radius: 50%;
}
.dot.safe {
  background: var(--color-green);
}
.dot.caution {
  background: var(--color-caution);
}
.dot.danger {
  background: var(--color-danger);
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
.text span {
  font-size: 12px;
  color: var(--color-text-sub);
}
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
</style>
