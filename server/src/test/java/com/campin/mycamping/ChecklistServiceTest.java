package com.campin.mycamping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;
import com.campin.weather.DayForecast;

// DB·기상청 없이 출발 준비 체크리스트 규칙만 확인한다 (가짜 일정 · 가짜 특보)
class ChecklistServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private final List<CampPlan> plans = new ArrayList<>();
    private Optional<List<ChecklistResponse.Warning>> warnings = Optional.of(List.of());
    private final AtomicInteger warningCalls = new AtomicInteger();
    private Optional<List<DayForecast>> forecast = Optional.of(List.of());
    private final AtomicInteger forecastCalls = new AtomicInteger();
    private ChecklistService service;

    @BeforeEach
    void setUp() {
        MemberMapper members = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) {
                Member m = new Member();
                if ("camper01".equals(loginId)) m.setId(1L);
                else if ("camper02".equals(loginId)) m.setId(2L);
                else return null;
                m.setLoginId(loginId);
                return m;
            }
            @Override public boolean existsByLoginId(String loginId) { return false; }
            @Override public void insert(Member m) { }
        };
        CampPlanMapper planMapper = new CampPlanMapper() {
            @Override public List<CampPlan> findByMemberId(Long memberId) { return List.of(); }
            @Override public CampPlan findById(Long memberId, Long planId) {
                return plans.stream().filter(p -> p.getId().equals(planId) && p.getMemberId().equals(memberId))
                        .findFirst().orElse(null);
            }
            @Override public boolean exists(Long memberId, Long campId, LocalDate checkIn) { return false; }
            @Override public boolean existsSpot(Long memberId, double lat, double lng, LocalDate checkIn) { return false; }
            @Override public int insert(CampPlan plan) { return 1; }
            @Override public int delete(Long memberId, Long planId) { return 0; }
        };
        ChecklistSource source = new ChecklistSource() {
            @Override public Optional<Target> ofCamp(Long campId) {
                if (campId == 999L) return Optional.empty(); // 지워진 캠핑장
                return Optional.of(new Target(38.19, 128.32, new ChecklistResponse.Safety(55, "caution", 26, false)));
            }
            @Override public Target ofSpot(double lat, double lng) {
                return new Target(lat, lng, new ChecklistResponse.Safety(100, "safe", 3, true));
            }
            @Override public Optional<List<ChecklistResponse.Warning>> warnings(double lat, double lng) {
                warningCalls.incrementAndGet();
                return warnings;
            }
            @Override public Optional<List<DayForecast>> forecast(double lat, double lng) {
                forecastCalls.incrementAndGet();
                return forecast;
            }
        };
        service = new ChecklistService(members, planMapper, source);
    }

    // 오늘(11/18)에서 며칠 뒤 체크인, 1박. campId 가 null 이면 차박 자리
    private CampPlan plan(long id, Long campId, int daysLater) {
        CampPlan p = new CampPlan();
        p.setId(id);
        p.setMemberId(1L);
        p.setCampId(campId);
        p.setCampName(campId == null ? "475-3 잡종지" : "백담계곡오토캠핑장");
        if (campId == null) {
            p.setLat(38.057);
            p.setLng(128.166);
        }
        p.setCheckIn(TODAY.plusDays(daysLater));
        p.setCheckOut(TODAY.plusDays(daysLater + 1));
        plans.add(p);
        return p;
    }

    private static ChecklistResponse.Warning warning(String category) {
        return new ChecklistResponse.Warning(category, "강원도 일부", category + "가 발효 중입니다.");
    }

    @Test
    void 출발_3일_전부터_특보를_확인한다() {
        plan(1, 10L, 3);   // 체크인 3일 뒤 = 오늘이 3일 전
        plan(2, 10L, 4);   // 4일 뒤 = 아직 멀다

        ChecklistResponse near = service.find("camper01", 1L, TODAY);
        assertTrue(near.near());
        assertTrue(near.warningsAvailable());

        ChecklistResponse far = service.find("camper01", 2L, TODAY);
        assertFalse(far.near());
        assertFalse(far.warningsAvailable());
        assertEquals(TODAY.plusDays(1), far.openDate());   // 체크인 4일 뒤 → 3일 전 = 내일부터 보임
    }

    @Test
    void 먼_일정은_특보를_부르지_않고_안전_정보와_준비물만_준다() {
        plan(1, 10L, 20);

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertEquals(0, warningCalls.get());           // 기상청 호출 없음
        assertTrue(r.warnings().isEmpty());
        assertEquals(55, r.safety().score());          // 안전 정보는 항상
        assertEquals(26, r.safety().erDriveMin());
        assertFalse(r.safety().erApprox());
        assertEquals("겨울", r.season());              // 12월 8일 체크인
        assertTrue(r.items().stream().allMatch(i -> !i.group().equals("특보")));
    }

    @Test
    void 캠핑_중이면_가까운_일정으로_본다_끝난_캠핑은_지난_일정() {
        plan(1, 10L, 0);    // 오늘 체크인
        CampPlan ongoing = plan(2, 10L, -1);  // 어제 체크인, 오늘 체크아웃
        CampPlan past = plan(3, 10L, -5);     // 이미 끝남

        assertTrue(service.find("camper01", 1L, TODAY).near());
        assertTrue(service.find("camper01", ongoing.getId(), TODAY).near());
        ChecklistResponse pastRes = service.find("camper01", past.getId(), TODAY);
        assertTrue(pastRes.past());
        assertFalse(pastRes.near());
    }

    @Test
    void 특보가_있으면_이름에_맞는_항목이_맨_앞에_붙고_겹치지_않는다() {
        plan(1, 10L, 2);
        warnings = Optional.of(List.of(warning("한파주의보"), warning("한파경보"), warning("강풍주의보")));

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertEquals(List.of("한파주의보", "한파경보", "강풍주의보"),
                r.warnings().stream().map(ChecklistResponse.Warning::category).toList());
        List<ChecklistItem> warn = r.items().stream().filter(i -> i.group().equals("특보")).toList();
        assertEquals("특보", r.items().get(0).group());                 // 특보 항목이 맨 앞
        assertEquals(warn.size(), warn.stream().map(ChecklistItem::id).distinct().count());  // 한파주의보·경보 겹침 없음
        assertTrue(warn.stream().anyMatch(i -> i.text().equals("팩 추가 · 고정 줄 점검")));   // 강풍 항목
        assertTrue(warn.stream().allMatch(i -> i.level().equals("warn")));
        assertEquals("한파주의보", warn.get(0).why());                  // 처음 걸린 특보 이름
    }

    @Test
    void 특보를_못_받으면_비워_두고_받았다고_하지_않는다() {
        plan(1, 10L, 1);
        warnings = Optional.empty();   // 기상청 키 없음·호출 실패·예시(더미)뿐

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertTrue(r.near());
        assertFalse(r.warningsAvailable());
        assertTrue(r.warnings().isEmpty());
        assertTrue(r.items().stream().noneMatch(i -> i.group().equals("특보")));
    }

    @Test
    void 차박_자리는_차박_항목이_붙고_텐트_예약_항목은_빠진다() {
        plan(1, null, 5);

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertNull(r.plan().campId());
        assertTrue(r.safety().erApprox());               // 응급실 시간은 직선거리 어림
        assertTrue(r.items().stream().anyMatch(i -> i.group().equals("차박")));
        assertTrue(r.items().stream().noneMatch(i -> i.id().equals("common-tent") || i.id().equals("common-booking")));
        assertTrue(r.items().stream().anyMatch(i -> i.id().equals("common-light")));  // 나머지 공통은 그대로
    }

    @Test
    void 캠핑장이_지워졌으면_안전_정보와_특보_없이_준비물만() {
        plan(1, 999L, 1);

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertNull(r.safety());
        assertFalse(r.warningsAvailable());
        assertFalse(r.items().isEmpty());
        assertEquals(0, warningCalls.get());
    }

    @Test
    void 남의_일정이거나_없는_일정은_404() {
        plan(1, 10L, 5);

        assertThrows(NotFoundException.class, () -> service.find("camper02", 1L, TODAY));
        assertThrows(NotFoundException.class, () -> service.find("camper01", 77L, TODAY));
    }

    private static DayForecast day(LocalDate date, double min, double max) {
        return new DayForecast(date, min, max, 30, 7.0, "구름많음", "없음");
    }

    @Test
    void 가까운_일정은_캠핑_기간_날짜마다_예보_칸을_준다() {
        plan(1, 10L, 1);   // 내일 체크인 · 모레 체크아웃
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), -9, 2), day(TODAY.plusDays(2), -13, -1)));

        ChecklistResponse.Forecast f = service.find("camper01", 1L, TODAY).forecast();

        assertTrue(f.available());
        assertEquals(List.of(TODAY.plusDays(1), TODAY.plusDays(2)), f.days().stream().map(DayForecast::date).toList());
        assertEquals(-13.0, f.days().get(1).minTemp());
    }

    @Test
    void 기상청이_주지_않는_날은_값_없는_칸으로_남긴다() {
        plan(1, 10L, 2);   // 2일 뒤 체크인
        forecast = Optional.of(List.of(day(TODAY.plusDays(2), -3, 5)));   // 체크아웃(3일 뒤) 예보는 없음

        List<DayForecast> days = service.find("camper01", 1L, TODAY).forecast().days();

        assertEquals(2, days.size());
        assertTrue(days.get(0).hasData());
        assertFalse(days.get(1).hasData());
        assertEquals(TODAY.plusDays(3), days.get(1).date());
    }

    @Test
    void 먼_일정은_예보를_부르지_않는다() {
        plan(1, 10L, 20);

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertNull(r.forecast());
        assertEquals(0, forecastCalls.get());
    }

    @Test
    void 예보를_못_받으면_비워_두고_받았다고_하지_않는다() {
        plan(1, 10L, 1);
        forecast = Optional.empty();   // 키 없음 · 호출 실패

        ChecklistResponse.Forecast f = service.find("camper01", 1L, TODAY).forecast();

        assertFalse(f.available());
        assertTrue(f.days().isEmpty());
    }

    @Test
    void 캠핑_중이면_오늘부터의_날만_보여_준다() {
        CampPlan p = plan(1, 10L, -1);   // 어제 체크인, 오늘 체크아웃
        p.setCheckOut(TODAY.plusDays(2));
        forecast = Optional.of(List.of(day(TODAY.minusDays(1), 0, 5), day(TODAY, -2, 4), day(TODAY.plusDays(1), -4, 3),
                day(TODAY.plusDays(2), -6, 1)));

        List<DayForecast> days = service.find("camper01", 1L, TODAY).forecast().days();

        assertEquals(TODAY, days.get(0).date());   // 지난 날(어제)은 뺀다
        assertEquals(3, days.size());
    }

    @Test
    void 예보_칸은_최대_7일까지() {
        CampPlan p = plan(1, 10L, 1);
        p.setCheckOut(TODAY.plusDays(20));

        assertEquals(7, service.find("camper01", 1L, TODAY).forecast().days().size());
    }

    @Test
    void 예보_최저기온이_있으면_침낭_항목_설명에_보여_준다() {
        plan(1, 10L, 1);   // 예보 최저가 0° 이하라서 날씨 묶음에 침낭 항목이 생긴다 (계절이 아니라 온도로 판단)
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), -9, 2), day(TODAY.plusDays(2), -5, -1)));

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        ChecklistItem bag = r.items().stream().filter(i -> i.id().equals("wx-freeze-2")).findFirst().orElseThrow();
        assertEquals("날씨", bag.group());
        assertEquals("쾌적온도 비교 -9° 이하", bag.why());
    }

    private static long count(List<ChecklistItem> items, String text) {
        return items.stream().filter(i -> i.text().equals(text)).count();
    }

    @Test
    void 한파_특보가_있으면_온도_준비물은_특보_쪽에_한_번씩만() {
        List<ChecklistItem> items = ChecklistItems.build("겨울", false, List.of("한파주의보"));

        assertEquals(1, count(items, "핫팩 · 보온 매트"));
        assertEquals(1, count(items, "일산화탄소 경보기"));
        assertEquals(1, count(items, "물통 · 식수 동파 주의"));
        assertEquals(0, count(items, "침낭"));   // 특보의 "내 침낭의 쾌적온도 확인"만 있다
        assertEquals(1, count(items, "내 침낭의 쾌적온도 확인"));
        assertTrue(items.stream().anyMatch(i -> i.id().equals("warn-cold-1")));
    }

    @Test
    void 겨울에는_계절_묶음이_비고_온도_준비물은_예보가_있을_때만_날씨_묶음에_나온다() {
        // 침낭 · 핫팩 · 일산화탄소 경보기 · 동파는 계절(겨울)이 아니라 예보 온도로 정한다
        List<ChecklistItem> noForecast = ChecklistItems.build("겨울", false, List.of());
        assertTrue(noForecast.stream().noneMatch(i -> i.group().equals("계절")));
        assertEquals(0, count(noForecast, "핫팩 · 보온 매트") + count(noForecast, "침낭")
                + count(noForecast, "일산화탄소 경보기") + count(noForecast, "물통 · 식수 동파 주의"));

        List<ChecklistItem> cold = ChecklistItems.build("겨울", false, List.of(),
                List.of(day(TODAY.plusDays(1), -13, -2)));
        assertEquals(List.of("핫팩 · 보온 매트", "일산화탄소 경보기", "물통 · 식수 동파 주의", "내 침낭의 쾌적온도 확인"),
                cold.stream().filter(i -> i.group().equals("날씨")).map(ChecklistItem::text).toList());
    }

    @Test
    void 예보가_한파_기준이어도_침낭은_한_번만() {
        plan(1, 10L, 1);   // 가을
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), -13, -2)));

        List<ChecklistItem> items = service.find("camper01", 1L, TODAY).items();

        assertEquals(1, count(items, "내 침낭의 쾌적온도 확인") + count(items, "침낭"));
    }

    @Test
    void 특보와_계절이_같은_준비물은_한_번만_보인다() {
        // 폭염(그늘막) · 건조(화기) · 황사(마스크) 와 계절 항목이 겹치는 경우. 특보를 못 받는 예보 없는 상태에서도 적용
        assertEquals(1, ChecklistItems.build("여름", false, List.of("폭염주의보")).stream()
                .filter(i -> i.text().equals("그늘막 · 타프")).count());
        assertEquals(1, ChecklistItems.build("가을", false, List.of("건조주의보")).stream()
                .filter(i -> i.text().equals("화기 사용 가능 여부 확인")).count());
        assertEquals(1, ChecklistItems.build("봄", false, List.of("황사경보")).stream()
                .filter(i -> i.text().startsWith("마스크")).count());
    }

    @Test
    void 호우_특보가_있으면_비_예보_항목은_같은_말이라_빠진다() {
        plan(1, 10L, 1);
        warnings = Optional.of(List.of(warning("호우주의보")));
        forecast = Optional.of(List.of(wet(TODAY.plusDays(1), "비")));

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertTrue(weatherItemsOf(r).isEmpty());
        assertTrue(r.items().stream().anyMatch(i -> i.text().equals("방수 장비 · 배수 대비")));
    }

    @Test
    void 여름_우비와_비_예보의_우비는_한_번만() {
        plan(1, 10L, 1);
        forecast = Optional.of(List.of(wet(TODAY.plusDays(1), "비")));
        ChecklistResponse r = service.find("camper01", 1L, TODAY);   // 가을이라 계절 우비는 없다 — 아래는 여름 직접 확인

        assertEquals(1, ChecklistItems.build("여름", false, List.of(), List.of(wet(TODAY.plusDays(1), "비"))).stream()
                .filter(i -> i.text().startsWith("우비")).count());
        assertEquals(1, r.items().stream().filter(i -> i.text().startsWith("우비")).count());
    }

    private static DayForecast wet(LocalDate date, String precip) {
        return new DayForecast(date, 3.0, 8.0, 80, 3.0, "흐림", precip);
    }

    private List<ChecklistItem> weatherItemsOf(ChecklistResponse r) {
        return r.items().stream().filter(i -> i.group().equals("날씨")).toList();
    }

    @Test
    void 예보_최저가_영하_12도_이하면_한파주의보_기준에_해당한다고_알린다() {
        plan(1, 10L, 1);
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), -12, 0), day(TODAY.plusDays(2), -13, -1)));

        List<ChecklistItem> w = weatherItemsOf(service.find("camper01", 1L, TODAY));

        assertTrue(w.stream().anyMatch(i -> i.text().equals("핫팩 · 보온 매트")
                && i.why().contains("한파주의보 기준 이하")));
        assertTrue(w.stream().anyMatch(i -> i.text().equals("물통 · 식수 동파 주의")));   // -12 는 0 이하이기도 하다
        assertEquals("11.19 예보 최저 -12° · 한파주의보 기준 이하", w.get(0).why());  // 처음 해당하는 날
    }

    @Test
    void 영하_11도는_한파_기준에_해당하지_않고_동파_항목만() {
        plan(1, 10L, 1);
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), -11, 0)));

        List<ChecklistItem> w = weatherItemsOf(service.find("camper01", 1L, TODAY));

        assertEquals(List.of("물통 · 식수 동파 주의", "내 침낭의 쾌적온도 확인"), w.stream().map(ChecklistItem::text).toList());
        assertEquals("11.19 예보 최저 -11° · 0° 이하", w.get(0).why());
    }

    @Test
    void 영상이고_강수도_없으면_날씨_항목이_없다() {
        plan(1, 10L, 1);
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), 0.5, 9)));   // 0.5° 는 0 이하가 아니다

        assertTrue(weatherItemsOf(service.find("camper01", 1L, TODAY)).isEmpty());
    }

    @Test
    void 비나_눈_예보가_있으면_방수_항목() {
        plan(1, 10L, 1);
        forecast = Optional.of(List.of(wet(TODAY.plusDays(1), "없음"), wet(TODAY.plusDays(2), "눈")));

        List<ChecklistItem> w = weatherItemsOf(service.find("camper01", 1L, TODAY));

        assertEquals(List.of("우비 · 방수 장비", "배수 · 물 고임 확인"), w.stream().map(ChecklistItem::text).toList());
        assertEquals("11.20 눈 예보", w.get(0).why());
    }

    @Test
    void 풍속이_세도_날씨_항목은_만들지_않는다() {
        plan(1, 10L, 1);
        forecast = Optional.of(List.of(new DayForecast(TODAY.plusDays(1), 5.0, 12.0, 10, 25.0, "맑음", "없음")));

        assertTrue(weatherItemsOf(service.find("camper01", 1L, TODAY)).isEmpty());   // 풍속 기준은 쓰지 않는다
    }

    @Test
    void 한파_특보와_같은_문구는_한_번만_보인다() {
        plan(1, 10L, 1);
        warnings = Optional.of(List.of(warning("한파주의보")));
        forecast = Optional.of(List.of(day(TODAY.plusDays(1), -14, -3)));

        ChecklistResponse r = service.find("camper01", 1L, TODAY);

        assertEquals(1, r.items().stream().filter(i -> i.text().equals("핫팩 · 보온 매트")).count());
        assertEquals(r.items().size(), r.items().stream().map(ChecklistItem::id).distinct().count());
        assertTrue(r.items().stream().anyMatch(i -> i.id().equals("warn-cold-1")));  // 남는 것은 특보 쪽 항목
    }

    @Test
    void 날씨_묶음은_특보_바로_뒤에_온다() {
        plan(1, 10L, 1);
        warnings = Optional.of(List.of(warning("강풍주의보")));
        forecast = Optional.of(List.of(wet(TODAY.plusDays(1), "비")));

        List<String> groups = service.find("camper01", 1L, TODAY).items().stream().map(ChecklistItem::group).distinct().toList();

        assertEquals(List.of("특보", "날씨", "계절", "공통"), groups);
    }

    @Test
    void 예보를_못_받으면_날씨_항목도_없다() {
        plan(1, 10L, 1);
        forecast = Optional.empty();

        assertTrue(weatherItemsOf(service.find("camper01", 1L, TODAY)).isEmpty());
    }

    @Test
    void 예보가_없으면_침낭_항목은_없다_온도를_모르면_권하지_않는다() {
        plan(1, 10L, 1);
        forecast = Optional.empty();

        assertTrue(service.find("camper01", 1L, TODAY).items().stream()
                .noneMatch(i -> i.text().contains("침낭")));
    }

    @Test
    void 계절은_체크인_월로_정한다() {
        assertEquals("봄", ChecklistItems.seasonOf(3));
        assertEquals("봄", ChecklistItems.seasonOf(5));
        assertEquals("여름", ChecklistItems.seasonOf(6));
        assertEquals("가을", ChecklistItems.seasonOf(11));
        assertEquals("겨울", ChecklistItems.seasonOf(12));
        assertEquals("겨울", ChecklistItems.seasonOf(2));
    }

    @Test
    void 항목_id는_겹치지_않는다() {
        for (String season : List.of("봄", "여름", "가을", "겨울")) {
            List<ChecklistItem> items = ChecklistItems.build(season, true,
                    List.of("폭염주의보", "열대야주의보", "한파경보", "강풍주의보", "호우경보", "대설주의보",
                            "건조주의보", "태풍주의보", "황사경보", "풍랑주의보"));
            assertEquals(items.size(), items.stream().map(ChecklistItem::id).distinct().count(), season);
        }
    }
}
