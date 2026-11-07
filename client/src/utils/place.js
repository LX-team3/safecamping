// 상세 화면들이 같이 쓰는 이름표·링크

export const FACILITY_LABEL = { er: '응급실', fire: '소방서', police: '지구대', store: '편의점', toilet: '화장실' }

// 강원특별자치도 → 강원
const SHORT_SIDO = {
  서울특별시: '서울', 부산광역시: '부산', 대구광역시: '대구', 인천광역시: '인천', 광주광역시: '광주',
  대전광역시: '대전', 울산광역시: '울산', 세종특별자치시: '세종', 경기도: '경기', 강원특별자치도: '강원',
  충청북도: '충북', 충청남도: '충남', 전북특별자치도: '전북', 전라남도: '전남', 경상북도: '경북',
  경상남도: '경남', 제주특별자치도: '제주',
}
export function shortSido(name) {
  return SHORT_SIDO[name] || name || ''
}

// 카카오맵 길찾기 링크 (키 없이 열림)
// 더미 좌표로 길을 안내하면 엉뚱한 곳으로 갈 수 있다 → 더미는 이름 검색으로 연다
export function directionsUrl(place, from) {
  const point = (p) => `${encodeURIComponent(p.name)},${p.lat},${p.lng}`
  if (place.dummy) return `https://map.kakao.com/link/search/${encodeURIComponent(place.name)}`
  if (from) return `https://map.kakao.com/link/from/${point(from)}/to/${point(place)}`
  return `https://map.kakao.com/link/to/${point(place)}`
}
