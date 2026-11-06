package com.campin.family;

import java.util.List;

// 내 가족방 정보. 구성원은 이름만 보여 준다 (아이디·연락처는 내보내지 않는다)
public record FamilyRoomResponse(Long id, String name, String inviteCode, List<Person> members) {

    // me: 지금 로그인한 사람이면 true → 화면에서 "(나)" 표시
    public record Person(String name, boolean me) {
    }
}
