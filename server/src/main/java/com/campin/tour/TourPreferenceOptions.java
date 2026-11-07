package com.campin.tour;

import java.util.List;

// 여행 취향 설문의 질문·선택지 (화면도 이 목록을 API 로 받아 그린다)
// code 는 tour 테이블의 category·nature 값과 같아야 한다 → 그래야 관광지 탭 순서가 바뀐다
public final class TourPreferenceOptions {

    public static final String ACTIVITY = "activity";
    public static final String NATURE = "nature";

    public record Option(String code, String label) {
    }

    public record Question(String code, String title, int max, List<Option> options) {

        boolean hasOption(String code) {
            return options.stream().anyMatch(o -> o.code().equals(code));
        }
    }

    public static final Question ACTIVITY_QUESTION = new Question(ACTIVITY, "가고 싶은 관광지 · 체험은?", 3, List.of(
            new Option("trekking", "자연 · 트레킹"),
            new Option("water", "물놀이 · 레저"),
            new Option("activity", "액티비티"),
            new Option("food", "맛집 · 시장"),
            new Option("culture", "문화 · 전시"),
            new Option("stars", "별 보기 · 야경"),
            new Option("festival", "축제 · 공연"),
            new Option("rest", "쉬기만")));

    public static final Question NATURE_QUESTION = new Question(NATURE, "어떤 자연환경이 좋나요?", 2, List.of(
            new Option("mountain", "산 · 숲"),
            new Option("valley", "계곡 · 강"),
            new Option("lake", "호수"),
            new Option("sea", "바다 · 해변"),
            new Option("field", "평지 · 잔디")));

    public static final List<Question> QUESTIONS = List.of(ACTIVITY_QUESTION, NATURE_QUESTION);

    private TourPreferenceOptions() {
    }
}
