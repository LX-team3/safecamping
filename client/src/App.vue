<script setup>
import { RouterView } from 'vue-router'
</script>

<template>
  <!-- 모바일 390px 폭 틀 -->
  <div class="frame">
    <RouterView />
  </div>
</template>

<style scoped>
.frame {
  max-width: var(--frame-width);
  height: 100vh;
  height: 100dvh; /* 모바일 주소창을 뺀 실제 보이는 높이 */
  margin: 0 auto;
  overflow-y: auto; /* 화면이 틀보다 길면 틀 안에서 스크롤 */
  background: var(--color-bg);
}

/* PC 처럼 화면이 틀보다 넓을 때: 설계의 폰 크기(390×844)로 줄이고 가운데에 둔다 */
@media (min-width: 391px) {
  .frame {
    height: min(var(--frame-height), calc(100dvh - 48px));
    margin: 24px auto;
    border-radius: 20px;
    box-shadow: 0 0 0 1px var(--color-border), 0 4px 24px rgba(0, 0, 0, 0.08);
  }
}
</style>
