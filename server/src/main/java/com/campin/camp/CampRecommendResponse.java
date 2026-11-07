package com.campin.camp;

import java.util.List;

// 내 유형 추천 TOP N
// - camps   : 추천 목록 (위험 등급은 뺌)
// - excluded: 위험 등급이라 추천에서 뺀 캠핑장 ("따로 보기"에 씀)
// - criteria: 지금 쓰는 추천 기준 설명 (임시 기준이라 화면에 그대로 보여 준다)
public record CampRecommendResponse(
        List<CampResponse> camps,
        List<CampResponse> excluded) {
}
