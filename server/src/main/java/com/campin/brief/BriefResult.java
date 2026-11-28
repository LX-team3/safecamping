package com.campin.brief;

import java.util.List;

// AI 브리핑 결과 (10단계 검사를 통과한 것만)
//   ai = true  → lines 에 AI 대비 행동(최대 3줄)과 근거 출처
//   ai = false → lines 는 비어 있고, 화면이 지금의 문장 틀 브리핑(client campBrief.js)을 그대로 보여 준다
//               fallback = 왜 대체했는지 (로그 · 평가용, 화면에 그대로 보여 주지 않는다)
//     search-off   벡터 DB 주소나 OpenAI 키가 없음
//     search-failed 임베딩 · 벡터 DB 호출 실패 (터널 꺼짐 등)
//     no-docs      조건에 맞는 자료가 없음
//     ai-failed    Claude 호출 실패 · JSON 이 아님
//     all-dropped  답은 왔지만 검사를 통과한 줄이 없음 (0줄 답 포함)
public record BriefResult(boolean ai, List<Line> lines, String fallback) {

    static BriefResult fallback(String reason) {
        return new BriefResult(false, List.of(), reason);
    }

    // 대비 행동 한 줄 + 근거 (화면에 "행정안전부 2016 · 겨울철 안전 ②" 처럼 보여 준다)
    public record Line(String text, List<Source> sources) {
    }

    public record Source(String id, String org, Integer year, String heading, String source, String page) {
    }
}
