package com.campin.score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 안전점수 기준을 한곳에 모은다 (DriveTone · SurveyQuestions 처럼 코드 상수로 둔다)
// 바꾸면 PR 로 남고, 다시 빌드해야 반영된다
//
// 점수 = 시설마다 "배점 × 시간 비율"을 더한 값 (만점 100)
//   시간 비율: 만점 분 이하면 1, 0점 분 이상이면 0, 그 사이는 시간에 비례해 줄어든다
//     예) 응급실 배점 20, 30분 만점 · 60분 0점 → 40분이면 20 × (60-40)/(60-30) = 13.3점
//   배점: 중요한 순서(등급)만 정하고 ROC 공식으로 나눈다 (팀이 숫자를 임의로 정하지 않기 위해)
// 시간은 직선거리가 아니라 카카오 길찾기 차 소요시간 (산에서는 직선 2km 도 20분 걸릴 수 있어서)
public final class ScoreRules {

    // 시간 기준(분). 정해진 숫자가 없는 칸은 null → 캠핑장끼리 비교한다
    //   fullMin 이 null : 점수를 매긴 캠핑장 중 가장 빠른 곳이 만점
    //   zeroMin 이 null : 점수를 매긴 캠핑장 중 가장 먼 곳이 0점
    public record TimeRule(Double fullMin, Double zeroMin) {
        boolean valid() {
            if (fullMin != null && fullMin < 0) return false;
            return fullMin == null || zeroMin == null || zeroMin > fullMin;
        }
    }

    // 중요한 순서. 앞 등급일수록 배점이 크다. 같은 등급 안에서는 똑같이 나눈다
    //   상 · 생명을 지켜 줘요 : 응급실 · 소방서 · 경찰(지구대·파출소 포함)
    //   중 · 먼저 도와줘요    : 약국 · 편의점(상비약 파는 24시간 편의점)
    // CCTV · 가로등(하 등급)은 팀 결정으로 계산에서 뺐다 → ROC 를 2등급으로 계산해 상 75 · 중 25 점
    static final List<List<String>> TIERS = List.of(
            List.of("er", "fire", "police"),
            List.of("pharmacy", "store")
    );

    // 종류 → 분 기준
    static final Map<String, TimeRule> TIME = Map.of(
            "er", new TimeRule(30.0, 60.0),       // 응급실: 30분 안 만점, 60분 넘으면 0점
            "fire", new TimeRule(5.0, 15.0),      // 소방서: 5분 안 만점, 15분 넘으면 0점
            "police", new TimeRule(5.0, null),    // 경찰: 5분 안 만점, 캠핑장 중 가장 먼 곳 0점
            // 약국 · 편의점: 정해진 분 기준이 없어 캠핑장끼리 비교한다 (팀 결정 2026-11-17)
            //   가장 빠른 캠핑장 만점 → 멀수록 조금씩 비례해 깎임 → 가장 먼 캠핑장 0점
            "pharmacy", new TimeRule(null, null),
            "store", new TimeRule(null, null)
    );

    // 카카오 호출 사이 쉬는 시간(ms). 한꺼번에 몰아 부르지 않게
    static final long CALL_INTERVAL_MS = 100;

    // ---- 진단 유형 가산점 (지도의 "내 유형 기준") ----
    // 진단에서 아이·어르신·차박·겨울·걸어서 중 하나를 고른 사람에게, 응급실이 가까운 캠핑장일수록 점수를 조금 더 얹는다
    // (어느 답이 해당하는지는 화면 utils/webSurvey.js 의 needsErBonus — 진단 답은 브라우저에만 있어서)
    // 크기: 응급실 부분점수(위 TIME 의 er 기준: 30분 안 1 → 60분 넘으면 0) × 최대 5점, 반올림
    //   5점 = 응급실 배점(25점)의 5분의 1. 10점은 60점대가 안전 등급(70)으로 넘어가 너무 커서 줄였다 (팀 결정 2026-11-23)
    // 저장된 안전점수(camp.safety_score)·등급은 바꾸지 않는다. 화면이 "79 + 응급실 +5" 로 따로 보여 준다
    public static final int ER_BONUS_MAX = 5;

    // 응급실 분 → 가산점 0~5. 응급실 정보가 없으면 0
    public static int erBonus(Integer erDriveMin) {
        if (erDriveMin == null) return 0;
        TimeRule er = TIME.get("er");
        return (int) Math.round(ER_BONUS_MAX * SafetyScoreService.part(erDriveMin, er.fullMin(), er.zeroMin()));
    }

    private ScoreRules() {
    }

    // ROC(Rank Order Centroid) 가중치: n 등급 중 k 번째(1부터) = (1/n) × (1/k + 1/(k+1) + … + 1/n)
    //   3등급이면 0.611 · 0.278 · 0.111 (사진의 61 · 28 · 11), 2등급이면 0.75 · 0.25. 합은 항상 1
    static double roc(int k, int n) {
        double sum = 0;
        for (int i = k; i <= n; i++) sum += 1.0 / i;
        return sum / n;
    }

    // 종류 → 배점(100점 만점). 등급의 ROC 가중치 × 100 을 그 등급 시설 수로 똑같이 나눈다
    static Map<String, Double> points(List<List<String>> tiers) {
        Map<String, Double> result = new LinkedHashMap<>();
        int n = tiers.size();
        for (int k = 1; k <= n; k++) {
            List<String> tier = tiers.get(k - 1);
            double each = roc(k, n) * 100 / tier.size();
            for (String type : tier) result.put(type, each);
        }
        return result;
    }
}
