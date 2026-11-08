package com.campin.mycamping;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// camp_plan 테이블 한 줄 (내 캠핑에 등록한 캠핑)
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class CampPlan {
    private Long id;
    private Long memberId;
    private Long campId;
    private String campName;
    private String place;        // 선택
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String memo;         // 선택
}
