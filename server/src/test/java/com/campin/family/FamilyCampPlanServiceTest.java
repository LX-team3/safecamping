package com.campin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// DB 없이 가족 캠핑 공유 규칙만 확인한다 (mapper 는 메모리 List, 가족방은 FakeFamily: mom·dad 1번 방, other 2번 방, alone 방 없음)
class FamilyCampPlanServiceTest {

    private static final Map<Long, Long> ROOM_OF = Map.of(1L, 1L, 2L, 1L, 3L, 2L);  // FakeFamily 와 같은 배치
    private final List<FamilyCampPlan> plans = new ArrayList<>();
    private FamilyCampPlanService service;

    @BeforeEach
    void setUp() {
        // SQL 과 같은 조건: 같은 방 + 나 빼고 + 체크인 순
        FamilyCampPlanMapper fakeMapper = (roomId, memberId) -> plans.stream()
                .filter(p -> roomId.equals(ROOM_OF.get(p.getOwnerId())) && !p.getOwnerId().equals(memberId))
                .sorted(Comparator.comparing(FamilyCampPlan::getCheckIn))
                .toList();
        service = new FamilyCampPlanService(fakeMapper, FakeFamily.service());
    }

    private void plan(long id, long ownerId, String name, String checkIn) {
        FamilyCampPlan p = new FamilyCampPlan();
        p.setId(id);
        p.setOwnerId(ownerId);
        p.setOwnerName(FakeFamily.nameOf(ownerId));
        p.setCampId(16L);
        p.setCampName(name);
        p.setCheckIn(LocalDate.parse(checkIn));
        p.setCheckOut(LocalDate.parse(checkIn).plusDays(1));
        plans.add(p);
    }

    @Test
    void 같은_가족방_다른_사람의_캠핑이_체크인_순으로_보인다() {
        plan(1, 1, "엄마 캠핑 늦은 날", "2026-12-20");
        plan(2, 1, "엄마 캠핑", "2026-12-05");
        plan(3, 2, "아빠 캠핑", "2026-12-10");

        List<FamilyCampPlanResponse> forDad = service.findOthers("dad");
        assertEquals(List.of("엄마 캠핑", "엄마 캠핑 늦은 날"), forDad.stream().map(FamilyCampPlanResponse::campName).toList());
        assertEquals("엄마", forDad.get(0).ownerName());
        assertEquals(List.of("아빠 캠핑"), service.findOthers("mom").stream().map(FamilyCampPlanResponse::campName).toList());
    }

    @Test
    void 다른_가족방이나_방이_없으면_보이지_않는다() {
        plan(1, 1, "엄마 캠핑", "2026-12-05");

        assertTrue(service.findOthers("other").isEmpty());
        assertTrue(service.findOthers("alone").isEmpty());  // 방이 없어도 404 가 아니라 빈 목록
    }
}
