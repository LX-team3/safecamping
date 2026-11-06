package com.campin.survey;

import java.util.List;

// 1분 진단 답 (요청·응답 공통). 값은 SurveyQuestions 의 code. 규칙 검사는 SurveyService 에서
public record SurveyAnswers(
        String campType,
        String transport,
        List<String> heating,
        List<String> safetyItems,
        List<String> facilities   // 고른 순서 = 중요한 순서 (1순위가 맨 앞)
) {
}
