package com.campin.chabak;

import lombok.Data;

// 차박 지도 위 안전 시설 한 곳 (찍은 지점에서 가까운 시설). 값은 facility 테이블
@Data  // lombok: getter·setter 를 만들어 준다 (MyBatis 가 setter 로 값을 채움)
public class ChabakFacility {
    private Long id;
    private String type;        // er · fire · police · store · pharmacy (facility.type)
    private String name;
    private Double lat;
    private Double lng;
    private Double distanceKm;  // 찍은 지점에서 직선거리 (어림값)
    private Boolean dummy;      // 더미 데이터면 true → 화면에 표시
}
