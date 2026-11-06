package com.campin.member;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// member 테이블 한 줄. MyBatis 가 조회 결과를 이 객체에 채운다
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class Member {
    private Long id;
    private String loginId;
    private String passwordHash;     // BCrypt 로 암호화한 값. 원래 비밀번호는 어디에도 저장하지 않는다
    private String name;
    private String phone;            // 숫자만 (예: 01012345678)
    // 비상연락처는 emergency_contact 테이블 (EmergencyContactMapper)
    private String address;
    private String addressDetail;
    private OffsetDateTime createdAt;
}
