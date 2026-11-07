package com.campin.facility;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 어느 캠핑장에서 왔는지: 그 캠핑장에서 이 시설까지 거리·시간 (camp_facility + camp 조회 결과)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacilityFrom {
    private Long campId;
    private String campName;
    private double lat;
    private double lng;
    private double distanceKm;
    private int driveMin;
}
