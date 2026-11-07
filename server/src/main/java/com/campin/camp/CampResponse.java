package com.campin.camp;

// 캠핑장 한 곳 (목록 한 줄 · 지도 마커 하나)
// grade: safe(안전) / caution(주의) / danger(위험) / unknown(점수 없음) — 계산은 CampService.gradeOf
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
        boolean dummy) {

    static CampResponse from(Camp c) {
        return new CampResponse(c.getId(), c.getName(), c.getSigungu(), c.getEupmyeon(), c.getCampType(),
                c.getLat(), c.getLng(), c.getSafetyScore(), CampService.gradeOf(c.getSafetyScore()),
                c.getErDriveMin(), c.getImageUrl(), c.isDummy());
    }
}
