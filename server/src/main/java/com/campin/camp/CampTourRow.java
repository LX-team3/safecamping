package com.campin.camp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 캠핑장의 주변 관광지 한 줄 (camp_tour + tour 조회 결과)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampTourRow {
    private Long id;                // tour.id
    private String name;
    private String category;        // 여행 취향 선택지 code (예: trekking)
    private String nature;          // 여행 취향 선택지 code (예: mountain)
    private Integer popularityRank;
    private int driveMin;
    private String note;            // 예: 연령 제한 확인
}
