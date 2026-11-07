package com.campin.survey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// survey_option 테이블 한 줄 (선택지)
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 조회 결과를 채울 때 빈 객체가 필요
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class SurveyOption {
    private Long id;
    private Long questionId;
    private String code;
    private int sortOrder;
    private String label;
    private String description;
    private boolean exclusive;   // 고르면 같은 질문의 다른 선택을 푼다
    private String warningText;  // 고르면 띄울 위험 안내 (없으면 null)
}
