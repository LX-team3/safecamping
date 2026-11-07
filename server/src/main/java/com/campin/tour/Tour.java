package com.campin.tour;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// tour 테이블 한 줄 (관광지)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tour {
    private Long id;
    private String name;
    private String categoryLabel;    // 예: 자연 · 트레킹
    private Integer popularityRank;
    private String sido;
    private String sigungu;
    private String eupmyeon;
    private double lat;
    private double lng;
    private boolean dummy;
}
