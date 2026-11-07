package com.campin.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

// DB 없이 여행 취향 규칙만 확인한다 (회원 한 명 + 메모리 표)
class TourPreferenceServiceTest {

    private final List<TourPreference> table = new ArrayList<>();
    private TourPreferenceService service;

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
        TourPreferenceMapper fakePreferences = new TourPreferenceMapper() {
            @Override public List<TourPreference> findByMemberId(Long id) { return new ArrayList<>(table); }
            @Override public void deleteByMemberId(Long id) { table.clear(); }
            @Override public void insert(Long id, String question, String optionCode) {
                table.add(new TourPreference(question, optionCode));
            }
        };
        service = new TourPreferenceService(fakeMembers, fakePreferences);
    }

    @Test
    void 질문은_두_개이고_선택지는_8개와_5개() {
        assertEquals(2, service.questions().size());
        assertEquals(8, service.questions().get(0).options().size());
        assertEquals(5, service.questions().get(1).options().size());
    }

    @Test
    void 저장한_적이_없으면_빈_답() {
        TourPreferenceResponse res = service.get("camper01");
        assertTrue(res.activity().isEmpty());
        assertTrue(res.nature().isEmpty());
    }

    @Test
    void 최대_개수까지_저장되고_선택지_순서로_돌려준다() {
        service.save("camper01", new TourPreferenceRequest(
                List.of("stars", "trekking", "rest"), List.of("valley", "mountain")));

        TourPreferenceResponse res = service.get("camper01");
        assertEquals(List.of("trekking", "stars", "rest"), res.activity());
        assertEquals(List.of("mountain", "valley"), res.nature());
    }

    @Test
    void 같은_값은_하나로_친다() {
        TourPreferenceResponse res = service.save("camper01",
                new TourPreferenceRequest(List.of("food", "food"), List.of()));
        assertEquals(List.of("food"), res.activity());
    }

    @Test
    void 다시_저장하면_이전_답을_바꾼다() {
        service.save("camper01", new TourPreferenceRequest(List.of("food"), List.of("sea")));
        service.save("camper01", new TourPreferenceRequest(List.of("culture"), List.of()));

        TourPreferenceResponse res = service.get("camper01");
        assertEquals(List.of("culture"), res.activity());
        assertTrue(res.nature().isEmpty());
    }

    @Test
    void 최대_개수를_넘기면_기존_답이_그대로_남는다() {
        service.save("camper01", new TourPreferenceRequest(List.of("food"), List.of("sea")));

        assertThrows(InvalidRequestException.class, () -> service.save("camper01", new TourPreferenceRequest(
                List.of("trekking", "water", "food", "culture"), List.of())));
        assertThrows(InvalidRequestException.class, () -> service.save("camper01", new TourPreferenceRequest(
                List.of(), List.of("mountain", "valley", "lake"))));

        assertEquals(List.of("food"), service.get("camper01").activity());
    }

    @Test
    void 다른_질문의_선택지나_없는_값은_저장되지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                new TourPreferenceRequest(List.of("mountain"), List.of())));
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                new TourPreferenceRequest(List.of("fishing"), List.of())));
        assertTrue(table.isEmpty());
    }

    @Test
    void 없는_회원이면_찾을_수_없다() {
        assertThrows(NotFoundException.class, () -> service.get("nobody"));
    }
}
