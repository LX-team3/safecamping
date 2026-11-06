package com.campin.member;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 비상연락처 한 명 (emergency_contact 한 줄 · 요청 · 응답 공통). 이름은 회원가입 때 번호만 넣었으면 null
@Getter
@Setter
@NoArgsConstructor   // MyBatis·Jackson 이 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // Lombok: 모든 필드를 받는 생성자 (서비스·테스트에서 바로 만들 때)
public class EmergencyContact {
    private String name;
    private String phone;  // 숫자만
}
