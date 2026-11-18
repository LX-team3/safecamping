package com.campin.facility;

import lombok.Data;

// 지도 위 시설 점 하나 (탐색 지도 "지도 설정"에서 켠 종류). 값은 facility 테이블
@Data  // lombok: getter·setter 를 만들어 준다 (MyBatis 가 setter 로 값을 채움)
public class FacilityPin {
    private Long id;
    private String type;    // er · fire · police · store · pharmacy (facility.type)
    private String name;
    private Double lat;
    private Double lng;
    private Boolean dummy;  // 더미 데이터면 true
}
