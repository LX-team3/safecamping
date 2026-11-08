package com.campin.nearby;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 내 위치에서 가장 가까운 캠핑장 (camp 테이블 조회 결과). 안전점수·시설 시간의 기준이 된다
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class NearbyCamp {
    private Long id;
    private String name;
    private String sido;
    private String sigungu;
    private String eupmyeon;
    private double lat;
    private double lng;
    private Integer safetyScore;  // 없으면 null
    private boolean dummy;
}
