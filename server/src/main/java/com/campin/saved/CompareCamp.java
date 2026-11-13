package com.campin.saved;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 비교 표에 쓸 캠핑장 한 곳의 값. SavedMapper.findCompareCamps 가 camp · camp_facility · raw.camps(원본) 에서 채운다
// 값이 없으면 null → 화면에는 "확인 필요"
@Getter
@Setter
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class CompareCamp {
    private Long id;              // saved_camp.camp_id (= camp.id)
    private String name;
    private Integer safetyScore;  // camp.safety_score
     private Integer erMinutes;    // camp.er_drive_min (가장 가까운 응급실까지 차로. score/ 계산 전이면 직선 추정, 계산 후 카카오 시간)
    private Integer fireMinutes;  // camp_facility 중 소방서까지 차로 (계산 전 추정 / 계산 후 카카오)
    private Boolean hasPower;     // 원본 camps.has_electricity
    private Boolean allowsPet;    // 원본 camps.pet_allowed
}
