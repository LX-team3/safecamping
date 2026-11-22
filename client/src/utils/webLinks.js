// 웹 화면이 다른 앱으로 보내는 주소
// 메타버스(metaverse/ 폴더)는 따로 실행하는 별도 앱이지만, client 와 같은 주소 아래 /metaverse/ 로 묶는다
// (개발: client vite.config.js 의 proxy 가 /metaverse → 5174 로 넘긴다)
// 앞의 /metaverse/ = 메타버스 base, 뒤의 metaverse = 메타버스 라우터의 게임 화면 경로
// http://localhost:5174 같은 주소를 직접 쓰지 말 것 — 다른 주소가 되면 로그인 쿠키가 안 넘어가고 앱 안에서도 안 열린다
// 링크는 새 창(target="_blank") 없이 같은 탭에서 연다
export const METAVERSE_URL = import.meta.env.VITE_METAVERSE_URL || '/metaverse/metaverse'

// 3D 재난 대피 지도는 client 안의 화면이다 (router/webRoutes.js 의 /web/disaster. 앱은 /app/disaster)
export const DISASTER_PATH = '/web/disaster'
