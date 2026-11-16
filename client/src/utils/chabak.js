// 차박 판정 화면에서 같이 쓰는 값 (판정 색·문구, 거리 표시)
// 판정 4단계는 서버 GET /api/chabak/verdict 의 verdict 값 그대로 (server/CLAUDE.md)
// 색은 styles/tokens.css 의 --color-verdict-* 변수 (화면은 var(), 카카오 지도는 verdictColor() 로 읽음)
export const VERDICT = {
  불가: { colorVar: '--color-verdict-no', title: '차박할 수 없는 곳이에요' },
  주의: { colorVar: '--color-verdict-caution', title: '확인이 필요한 곳이에요' },
  '제한 없음': { colorVar: '--color-verdict-open', title: '공공데이터상 제한이 없는 곳이에요' },
  '야영 가능': { colorVar: '--color-verdict-camp', title: '등록된 야영장이에요' },
}

// 카카오 지도 다각형은 CSS 변수를 못 읽어서, 변수에 들어 있는 실제 색 값을 꺼내 준다
export function verdictColor(verdict) {
  const name = VERDICT[verdict]?.colorVar ?? '--color-text-sub'
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

// 차박 지도 "판정 표시" 레이어 ("지도 설정" 시트에서 하나만 켬) — 서버 GET /api/chabak/layer?type=
// 이름에 "구역 · 후보지 · 야영장"을 붙인 이유: 판정 단계 전체가 아니라 데이터로 보여 줄 수 있는 것만 보이기 때문
//  (예: "주의"를 골라도 사유지·급경사 같은 필지별 주의는 안 나온다 → 정확한 판정은 지도를 눌러서)
export const LAYER_CHIPS = [
  // note: 시트를 올렸을 때만 보이는 한 줄 설명 (지도를 가리지 않게 짧게). zone: 구역(면) 레이어 — 서버가 한 덩어리로 합쳐 보냄
  { type: 'no', label: '불가 구역', verdict: '불가', zone: true, note: '국립공원 등 금지 구역 · 구역 밖도 불가일 수 있어요' },
  { type: 'caution', label: '주의 구역', verdict: '주의', zone: true, note: '하천·산림보호구역 등 · 사유지·급경사는 눌러야 보여요' },
  { type: 'open', label: '차박 후보지', verdict: '제한 없음', note: '제한 없는 국공유 공터 · 합법 보증은 아니에요' },
  { type: 'camp', label: '등록 야영장', verdict: '야영 가능', note: '점을 누르면 그 자리 판정을 볼 수 있어요' },
]

// 처음 화면·"인제군으로 이동" 때 보여 줄 위치 (판정 범위가 지금 인제군뿐)
export const INJE_CENTER = { lat: 38.07, lng: 128.25 }

// 카카오 키가 없을 때 눌러 볼 시연 지점 (노션 '혼박 차박 판정' 페이지의 시연 후보)
export const DEMO_POINTS = [
  { label: '불가 · 설악산 국립공원', lat: 38.128712, lng: 128.422383 },
  { label: '불가 · 상남천 하천', lat: 37.873166, lng: 128.27298 },
  { label: '주의 · 주차장', lat: 38.069826, lng: 128.174655 },
  { label: '주의 · 개인 밭', lat: 38.073123, lng: 128.176086 },
  { label: '제한 없음 · 국유 공터', lat: 38.078121, lng: 128.324378 },
]

// 850 → "850m", 10360 → "10.4km"
export function distanceText(m) {
  if (m == null) return ''
  return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1)}km`
}
