import './styles/base.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { initNotificationTap } from './utils/localNotify'
import { initPush, linkPushToken } from './utils/pushNotify'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 안드로이드 앱: 브리핑 알림을 누르면 내 캠핑으로 이동 (브라우저에서는 아무것도 안 함)
initNotificationTap(router)
// 안드로이드 앱: Firebase 푸시 등록 (기기 토큰은 알림 테스트 화면에서 확인). 기다리지 않는다 — 실패해도 앱은 그대로 뜬다
initPush(router)
// 안드로이드 앱: 토큰과 로그인이 모두 준비되면 서버에 "이 회원의 폰"으로 등록
const auth = useAuthStore()
linkPushToken(() => auth.member?.id)

// 화면을 그리기 전에 서버 세션으로 로그인 상태를 확인한다 (새로고침해도 로그인 유지)
auth
  .fetchMe()
  .finally(() => app.mount('#app'))
