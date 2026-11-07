package com.campin.survey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 저장된 답변에서 고른 선택지 한 개 (내 답변 불러오기용)
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 조회 결과를 채울 때 빈 객체가 필요
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class SurveyAnswerItem {
    private String questionCode;
    private String optionCode;
    private int rank;  // 고른 순서
}
