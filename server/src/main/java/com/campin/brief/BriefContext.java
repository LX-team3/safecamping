package com.campin.brief;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.campin.mycamping.CampPlan;
import com.campin.mycamping.ChecklistItem;
import com.campin.mycamping.ChecklistResponse;
import com.campin.weather.DayForecast;

// AI 브리핑에 쓸 "이 캠핑의 상황" 한 묶음 (7단계)
// 재료: 출발 준비 체크리스트 응답(일정 · 특보 · 예보 · 안전점수 · 준비 항목) + 일정 메모(동행) + 화면이 보낸 진단 요약
// 결과: 상황 값들 + 안전자료 검색 질문(question) + 검색 조건(filter)
//   question · filter → 6단계 검색 (EmbeddingClient → SafetyDocRepository.search)
//   나머지 값 → 8단계 프롬프트에 그대로 넣는다 (기상청 값 그대로. 여기서 수치를 만들거나 바꾸지 않는다)
//
//   companions: 이번 캠핑에 함께 가는 사람 (아이는 12세 이하일 때만 true)
//   sleepType:  tent · car · null(모름)
//   forecast:   출발 3일 전부터만 있음 (그 전이면 null). available=false 면 못 받은 것
//   weak:       진단의 조심할 분야 (진단이 없으면 빈 목록)
//   items:      서버가 만든 출발 준비 항목 (체크 상태는 브라우저에만 있어 여기엔 없음)
public record BriefContext(
        String placeName, LocalDate checkIn, LocalDate checkOut, boolean spot,
        String season, boolean near, LocalDate openDate,
        Companions companions, String sleepType,
        boolean warningsAvailable, List<ChecklistResponse.Warning> warnings,
        ChecklistResponse.Forecast forecast, ChecklistResponse.Safety safety,
        List<String> weak, List<ChecklistItem> items,
        String question, SafetyDocFilter filter) {

    public record Companions(boolean child, boolean pet, boolean senior) {
    }

    // 체크리스트 응답 + 일정 + 진단(없으면 null) → 상황
    static BriefContext of(CampPlan plan, ChecklistResponse c, BriefSurvey survey) {
        boolean spot = plan.getCampId() == null;
        Companions who = companionsOf(plan.getMemo(), survey);
        String sleepType = sleepTypeOf(spot, survey);
        List<String> weak = survey == null ? List.of() : survey.weak();
        List<DayForecast> days = c.forecast() != null && c.forecast().available() ? c.forecast().days() : List.of();

        String question = questionOf(who, sleepType, c.season(), c.warnings(), days, weak);
        SafetyDocFilter filter = SafetyDocFilter.of(who.child(), who.pet(), sleepType, plan.getCheckIn());

        return new BriefContext(plan.getCampName(), plan.getCheckIn(), plan.getCheckOut(), spot,
                c.season(), c.near(), c.openDate(), who, sleepType,
                c.warningsAvailable(), c.warnings(), c.forecast(), c.safety(),
                weak, c.items(), question, filter);
    }

    // ---- 동행 ----
    // 일정 메모 예: "성인 2명 · 어린이 0명 · 친구·연인 · 자동차" (client/src/utils/bookingMemo.js 의 makeBookingMemo)
    // 이번 여행의 정보라 진단(평소 답)보다 앞선다. 메모에 동행 정보가 없을 때(직접 쓴 메모 · 빈 메모)만 진단을 쓴다
    // 반려동물은 진단에 문항이 없어 메모로만 안다
    private static final Pattern KIDS = Pattern.compile("어린이 (\\d+)명");
    private static final List<String> WITH = List.of("혼자", "친구·연인", "아이 동반", "어르신 동반", "반려동물");

    static Companions companionsOf(String memo, BriefSurvey survey) {
        String t = memo == null ? "" : memo;
        Matcher m = KIDS.matcher(t);
        Integer kids = m.find() ? Integer.valueOf(m.group(1)) : null;
        boolean memoKnown = kids != null || WITH.stream().anyMatch(t::contains);

        if (memoKnown) {
            boolean child = t.contains("아이 동반") || (kids != null && kids > 0);
            return new Companions(child, t.contains("반려동물"), t.contains("어르신 동반"));
        }
        if (survey != null) {
            return new Companions(survey.youngChild(), false, survey.who().contains("senior"));
        }
        return new Companions(false, false, false);
    }

    // ---- 숙박 ----
    // 캠핑장이 아닌 자리(campId 없음)는 차박 판정 지도에서 고른 곳이라 차박으로 본다
    // 캠핑장이면 진단 답: 차박 → car, 오토캠핑 텐트 → tent, 글램핑 · 모름 → null (조각 꼬리표에 글램핑이 없어 거르지 않음)
    static String sleepTypeOf(boolean spot, BriefSurvey survey) {
        if (spot) return "car";
        String site = survey == null ? null : survey.site();
        if ("carcamp".equals(site)) return "car";
        if ("auto".equals(site)) return "tent";
        return null;
    }

    // ---- 검색 질문 ----
    // 안전자료 조각과 뜻으로 비교할 한 덩어리 글. 사람에게 보이지 않고 임베딩에만 쓴다
    // 수치는 넣지 않는다 — 조각은 수칙 글이라 "영하" 같은 말이 숫자보다 잘 맞고, 기상청 수치는 8단계에서 따로 넘긴다
    // 날씨 말은 체크리스트와 같은 근거만 쓴다: 최저 0° 이하(물 어는 온도) · 비 · 눈 · 소나기 예보. 풍속은 기준이 없어 안 씀
    private static final Map<String, String> AXIS_WORDS = Map.of(
            "일산화탄소", "텐트 안 난방기구와 일산화탄소 중독",
            "화재", "화로 불씨와 텐트 화재",
            "물·호우", "비가 와서 계곡 물이 불어날 때 대피");

    static String questionOf(Companions who, String sleepType, String season,
                             List<ChecklistResponse.Warning> warnings, List<DayForecast> days, List<String> weak) {
        List<String> parts = new ArrayList<>();

        List<String> with = new ArrayList<>();
        if (who.child()) with.add("아이");
        if (who.senior()) with.add("어르신");
        if (who.pet()) with.add("반려동물");
        String how = "car".equals(sleepType) ? "차박" : "tent".equals(sleepType) ? "텐트 캠핑" : "캠핑";
        // "와/과"는 앞 글자 받침에 따라 달라져서 "동반"으로 쓴다
        parts.add(with.isEmpty() ? how : String.join("·", with) + " 동반 " + how);

        if (season != null) parts.add(season + "철");

        List<String> wx = new ArrayList<>();
        warnings.stream().map(ChecklistResponse.Warning::category).distinct().forEach(cat -> wx.add(cat + " 발효 중"));
        if (days.stream().anyMatch(d -> d.minTemp() != null && d.minTemp() <= 0)) wx.add("영하로 내려가는 추위");
        for (String p : List.of("비", "눈", "소나기")) {
            // precip 값: 없음 · 비 · 비/눈 · 눈 · 소나기 → "비/눈" 은 비 · 눈 둘 다로 센다
            if (days.stream().anyMatch(d -> d.precip() != null && List.of(d.precip().split("/")).contains(p))) {
                wx.add(p + " 예보");
            }
        }
        if (!wx.isEmpty()) parts.add(String.join(", ", wx));

        if (!weak.isEmpty()) {
            parts.add("조심할 것: " + String.join(", ", weak.stream().map(AXIS_WORDS::get).toList()));
        }
        return String.join(". ", parts) + ". 안전 수칙";
    }
}
