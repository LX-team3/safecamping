package com.campin.survey;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

// POST /api/surveys/{code}/answers 요청
// answers: { "질문 code": ["선택지 code", ...] } — 배열 순서가 고른 순서(순위 질문은 곧 1·2·3순위)
// 예: { "answers": { "camp_type": ["auto"], "nearby": ["er", "fire", "police"] } }
// 개수·단독 선택 같은 규칙은 질문마다 달라서 SurveyService 에서 검사한다
public record SurveyAnswerRequest(
        @NotNull(message = "답변이 없습니다.")
        Map<String, List<String>> answers
) {
}
