package com.campin.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// DB 없이 설문 규칙만 확인한다 (mapper 는 메모리 목록으로 대신함)
class SurveyServiceTest {

    // 테스트용 설문: 1단계에 질문 2개
    //  - heating: 복수 선택, "none" 은 단독 선택지
    //  - nearby : 순위, 최대 2개
    private final Survey survey = new Survey(1L, "diagnosis", "1분 진단", null, "끝", true);
    private final List<SurveyStep> steps = List.of(new SurveyStep(10L, 1L, 1, "제목", null, null));
    private final List<SurveyQuestion> questions = List.of(
            new SurveyQuestion(100L, 10L, "heating", 1, "어떤 난방을 쓰나요?", "card", 0, true, null),
            new SurveyQuestion(101L, 10L, "nearby", 2, "안심되는 주변 시설은?", "rank", 2, true, null));
    private final List<SurveyOption> options = List.of(
            new SurveyOption(1000L, 100L, "gas", 1, "가스", null, false, "일산화탄소 위험"),
            new SurveyOption(1001L, 100L, "electric", 2, "전기", null, false, null),
            new SurveyOption(1002L, 100L, "none", 3, "난방은 하지 않아요", null, true, null),
            new SurveyOption(1010L, 101L, "er", 1, "응급실", null, false, null),
            new SurveyOption(1011L, 101L, "fire", 2, "소방서", null, false, null),
            new SurveyOption(1012L, 101L, "police", 3, "파출소", null, false, null));

    private final List<long[]> savedItems = new ArrayList<>(); // [answerId, optionId, rank]
    private boolean memberExists = true;
    private SurveyService service;

    @BeforeEach
    void setUp() {
        SurveyMapper fakeMapper = new SurveyMapper() {
            @Override public Survey findSurveyByCode(String code) { return code.equals("diagnosis") ? survey : null; }
            @Override public List<SurveyStep> findSteps(Long surveyId) { return steps; }
            @Override public List<SurveyQuestion> findQuestions(Long surveyId) { return questions; }
            @Override public List<SurveyOption> findOptions(Long surveyId) { return options; }
            @Override public int insertAnswer(SurveyAnswer answer) {
                if (!memberExists) return 0;
                answer.setId(77L);
                return 1;
            }
            @Override public void insertAnswerItem(Long answerId, Long optionId, int rank) {
                savedItems.add(new long[] {answerId, optionId, rank});
            }
            @Override public List<SurveyAnswerItem> findLatestAnswerItems(Long surveyId, String loginId) {
                return List.of(new SurveyAnswerItem("nearby", "fire", 1), new SurveyAnswerItem("nearby", "er", 2));
            }
        };
        service = new SurveyService(fakeMapper);
    }

    private SurveyAnswerRequest answers(List<String> heating, List<String> nearby) {
        return new SurveyAnswerRequest(Map.of("heating", heating, "nearby", nearby));
    }

    @Test
    void 설문_내용은_단계_질문_선택지로_묶여서_나온다() {
        SurveyResponse res = service.getSurvey("diagnosis");

        assertEquals(1, res.steps().size());
        assertEquals(2, res.steps().get(0).questions().size());
        SurveyResponse.Question heating = res.steps().get(0).questions().get(0);
        assertEquals(3, heating.options().size());
        assertEquals("일산화탄소 위험", heating.options().get(0).warning());
        assertTrue(heating.options().get(2).exclusive());
    }

    @Test
    void 없는_설문은_오류() {
        assertThrows(InvalidRequestException.class, () -> service.getSurvey("nothing"));
    }

    @Test
    void 회원이_내면_고른_순서대로_저장된다() {
        Map<String, Boolean> res = service.submit("diagnosis",
                answers(List.of("gas"), List.of("fire", "er")), "camper01");

        assertTrue(res.get("saved"));
        assertEquals(3, savedItems.size());
        assertEquals(1011L, savedItems.get(1)[1]); // fire 가 1순위
        assertEquals(1, savedItems.get(1)[2]);
        assertEquals(2, savedItems.get(2)[2]);     // er 이 2순위
    }

    @Test
    void 비회원은_검사만_하고_저장하지_않는다() {
        Map<String, Boolean> res = service.submit("diagnosis", answers(List.of("gas"), List.of("er")), null);

        assertFalse(res.get("saved"));
        assertTrue(savedItems.isEmpty());
    }

    @Test
    void 회원이_없으면_저장되지_않는다() {
        memberExists = false;
        Map<String, Boolean> res = service.submit("diagnosis", answers(List.of("gas"), List.of("er")), "ghost");

        assertFalse(res.get("saved"));
        assertTrue(savedItems.isEmpty());
    }

    @Test
    void 필수_질문을_비우면_오류() {
        assertThrows(InvalidRequestException.class,
                () -> service.submit("diagnosis", answers(List.of(), List.of("er")), null));
    }

    @Test
    void 최대_개수를_넘으면_오류() {
        assertThrows(InvalidRequestException.class,
                () -> service.submit("diagnosis", answers(List.of("gas"), List.of("er", "fire", "police")), null));
    }

    @Test
    void 단독_선택지를_다른_것과_같이_고르면_오류() {
        assertThrows(InvalidRequestException.class,
                () -> service.submit("diagnosis", answers(List.of("none", "gas"), List.of("er")), null));
    }

    @Test
    void 없는_선택지나_질문이면_오류() {
        assertThrows(InvalidRequestException.class,
                () -> service.submit("diagnosis", answers(List.of("fire"), List.of("er")), null)); // fire 는 다른 질문 선택지
        assertThrows(InvalidRequestException.class,
                () -> service.submit("diagnosis", new SurveyAnswerRequest(
                        Map.of("heating", List.of("gas"), "nearby", List.of("er"), "unknown", List.of("x"))), null));
    }

    @Test
    void 같은_선택지를_두_번_고르면_오류() {
        assertThrows(InvalidRequestException.class,
                () -> service.submit("diagnosis", answers(List.of("gas"), List.of("er", "er")), null));
    }

    @Test
    void 내_지난_답변은_질문별_목록으로_나온다() {
        Map<String, List<String>> res = service.myAnswers("diagnosis", "camper01");
        assertEquals(List.of("fire", "er"), res.get("nearby"));
    }
}
