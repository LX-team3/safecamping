package com.campin.family;

import java.time.OffsetDateTime;

// 위치 탭에 찍을 사람 하나
// 좌표 이름은 화면의 카카오 지도 도우미(utils/kakaoMap.js)에 맞춰 lat · lng 로 보낸다 (DB 는 lon)
// me: 로그인한 사람이면 true → 화면이 마커 아래에 "나" 표시를 붙인다 (구성원 목록의 me 와 같은 방식)
// seq: 방에 들어온 순번(1부터) → 화면이 구성원 이름 칩과 같은 색으로 마커를 칠한다. 누가 보든 같은 사람은 같은 색
public record LocationResponse(Long id, String name, Double lat, Double lng, boolean me, OffsetDateTime updatedAt, Integer seq) {

    public static LocationResponse from(FamilyLocation l, Long myMemberId) {
        return new LocationResponse(l.getMemberId(), l.getName(), l.getLat(), l.getLon(),
                l.getMemberId().equals(myMemberId), l.getUpdatedAt(), l.getSeq());
    }
}
