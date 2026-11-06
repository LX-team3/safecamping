package com.campin.survey;

import java.util.List;

import com.campin.survey.SurveyQuestion.Option;

// 1분 진단 5문항 (설계 Survey.dc.html 의 문구·선택지 그대로). 질문은 여기 한곳에서만 고친다
// code 는 survey_answer 에 저장되는 값이라 한 번 정하면 바꾸지 않는다 (label 은 바꿔도 됨)
// 지역·날짜·동행은 여행마다 바뀌어 탐색 화면에서 고르고, 관광지·체험은 내 정보 · 여행 취향으로 옮겼다
public final class SurveyQuestions {

    private SurveyQuestions() {
    }

    public static final List<SurveyQuestion> ALL = List.of(
            new SurveyQuestion("campType", "어떤 캠핑을 하나요?", "single", 1, null, List.of(
                    new Option("auto", "오토캠핑장", "차 옆에 텐트를 쳐요 · 차박 포함"),
                    new Option("glamping", "글램핑 · 카라반", "장비 없이 시설을 이용해요"),
                    new Option("wild", "노지 · 백패킹", "캠핑이 허용된 구역만 안내해요"),
                    new Option("unknown", "모르겠어요", "안전 기준에 맞춰 정해드려요"))),
            new SurveyQuestion("transport", "어떻게 이동하나요?", "single", 1, null, List.of(
                    new Option("car", "자동차", null),
                    new Option("public", "대중교통", null),
                    new Option("other", "그 외", "자전거 · 도보 · 바이크"))),
            new SurveyQuestion("heating", "어떤 난방을 쓰나요?", "multi", 4, "none", List.of(
                    new Option("electric", "전기장판 · 전기히터", null),
                    new Option("fuel", "가스 · 석유난로", null),
                    new Option("wood", "화목난로 · 숯불", null),
                    new Option("none", "난방은 하지 않아요", null))),
            new SurveyQuestion("safetyItems", "챙겨둔 안전 용품은?", "multi", 6, "none", List.of(
                    new Option("extinguisher", "소화기", null),
                    new Option("firstAid", "구급상자", null),
                    new Option("coAlarm", "일산화탄소 경보기", null),
                    new Option("lantern", "랜턴 · 호루라기", null),
                    new Option("battery", "보조배터리", null),
                    new Option("none", "아직 없어요", null))),
            new SurveyQuestion("facilities", "안심되는 주변 시설은?", "rank", 3, null, List.of(
                    new Option("er", "응급실", null),
                    new Option("fire", "소방서 · 119 구조대", null),
                    new Option("police", "지구대 · 파출소", null),
                    new Option("store", "편의점", null),
                    new Option("toilet", "공중화장실", null),
                    new Option("cctv", "CCTV", null),
                    new Option("light", "보안등", null),
                    new Option("house", "인근 주택", null))));

    public static SurveyQuestion get(String key) {
        return ALL.stream().filter(q -> q.key().equals(key)).findFirst().orElseThrow();
    }
}
