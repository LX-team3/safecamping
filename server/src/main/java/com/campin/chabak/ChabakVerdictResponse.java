package com.campin.chabak;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import tools.jackson.databind.JsonNode;

// 차박 판정 응답 (verdict = 판정). 값은 DB 함수 chabak.fn_verdict_json 이 만든 JSON 을 이 모양으로 옮긴 것
// DB 함수에 필드를 새로 넣으면 여기에도 추가해야 화면까지 간다
public record ChabakVerdictResponse(
        String pnu,                     // 필지 고유번호
        String jibun,                   // 지번 (예: 산12-21)
        String jimok,                   // 지목 (예: 임야, 주차장)
        String owner,                   // 소유 (예: 국유지, 개인)
        String landUse,                 // 토지이용상황
        String terrain,                 // 지형높이 (급경사 등)
        String roadSide,                // 도로접면 (맹지 등)
        String verdict,                 // 판정: 불가 · 주의 · 제한 없음 · 야영 가능
        List<String> reasons,           // 판정 사유 목록
        List<String> zones,             // 걸린 규제구역 목록
        Boolean nearBoundary,           // 필지 경계 3m 안쪽인지 (연속지적도 위치 오차)
        List<Camp> nearbyCamps,         // 가까운 등록 야영장 3곳
        List<Alternative> alternatives, // 주의·불가일 때 가까운 '제한 없음' 자리 3곳
        JsonNode parcel) {              // 필지 경계 GeoJSON (모양이 복잡해서 그대로 전달 → 지도에 판정 색으로 칠함)

    // DB 함수는 dist_m 으로 보내지만 다른 필드와 맞춰 distM 으로 내보낸다
    public record Camp(String name, @JsonAlias("dist_m") Integer distM) { }

    // radiusM: 차를 댈 수 있는 공간 크기 (필지 안 가장 큰 원의 반지름, m)
    public record Alternative(String jibun, String owner, Double radiusM, Integer distM,
                              Double lat, Double lng) { }
}
