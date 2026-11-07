package com.campin.tour;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 어느 캠핑장에서 왔는지: 그 캠핑장에서 이 관광지까지 시간 (camp_tour + camp 조회 결과)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourFrom {
    private Long campId;
    private String campName;
    private double lat;
    private double lng;
    private int driveMin;
}
