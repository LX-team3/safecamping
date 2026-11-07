package com.campin.camp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 캠핑장의 가까운 시설 한 줄 (camp_facility + facility 조회 결과)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampFacilityRow {
    private Long id;          // facility.id
    private String type;      // er, fire, police, store, toilet
    private String name;
    private double lat;
    private double lng;
    private double distanceKm;
    private int driveMin;
}
