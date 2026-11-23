import { createRouter, createWebHistory } from 'vue-router'
import { mobileRoutes, legacyRedirects, APP_PREFIX } from './mobileRoutes'
import { webRoutes } from './webRoutes'
import { webToAppPath } from '@/utils/webLinks'

// 주소 규칙: 웹(PC)은 /web/..., 모바일(앱)은 /app/... (화면 틀은 App.vue 가 route.meta.layout 으로 고른다)
//   - 모바일 라우트: router/mobileRoutes.js   - 웹 라우트: router/webRoutes.js

// 화면 폭이 이 값 이상이면 웹(PC)으로 본다 (주소에 /web · /app 이 없는 처음 접속에만 쓴다)
const WEB_MIN_WIDTH = 768
const isWideScreen = () => window.matchMedia(`(min-width: ${WEB_MIN_WIDTH}px)`).matches

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 처음 들어올 때 화면 폭을 보고 웹(PC)이면 /web, 모바일이면 /app/explore 로 보낸다
    { path: '/', redirect: () => (isWideScreen() ? '/web' : `${APP_PREFIX}/explore`) },
    { path: APP_PREFIX, redirect: `${APP_PREFIX}/explore` },

    // 메타버스 안전 탐험 (웹·모바일 같이 씀). 3D(three.js)는 이 화면에 들어올 때만 불러온다
    // layout 'full' = 웹·모바일 틀 없이 화면 전체에 그린다 (App.vue)
    {
      path: '/metaverse',
      name: 'metaverse',
      component: () => import('../views/metaverse/MetaverseView.vue'),
      meta: { layout: 'full' },
    },

    ...mobileRoutes, // 모바일(앱) 주소 (/app/...)
    ...webRoutes, // 웹(PC) 주소 (/web/...)
    ...legacyRedirects, // 옛 모바일 주소(/explore …) → /app/... (이전 링크·북마크 보호)
  ],
})

// 좁은 화면(휴대폰)에서 /web/... 주소로 들어오면 같은 화면의 모바일 주소(/app/...)로 보낸다 (공유된 링크 대비)
// 모바일에 같은 이름의 화면이 없으면(/web/my-booking 등) 탐색으로 보낸다
router.beforeEach((to) => {
  if (/^\/web(\/|$)/.test(to.path) && !isWideScreen()) {
    // 웹에만 있는 이름은 모바일의 같은 화면으로 바꿔 준다 (대응표는 utils/webLinks.js 의 webToAppPath 한곳)
    const target = webToAppPath(to.path)
    if (target && router.resolve(target).matched.length) return { path: target, query: to.query, hash: to.hash }
    return `${APP_PREFIX}/explore`
  }
})

export default router
