package com.campin.camp;

import java.util.List;

import com.campin.place.PlaceInfo;

// GET /api/camps/{id} 응답: 캠핑장 상세 화면 (안전점수 탭 · 캠핑장 정보 탭 · 경로 지도)
// 관광지 탭은 취향에 따라 순서가 바뀌어서 /api/camps/{id}/tours 로 따로 받는다
public record CampDetailResponse(
        Long id,
        String name,
        String sido,
        String sigungu,
        String eupmyeon,
        String campType,
        double lat,
        double lng,
        Integer safetyScore,
        String grade,
        String headline,      // 점수 옆 한 줄 (등급에 따라)
        String imageUrl,
        String reservationUrl,
        boolean dummy,
        List<Facility> facilities,
        List<PlaceInfo> infos,
        int erBonus) {        // 진단 유형 가산점 0~5 (ScoreRules.erBonus). 화면이 진단 답에 따라 "71 +5" 로 따로 보여 준다

    // tone: safe(초록) / caution(주황) — DriveTone 참고
    public record Facility(Long id, String type, String name, double lat, double lng,
                           double distanceKm, int driveMin, String tone) {
    }
}
