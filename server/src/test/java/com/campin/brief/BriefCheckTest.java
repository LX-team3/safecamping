package com.campin.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

// 10단계 검사: 규칙을 어긴 줄은 버리고, 통과한 줄에 근거 출처를 붙인다
class BriefCheckTest {

    private static final String SITUATION = """
            <situation>
            일정: 2026-12-05 ~ 2026-12-06 (겨울)
            예보(기상청): 12월 5일 최저 -5° 최고 2° 강수확률 60% 흐림 눈
            안전점수 71 · 응급실까지 차로 25분
            </situation>
            """;
    private static final List<SafetyDocRepository.Chunk> DOCS = List.of(
            new SafetyDocRepository.Chunk("JD-031", "교육지도서", "행정안전부", 2016, "12", "겨울철 안전 ②",
                    "얼음 20cm 정도 두께가 되어야 안전합니다. 텐트 위에 쌓인 눈을 수시로 치워주세요.", "rule", 0.37),
            new SafetyDocRepository.Chunk("NB-002", "보도자료", "소방청", 2024, null, "텐트 안 화로 실험",
                    "1,600ppm에서는 2시간 후 사망에 이를 수 있다.", "stat", 0.46));

    private static BriefCheck.Result check(BriefAnswer.Line... lines) {
        return BriefCheck.check(new BriefAnswer(List.of(lines)), SITUATION, DOCS);
    }

    private static BriefAnswer.Line line(String text, String... sources) {
        return new BriefAnswer.Line(text, List.of(sources));
    }

    @Test
    void 통과한_줄에_근거_출처를_붙인다() {
        var r = check(line("텐트 위에 쌓인 눈을 수시로 치워요", "JD-031"));
        assertEquals(1, r.lines().size());
        assertEquals(List.of(), r.dropped());
        var s = r.lines().get(0).sources().get(0);
        assertEquals(new BriefResult.Source("JD-031", "행정안전부", 2016, "겨울철 안전 ②", "교육지도서", "12"), s);
    }

    @Test
    void 근거가_없거나_넘기지_않은_id_거나_3개_이상이면_버린다() {
        var r = check(
                new BriefAnswer.Line("난로를 꺼요", null),
                line("난로를 꺼요", "XX-999"),
                line("난로를 꺼요", "JD-031", "NB-002", "JD-031"));
        assertEquals(0, r.lines().size());
        assertEquals(3, r.dropped().size());
        assertTrue(r.dropped().get(1).startsWith("넘기지 않은 근거"), r.dropped().get(1));
    }

    @Test
    void 상황_자료에_있는_숫자는_통과_없는_숫자는_버린다() {
        var r = check(
                line("응급실까지 차로 25분이에요", "JD-031"),          // 상황에 있음
                line("1600ppm에서는 2시간 뒤 위험해요", "NB-002"),       // 자료에 1,600 · 2 있음 (쉼표 달라도 같음)
                line("응급실까지 차로 15분이에요", "JD-031"));          // 어디에도 없음
        assertEquals(2, r.lines().size());
        assertEquals(1, r.dropped().size());
        assertTrue(r.dropped().get(0).startsWith("상황 · 자료에 없는 숫자 15"), r.dropped().get(0));
    }

    @Test
    void 자료_id_의_숫자로는_통과하지_않는다() {
        // JD-031 의 "31" 을 쓸 수 있는 숫자로 치지 않는다
        var r = check(line("31분 안에 대피해요", "JD-031"));
        assertEquals(0, r.lines().size());
    }

    @Test
    void 보증하는_말은_버리고_행동을_꾸미는_말은_둔다() {
        var r = check(
                line("얼음이 20cm 정도면 안전해요", "JD-031"),
                line("눈을 치워 안전하게 지내요", "JD-031"),
                line("이 정도는 괜찮아요", "JD-031"));
        assertEquals(List.of("눈을 치워 안전하게 지내요"), r.lines().stream().map(BriefResult.Line::text).toList());
        assertEquals(2, r.dropped().size());
    }

    @Test
    void 빈_글_60자_초과_같은_글_4번째_줄은_버린다() {
        String longText = "가".repeat(61);
        var r = check(
                line("  ", "JD-031"),
                line(longText, "JD-031"),
                line("눈을 치워요", "JD-031"),
                line("눈을 치워요", "JD-031"),
                line("난로는 꺼요", "JD-031"),
                line("화로를 꺼요", "NB-002"),
                line("네 번째 줄이에요", "JD-031"));
        assertEquals(List.of("눈을 치워요", "난로는 꺼요", "화로를 꺼요"),
                r.lines().stream().map(BriefResult.Line::text).toList());
        assertEquals(4, r.dropped().size());
        assertTrue(r.dropped().get(3).startsWith("4번째 줄부터"));
    }

    @Test
    void 숫자_모양을_맞춘다() {
        assertEquals(Set.of("1600", "5", "2.5", "20"), BriefCheck.numbersIn("1,600ppm · 05일 · 2.50도 · -20°"));
        assertEquals(Set.of(), BriefCheck.numbersIn(null));
    }

    // ---- BriefService: 앞 단계가 안 되면 예외 없이 대체 브리핑 ----

    @Test
    void 벡터DB나_OpenAI_키가_없으면_대체_브리핑() {
        var service = new BriefService(null, new EmbeddingClient(""), new SafetyDocRepository("", "", ""),
                new BriefAiClient(prompt -> { throw new IllegalStateException("부르면 안 됨"); }));
        var ctx = new BriefContext("캠핑장", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6), false, "겨울", true,
                LocalDate.of(2026, 12, 2), new BriefContext.Companions(false, false, false), null, true, List.of(),
                null, null, List.of(), List.of(), "질문", SafetyDocFilter.of(false, false, null, null));
        assertEquals(BriefResult.fallback("search-off"), service.make(ctx));
    }

    @Test
    void Claude_호출이_실패하면_빈_결과() {
        var client = new BriefAiClient(prompt -> { throw new RuntimeException("401 invalid x-api-key"); });
        assertTrue(client.ask(new BriefPrompt("s", "u")).isEmpty());
    }
}
