<script setup>
import { RouterView, useRoute } from 'vue-router'
import MobileLayout from './layouts/MobileLayout.vue'
import MobileConfirmHost from './components/mobile/MobileConfirmHost.vue'
import ReservationReturnPopup from './components/common/ReservationReturnPopup.vue'

// 주소가 /web 으로 시작하면 웹(PC) — 틀(WebLayout)은 라우트가 직접 가져오므로 그대로 둔다
// 'full'(메타버스 등)은 틀 없이 화면 전체에 그린다
// 그 밖의 주소(/app/explore 등)는 모바일 390px 틀을 씌운다
const route = useRoute()
</script>

<template>
  <RouterView v-if="route.meta.layout === 'web' || route.meta.layout === 'full'" />
  <MobileLayout v-else>
    <RouterView />
  </MobileLayout>
  <!-- 예약 사이트에서 돌아오면 "예약하셨나요?" (어느 화면에 있든 뜨게 여기 한 번만) -->
  <ReservationReturnPopup />
  <!-- 앱 확인 팝업 (utils/confirm.js 의 askConfirm) -->
  <MobileConfirmHost />
</template>
