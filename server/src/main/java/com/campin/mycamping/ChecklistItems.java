package com.campin.mycamping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.campin.weather.DayForecast;

// 출발 준비 체크리스트의 항목 목록 (문구 모음). 네트워크·DB 를 쓰지 않는 순수 계산이라 따로 떼어 두었다
//
// ⚠ 안전 수치(몇 도 이하·몇 % 이상)는 출처가 있는 것만 둔다 (아래 "날씨 예보 → 항목" 참고). 새 수치를 넣으려면 출처부터 확인한다
//   - 특보 항목: 기상청이 발표한 특보 "이름"(한파·강풍·호우…)에 연결만 한다. 기준 수치는 기상청이 정해 발표한 그대로 따른다
//   - 침낭: 몇 도면 어떤 침낭이라고 정하지 않고 "내 침낭의 쾌적온도 확인"으로 안내한다 (침낭은 제품마다 표기 온도가 다름)
// 항목 문구는 초안이다 — 팀 검토 후 이 파일만 고치면 된다. id 는 체크 상태 저장 키라 바꾸지 않는다
final class ChecklistItems {

    private ChecklistItems() {
    }

    // 계절: 체크인 월 기준 (봄 3~5 · 여름 6~8 · 가을 9~11 · 겨울 12~2)
    static String seasonOf(int month) {
        if (month >= 3 && month <= 5) return "봄";
        if (month >= 6 && month <= 8) return "여름";
        if (month >= 9 && month <= 11) return "가을";
        return "겨울";
    }

    private record Entry(String id, String text, String why) {
    }

    // ---- 계절 준비물 ----
    private static final Map<String, List<Entry>> SEASON = Map.of(
            "봄", List.of(
                    new Entry("season-spring-jacket", "겉옷 (일교차)", "봄 · 아침저녁 쌀쌀해요"),
                    new Entry("season-spring-bug", "벌레 퇴치제", "봄"),
                    new Entry("season-spring-mask", "마스크 (황사 · 꽃가루)", "봄"),
                    new Entry("season-spring-fire", "화기 사용 가능 여부 확인", "산불 조심 시기")),
            "여름", List.of(
                    new Entry("season-summer-shade", "그늘막 · 타프", "여름"),
                    new Entry("season-summer-bug", "벌레 퇴치제", "여름"),
                    new Entry("season-summer-ice", "아이스박스 · 얼음", "여름"),
                    new Entry("season-summer-rain", "우비", "여름 · 소나기")),
            "가을", List.of(
                    new Entry("season-autumn-jacket", "겉옷 (일교차)", "가을 · 아침저녁 쌀쌀해요"),
                    new Entry("season-autumn-bag", "침낭", "내 침낭의 쾌적온도를 확인하세요"),
                    new Entry("season-autumn-fire", "화기 사용 가능 여부 확인", "낙엽 · 건조 시기")),
            "겨울", List.of(
                    new Entry("season-winter-bag", "침낭", "내 침낭의 쾌적온도를 확인하세요"),
                    new Entry("season-winter-warm", "핫팩 · 보온 매트", "겨울"),
                    new Entry("season-winter-co", "일산화탄소 경보기", "난방기구를 쓴다면 꼭 챙기세요"),
                    new Entry("season-winter-water", "물통 · 식수 동파 주의", "겨울")));

    // ---- 공통 준비물 (campOnly: 캠핑장 예약이 있을 때만 — 차박 자리에는 텐트·예약이 없다) ----
    private record Common(String id, String text, boolean campOnly) {
    }

    private static final List<Common> COMMON = List.of(
            new Common("common-tent", "텐트 · 팩 · 망치", true),
            new Common("common-light", "랜턴 · 보조배터리", false),
            new Common("common-firstaid", "상비약 · 구급 용품", false),
            new Common("common-trash", "쓰레기봉투", false),
            new Common("common-water", "물 · 취사 도구", false),
            new Common("common-booking", "예약 확인서 · 신분증", true));

    // ---- 차박 자리 전용 ----
    private static final List<Entry> SPOT = List.of(
            new Entry("spot-air", "창문 조금 열어 환기 · 일산화탄소 주의", "차박은 밀폐되기 쉬워요"),
            new Entry("spot-car", "차량 연료 · 배터리 확인", "차박"),
            new Entry("spot-park", "물이 고이지 않는 곳에 주차", "배수 · 낙석 확인"));

    // ---- 특보 → 항목 (특보 이름에 keyword 가 들어 있으면) ----
    // 같은 종류가 주의보·경보로 함께 와도, 여러 지역에서 와도 항목은 한 번만 (id 로 겹침 제거)
    private record Reaction(String key, String idPrefix, List<String> texts) {
    }

    private static final List<Reaction> REACTIONS = List.of(
            new Reaction("폭염", "heat", List.of("물 · 이온음료 넉넉히", "그늘막 · 타프", "한낮 야외 활동 줄이기")),
            new Reaction("열대야", "heat", List.of("물 · 이온음료 넉넉히", "그늘막 · 타프", "한낮 야외 활동 줄이기")),
            new Reaction("한파", "cold", List.of("핫팩 · 보온 매트", "일산화탄소 경보기", "물통 · 식수 동파 주의", "내 침낭의 쾌적온도 확인")),
            new Reaction("강풍", "wind", List.of("팩 추가 · 고정 줄 점검", "타프 철수 준비", "큰 나무 아래 피하기")),
            new Reaction("호우", "rain", List.of("일정 연기 검토", "하천 · 계곡 가까운 자리 피하기", "방수 장비 · 배수 대비")),
            new Reaction("대설", "snow", List.of("체인", "삽 · 제설 도구", "텐트 눈 치우기 준비")),
            new Reaction("건조", "dry", List.of("화기 사용 가능 여부 확인", "소화기 · 물 준비")),
            new Reaction("태풍", "typhoon", List.of("예약 변경 · 취소 검토")),
            new Reaction("황사", "dust", List.of("마스크", "야외 활동 줄이기")));
    // 풍랑 · 폭풍해일 · 안개 등은 캠핑 항목이 없다 (특보 이름은 화면 배너에 그대로 보임)

    // ---- 날씨 예보 → 항목 (묶음 "날씨") ----
    // 이 묶음의 수치는 출처가 있는 것만 쓴다 (출처는 Notion · docs/progress.md 에 정리)
    //  - -12℃: 기상청 한파주의보 발표 기준(아침 최저기온 -12℃ 이하). 실제 특보는 "2일 이상 지속" 등 조건이 더 있어서
    //    "특보가 발표된다"고 하지 않고 "발표 기준 온도에 해당하는 예보가 있다"까지만 말한다
    //  - 0℃: 물이 어는 온도 (물리적 기준, 별도 기관 기준은 아님)
    //  - 비·눈: 예보에 강수가 있는지만 본다 (몇 % 이상 같은 수치 기준 없음)
    // 일부러 쓰지 않는 것: 풍속(캠핑에 맞는 공식 기준 없음) · 폭염(기상청 기준이 체감온도라 단기예보 기온으로 판정 불가 → 특보에 맡김)
    //  · "10℃ 이하" 같은 우리만의 구간
    static final double COLD_WAVE_ADVISORY_C = -12.0;
    static final double FREEZING_C = 0.0;

    // 침낭 항목들 (계절·한파 특보). 예보 최저기온이 있으면 설명에 그 값을 보여 준다 — 판단(쾌적온도 비교)은 사용자 몫
    private static final List<String> SLEEPING_BAG_IDS = List.of("season-autumn-bag", "season-winter-bag", "warn-cold-4");

    static List<ChecklistItem> build(String season, boolean spot, List<String> warningCategories) {
        return build(season, spot, warningCategories, null);
    }

    // forecastDays: 캠핑 기간 날씨 예보(날짜 순). 예보를 못 받았거나 먼 일정이면 null
    // 순서: 특보 → 날씨 → 차박 → 계절 → 공통 (화면이 이 순서로 묶음을 보여 줌)
    static List<ChecklistItem> build(String season, boolean spot, List<String> warningCategories,
                                     List<DayForecast> forecastDays) {
        List<ChecklistItem> items = buildBase(season, spot, warningCategories);
        if (forecastDays == null) {
            return dedupe(items);
        }

        // 날씨 항목은 특보 묶음 바로 뒤에 끼운다
        List<ChecklistItem> weather = weatherItems(forecastDays);
        // 호우 특보가 이미 "방수 장비 · 배수 대비"를 안내하면 비 예보 항목은 같은 말이라 뺀다
        if (items.stream().anyMatch(i -> i.id().startsWith("warn-rain-"))) {
            weather = weather.stream().filter(w -> !w.id().startsWith("wx-rain-")).toList();
        }
        int at = (int) items.stream().filter(i -> "특보".equals(i.group())).count();
        items = new ArrayList<>(items);
        items.addAll(at, weather);
        items = dedupe(items);

        // 침낭 항목: 예보 최저기온을 설명에 보여 준다
        Double coldest = forecastDays.stream().map(DayForecast::minTemp).filter(t -> t != null)
                .min(Double::compare).orElse(null);
        if (coldest == null) {
            return items;
        }
        String why = "예보 최저 " + Math.round(coldest) + "°와 침낭 쾌적온도를 비교하세요";
        return items.stream()
                .map(i -> SLEEPING_BAG_IDS.contains(i.id())
                        ? new ChecklistItem(i.id(), i.group(), i.text(), why, i.level()) : i)
                .toList();
    }

    // 문구는 다르지만 같은 준비물인 것들 (같은 키로 본다). 문구가 완전히 같은 것은 따로 적지 않아도 된다
    private static final Map<String, String> SAME_THING = Map.of(
            "내 침낭의 쾌적온도 확인", "침낭",
            "마스크 (황사 · 꽃가루)", "마스크",
            "우비 · 방수 장비", "우비");

    // 같은 준비물은 한 번만. 묶음 순서(특보 → 날씨 → 차박 → 계절 → 공통)에서 앞에 있는 것을 남긴다
    // 예) 겨울에 한파주의보가 나면 특보의 "핫팩 · 보온 매트"만 남고 계절의 같은 항목은 빠진다
    private static List<ChecklistItem> dedupe(List<ChecklistItem> items) {
        Set<String> seen = new HashSet<>();
        List<ChecklistItem> out = new ArrayList<>();
        for (ChecklistItem i : items) {
            if (seen.add(SAME_THING.getOrDefault(i.text(), i.text()))) {
                out.add(i);
            }
        }
        return out;
    }

    private static List<ChecklistItem> weatherItems(List<DayForecast> days) {
        List<ChecklistItem> out = new ArrayList<>();

        DayForecast cold = days.stream().filter(d -> d.minTemp() != null && d.minTemp() <= COLD_WAVE_ADVISORY_C)
                .findFirst().orElse(null);
        if (cold != null) {
            String why = dayLabel(cold) + " 예보 최저 " + Math.round(cold.minTemp()) + "° · 한파주의보 발표 기준(-12° 이하)에 해당";
            out.add(ChecklistItem.of("wx-cold-1", "날씨", "핫팩 · 보온 매트", why));
            out.add(ChecklistItem.of("wx-cold-2", "날씨", "일산화탄소 경보기", why));
            out.add(ChecklistItem.of("wx-cold-3", "날씨", "내 침낭의 쾌적온도 확인",
                    dayLabel(cold) + " 예보 최저 " + Math.round(cold.minTemp()) + "°와 침낭 쾌적온도를 비교하세요"));
        }

        DayForecast freeze = days.stream().filter(d -> d.minTemp() != null && d.minTemp() <= FREEZING_C)
                .findFirst().orElse(null);
        if (freeze != null) {
            out.add(ChecklistItem.of("wx-freeze-1", "날씨", "물통 · 식수 동파 주의",
                    dayLabel(freeze) + " 예보 최저 " + Math.round(freeze.minTemp()) + "° · 0° 이하면 물이 얼어요"));
        }

        DayForecast wet = days.stream().filter(d -> d.precip() != null && !"없음".equals(d.precip())).findFirst().orElse(null);
        if (wet != null) {
            String why = dayLabel(wet) + " " + wet.precip() + " 예보";
            out.add(ChecklistItem.of("wx-rain-1", "날씨", "우비 · 방수 장비", why));
            out.add(ChecklistItem.of("wx-rain-2", "날씨", "배수 · 물 고임 확인", why));
        }
        return out;
    }

    // "11.19"
    private static String dayLabel(DayForecast d) {
        return d.date().getMonthValue() + "." + d.date().getDayOfMonth();
    }

    private static List<ChecklistItem> buildBase(String season, boolean spot, List<String> warningCategories) {
        List<ChecklistItem> items = new ArrayList<>();

        Map<String, ChecklistItem> warn = new LinkedHashMap<>();
        for (String category : warningCategories) {
            for (Reaction r : REACTIONS) {
                if (category.contains(r.key())) {
                    for (int i = 0; i < r.texts().size(); i++) {
                        String id = "warn-" + r.idPrefix() + "-" + (i + 1);
                        warn.putIfAbsent(id, ChecklistItem.of(id, "특보", r.texts().get(i), category));
                    }
                }
            }
        }
        items.addAll(warn.values());

        if (spot) {
            SPOT.forEach(e -> items.add(ChecklistItem.of(e.id(), "차박", e.text(), e.why())));
        }
        SEASON.get(season).forEach(e -> items.add(ChecklistItem.of(e.id(), "계절", e.text(), e.why())));
        COMMON.stream().filter(c -> !spot || !c.campOnly())
                .forEach(c -> items.add(ChecklistItem.of(c.id(), "공통", c.text(), null)));
        return items;
    }
}
