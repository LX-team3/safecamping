package com.campin.score;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 캠핑장에 연결된 시설 한 줄 (camp_facility + facility). 종류마다 직선으로 가장 가까운 1곳 (merge_to_public.sql 6 단계)
// 이 시설까지의 카카오 소요시간으로 점수를 내고, 화면의 분(drive_min)도 이 값으로 고친다
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoreCampFacility {
    private Long facilityId;
    private String type;  // er, fire, police, store, pharmacy, toilet
    private double lat;
    private double lng;
}
