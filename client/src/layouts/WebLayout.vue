<script setup>
// 웹(PC) 레이아웃: 상단 헤더 + 본문 + 하단.
// /web 으로 시작하는 주소의 공통 틀이다 (router/webRoutes.js). 본문에는 웹 전용 화면만 들어온다
// (로그인·회원가입은 헤더·하단이 없는 화면이라 이 틀을 쓰지 않는다)
import { RouterView } from 'vue-router'
import WebHeader from '@/components/web/WebHeader.vue'
import WebFooter from '@/components/web/WebFooter.vue'
import '@/styles/tokens-web.css'
</script>

<template>
  <div class="web-layout">
    <WebHeader />
    <main class="web-main">
      <RouterView />
    </main>
    <WebFooter />
  </div>
</template>

<style scoped>
.web-layout {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--web-bg);
  color: var(--web-text);
  /* 한국어 줄바꿈: 단어 중간에서 끊지 않기 */
  word-break: keep-all;
  overflow-wrap: break-word;
}
.web-main {
  flex: 1 0 auto;
}
</style>
