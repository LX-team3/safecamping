package com.campin.camp;

// 캠핑장 상세 관광지 탭 한 줄. matched: 내 여행 취향과 맞는지
public record CampTourResponse(Long id, String name, int driveMin, String note, boolean matched) {
}
