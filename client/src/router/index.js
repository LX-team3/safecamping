import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useSurveyStore } from '@/stores/survey'

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
    // ② 설문: 한 화면 파일이 1분 진단(/survey/diagnosis/1~3)과 여행 취향(/survey/travel)을 처리한다
    // step 을 빼면 1단계 (여행 취향은 단계가 하나라 /survey/travel 로 씀)
    {
      path: '/survey/:code(diagnosis|travel)/:step(\\d+)?',
      name: 'survey',
      component: () => import('../views/survey/SurveyView.vue'),
      props: (route) => ({ code: route.params.code, step: Number(route.params.step || 1) }),
    },
    // 탐색 탭: 캠핑장 목록(내 유형 추천 TOP 5) + 지도 — 한 화면에서 시트를 펼치고 접는다
    { path: '/explore', name: 'explore', component: () => import('../views/explore/ExploreView.vue') },
    // 상세 화면 3개. 시설·관광지는 ?camp=캠핑장id 로 어느 캠핑장에서 왔는지 받는다 (거리·시간 표시)
    {
      path: '/camps/:id(\\d+)',
      name: 'camp-detail',
      component: () => import('../views/camp/CampDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id) }),
    },
    {
      path: '/facilities/:id(\\d+)',
      name: 'facility-detail',
      component: () => import('../views/facility/FacilityDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null }),
    },
    {
      path: '/tours/:id(\\d+)',
      name: 'tour-detail',
      component: () => import('../views/tour/TourDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id), campId: route.query.camp ? Number(route.query.camp) : null }),
    },
  ],
})


export default router
