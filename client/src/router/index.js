import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/', redirect: '/explore'
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
    // 1분 진단 (3단계 5문항). 누구나 할 수 있고, 로그인했으면 서버에 저장
    { path: '/survey', name: 'survey', component: () => import('../views/survey/SurveyView.vue') },
    { path: '/my-info', name: 'my-info', component: () => import('../views/member/MyInfoView.vue') },
    // 내 정보 · 비상연락처 (2명). 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-info/emergency-contacts', name: 'emergency-contacts', component: () => import('../views/member/EmergencyContactView.vue') },
    // 내 정보 · 함께 가는 가족 (가족방 아이 카드를 같이 씀). 가족방이 없으면 화면 안에서 가족방 만들기 안내
    { path: '/my-info/family', name: 'travel-family', component: () => import('../views/member/TravelFamilyView.vue') },
    // 내 정보 · 여행 취향 (6개 중 최대 3개). 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-info/activity', name: 'activity', component: () => import('../views/member/ActivityView.vue') },
    // 내 정보 · 알림 (기상특보 + 재난문자, 지금은 더미). 로그인 없이도 볼 수 있다
    { path: '/my-info/alerts', name: 'alerts', component: () => import('../views/member/AlertView.vue') },
    { path: '/explore', name: 'explore', component: () => import('../views/explore/ExploreView.vue') },
    {
      path: '/camps/:id(\\d+)', name: 'camp-detail', component: () => import('../views/camp/CampDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id) })
    },
    {
      path: '/facilities/:id(\\d+)', name: 'facility-detail', component: () => import('../views/facility/FacilityDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null })
    },
    {
      path: '/tours/:id(\\d+)', name: 'tour-detail', component: () => import('../views/tour/TourDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null })
    },
    // 여행 취향 (관광지 탭 순서용). ?camp=캠핑장id 로 끝난 뒤 돌아갈 캠핑장을 받는다
    {
      path: '/tour-preference', name: 'tour-preference', component: () => import('../views/tour/TourPreferenceView.vue'),
      props: (route) => ({ campId: route.query.camp ? Number(route.query.camp) : null })
    },
      // 내 주변 (지금 위치 안전점수 · 가까운 시설 · 지도 레이어). 로그인 없이도 볼 수 있다
    { path: '/nearby', name: 'nearby', component: () => import('../views/nearby/NearbyView.vue') },

  ],
})

export default router
