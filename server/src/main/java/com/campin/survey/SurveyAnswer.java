package com.campin.survey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// survey_answer 에 넣을 값. INSERT 후 DB 가 만든 id 가 채워진다
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 조회 결과를 채울 때 빈 객체가 필요
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class SurveyAnswer {
    private Long id;
    private Long surveyId;
    private String loginId;  // 이 아이디의 member.id 를 SQL 안에서 찾아 넣는다
}
