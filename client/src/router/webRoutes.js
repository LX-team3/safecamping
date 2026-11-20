// 웹(PC) 전용 라우트: 주소가 /web 으로 시작한다 (모바일은 /explore 처럼 접두사 없음)
// 모바일 라우트(router/index.js)와 모바일 화면은 건드리지 않고, 웹은 여기에 따로 모은다
//
// 웹 화면을 만들면 views/web/ 에 파일을 두고 아래 children 에 한 줄 추가한다. 예:
//   { path: 'explore', name: 'web-explore', component: () => import('../views/web/ExploreWebView.vue') },
// 등록하지 않은 /web/... 주소는 "PC 화면 준비 중" 안내가 뜬다
import WebNotReadyView from '../views/web/WebNotReadyView.vue'

export const webRoutes = [
  // 로그인 · 회원가입: 헤더·하단이 없는 화면이라 WebLayout 밖에 둔다
  { path: '/web/login', name: 'web-login', component: () => import('../views/web/WebLoginView.vue'), meta: { layout: 'web' } },
  { path: '/web/signup', name: 'web-signup', component: () => import('../views/web/WebSignupView.vue'), meta: { layout: 'web' } },
  // 탐색 · 내 주변 · 저장: 왼쪽 패널 + 지도 화면이라 헤더·하단이 없다 (공통 틀은 components/web/WebMapShell.vue)
  { path: '/web/explore', name: 'web-explore', component: () => import('../views/web/WebExploreView.vue'), meta: { layout: 'web' } },
  // 같은 화면(WebExploreView)이 주소에 따라 왼쪽 패널만 바꾼다: 차박지 찾기 · 캠핑장 상세 · 시설 상세
  { path: '/web/chabak', name: 'web-chabak', component: () => import('../views/web/WebExploreView.vue'), props: { mode: 'car' }, meta: { layout: 'web' } },
  {
    path: '/web/camps/:id(\\d+)', name: 'web-camp-detail', component: () => import('../views/web/WebExploreView.vue'),
    props: (route) => ({ campId: Number(route.params.id) }), meta: { layout: 'web' },
  },
  {
    path: '/web/facilities/:id(\\d+)', name: 'web-facility-detail', component: () => import('../views/web/WebExploreView.vue'),
    props: (route) => ({ facId: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null }), meta: { layout: 'web' },
  },
  // 1분 진단: 헤더·하단 없이 한 화면 (설문 → 결과표)
  { path: '/web/survey', name: 'web-survey', component: () => import('../views/web/WebSurveyView.vue'), meta: { layout: 'web' } },
  { path: '/web/nearby', name: 'web-nearby', component: () => import('../views/web/WebNearbyView.vue'), meta: { layout: 'web' } },
  { path: '/web/saved', name: 'web-saved', component: () => import('../views/web/WebSavedView.vue'), meta: { layout: 'web' } },
  // 재난 대피 3D 지도: 전체 화면 지도라 헤더·하단이 없다 (이 화면에 들어올 때만 Cesium 을 불러온다)
  { path: '/web/disaster', name: 'web-disaster', component: () => import('../views/web/WebDisasterMapView.vue'), meta: { layout: 'web' } },
  {
    path: '/web',
    component: () => import('../layouts/WebLayout.vue'),
    meta: { layout: 'web' }, // App.vue 가 보고 모바일 틀을 씌우지 않는다
    children: [
      { path: '', name: 'web-home', component: () => import('../views/web/WebHomeView.vue') },
      { path: 'my-booking', name: 'web-my-booking', component: () => import('../views/web/WebMyBookingView.vue') },
      { path: 'booking-check', name: 'web-booking-check', component: () => import('../views/web/WebBookingCheckView.vue') },
      { path: 'tours/:id(\\d+)', name: 'web-tour-detail', component: () => import('../views/web/WebTourDetailView.vue') },
      { path: 'my-info', name: 'web-my-info', component: () => import('../views/web/WebMyInfoView.vue') },
      { path: 'alerts', name: 'web-alerts', component: () => import('../views/web/WebAlertsView.vue') },
      { path: 'family', name: 'web-family', component: () => import('../views/web/WebFamilyView.vue') },
      { path: 'checklist/:planId(\\d+)', name: 'web-checklist', component: () => import('../views/web/WebChecklistView.vue') },
      // ↓ 웹 화면은 여기에 추가
      { path: ':pathMatch(.*)*', name: 'web-not-ready', component: WebNotReadyView },
    ],
  },
]
