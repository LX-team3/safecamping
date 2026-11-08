package com.campin.mycamping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

// DB 없이 내 캠핑 규칙만 확인한다 (회원 두 명 + 메모리 표)
class CampPlanServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private final List<CampPlan> table = new ArrayList<>();
    private CampPlanService service;

    @BeforeEach
    void setUp() {
        MemberMapper fakeMembers = new MemberMapper() {
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
        CampPlanMapper fakePlans = new CampPlanMapper() {
            @Override public List<CampPlan> findByMemberId(Long memberId) {
                return table.stream().filter(p -> p.getMemberId().equals(memberId))
                        .sorted(Comparator.comparing(CampPlan::getCheckIn)).toList();  // SQL 의 ORDER BY check_in
            }
            @Override public boolean exists(Long memberId, Long campId, LocalDate checkIn) {
                return table.stream().anyMatch(p -> p.getMemberId().equals(memberId)
                        && p.getCampId().equals(campId) && p.getCheckIn().equals(checkIn));
            }
            @Override public int insert(CampPlan plan) {
                plan.setId((long) table.size() + 1);
                table.add(plan);
                return 1;
            }
            @Override public int delete(Long memberId, Long planId) {
                return table.removeIf(p -> p.getId().equals(planId) && p.getMemberId().equals(memberId)) ? 1 : 0;
            }
        };
        service = new CampPlanService(fakeMembers, fakePlans);
    }

    // 오늘에서 며칠 뒤 체크인, nights 박
    private static CampPlanRequest plan(long campId, int daysLater, int nights) {
        LocalDate in = TODAY.plusDays(daysLater);
        return new CampPlanRequest(campId, " 설하캠핑장 ", "강원 인제군 북면", in, in.plusDays(nights), "  ");
    }

    @Test
    void 등록하면_체크인_빠른_순으로_보인다() {
        service.add("camper01", plan(2L, 20, 1));
        CampPlanResponse first = service.add("camper01", plan(1L, 5, 2));

        assertEquals("설하캠핑장", first.campName());  // 앞뒤 공백은 지운다
        assertNull(first.memo());                      // 빈 메모는 null
        assertEquals(List.of(1L, 2L), service.findAll("camper01").stream().map(CampPlanResponse::campId).toList());
    }

    @Test
    void 오늘_체크인은_되고_지난_날짜는_안_된다() {
        service.add("camper01", plan(1L, 0, 1));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(2L, -1, 1)));
    }

    @Test
    void 체크아웃은_체크인_다음_날부터() {
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(1L, 3, 0)));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(1L, 3, -1)));
        assertTrue(service.findAll("camper01").isEmpty());
    }

    @Test
    void 같은_캠핑장_같은_체크인은_두_번_등록되지_않는다() {
        service.add("camper01", plan(1L, 5, 1));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(1L, 5, 3)));
        service.add("camper02", plan(1L, 5, 1));  // 다른 회원은 따로
        assertEquals(1, service.findAll("camper01").size());
    }

    @Test
    void 남의_등록은_보이지도_지우지도_못한다() {
        Long id = service.add("camper01", plan(1L, 5, 1)).id();

        assertTrue(service.findAll("camper02").isEmpty());
        assertThrows(NotFoundException.class, () -> service.delete("camper02", id));

        service.delete("camper01", id);
        assertTrue(service.findAll("camper01").isEmpty());
    }
}
