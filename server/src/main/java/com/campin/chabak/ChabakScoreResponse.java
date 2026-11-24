package com.campin.chabak;

import java.util.List;

// 찍은 지점의 안전점수 (db/merge_to_public.sql 8 단계의 직선거리 + raw.score_weights 계산)
// 지금 캠핑장 점수(camp.safety_score)는 카카오 차 시간 + ScoreRules 로 계산해서 기준이 다르다
public record ChabakScoreResponse(
        Integer score,          // 0~100 (계산할 시설 데이터가 없으면 null)
        String grade,           // safe 70↑ · caution 40↑ · danger · unknown (CampService.gradeOf 와 같음)
        String headline,        // 점수 옆 한 줄 (캠핑장 상세와 같은 문구, CampService.headlineOf)
        Integer erDriveMin,     // 응급실까지 차로 몇 분 (추정: 직선거리 × 1.4 ÷ 시속 45km, 캠핑장과 같은 식)
        List<Item> items) {     // 종류별 가장 가까운 시설 (가중치 큰 순)

    public record Item(String type, Integer distM, Integer weight, Integer part) { } // part: 부분점수 0~100
}
