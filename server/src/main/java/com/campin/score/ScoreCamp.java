package com.campin.score;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 점수를 계산할 캠핑장 (camp 테이블)
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class ScoreCamp {
    private Long id;
    private String name;
    private double lat;
    private double lng;
}
