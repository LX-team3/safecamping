package com.campin.facility;

import java.util.List;

import com.campin.place.PlaceInfo;
import com.campin.place.PlaceNote;

// GET /api/facilities/{id}?campId= 응답: 시설 상세 화면
// from: 어느 캠핑장에서 왔는지 (campId 를 안 주거나 연결이 없으면 null)
// from.grade: 그 캠핑장의 안전점수 등급 safe·caution·danger·unknown (CampService.gradeOf) → 지도 마커 색
public record FacilityDetailResponse(
        Long id,
        String type,
        String name,
        String sido,
        String sigungu,
        String eupmyeon,
        String address,
        String phone,
        String hours,
        double lat,
        double lng,
        boolean dummy,
        From from,
        List<PlaceInfo> infos,
        List<PlaceNote> notes) {

    public record From(Long campId, String campName, double lat, double lng,
                       double distanceKm, int driveMin, String tone, String grade) {
    }
}
