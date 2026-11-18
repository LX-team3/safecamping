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

// DB·기상청 없이 출발 준비 체크리스트 규칙만 확인한다 (가짜 일정 · 가짜 특보)
class ChecklistServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private final List<CampPlan> plans = new ArrayList<>();
    private Optional<List<ChecklistResponse.Warning>> warnings = Optional.of(List.of());
    private final AtomicInteger warningCalls = new AtomicInteger();
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
