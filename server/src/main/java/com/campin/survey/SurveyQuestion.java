package com.campin.survey;

import java.util.List;

// 1분 진단 질문 하나. 화면도 이 모양 그대로 API 로 받아 그린다
// type: single(1개) · multi(여러 개, exclusive 는 혼자만 고를 수 있는 값) · rank(max 개까지, 고른 순서 = 중요한 순서)
public record SurveyQuestion(String key, String title, String type, int max, String exclusive, List<Option> options) {

    public record Option(String code, String label, String sub) {
    }

    boolean hasCode(String code) {
        return options.stream().anyMatch(o -> o.code().equals(code));
    }
}
