package com.campin.tour;

import java.util.List;

import com.campin.place.PlaceInfo;
import com.campin.place.PlaceNote;

// GET /api/tours/{id}?campId= 응답: 관광지 상세 화면
// from: 어느 캠핑장에서 왔는지 (campId 를 안 주거나 연결이 없으면 null)
public record TourDetailResponse(
        Long id,
        String name,
        String categoryLabel,
        Integer popularityRank,
        String sido,
        String sigungu,
        String eupmyeon,
        double lat,
        double lng,
        boolean dummy,
        From from,
        List<PlaceInfo> infos,
        List<PlaceNote> notes) {

    public record From(Long campId, String campName, double lat, double lng, int driveMin) {
    }
}
