package com.campin.family;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 가족방 다른 구성원이 내 캠핑에 등록한 캠핑 한 줄 (camp_plan + 등록한 사람 이름)
// camp_plan 테이블은 mycamping 패키지 것이지만, "같은 가족방 사람" 조건이 가족방 규칙이라 여기서 읽는다
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class FamilyCampPlan {
    private Long id;
    private Long ownerId;        // 등록한 회원 id
    private String ownerName;    // 등록한 회원 이름 (카드에 "엄마님이 등록")
    private Long campId;         // 캠핑장이 아닌 자리(차박)면 null (그때는 lat·lng 가 있다)
    private String campName;
    private String place;
    private Double lat;
    private Double lng;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String memo;         // 예약 정보 확인에서 적은 인원·동행·수단 (가족끼리는 같이 본다)
}
