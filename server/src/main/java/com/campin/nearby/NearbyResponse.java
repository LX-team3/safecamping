package com.campin.nearby;

import java.util.List;

// GET /api/nearby?lat=&lng= 응답: 내 주변 탭 (안전점수 카드 + 시설 목록 + 지도 마커)
// 지금은 "내 위치" 자체의 점수를 계산하지 못한다 → 가장 가까운 캠핑장(basis)의 점수·시설 시간을 그대로 보여 준다
// basis 가 null 이면 주변 시설 데이터가 있는 캠핑장이 하나도 없다는 뜻
public record NearbyResponse(
        boolean dummy,
        Basis basis,
        Integer safetyScore,
        String grade,          // safe · caution · danger · unknown (CampService.gradeOf)
        List<Facility> facilities) {

    // distanceKm: 내 위치 → 기준 캠핑장 직선거리 (계산 값)
    // far: 기준 캠핑장이 멀어서 "지금 이 위치" 점수로 보기 어려울 때 → 화면에 안내
    public record Basis(Long campId, String campName, String sido, String sigungu, String eupmyeon,
                        double lat, double lng, double distanceKm, boolean far) {
    }

    // no: 화면·지도 마커 번호. tone: 차로 시간 색 (DriveTone)
    public record Facility(int no, Long id, String type, String name, double lat, double lng,
                           double distanceKm, int driveMin, String tone, boolean dummy) {
    }
}
