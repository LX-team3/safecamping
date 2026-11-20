// 웹 화면이 다른 앱으로 보내는 주소
// 메타버스(metaverse/ 폴더)는 client 와 따로 실행·배포하는 별도 앱이다.
// 배포 주소는 client/.env.local 의 VITE_METAVERSE_URL 에 넣는다. 없으면 로컬 개발 주소를 쓴다
// (client 가 5173 을 쓰므로 metaverse 는 npm run dev 하면 5174 로 뜬다)
export const METAVERSE_URL = import.meta.env.VITE_METAVERSE_URL || 'http://localhost:5174/metaverse'

// 3D 재난 대피 지도는 client 안의 화면이다 (router/mobileRoutes.js 의 /app/disaster)
export const DISASTER_PATH = '/app/disaster'
