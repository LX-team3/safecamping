package com.campin.family;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// family_location 테이블 한 줄 + 회원 이름 (가족방 위치 탭에 찍을 사람 하나)
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class FamilyLocation {
    private Long memberId;
    private String name;             // 조회할 때 member 와 이어서 채운다 (테이블 컬럼 아님)
    private Double lon;              // 경도
    private Double lat;              // 위도
    private OffsetDateTime updatedAt;
    private Integer seq;             // 방에 들어온 순번(1부터). 조회할 때 계산한다 (테이블 컬럼 아님)
}
