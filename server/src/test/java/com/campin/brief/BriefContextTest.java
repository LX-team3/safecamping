package com.campin.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.mycamping.CampPlan;
import com.campin.mycamping.ChecklistItem;
import com.campin.mycamping.ChecklistResponse;
import com.campin.weather.DayForecast;

// 7단계 상황 모으기: DB · 기상청 없이 규칙만 본다 (메모 · 진단 → 동행 · 숙박, 검색 질문, 검색 조건)
class BriefContextTest {

    private static BriefSurvey survey(String kid, String site, String... who) {
        return new BriefSurvey(Arrays.asList(who), kid, site, List.of());
    }

    // ---- 동행 ----

    @Test
    void 메모에_어린이가_있으면_아이_동반() {
        var c = BriefContext.companionsOf("성인 2명 · 어린이 1명 · 자동차", null);
        assertTrue(c.child());
        assertFalse(c.pet());
    }

    @Test
    void 메모가_이번_여행이라_진단보다_앞선다() {
        // 진단은 "아이와 감"이어도 이번 일정 메모가 어린이 0명 · 친구·연인이면 아이 조각을 쓰지 않는다
        var c = BriefContext.companionsOf("성인 2명 · 어린이 0명 · 친구·연인 · 자동차", survey("baby", "auto", "child"));
        assertFalse(c.child());
    }

    @Test
    void 메모에서_반려동물과_어르신을_찾는다() {
        var c = BriefContext.companionsOf("성인 3명 · 어린이 0명 · 어르신 동반·반려동물 · 자동차", null);
        assertFalse(c.child());
        assertTrue(c.pet());
        assertTrue(c.senior());
    }

    @Test
    void 메모에_동행_정보가_없으면_진단을_쓴다() {
        assertTrue(BriefContext.companionsOf("텐트 두 동", survey("kid", "auto", "child")).child());
        assertTrue(BriefContext.companionsOf(null, survey(null, "auto", "senior")).senior());
    }

    @Test
    void 진단의_아이가_13세_이상이면_아이_규칙을_쓰지_않는다() {
        assertFalse(BriefContext.companionsOf(null, survey("teen", "auto", "child")).child());
    }

    @Test
    void 메모도_진단도_없으면_모두_false() {
        assertEquals(new BriefContext.Companions(false, false, false), BriefContext.companionsOf("", null));
    }

    // ---- 숙박 ----

    @Test
    void 캠핑장이_아닌_자리는_차박() {
        assertEquals("car", BriefContext.sleepTypeOf(true, null));
        assertEquals("car", BriefContext.sleepTypeOf(true, survey(null, "glamping", "solo")));
    }

    @Test
    void 캠핑장이면_진단의_자는_곳을_쓴다() {
        assertEquals("tent", BriefContext.sleepTypeOf(false, survey(null, "auto", "solo")));
        assertEquals("car", BriefContext.sleepTypeOf(false, survey(null, "carcamp", "solo")));
        assertNull(BriefContext.sleepTypeOf(false, survey(null, "glamping", "solo")));
        assertNull(BriefContext.sleepTypeOf(false, null));
    }

    // ---- 진단 요약 ----

    @Test
    void 진단의_모르는_값은_버린다() {
        var s = new BriefSurvey(List.of("child", "alien"), "old", "boat", List.of("화재", "가스", "화재"));
        assertEquals(List.of("child"), s.who());
        assertNull(s.kid());
        assertNull(s.site());
        assertEquals(List.of("화재"), s.weak());
        assertEquals(List.of(), new BriefSurvey(null, null, null, null).who());
        assertEquals(List.of("solo"), new BriefSurvey(Arrays.asList("solo", null), null, null, Arrays.asList((String) null)).who());
    }

    // ---- 검색 질문 ----

    @Test
    void 질문에_동행_숙박_계절_특보_날씨_약한분야가_들어간다() {
        List<DayForecast> days = List.of(
                new DayForecast(LocalDate.of(2026, 12, 5), -3.0, 4.0, 60, 5.0, "흐림", "비/눈"),
                new DayForecast(LocalDate.of(2026, 12, 6), 1.0, 6.0, 10, 3.0, "맑음", "없음"));
        String q = BriefContext.questionOf(new BriefContext.Companions(true, false, false), "tent", "겨울",
                List.of(new ChecklistResponse.Warning("한파주의보", "강원도", "…")), days, List.of("일산화탄소"));
        assertEquals("아이 동반 텐트 캠핑. 겨울철. 한파주의보 발효 중, 영하로 내려가는 추위, 비 예보, 눈 예보. "
                + "조심할 것: 텐트 안 난방기구와 일산화탄소 중독. 안전 수칙", q);
    }

    @Test
    void 예보와_진단이_없으면_짧은_질문() {
        String q = BriefContext.questionOf(new BriefContext.Companions(false, false, false), null, "가을",
                List.of(), List.of(), List.of());
        assertEquals("캠핑. 가을철. 안전 수칙", q);
    }

    @Test
    void 질문에_수치를_넣지_않는다() {
        List<DayForecast> days = List.of(new DayForecast(LocalDate.of(2026, 8, 1), 22.5, 33.1, 80, 9.0, "흐림", "소나기"));
        String q = BriefContext.questionOf(new BriefContext.Companions(false, false, false), "car", "여름",
                List.of(), days, List.of());
        assertFalse(q.matches(".*\\d.*"), q);
        assertTrue(q.contains("소나기 예보"));
    }

    // ---- 한 묶음 ----

    @Test
    void 체크리스트_응답과_일정으로_상황과_검색조건을_만든다() {
        CampPlan plan = new CampPlan();
        plan.setId(7L);
        plan.setCampId(100568L);
        plan.setCampName("인북천 물빛테마공원 캠핑장");
        plan.setCheckIn(LocalDate.of(2026, 12, 5));
        plan.setCheckOut(LocalDate.of(2026, 12, 6));
        plan.setMemo("성인 2명 · 어린이 1명 · 아이 동반 · 자동차");

        var forecast = new ChecklistResponse.Forecast(true, List.of(
                new DayForecast(LocalDate.of(2026, 12, 5), -5.0, 2.0, 20, 4.0, "맑음", "없음")));
        var safety = new ChecklistResponse.Safety(71, "mid", 25, false);
        var items = List.of(new ChecklistItem("w-cold", "날씨", "침낭 쾌적온도 확인", null, "warn"));
        var checklist = new ChecklistResponse(null, "겨울", false, true, LocalDate.of(2026, 12, 2),
                true, List.of(), forecast, safety, items);

        BriefContext ctx = BriefContext.of(plan, checklist,
                new BriefSurvey(List.of("child"), "baby", "auto", List.of("일산화탄소", "화재")));

        assertEquals("인북천 물빛테마공원 캠핑장", ctx.placeName());
        assertFalse(ctx.spot());
        assertTrue(ctx.companions().child());
        assertEquals("tent", ctx.sleepType());
        assertEquals(List.of("일산화탄소", "화재"), ctx.weak());
        assertEquals(safety, ctx.safety());
        assertEquals(items, ctx.items());
        assertTrue(ctx.question().startsWith("아이 동반 텐트 캠핑. 겨울철. 영하로 내려가는 추위."));
        // 검색 조건: 아이 조각 남김, 텐트, 12월 → 여름 조각 뺌
        assertEquals(new SafetyDocFilter(List.of("all", "child"), List.of("all", "tent"), List.of("summer")), ctx.filter());
    }

    @Test
    void 먼_일정이면_예보가_없어도_만든다() {
        CampPlan plan = new CampPlan();
        plan.setCampId(null);  // 차박 자리
        plan.setCampName("475-3 잡종지");
        plan.setCheckIn(LocalDate.of(2027, 1, 20));
        plan.setCheckOut(LocalDate.of(2027, 1, 21));
        var checklist = new ChecklistResponse(null, "겨울", false, false, LocalDate.of(2027, 1, 17),
                false, List.of(), null, null, List.of());

        BriefContext ctx = BriefContext.of(plan, checklist, null);

        assertTrue(ctx.spot());
        assertEquals("car", ctx.sleepType());
        assertNull(ctx.forecast());
        assertEquals(List.of(), ctx.weak());
        assertEquals("차박. 겨울철. 안전 수칙", ctx.question());
    }
}
