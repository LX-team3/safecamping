<script setup>
import { RouterView, useRoute } from 'vue-router'
import MobileLayout from './layouts/MobileLayout.vue'

// 주소가 /web 으로 시작하면 웹(PC) — 틀(WebLayout)은 라우트가 직접 가져오므로 그대로 둔다
// 그 밖의 주소(/app/explore 등)는 모바일 390px 틀을 씌운다
const route = useRoute()
</script>

<template>
  <RouterView v-if="route.meta.layout === 'web'" />
  <MobileLayout v-else>
    <RouterView />
  </MobileLayout>
</template>
