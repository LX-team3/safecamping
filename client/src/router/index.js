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
    // 저장 (목록 탭) · 관심 캠핑장 비교. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/saved', name: 'saved', component: () => import('../views/saved/SavedView.vue') },
    { path: '/saved/lists/:id', name: 'saved-compare', component: () => import('../views/saved/CompareView.vue') },
    // 내 캠핑 (예약 후 정보) → 가족 모임방 버튼. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-camping', name: 'my-camping', component: () => import('../views/mycamping/MyCampingView.vue') },
    { path: '/my-camping/family', name: 'family-room', component: () => import('../views/family/FamilyRoomView.vue') },
  ],
})

export default router
