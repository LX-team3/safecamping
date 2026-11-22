// 모바일(앱) 전용 라우트: 주소가 /app 으로 시작한다 (웹은 /web — router/webRoutes.js)
// 화면 파일은 그대로 views/ 아래에 있고, 주소만 /app 접두사를 붙였다
//
// 옛 주소(/explore, /my-camping …)로 들어온 링크·북마크는 router/index.js 가 /app/... 으로 넘겨 준다
// → 여기 appPaths 에 있는 주소가 그 대상이다. 새 모바일 화면을 만들면 아래 routes 에 한 줄 추가하면 된다

// [옛 주소(접두사 없음), 이름, 화면, props]  — 접두사 /app 은 아래에서 붙인다
const defs = [
  // 로그인 · 회원가입
  ['/login', 'login', () => import('../views/mobile/MobileLoginView.vue')],
  ['/signup', 'signup', () => import('../views/mobile/MobileSignupView.vue')],
  // 저장 (목록 탭) · 관심 캠핑장 비교. 회원 전용 — 로그인 안 했으면 화면 안에서 로그인 안내
  ['/saved', 'saved', () => import('../views/mobile/MobileSavedView.vue')],
  ['/saved/lists/:id', 'saved-compare', () => import('../views/mobile/MobileCompareView.vue')],
  // 내 캠핑 (예약 후 정보) → 출발 준비 체크리스트 · 가족 모임방. 회원 전용
  ['/my-camping', 'my-camping', () => import('../views/mobile/MobileMyCampingView.vue')],
  ['/my-camping/:planId(\\d+)/checklist', 'my-camping-checklist', () => import('../views/mobile/MobileChecklistView.vue')],
  ['/my-camping/family', 'family-room', () => import('../views/mobile/MobileFamilyRoomView.vue')],
  // 1분 진단 (누구나 할 수 있고, 로그인했으면 서버에 저장)
  ['/survey', 'survey', () => import('../views/mobile/MobileSurveyView.vue')],
  // 내 정보와 그 안의 화면들 (여행 취향 · 알림)
  ['/my-info', 'my-info', () => import('../views/mobile/MobileMyInfoView.vue')],
  ['/my-info/activity', 'activity', () => import('../views/mobile/MobileActivityView.vue')],
  ['/my-info/alerts', 'alerts', () => import('../views/mobile/MobileAlertView.vue')],
  // 탐색 · 캠핑장 · 시설 · 관광지
  ['/explore', 'explore', () => import('../views/mobile/MobileExploreView.vue')],
  [
    '/camps/:id(\\d+)', 'camp-detail', () => import('../views/mobile/MobileCampDetailView.vue'),
    (route) => ({ id: Number(route.params.id) }),
  ],
  [
    '/facilities/:id(\\d+)', 'facility-detail', () => import('../views/mobile/MobileFacilityDetailView.vue'),
    (route) => ({ id: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null }),
  ],
  [
    '/tours/:id(\\d+)', 'tour-detail', () => import('../views/mobile/MobileTourDetailView.vue'),
    (route) => ({ id: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null }),
  ],
  // 여행 취향 (관광지 탭 순서용). ?camp=캠핑장id 로 끝난 뒤 돌아갈 캠핑장을 받는다
  [
    '/tour-preference', 'tour-preference', () => import('../views/mobile/MobileTourPreferenceView.vue'),
    (route) => ({ campId: route.query.camp ? Number(route.query.camp) : null }),
  ],
  // 내 주변 · 차박 지도 · 재난 대피 3D 지도(이 화면에 들어올 때만 Cesium 을 불러온다). 로그인 없이도 볼 수 있다
  ['/nearby', 'nearby', () => import('../views/mobile/MobileNearbyView.vue')],
  ['/chabak', 'chabak', () => import('../views/mobile/MobileChabakView.vue')],
  ['/disaster', 'disaster', () => import('../views/mobile/MobileDisasterMapView.vue')],
]

export const APP_PREFIX = '/app'

// 실제 모바일 라우트 (/app/...)
export const mobileRoutes = defs.map(([path, name, component, props]) => ({
  path: APP_PREFIX + path,
  name,
  component,
  ...(props ? { props } : {}),
}))

// 옛 주소 → /app 주소로 넘기는 라우트. 쿼리(?camp=…)와 # 는 그대로 가져간다
// (예: 공유된 /camps/12?tab=tour → /app/camps/12?tab=tour)
export const legacyRedirects = defs.map(([path]) => ({
  path,
  redirect: (to) => ({ path: APP_PREFIX + to.path, query: to.query, hash: to.hash }),
}))
