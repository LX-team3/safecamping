<script setup>
// 웹(PC) 상단 헤더 (설계 ① 상단 헤더): 로고 · 메뉴 · 로그인/검색/알림/전체 메뉴
// 아직 웹 화면이 없는 주소는 "PC 화면 준비 중" 안내가 뜬다
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import WebGuideModal from './WebGuideModal.vue'
import WebMenuDrawer from './WebMenuDrawer.vue'

const auth = useAuthStore()
const initial = computed(() => auth.member?.name?.slice(0, 1) ?? '')

const menuOpen = ref(false)
const guideOpen = ref(false)
</script>

<template>
  <header class="web-header">
    <div class="bar">
      <RouterLink to="/web" class="logo" aria-label="CAMPINE 홈">
        <img src="/web/logo.png" alt="CAMPINE" />
      </RouterLink>

      <nav class="menus" aria-label="주요 메뉴">
        <RouterLink to="/web/explore" class="menu">안전지도</RouterLink>
        <RouterLink to="/web/nearby" class="menu">지역 찾기</RouterLink>
        <RouterLink to="/web/survey" class="menu">캠핑유형 진단</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/web/my-booking" class="menu">내 예약</RouterLink>
        <button v-else type="button" class="menu" @click="guideOpen = true">내 예약</button>
      </nav>

      <div class="tools">
        <RouterLink v-if="!auth.isLoggedIn" to="/web/login" class="login">로그인</RouterLink>
        <a v-else href="#" class="avatar" :aria-label="`마이페이지 (${auth.member?.name})`" @click.prevent>{{ initial }}</a>

        <button type="button" class="icon-btn" aria-label="검색">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        </button>
        <a href="#" class="icon-btn" aria-label="알림" @click.prevent>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10 21h4" />
          </svg>
        </a>
        <button type="button" class="icon-btn" aria-label="전체 메뉴" @click="menuOpen = true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
        </button>
      </div>
    </div>

    <WebMenuDrawer :open="menuOpen" @close="menuOpen = false" />
    <WebGuideModal :open="guideOpen" text="내 예약과 여행 중 안전 안내는&#10;로그인하면 확인할 수 있어요." @close="guideOpen = false" />
  </header>
</template>

<style scoped>
.web-header {
  background: var(--web-bg);
  border-bottom: 1px solid var(--web-line);
}
.bar {
  height: 100px;
  padding: 0 80px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
}
.logo {
  justify-self: start;
  display: flex;
  align-items: center;
}
.logo img {
  height: 54px;
  width: auto;
  display: block;
}
.menus {
  display: flex;
  gap: 80px;
}
.menu {
  padding: 8px 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 21px;
  font-weight: 500;
  letter-spacing: -0.3px;
  color: var(--web-text);
  cursor: pointer;
}
.tools {
  justify-self: end;
  display: flex;
  align-items: center;
  gap: 10px;
}
.login {
  margin-right: 8px;
  font-size: 16px;
  font-weight: 500;
  color: var(--web-text);
}
.avatar {
  margin-right: 6px;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.icon-btn {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  border: 1px solid var(--web-line-btn);
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
</style>
