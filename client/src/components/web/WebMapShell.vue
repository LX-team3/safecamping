<script setup>
// 웹 지도 화면의 공통 틀 (설계 4-1 · 4-2 · 4-3 · 4-4): 왼쪽 패널(로고 · 로그인 · 검색/내 주변/저장 탭) + 오른쪽 넓은 칸
// 왼쪽 패널 아래 내용은 slot "side", 오른쪽(지도 등)은 기본 slot. 패널 가장자리 손잡이로 접고 펼친다
// 헤더·하단이 없는 화면이라 WebLayout 을 쓰지 않는다
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import '@/styles/tokens-web.css'

defineProps({ active: { type: String, default: 'search' } }) // search | nearby | saved

const TABS = [
  { key: 'search', label: '검색', to: '/web/explore' },
  { key: 'nearby', label: '내 주변', to: '/web/nearby' },
  { key: 'saved', label: '저장', to: '/web/saved' },
]

const auth = useAuthStore()
const initial = computed(() => auth.member?.name?.slice(0, 1) ?? '')
const open = ref(true)
</script>

<template>
  <div class="shell">
    <aside class="aside" :class="{ closed: !open }" :aria-hidden="!open">
      <div class="head">
        <div class="top">
          <RouterLink to="/web" aria-label="CAMPINE 홈"><img src="/web/logo-white.png" alt="CAMPINE" /></RouterLink>
          <RouterLink v-if="!auth.isLoggedIn" to="/web/login" class="chip">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>로그인하세요 ›
          </RouterLink>
          <a v-else href="#" class="chip member" aria-label="마이페이지로 가기" @click.prevent>
            <span class="avatar">{{ initial }}</span><span><b>{{ auth.member?.name }}</b>님 환영합니다 ›</span>
          </a>
        </div>
        <nav class="tabs" aria-label="주요 메뉴">
          <RouterLink v-for="t in TABS" :key="t.key" :to="t.to" class="tab" :class="{ on: t.key === active }">{{ t.label }}</RouterLink>
        </nav>
      </div>
      <slot name="side" />
    </aside>

    <button type="button" class="fold" :class="{ closed: !open }" :aria-label="open ? '왼쪽 패널 접기' : '왼쪽 패널 펼치기'"
      :aria-expanded="open" @click="open = !open">
      <svg width="12" height="12" viewBox="0 0 24 24" aria-hidden="true" :style="{ transform: open ? 'none' : 'rotate(180deg)' }">
        <path d="M16 4 6 12l10 8z" />
      </svg>
    </button>

    <main class="main"><slot /></main>
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  height: 100dvh;
  display: flex;
  background: var(--web-bg-map);
  color: var(--web-text);
  overflow: hidden;
  word-break: keep-all;
  overflow-wrap: break-word;
}
.aside {
  flex: 0 0 400px;
  height: 100%;
  background: var(--web-bg);
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.08);
  position: relative;
  z-index: 2;
  overflow: hidden;
  transition: flex-basis 0.2s ease;
}
.aside.closed {
  flex-basis: 0;
  visibility: hidden;
}
.head {
  flex: none;
  background: var(--web-green);
  padding: 18px 20px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.top img {
  height: 26px;
  width: auto;
  display: block;
}
.chip {
  height: 36px;
  padding: 0 14px 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  color: var(--web-white);
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
}
.chip.member {
  padding: 0 14px 0 4px;
  font-weight: 400;
  gap: 8px;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--web-white);
  color: var(--web-green);
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tabs {
  display: flex;
  gap: 4px;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 10px 0 12px;
  color: var(--web-text-light);
  font-size: 15px;
  border-bottom: 3px solid transparent;
}
.tab.on {
  color: var(--web-white);
  font-weight: 700;
  border-bottom-color: var(--web-white);
}
.fold {
  position: absolute;
  left: 400px;
  top: 50%;
  transform: translateY(-50%);
  width: 26px;
  height: 64px;
  padding: 0;
  border: 1px solid var(--web-line-panel);
  border-left: 0;
  border-radius: 0 10px 10px 0;
  background: var(--web-bg);
  box-shadow: 3px 0 8px rgba(0, 0, 0, 0.1);
  z-index: 6;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: left 0.2s ease;
}
.fold.closed {
  left: 0;
}
.fold svg {
  fill: var(--web-green);
}
.main {
  flex: 999 1 560px;
  min-width: 0;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: var(--web-bg-map);
}
</style>
