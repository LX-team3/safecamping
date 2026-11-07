<script setup>
// 아래 탭 메뉴 (탐색 · 내 주변 · 내 캠핑 · 저장 · 내 정보)
// 아직 만든 화면이 탐색뿐이라 나머지는 눌리지 않게 둔다 → 화면을 만들면 to 를 채운다
const TABS = [
  { key: 'explore', label: '탐색', to: '/explore', icon: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-4.5-4.5' },
  { key: 'nearby', label: '내 주변', to: null, icon: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v3M12 19v3M2 12h3M19 12h3' },
  { key: 'mycamp', label: '내 캠핑', to: null, icon: 'M12 3L2 21h20zM12 10v5M12 18v.01' },
  { key: 'saved', label: '저장', to: null, icon: 'M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z' },
  { key: 'me', label: '내 정보', to: null, icon: 'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM4 21a8 8 0 0 1 16 0' },
]

defineProps({ active: { type: String, default: 'explore' } })
</script>

<template>
  <nav class="nav" aria-label="주 메뉴">
    <component :is="tab.to ? 'RouterLink' : 'button'" v-for="tab in TABS" :key="tab.key" :to="tab.to || undefined"
      :type="tab.to ? undefined : 'button'" :disabled="!tab.to || undefined" class="tab"
      :class="{ on: tab.key === active }" :title="tab.to ? undefined : '준비 중'">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor"
        :stroke-width="tab.key === active ? 2.4 : 1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path :d="tab.icon" />
      </svg>
      <span>{{ tab.label }}</span>
    </component>
  </nav>
</template>

<style scoped>
.nav {
  flex: none;
  display: flex;
  border-top: 1px solid var(--color-track);
  background: var(--color-card);
}
.tab {
  flex: 1;
  height: 64px;
  padding: 0;
  border: none;
  background: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.tab:disabled {
  cursor: default;
}
.tab.on {
  color: var(--color-text);
  font-weight: 700;
}
</style>
