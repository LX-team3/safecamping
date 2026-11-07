package com.campin.survey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// survey_step 테이블 한 줄 (화면 한 장)
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 조회 결과를 채울 때 빈 객체가 필요
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class SurveyStep {
    private Long id;
    private Long surveyId;
    private int step;
    private String title;
    private String sub;
    private String note;  // 화면 아래 안내 박스 (없으면 null)
}
