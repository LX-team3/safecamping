package com.campin.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

// DB 없이 1분 진단 답 규칙만 확인한다 (회원 한 명 + 메모리 한 줄)
class SurveyServiceTest {

    private SurveyAnswerRow saved;
    private SurveyService service;

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
        SurveyMapper fakeSurvey = new SurveyMapper() {
            @Override public SurveyAnswerRow findByMemberId(Long id) { return saved; }
            @Override public void upsert(SurveyAnswerRow row) { saved = row; }
        };
        service = new SurveyService(fakeSurvey, fakeMembers);
    }

    private static SurveyAnswers answers(List<String> heating, List<String> items, List<String> facilities) {
        return new SurveyAnswers("auto", "car", heating, items, facilities);
    }

    @Test
    void 진단한_적이_없으면_null() {
        assertNull(service.get("camper01"));
    }

    @Test
    void 저장하면_시설은_고른_순서대로_돌려준다() {
        service.save("camper01", answers(List.of("fuel"), List.of("none"), List.of("fire", "er", "store")));
        SurveyAnswers got = service.get("camper01");
        assertEquals("auto", got.campType());
        assertEquals(List.of("fire", "er", "store"), got.facilities());
        assertEquals(List.of("none"), got.safetyItems());
    }

    @Test
    void 다시_진단하면_덮어쓴다() {
        service.save("camper01", answers(List.of("fuel"), List.of("none"), List.of("er")));
        service.save("camper01", answers(List.of("electric", "wood"), List.of("extinguisher"), List.of("police")));
        assertEquals(List.of("electric", "wood"), service.get("camper01").heating());
    }

    @Test
    void 답이_빠지거나_시설을_네_개_고르면_저장되지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                new SurveyAnswers(null, "car", List.of("fuel"), List.of("none"), List.of("er"))));
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                answers(List.of("fuel"), List.of("none"), List.of())));
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                answers(List.of("fuel"), List.of("none"), List.of("er", "fire", "police", "store"))));
    }

    @Test
    void 아직_없어요는_다른_용품과_같이_고를_수_없다() {
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                answers(List.of("fuel"), List.of("none", "extinguisher"), List.of("er"))));
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                answers(List.of("none", "fuel"), List.of("extinguisher"), List.of("er"))));
    }

    @Test
    void 없는_값이나_같은_값_두_번은_저장되지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                new SurveyAnswers("tent", "car", List.of("fuel"), List.of("none"), List.of("er"))));
        assertThrows(InvalidRequestException.class, () -> service.save("camper01",
                answers(List.of("fuel"), List.of("none"), List.of("er", "er"))));
    }
}
