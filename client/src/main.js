import './styles/base.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 화면을 그리기 전에 서버 세션으로 로그인 상태를 확인한다 (새로고침해도 로그인 유지)
useAuthStore()
  .fetchMe()
  .finally(() => app.mount('#app'))
