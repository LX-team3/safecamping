package com.campin.survey;

import java.util.List;

// GET /api/surveys/{code} 응답. 화면이 그대로 그릴 수 있게 단계 → 질문 → 선택지로 묶어서 준다
// DB id 는 내보내지 않고 code 로만 주고받는다 (DB 를 다시 만들어도 화면 코드가 안 바뀜)
public record SurveyResponse(
        String code,
        String title,
        String durationText,
        String finishLabel,
        boolean numbered,
        List<Step> steps) {

    public record Step(int step, String title, String sub, String note, List<Question> questions) {
    }

    public record Question(String code, String title, String inputType, int maxSelect, boolean required,
                           String note, List<Option> options) {
    }

    public record Option(String code, String label, String description, boolean exclusive, String warning) {
        static Option from(SurveyOption o) {
            return new Option(o.getCode(), o.getLabel(), o.getDescription(), o.isExclusive(), o.getWarningText());
        }
    }
}
