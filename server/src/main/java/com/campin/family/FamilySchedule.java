package com.campin.family;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// family_schedule 테이블 한 줄 (가족 일정)
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class FamilySchedule {
    private Long id;
    private Long familyRoomId;
    private String title;
    private OffsetDateTime startAt;
    private String memo;
    private Long createdBy;
    private String createdByName;    // 조회할 때 member 와 이어서 채운다 (테이블 컬럼 아님)
}
