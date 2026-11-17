package com.campin.chabak;

import tools.jackson.databind.JsonNode;

// 차박 지도 레이어 한 곳 (응답). 구역(불가·주의)은 geometry, 점(후보지·야영장)은 lat·lng
public record ChabakLayerItem(
        Long id,            // 등록 야영장이면 camp.id, 나머지는 null
        String name,        // 구역 이름 · 지번 · 야영장 이름
        Double lat,
        Double lng,
        Integer score,      // 안전점수 (후보지·야영장, 캠핑장과 같은 계산). 구역·데이터 없음은 null
        String grade,       // safe 70↑ · caution 40↑ · danger (score 가 null 이면 null) — 화면 "주변 안전" 필터
        String area,        // 목록 둘째 줄 (야영장 "인제군 북면" · 후보지 "군유지"). 구역은 null
        String imageUrl,    // 야영장 사진. 후보지·구역은 null
        Integer erDriveMin, // 응급실까지 차로 몇 분 (후보지는 직선거리 어림). 모르면 null
        JsonNode geometry) { // 구역 경계 GeoJSON (점이면 null)
}
