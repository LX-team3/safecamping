package com.campin.score;

import java.util.Map;

// 안전점수 기준을 한곳에 모은다 (DriveTone · SurveyQuestions 처럼 코드 상수로 둔다)
// 바꾸면 PR 로 남고, 다시 빌드해야 반영된다
public final class ScoreRules {

    // 시간 t(분)이 fullMin 이하면 만점, zeroMin 이상이면 0점, 그 사이는 비례해서 줄어든다
    public record TimeRule(double fullMin, double zeroMin) {
        boolean valid() {
            return fullMin >= 0 && zeroMin > fullMin;
        }
    }

    // 종류 → 분 기준. 카카오 길찾기 소요시간으로 점수를 낸다
    // 여기 없는 종류(보안등·CCTV 등 차로 갈 곳이 아닌 것)는 예전처럼 public.score_weights 의 거리 기준
    // TODO 팀 결정 필요: 안전 수치라 임의로 넣지 않았다. 비어 있으면 계산하지 않는다
    //   예) "er", new TimeRule(만점 분, 0점 분),
    static final Map<String, TimeRule> TIME = Map.of(
    );

    // 카카오 호출 사이 쉬는 시간(ms). 한꺼번에 몰아 부르지 않게
    static final long CALL_INTERVAL_MS = 100;

    private ScoreRules() {
    }
}
