package com.campin.chabak;

import tools.jackson.databind.JsonNode;

// 차박 지도 레이어 한 곳 (응답). 구역(불가·주의)은 geometry, 점(후보지·야영장)은 lat·lng
public record ChabakLayerItem(
        Long id,            // 등록 야영장이면 camp.id, 나머지는 null
        String name,        // 구역 이름 · 지번 · 야영장 이름
        Double lat,
        Double lng,
        JsonNode geometry) { // 구역 경계 GeoJSON (점이면 null)
}
