package com.campin.nearby;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 기준 캠핑장의 가까운 시설 한 줄 (camp_facility + facility 조회 결과)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NearbyFacilityRow {
    private Long id;          // facility.id
    private String type;      // er, fire, police, store (NearbyLayer code 와 같다)
    private String name;
    private double lat;
    private double lng;
    private double distanceKm;
    private int driveMin;
    private boolean dummy;    // 시설이나 거리 중 하나라도 더미면 true → 길찾기를 이름 검색으로 연다
}
