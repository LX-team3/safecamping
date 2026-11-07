<script setup>
// 내 캠핑 탭 첫 화면. 원래 목적은 예약한 캠핑 정보(일정·날씨·위험 알림·재난지도)를 보여 주는 것
// 예약·날씨 데이터가 아직 없어 그 부분은 비워 두고(수치를 지어내지 않기 위해), 가족 모임방 버튼만 둔다
import { useAuthStore } from '@/stores/auth'
import BottomNav from '@/components/common/BottomNav.vue'

const auth = useAuthStore()
</script>

<template>
  <div class="page">
    <div class="mycamp">
      <h1>내 캠핑</h1>

      <div v-if="!auth.isLoggedIn" class="empty">
        <p>내 캠핑은 로그인하면 쓸 수 있어요.</p>
        <RouterLink to="/login" class="primary">로그인</RouterLink>
      </div>

      <template v-else>
        <!-- 예약한 캠핑장·날씨·위험 알림 카드 자리 (예약 데이터 연결 후) -->
        <p class="placeholder">예약한 캠핑 정보는 준비 중이에요.</p>

        <RouterLink to="/my-camping/family" class="family-btn">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="9" cy="8" r="3" />
            <circle cx="17" cy="9" r="2.5" />
            <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M15 14.5c.6-.3 1.3-.5 2-.5 2.8 0 4.5 2.2 4.5 5" />
          </svg>
          <span class="text">
            <b>가족 모임방</b>
            <small>모일 곳 · 아이 카드 · 일정을 가족과 함께 봐요</small>
          </span>
          <span aria-hidden="true">›</span>
        </RouterLink>
      </template>
    </div>
    <BottomNav active="mycamp" />
  </div>
</template>

<style scoped>
/* 화면 전체 틀: 위는 내용(스크롤), 맨 아래는 하단바 (탐색 화면과 같은 방식) */
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.mycamp {
  flex: 1;
  min-height: 0; /* 이게 없으면 내용이 길 때 하단바가 아래로 밀려난다 */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 20px;
}
h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.primary {
  height: 48px;
  padding: 0 20px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  display: inline-flex;
  align-items: center;
  font-size: 15px;
  font-weight: 700;
}
.placeholder {
  margin: 0;
  padding: 24px 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.family-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
}
.family-btn .text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.family-btn b {
  font-size: 16px;
}
.family-btn small {
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
