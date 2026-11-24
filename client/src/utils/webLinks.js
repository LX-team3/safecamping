// 웹 화면이 다른 앱으로 보내는 주소
// 메타버스 안전 탐험은 client 안의 화면이다 (router/index.js 의 /metaverse, 코드는 src/metaverse/)
// RouterLink 로 연다. 새 창(target="_blank")은 앱 안에서 안 열리거나 밖으로 나가므로 쓰지 않는다
export const METAVERSE_PATH = '/metaverse'

// 3D 재난 대피 지도는 client 안의 화면이다 (router/webRoutes.js 의 /web/disaster. 앱은 /app/disaster)
export const DISASTER_PATH = '/web/disaster'

// 웹 주소 → 같은 화면의 앱 주소 (없을 수도 있는 후보. 실제로 있는지는 router.resolve 로 확인해서 쓴다)
// 웹에만 있는 이름은 앱의 같은 화면으로 바꾼다 (예: /web/alerts → /app/my-info/alerts, /web/my-booking → /app/my-camping)
// router/index.js 의 "좁은 화면이면 앱으로" 와 웹 헤더의 "앱 화면" 버튼이 같이 쓴다
export function webToAppPath(path) {
  const rest = path
    .replace(/^\/web/, '')
    .replace(/^\/alerts$/, '/my-info/alerts')
    .replace(/^\/family$/, '/my-camping/family')
    .replace(/^\/my-booking$/, '/my-camping')
    .replace(/^\/checklist\/(\d+)$/, '/my-camping/$1/checklist')
  return rest ? `/app${rest}` : ''
}
