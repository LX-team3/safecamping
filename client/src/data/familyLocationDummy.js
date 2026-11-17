// 가족방 · 위치 탭 더미 데이터 (시연용 가상 인물 · 가상 좌표)
// 실제 GPS 위치가 아니다. DB 테이블(family_location 예정)을 협의하기 전까지 화면 확인용으로만 쓴다
// 나중에 GET /api/family/locations 로 바꾸면 이 파일은 지운다
// 좌표 이름은 카카오 지도 도우미(utils/kakaoMap.js)에 맞춰 lat(위도) · lng(경도)
// 좌표: 인제군청(강원 인제군 인제읍) 근처 — 지도에서 눈으로 맞춘 대략값
// me: 지금 로그인한 사람(나)이면 true. 가족방 구성원 응답(FamilyRoomResponse 의 me)과 같은 방식이라 서버 연결 때도 그대로 쓴다
//     더미라 로그인 계정과 실제로 연결돼 있지 않다 → 시연에서 "나"로 보일 사람에게 붙인다
export const familyLocationDummy = [
  { id: 1, name: '이용효', lat: 38.0699, lng: 128.17},
  { id: 2, name: '이용순', lat: 38.0695, lng: 128.1706 },
  { id: 3, name: '박석진', lat: 38.0633, lng: 128.17, me: true },
]
