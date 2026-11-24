package com.campin.camp;

import com.campin.score.ScoreRules;

// 캠핑장 한 곳 (목록 한 줄 · 지도 마커 하나)
// grade: safe(안전) / caution(주의) / danger(위험) / unknown(점수 없음) — 계산은 CampService.gradeOf
// erBonus: 진단 유형 가산점 0~5 (응급실이 가까울수록 큼, ScoreRules.erBonus). 진단 답이 해당할 때만 화면이 더해 보여 준다
//          safetyScore · grade 는 가산점 없이 그대로
public record CampResponse(
        Long id,
        String name,
        String sigungu,
        String eupmyeon,
        String campType,
        double lat,
        double lng,
        Integer safetyScore,
        String grade,
        Integer erDriveMin,
        String imageUrl,
        boolean dummy,
        int erBonus) {

    static CampResponse from(Camp c) {
        return new CampResponse(c.getId(), c.getName(), c.getSigungu(), c.getEupmyeon(), c.getCampType(),
                c.getLat(), c.getLng(), c.getSafetyScore(), CampService.gradeOf(c.getSafetyScore()),
                c.getErDriveMin(), c.getImageUrl(), c.isDummy(), ScoreRules.erBonus(c.getErDriveMin()));
    }
}
