package com.campin.saved;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// saved_list 테이블 한 줄 (+ 담은 캠핑장 수). MyBatis 가 조회 결과를 이 객체에 채운다
@Getter
@Setter
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class SavedList {
    private Long id;
    private Long memberId;
    private String name;
    private Boolean isDefault;       // '관심 캠핑장' 기본 목록이면 true
    private Integer campCount;       // 조회할 때 saved_camp 를 세어 채운다 (테이블 컬럼 아님)
    private OffsetDateTime createdAt;
}
