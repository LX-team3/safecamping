package com.campin.survey;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// survey_answer 테이블 한 줄. 여러 개 고르는 답은 쉼표로 이은 글자 그대로 (SurveyService 가 목록으로 바꾼다)
@Getter
@Setter
@NoArgsConstructor  // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
public class SurveyAnswerRow {
    private Long memberId;
    private String campType;
    private String transport;
    private String heating;
    private String safetyItems;
    private String facilities;
}
