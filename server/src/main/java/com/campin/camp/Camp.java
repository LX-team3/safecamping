package com.campin.camp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// camp 테이블 한 줄. MyBatis 가 조회 결과를 이 객체에 채운다
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class Camp {
    private Long id;
    private String name;
    private String sido;
    private String sigungu;
    private String eupmyeon;
    private String campType;     // auto, glamping, backpacking
    private double lat;          // 위도
    private double lng;          // 경도
    private Integer safetyScore; // 안전점수 0~100 (없으면 null)
    private Integer erDriveMin;  // 응급실까지 차로 몇 분 (없으면 null)
    private String imageUrl;
    private boolean dummy;       // 더미 데이터인지 → 화면에 "더미" 표시
    private String reservationUrl;  // 예약 사이트 (상세 화면, 없으면 null)
}
