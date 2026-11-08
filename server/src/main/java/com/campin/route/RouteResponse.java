package com.campin.route;

import java.util.List;

// GET /api/camps/{id}/routes 응답: 캠핑장 → 응급실·소방서·경찰서 자동차 경로
// source: kakao(하나라도 실제 경로를 받음) / straight(전부 실패 → 직선과 DB 시간)
public record RouteResponse(String source, List<Route> routes) {

    // ok: 카카오 경로를 받았는지. false 면 path 는 출발지·도착지 두 점(직선)이고 시간·거리는 DB 값
    // dummy: 시설 좌표가 더미 → 화면에 "예시 위치 기준"
    // path: [[위도, 경도], ...] — 카카오맵 JS 의 LatLng(위도, 경도) 순서에 맞춰 바꿔서 준다
    public record Route(Long facilityId, String type, String name, boolean ok, boolean dummy,
                        double distanceKm, int durationMin, List<double[]> path) {
    }
}
