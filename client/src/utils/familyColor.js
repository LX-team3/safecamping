// 가족 모임방 사람별 색 · 시연용 위치. 구성원 이름 칩(MobileFamilyRoomView)과 위치 탭 마커(MobileLocationTab)가 같이 쓴다
// 둘 다 "방에 들어온 순번(seq, 1부터)"으로 정한다 → 칩과 마커가 같은 색, 누가 보든 같은 사람은 같은 색
//   구성원 목록(room.members)은 들어온 순서라 "몇 번째인가 + 1" 이 seq 와 같다 (서버 FamilyMapper.findMembers · LocationMapper.findAll 순서가 같음)

// tokens.css 의 --color-family-1 ~ 8. 9번째 사람부터는 1번 색부터 다시 쓴다 (이름표로 구분)
export const FAMILY_COLOR_COUNT = 8

export function familyColor(seq) {
  const no = (((seq ?? 1) - 1) % FAMILY_COLOR_COUNT) + 1
  return `var(--color-family-${no})`
}

// ---- 시연용 위치 (실제 GPS 아님) ----
// 기준점: 사용자가 준 좌표 (2026-11-27). 모든 구성원을 이 주변에 사람마다 정해진 자리로 찍는다
export const DEMO_BASE = { lat: 37.494361, lng: 126.887681 }

// seq 번째 사람의 시연용 자리. 같은 사람은 몇 번을 눌러도 같은 자리 → 시연 화면이 매번 똑같다
// 방향: 137.5°씩 돌린다 (해바라기 씨 배치처럼 사람이 늘어도 한쪽에 몰리지 않음)
// 거리: 120m 에서 시작해 한 사람마다 30m 씩 멀어진다 (8번째 사람이 330m) → 캠핑장 안에 흩어진 정도
// 거리(m) → 위·경도: 위도 1° ≈ 111,320m, 경도 1° 는 위도가 높을수록 짧아져 cos(위도)를 곱한다
export function demoSpot(seq) {
  const i = (seq ?? 1) - 1
  const angle = (i * 137.5 * Math.PI) / 180
  const meters = 120 + 30 * i
  const dLat = (meters * Math.cos(angle)) / 111320
  const dLng = (meters * Math.sin(angle)) / (111320 * Math.cos((DEMO_BASE.lat * Math.PI) / 180))
  // 소수 6자리(약 10cm)까지만 → 서버에 저장되는 값이 깔끔하다
  return {
    lat: Number((DEMO_BASE.lat + dLat).toFixed(6)),
    lng: Number((DEMO_BASE.lng + dLng).toFixed(6)),
  }
}
