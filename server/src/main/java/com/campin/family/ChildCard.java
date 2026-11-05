package com.campin.family;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// child_card 테이블 한 줄 (아이 건강 응급 카드)
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class ChildCard {
    private Long id;
    private Long familyRoomId;
    private String name;
    private LocalDate birthDate;     // 선택
    private String bloodType;        // 선택 (예: A+, 모름)
    private String allergy;
    private String disease;          // 지병
    private String medicine;         // 먹고 있는 약
    private String memo;
    private Long updatedBy;
    private String updatedByName;    // 조회할 때 member 와 이어서 채운다 (테이블 컬럼 아님)
    private OffsetDateTime updatedAt;
}
