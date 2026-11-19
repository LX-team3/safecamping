import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { webRoutes } from './webRoutes'

// 화면 폭이 이 값 이상이면 웹(PC)으로 본다
const WEB_MIN_WIDTH = 768
const isWideScreen = () => window.matchMedia(`(min-width: ${WEB_MIN_WIDTH}px)`).matches

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      // 처음 들어올 때 화면 폭을 보고 웹(PC)이면 /web, 모바일이면 /explore 로 보낸다
      path: '/', redirect: () => (isWideScreen() ? '/web' : '/explore')
    },
    // ①-3 로그인, ①-4 회원가입
    { path: '/login', name: 'login', component: () => import('../views/member/LoginView.vue') },
    { path: '/signup', name: 'signup', component: () => import('../views/member/SignupView.vue') },
    // 저장 (목록 탭) · 관심 캠핑장 비교. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/saved', name: 'saved', component: () => import('../views/saved/SavedView.vue') },
    { path: '/saved/lists/:id', name: 'saved-compare', component: () => import('../views/saved/CompareView.vue') },
    // 내 캠핑 (예약 후 정보) → 가족 모임방 버튼. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
    { path: '/my-camping', name: 'my-camping', component: () => import('../views/mycamping/MyCampingView.vue') },
    // 내 캠핑 카드 → 출발 준비 체크리스트 (계절 · 공통 준비물 + 출발 3일 전부터 지역 특보)
    { path: '/my-camping/:planId(\\d+)/checklist', name: 'my-camping-checklist', component: () => import('../views/mycamping/ChecklistView.vue') },
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
      // 차박 지도 (탐색 화면 위쪽 "캠핑장 | 차박" 토글로 들어옴). 지도에서 찍은 곳의 차박 판정. 로그인 없이도 된다
    { path: '/chabak', name: 'chabak', component: () => import('../views/chabak/ChabakView.vue') },
    // 재난 대피 3D 지도 (인북천 물빛테마공원 캠핑장, CesiumJS). 이 화면에 들어올 때만 Cesium 을 불러온다
    { path: '/disaster', name: 'disaster', component: () => import('../views/disaster/DisasterMapView.vue') },

    ...webRoutes, // 웹(PC) 전용 주소 (/web/...)
  ],
})

// 좁은 화면(휴대폰)에서 /web/... 주소로 들어오면 같은 화면의 모바일 주소로 보낸다 (공유된 링크 대비)
router.beforeEach((to) => {
  if (/^\/web(\/|$)/.test(to.path) && !isWideScreen()) {
    return to.path.replace(/^\/web/, '') || '/explore'
  }
})

export default router
