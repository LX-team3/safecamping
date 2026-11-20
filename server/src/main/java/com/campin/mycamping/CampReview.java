package com.campin.mycamping;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// camp_review 테이블 한 줄 (지난 캠핑 후기). 별점은 없다
@Getter              // Lombok: 모든 필드의 getter 를 자동으로 만든다
@Setter              // Lombok: 모든 필드의 setter 를 자동으로 만든다 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class CampReview {
    private Long id;
    private Long planId;
    private Long memberId;
    private String content;          // 사진만 올렸으면 null
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
