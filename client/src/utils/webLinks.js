// 웹 화면이 다른 앱으로 보내는 주소
// 메타버스 안전 탐험은 client 안의 화면이다 (router/index.js 의 /metaverse, 코드는 src/metaverse/)
// RouterLink 로 연다. 새 창(target="_blank")은 앱 안에서 안 열리거나 밖으로 나가므로 쓰지 않는다
export const METAVERSE_PATH = '/metaverse'

// 3D 재난 대피 지도는 client 안의 화면이다 (router/webRoutes.js 의 /web/disaster. 앱은 /app/disaster)
export const DISASTER_PATH = '/web/disaster'
