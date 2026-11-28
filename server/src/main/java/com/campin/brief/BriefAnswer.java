package com.campin.brief;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Claude 가 돌려준 대비 행동 (BriefPrompt.SYSTEM 의 형식 {"lines":[{"text","sources"}]})
// 이 값은 아직 검사 전이다 → 10단계에서 sources · 숫자 · 길이를 검사한 뒤에만 화면에 보낸다
@JsonIgnoreProperties(ignoreUnknown = true)  // Claude 가 칸을 더 붙여도 오류 없이 무시
public record BriefAnswer(List<Line> lines) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String text, List<String> sources) {
    }
}
