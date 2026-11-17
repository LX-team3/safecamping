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

// 차박 지도 레이어 — 서버 GET /api/chabak/layer?type=, "차박지 필터" 시트에서 켜고 끈다
//  - section 'go'   갈 수 있는 곳 (점): 처음부터 켬 (on: true). "주변 안전" 필터가 적용됨
//  - section 'avoid' 피해야 할 곳 (면, zone): 처음엔 꺼 둠 — 켜면 지도가 빨갛게 덮여 갈 수 있는 곳이 안 보여서
//    구역 이름에 "구역"을 붙인 이유: 판정 단계 전체가 아니라 데이터로 보여 줄 수 있는 구역만 보이기 때문
//    (예: 주의 구역을 켜도 사유지·급경사 같은 필지별 주의는 안 나온다 → 정확한 판정은 지도를 눌러서)
export const LAYER_CHIPS = [
  // note: 한 줄 설명
  { type: 'open', section: 'go', on: true, label: '차박 후보지', verdict: '제한 없음',
    note: '공공데이터상 제한 없는 국공유 공터 · 합법 보증은 아니에요' },
  { type: 'camp', section: 'go', on: true, label: '등록 야영장', verdict: '야영 가능',
    note: '등록된 야영장 · 점을 누르면 판정을 볼 수 있어요' },
  { type: 'no', section: 'avoid', on: false, zone: true, label: '불가 구역', verdict: '불가',
    note: '국립공원 · 산림유전자원보호구역 등 · 구역 밖도 불가일 수 있어요' },
  { type: 'caution', section: 'avoid', on: false, zone: true, label: '주의 구역', verdict: '주의',
    note: '하천 · 산림보호구역 등 · 사유지 · 급경사는 지도를 눌러야 보여요' },
]

// "주변 안전" 필터 (차박 후보지 · 등록 야영장의 안전점수로 거름). 기준은 캠핑장 등급과 같음 (안전 70↑ · 주의 40↑)
// min 이 있으면 점수가 그 이상인 곳만 (점수가 없는 곳은 '전체'에서만 보임)
export const SAFETY_FILTERS = [
  { key: 'all', label: '전체', min: null },
  { key: 'caution', label: '주의 이상', min: 40 },
  { key: 'safe', label: '안전만', min: 70 },
]

// 안전점수 (캠핑장 안전점수와 같은 등급·이름) — 서버 GET /api/chabak/score
export const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '-' }
// 점수 근거 줄 이름 (raw.score_weights.facility_type)
export const SCORE_LABEL = {
  er: '응급실', fire: '소방서', police: '지구대', store: '편의점', toilet: '공중화장실',
  pharmacy: '약국', cctv: 'CCTV', light: '보안등', house: '인근 주택',
}

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

// 차로 걸리는 시간(분) 어림: 직선거리 × 1.4(굽은 길) ÷ 시속 45km, 최소 1분
// 서버 ChabakService.driveMin · merge_to_public.sql 의 pg_temp.drive_min 과 같은 식 (실제 길찾기 시간 아님)
export function driveMinOf(km) {
  if (km == null) return null
  return Math.max(1, Math.ceil(((km * 1.4) / 45) * 60))
}

// 850 → "850m", 10360 → "10.4km"
export function distanceText(m) {
  if (m == null) return ''
  return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1)}km`
}
