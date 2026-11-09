<script setup>
// "지도에 보일 것" 시트 (내 주변 지도 오른쪽 위 레이어 버튼)
// 탭: 안전 시설만 동작. 지도 종류 · 날씨·재난은 아직 없어서 눌리지 않게 둔다
// 줄 목록은 서버(GET /api/nearby/layers)가 정본 — 대피소는 데이터가 없어 켜도 지도에 아무것도 안 나온다
import BottomSheet from '@/components/common/BottomSheet.vue'

defineProps({
  layers: { type: Array, required: true }, // [{ code, icon, label, desc, ready }]
  on: { type: Object, required: true }, // { er: true, ... } 켜짐 여부
})
const emit = defineEmits(['close', 'toggle'])

const TABS = [
  { key: 'facility', label: '안전 시설', ready: true },
  { key: 'mapType', label: '지도 종류', ready: false },
  { key: 'weather', label: '날씨 · 재난', ready: false },
]
</script>

<template>
  <BottomSheet title="지도에 보일 것" @close="emit('close')">
    <div class="tabs" role="tablist">
      <button v-for="t in TABS" :key="t.key" type="button" role="tab" class="tab" :class="{ on: t.key === 'facility' }"
        :aria-selected="t.key === 'facility'" :disabled="!t.ready" :title="t.ready ? undefined : '준비 중'">
        {{ t.label }}
      </button>
    </div>

    <ul class="rows">
      <li v-for="l in layers" :key="l.code" class="row">
        <span class="icon" aria-hidden="true">{{ l.icon }}</span>
        <div class="text">
          <b>{{ l.label }}</b>
          <span>{{ l.desc }}</span>
        </div>
        <button type="button" role="switch" class="switch" :class="{ on: on[l.code] }" :aria-checked="!!on[l.code]"
          :aria-label="`${l.label} 지도에 보이기`" @click="emit('toggle', l.code)">
          <i />
        </button>
      </li>
    </ul>
  </BottomSheet>
</template>

<style scoped>
.tabs {
  display: flex;
  padding: 4px;
  border-radius: 24px;
  background: var(--color-bg-soft);
}
.tab {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 20px;
  background: none;
  font: inherit;
  font-size: 15px;
  color: var(--color-text);
  cursor: pointer;
}
.tab.on {
  background: var(--color-text);
  color: var(--color-card);
  font-weight: 700;
}
.tab:disabled {
  color: var(--color-arrow);
  cursor: default;
}
.rows {
  margin: 12px 0 0;
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
  font-size: 13px;
  font-weight: 700;
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
