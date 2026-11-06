package com.campin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// DB 없이 여행 취향 규칙만 확인한다 (회원 한 명 + 메모리 취향 표)
class ActivityServiceTest {

    private final Map<Long, List<String>> table = new HashMap<>();
    private ActivityService service;

    @BeforeEach
    void setUp() {
        Member member = new Member();
        member.setId(1L);
        member.setLoginId("camper01");

        MemberMapper fakeMembers = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) { return "camper01".equals(loginId) ? member : null; }
            @Override public boolean existsByLoginId(String loginId) { return false; }
            @Override public void insert(Member m) { }
        };
        ActivityMapper fakeActivitys = new ActivityMapper() {
            @Override public List<String> findByMemberId(Long id) { return new ArrayList<>(table.getOrDefault(id, List.of())); }
            @Override public void deleteByMemberId(Long id) { table.remove(id); }
            @Override public void insert(Long id, String activity) { table.computeIfAbsent(id, k -> new ArrayList<>()).add(activity); }
        };
        service = new ActivityService(fakeMembers, fakeActivitys);
    }

    @Test
    void 처음에는_선택지_6개와_빈_선택() {
        ActivityResponse res = service.get("camper01");
        assertEquals(6, res.options().size());
        assertTrue(res.selected().isEmpty());
        assertEquals(3, res.max());
    }

    @Test
    void 세_개까지_저장되고_선택지_순서로_돌려준다() {
        service.update("camper01", new ActivityRequest(List.of("season", "fishing", "hiking")));
        assertEquals(List.of("fishing", "hiking", "season"), service.get("camper01").selected());
    }

    @Test
    void 같은_값은_하나로_친다() {
        service.update("camper01", new ActivityRequest(List.of("beach", "beach")));
        assertEquals(List.of("beach"), service.get("camper01").selected());
    }

    @Test
    void 네_개나_없는_값은_저장되지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.update("camper01",
                new ActivityRequest(List.of("fishing", "hiking", "valley", "beach"))));
        assertThrows(InvalidRequestException.class, () -> service.update("camper01",
                new ActivityRequest(List.of("star"))));  // 뺀 선택지(별 보기)
        assertTrue(service.get("camper01").selected().isEmpty());
    }

    @Test
    void 빈_목록을_보내면_모두_지운다() {
        service.update("camper01", new ActivityRequest(List.of("kids")));
        service.update("camper01", new ActivityRequest(List.of()));
        assertTrue(service.get("camper01").selected().isEmpty());
    }
}
