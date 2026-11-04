import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    // ①-3 로그인, ①-4 회원가입
    { path: '/login', name: 'login', component: () => import('../views/auth/LoginView.vue') },
    { path: '/signup', name: 'signup', component: () => import('../views/auth/SignupView.vue') },
  ],
})

export default router
