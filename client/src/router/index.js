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
    { path: '/login', name: 'login', component: () => import('../views/member/LoginView.vue') },
    { path: '/signup', name: 'signup', component: () => import('../views/member/SignupView.vue') },
    // 저장 (목록 탭) · 관심 캠핑장 비교. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/saved', name: 'saved', component: () => import('../views/saved/SavedView.vue') },
    { path: '/saved/lists/:id', name: 'saved-compare', component: () => import('../views/saved/CompareView.vue') },
    // 내 캠핑 (예약 후 정보) → 가족 모임방 버튼. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-camping', name: 'my-camping', component: () => import('../views/mycamping/MyCampingView.vue') },
    { path: '/my-camping/family', name: 'family-room', component: () => import('../views/family/FamilyRoomView.vue') },
    // 내 정보 (1차: 진단 전 모양). 로그인 여부에 따라 메뉴가 달라진다
    { path: '/my-info', name: 'my-info', component: () => import('../views/member/MyInfoView.vue') },
    // 내 정보 · 비상연락처 (2명). 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-info/emergency-contacts', name: 'emergency-contacts', component: () => import('../views/member/EmergencyContactView.vue') },
    // 내 정보 · 함께 가는 가족 (가족방 아이 카드를 같이 씀). 가족방이 없으면 화면 안에서 가족방 만들기 안내
    { path: '/my-info/family', name: 'travel-family', component: () => import('../views/member/TravelFamilyView.vue') },
    // 내 정보 · 여행 취향 (6개 중 최대 3개). 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-info/activity', name: 'activity', component: () => import('../views/member/ActivityView.vue') },
  ],
})

export default router
