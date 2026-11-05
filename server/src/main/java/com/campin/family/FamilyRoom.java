package com.campin.family;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// family_room 테이블 한 줄. MyBatis 가 조회 결과를 이 객체에 채운다
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class FamilyRoom {
    private Long id;
    private String name;
    private String inviteCode;   // 가족에게 알려 줄 입장 코드 (영문 대문자·숫자 6자)
    private OffsetDateTime createdAt;
}
