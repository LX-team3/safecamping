package com.campin.chabak;

import lombok.Data;

// 찍은 지점의 안전점수 계산 한 줄: 시설 종류 하나 (가장 가까운 것까지 거리 · 부분점수)
@Data  // lombok: getter·setter 를 만들어 준다 (MyBatis 가 setter 로 값을 채움)
public class ChabakScoreRow {
    private String type;      // raw.score_weights.facility_type (er · fire · police · store · toilet · cctv · light …)
    private Double weight;    // 가중치
    private Double distM;     // 가장 가까운 시설까지 직선거리(m)
    private Double part;      // 부분점수 0~1 (만점 거리 이하 1, 0점 거리 이상 0, 사이는 비례)
}
