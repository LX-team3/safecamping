import './styles/base.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { initNotificationTap } from './utils/localNotify'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 안드로이드 앱: 브리핑 알림을 누르면 내 캠핑으로 이동 (브라우저에서는 아무것도 안 함)
initNotificationTap(router)

// 화면을 그리기 전에 서버 세션으로 로그인 상태를 확인한다 (새로고침해도 로그인 유지)
useAuthStore()
  .fetchMe()
  .finally(() => app.mount('#app'))
