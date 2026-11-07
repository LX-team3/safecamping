// 카카오맵 JS SDK 불러오기
// 키는 client/.env.local 의 VITE_KAKAO_MAP_KEY 에 "JavaScript 키"를 넣는다 (깃에 올라가지 않음)
// 키가 없으면 null → 지도 부품이 임시 그림을 보여 준다. 키를 넣은 뒤에는 npm run dev 를 다시 켜야 읽힌다
// 카카오 개발자 콘솔 > 앱 > 플랫폼 > Web 에 http://localhost:5173 을 등록해야 지도가 뜬다
let loading = null

export function loadKakaoMap() {
  const key = import.meta.env.VITE_KAKAO_MAP_KEY
  if (!key) return Promise.resolve(null)
  if (window.kakao?.maps?.LatLng) return Promise.resolve(window.kakao)

  // 여러 화면이 동시에 불러도 스크립트는 한 번만 붙인다
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      // autoload=false: 스크립트를 받은 뒤 kakao.maps.load() 로 직접 초기화 (Vite 처럼 나중에 붙이는 경우 필요)
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`
      script.onload = () => window.kakao.maps.load(() => resolve(window.kakao))
      script.onerror = () => {
        loading = null
        reject(new Error('카카오 지도를 불러오지 못했어요. 키와 카카오 콘솔 설정을 확인해 주세요.'))
      }
      document.head.appendChild(script)
    })
  }
  return loading
}

// 점들이 전부 보이게 지도 범위를 맞춘다
// padding 은 [위, 오른쪽, 아래, 왼쪽](px). 점이 하나거나 아주 가까우면 setBounds 가 최대로 확대해 버려서 minLevel 까지만 확대한다
export function fitPoints(kakao, map, points, padding, minLevel = 5) {
  if (!points.length) return
  const bounds = new kakao.maps.LatLngBounds()
  points.forEach((p) => bounds.extend(new kakao.maps.LatLng(p.lat, p.lng)))
  map.setBounds(bounds, ...padding)
  if (map.getLevel() < minLevel) map.setLevel(minLevel)
}

// 지도 칸 크기가 바뀌면(창 크기 조절 등) 카카오 지도에 알려 준다 (안 알리면 회색 빈칸이 생긴다)
// 돌려준 함수를 화면을 떠날 때 부르면 감시를 멈춘다
export function keepMapSized(map, el) {
  const observer = new ResizeObserver(() => map.relayout())
  observer.observe(el)
  return () => observer.disconnect()
}
