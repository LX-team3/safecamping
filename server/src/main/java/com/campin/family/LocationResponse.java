package com.campin.family;

import java.time.OffsetDateTime;

// 위치 탭에 찍을 사람 하나
// 좌표 이름은 화면의 카카오 지도 도우미(utils/kakaoMap.js)에 맞춰 lat · lng 로 보낸다 (DB 는 lon)
// me: 로그인한 사람이면 true → 화면이 "나" 마커를 다른 색으로 그린다 (구성원 목록의 me 와 같은 방식)
public record LocationResponse(Long id, String name, Double lat, Double lng, boolean me, OffsetDateTime updatedAt) {

    public static LocationResponse from(FamilyLocation l, Long myMemberId) {
        return new LocationResponse(l.getMemberId(), l.getName(), l.getLat(), l.getLon(),
                l.getMemberId().equals(myMemberId), l.getUpdatedAt());
    }
}
