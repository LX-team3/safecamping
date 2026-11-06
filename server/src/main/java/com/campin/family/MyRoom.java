package com.campin.family;

// 로그인한 회원과 그 회원이 들어가 있는 가족방 번호. FamilyService.requireMyRoom 이 만든다
// 아이 카드·일정 서비스는 이 roomId 로만 데이터를 찾으므로 다른 가족방 데이터에 닿지 않는다
public record MyRoom(Long memberId, String memberName, Long roomId) {
}
