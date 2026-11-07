package com.campin.survey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// survey_question 테이블 한 줄
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 조회 결과를 채울 때 빈 객체가 필요
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class SurveyQuestion {
    private Long id;
    private Long stepId;
    private String code;
    private int sortOrder;
    private String title;
    private String inputType;  // card, chip, rank
    private int maxSelect;     // 1: 하나만, n: n개까지, 0: 제한 없음
    private boolean required;
    private String note;
}
