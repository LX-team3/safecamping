<script setup>
// 지도 위 시설 칩 줄 (대피소 · 병원 · 약국 …): 누르면 그 시설을 켜고 끈다. 켜면 종류별 색으로 칠해진다
// 서버 시설 코드(er · fire · police · store · pharmacy …) → 설계의 종류별 색
defineProps({
  layers: { type: Array, default: () => [] }, // [{ code, label }]
  on: { type: Object, default: () => ({}) }, // { code: true/false }
})
const emit = defineEmits(['toggle'])
const COLOR = { er: 'hospital', fire: 'fire', police: 'police', store: 'store', pharmacy: 'pharmacy', shelter: 'shelter' }
</script>

<template>
  <div v-if="layers.length" class="chips">
    <button v-for="l in layers" :key="l.code" type="button" class="fac" :class="[COLOR[l.code] || 'etc', { on: on[l.code] }]"
      :aria-pressed="!!on[l.code]" @click="emit('toggle', l.code)">{{ l.label }}</button>
  </div>
</template>

<style scoped>
.chips {
  position: absolute;
  top: 16px;
  left: 16px;
  right: 80px;
  z-index: 3;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.fac {
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 14px;
  color: var(--web-text);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  cursor: pointer;
}
.fac.on {
  font-weight: 800;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
.fac.shelter.on { border: 1.5px solid var(--web-fac-shelter); background: var(--web-fac-shelter-tint); }
.fac.hospital.on { border: 1.5px solid var(--web-fac-hospital); background: var(--web-fac-hospital-tint); }
.fac.pharmacy.on { border: 1.5px solid var(--web-fac-pharmacy); background: var(--web-fac-pharmacy-tint); }
.fac.fire.on { border: 1.5px solid var(--web-fac-fire); background: var(--web-fac-fire-tint); }
.fac.police.on { border: 1.5px solid var(--web-fac-police); background: var(--web-fac-police-tint); }
.fac.store.on { border: 1.5px solid var(--web-fac-store); background: var(--web-fac-store-tint); }
.fac.etc.on { border: 1.5px solid var(--web-green); background: var(--web-bg-soft); }
</style>
