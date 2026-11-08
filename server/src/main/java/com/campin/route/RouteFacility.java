package com.campin.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 경로의 목적지: 캠핑장과 연결된 응급실·소방서·경찰서 (camp_facility + facility 조회 결과)
// distanceKm·driveMin 은 DB 값 — 카카오 길찾기에 실패했을 때 직선과 함께 보여 준다
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteFacility {
    private Long id;          // facility.id
    private String type;      // er, fire, police
    private String name;
    private double lat;
    private double lng;
    private double distanceKm;
    private int driveMin;
    private boolean dummy;    // 시설 좌표가 더미면 true → 화면에 "예시 위치 기준"
}
