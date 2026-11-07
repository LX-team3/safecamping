<script setup>
// 서버 infos(place_info) 를 "라벨 | 값" 줄로 보여 준다 (캠핑장·시설·관광지 공통)
// 라벨이 없으면 값만 한 줄, 값이 없으면 지어내지 않고 "확인 필요"
defineProps({
  infos: { type: Array, default: () => [] },
})
</script>

<template>
  <dl class="rows">
    <template v-for="(info, i) in infos" :key="i">
      <div v-if="info.label" class="row">
        <dt>{{ info.label }}</dt>
        <dd :class="{ empty: !info.value }">{{ info.value || '확인 필요' }}</dd>
      </div>
      <p v-else class="plain">{{ info.value }}</p>
    </template>
    <slot />
  </dl>
</template>

<style scoped>
.rows {
  margin: 0;
}
.row {
  display: flex;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-track);
  font-size: 15px;
}
dt {
  flex: 0 0 60px;
  font-size: 14px;
  color: var(--color-text-sub);
}
dd {
  flex: 1;
  margin: 0;
}
dd.empty {
  color: var(--color-text-sub);
}
.plain {
  margin: 0 0 6px;
  font-size: 15px;
  line-height: 1.6;
}
</style>
