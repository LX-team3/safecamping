package com.campin.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.mycamping.ChecklistResponse;
import com.campin.weather.DayForecast;

// 8단계 프롬프트: 상황 · 자료가 정해진 모양으로 들어가는지, 값이 없을 때 규칙의 말("받지 못함" · "아직 없음")을 쓰는지
class BriefPromptTest {

    private static final LocalDate IN = LocalDate.of(2026, 12, 5);

    private static BriefContext ctx(boolean near, boolean warningsAvailable, List<ChecklistResponse.Warning> warnings,
                                    ChecklistResponse.Forecast forecast, ChecklistResponse.Safety safety,
                                    BriefContext.Companions who, String sleepType, boolean spot, List<String> weak) {
        return new BriefContext("인북천 물빛테마공원 캠핑장", IN, IN.plusDays(1), spot, "겨울", near, IN.minusDays(3),
                who, sleepType, warningsAvailable, warnings, forecast, safety, weak, List.of(), "질문",
                SafetyDocFilter.of(who.child(), who.pet(), sleepType, IN));
    }

    private static final BriefContext.Companions CHILD = new BriefContext.Companions(true, false, false);
    private static final BriefContext.Companions NOBODY = new BriefContext.Companions(false, false, false);

    @Test
    void 상황에_값이_다_있을_때() {
        var forecast = new ChecklistResponse.Forecast(true, List.of(
                new DayForecast(IN, -5.0, 2.5, 60, 9.0, "흐림", "눈"),
                DayForecast.none(IN.plusDays(1))));
        var c = ctx(true, true, List.of(new ChecklistResponse.Warning("한파주의보", "강원도", "…")), forecast,
                new ChecklistResponse.Safety(71, "mid", 25, false), CHILD, "tent", false, List.of("일산화탄소", "화재"));

        assertEquals("""
                <situation>
                장소: 인북천 물빛테마공원 캠핑장 (캠핑장)
                일정: 2026-12-05 ~ 2026-12-06 (겨울)
                동행: 아이(12세 이하)
                숙박: 텐트
                특보: 한파주의보(강원도)
                예보(기상청): 12월 5일 최저 -5° 최고 2.5° 강수확률 60% 흐림 눈 / 12월 6일 예보 없음
                진단의 조심할 분야: 일산화탄소, 화재
                안전점수 71 · 응급실까지 차로 25분
                </situation>
                """, BriefPrompt.situationOf(c));
    }

    @Test
    void 먼_일정이면_아직_없음과_확인_날짜() {
        var c = ctx(false, false, List.of(), null, null, NOBODY, null, false, List.of());
        String s = BriefPrompt.situationOf(c);
        assertTrue(s.contains("특보: 아직 없음(12월 2일부터 확인)"), s);
        assertTrue(s.contains("예보(기상청): 아직 없음(12월 2일부터 확인)"), s);
        assertTrue(s.contains("동행: 해당 없음 또는 모름"), s);
        assertTrue(s.contains("숙박: 모름"), s);
        assertTrue(s.contains("진단의 조심할 분야: 진단 안 함"), s);
        assertFalse(s.contains("안전점수"), s);  // 안전 정보가 없으면 줄을 빼고 지어내지 않는다
    }

    @Test
    void 기상청을_못_받았으면_받지_못함() {
        var c = ctx(true, false, List.of(), new ChecklistResponse.Forecast(false, List.of()), null, NOBODY, null, false, List.of());
        String s = BriefPrompt.situationOf(c);
        assertTrue(s.contains("특보: 받지 못함"), s);
        assertTrue(s.contains("예보(기상청): 받지 못함"), s);
    }

    @Test
    void 특보가_없으면_없다고_쓴다() {
        var c = ctx(true, true, List.of(), new ChecklistResponse.Forecast(false, List.of()), null, NOBODY, null, false, List.of());
        assertTrue(BriefPrompt.situationOf(c).contains("특보: 발효 중인 특보 없음"));
    }

    @Test
    void 차박_자리는_응급실_시간이_어림임을_적는다() {
        var c = ctx(true, true, List.of(), null, new ChecklistResponse.Safety(55, "caution", 40, true),
                new BriefContext.Companions(false, true, true), "car", true, List.of());
        String s = BriefPrompt.situationOf(c);
        assertTrue(s.contains("장소: 인북천 물빛테마공원 캠핑장 (차박 자리)"), s);
        assertTrue(s.contains("동행: 어르신 · 반려동물"), s);
        assertTrue(s.contains("숙박: 차박"), s);
        assertTrue(s.contains("응급실까지 차로 40분 (직선거리 어림)"), s);
    }

    @Test
    void 자료는_id_기관_연도_제목_본문으로() {
        var docs = List.of(
                new SafetyDocRepository.Chunk("JD-031", "교육지도서", "행정안전부", 2016, "12", "겨울철 안전 ②", " 난로는 환기와 함께. \n", "rule", 0.37),
                new SafetyDocRepository.Chunk("XX-001", "자료", "기관", null, null, "제목", "본문", "rule", 0.5));
        assertEquals("""
                <documents>
                <doc id="JD-031" org="행정안전부" year="2016">
                제목: 겨울철 안전 ②
                난로는 환기와 함께.
                </doc>
                <doc id="XX-001" org="기관">
                제목: 제목
                본문
                </doc>
                </documents>
                """, BriefPrompt.documentsOf(docs));
    }

    @Test
    void 숫자는_반올림하지_않고_끝의_0만_뺀다() {
        assertEquals("-5", BriefPrompt.num(-5.0));
        assertEquals("2.5", BriefPrompt.num(2.5));
        assertEquals("0", BriefPrompt.num(0.0));
        assertEquals("-12.3", BriefPrompt.num(-12.3));
    }

    @Test
    void 전체는_system_규칙과_user_상황_자료_요청() {
        var c = ctx(true, true, List.of(), null, null, NOBODY, null, false, List.of());
        BriefPrompt p = BriefPrompt.of(c, List.of());
        assertTrue(p.system().startsWith("너는 캠핑 안전 서비스 CAMPIN의 안전 안내자다."));
        assertTrue(p.system().contains("<documents>에 있는 id만 쓴다"));
        assertTrue(p.user().startsWith("<situation>"));
        assertTrue(p.user().contains("<documents>\n</documents>"));
        assertTrue(p.user().endsWith("위 상황에 맞는 대비 행동을 써 주세요."));
    }
}
