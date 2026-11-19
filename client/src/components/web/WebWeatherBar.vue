<script setup>
// 지도 왼쪽 아래 기상특보 띠 (설계 4-2 · 4-3): 지역 · 기상특보 건수
// 기온·특보 이름은 서버에 없어서 지역과 발효 중인 특보 건수만 보여 준다 (GET /api/alerts)
defineProps({
  region: { type: String, default: '' },
  count: { type: Number, default: null }, // 발효 중 기상특보 수. null 이면 못 받음
})
</script>

<template>
  <div class="bar" role="status">
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      stroke-linecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
    <b v-if="region">{{ region }}</b>
    <span v-if="region" class="sep" aria-hidden="true"></span>
    <span class="badge">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3 2 20h20zM12 10v4M12 17h.01" /></svg>기상특보
    </span>
    <span class="msg">{{ count === null ? '확인 못 함' : count ? `발효 중 ${count}건` : '없음' }}</span>
  </div>
</template>

<style scoped>
.bar {
  position: absolute;
  left: 16px;
  bottom: 22px;
  z-index: 3;
  height: 46px;
  padding: 0 18px 0 16px;
  border-radius: 12px;
  background: var(--web-weather-bg);
  backdrop-filter: blur(6px);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.18);
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--web-white);
  font-size: 14px;
}
.sep {
  width: 1px;
  height: 18px;
  background: rgba(255, 255, 255, 0.3);
}
.badge {
  height: 30px;
  padding: 0 12px 0 10px;
  border-radius: 8px;
  background: var(--web-weather-badge);
  color: var(--web-weather-badge-fg);
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 6px;
}
.msg {
  font-weight: 700;
}
</style>
