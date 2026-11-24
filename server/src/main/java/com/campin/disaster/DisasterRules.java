package com.campin.disaster;

import java.util.ArrayList;
import java.util.List;

// 재난지도 대피 단계 판단 규칙 (인북천 물빛테마공원 캠핑장). 숫자는 이곳 한곳에만 둔다
// 출처 (disaster-map/rag 단계 문서, client/public/disaster-map/criteria.json 과 같은 값):
//  - 준비 1단계: 3시간 60mm 또는 12시간 110mm  = 기상청 호우주의보 기준
//  - 대피 2단계: 3시간 90mm 또는 12시간 180mm  = 기상청 호우경보 기준
//  - 3단계(화면 이름은 '대피', 막대 표시 '침수'): 3시간 114mm 또는 24시간 310mm = 서화 관측소 50년에 한 번 올 큰비 (WAMIS 설계강수량)
// 숫자를 바꾸려면 criteria.json · rag 문서도 같이 바꾼다 (안전 기준이라 임의로 바꾸지 않는다)
// 2026-11-23: 기상청 호우특보도 단계에 넣는다 (judgeWarnings). 특보는 "3시간 60·90mm 이상 예상" 때 내리는 예보 기반 경고라,
//   이미 내린 비(실황)가 기준에 닿기 전에 단계를 올릴 수 있다. 최종 단계 = 비 · 수위 · 특보 중 가장 높은 쪽
public final class DisasterRules {

    public static final double READY_3H = 60;
    public static final double READY_12H = 110;
    public static final double EVAC_3H = 90;
    public static final double EVAC_12H = 180;
    public static final double NOW_3H = 114;
    public static final double NOW_24H = 310;

    private DisasterRules() {
    }

    // level: 0 안전 · 1 준비 · 2 대피 · 3 침수(화면은 '대피'). 3시간 비를 모르면 null (판단하지 않음)
    // reasons: 그 단계가 된 까닭 (예: "최근 3시간 95mm ≥ 90mm"). 안전이면 빈 목록
    public record Judgement(Integer level, List<String> reasons) {
    }

    // 3시간 값은 꼭 있어야 하고, 12·24시간 값은 모르면(null) 그 조건만 빼고 본다
    public static Judgement judge(Double rain3h, Double rain12h, Double rain24h) {
        if (rain3h == null) {
            return new Judgement(null, List.of());
        }
        int[] levels = { 3, 2, 1 };
        for (int lv : levels) {
            List<String> why = new ArrayList<>();
            double[] limit = limits(lv);
            if (rain3h >= limit[0]) {
                why.add(text("최근 3시간", rain3h, limit[0]));
            }
            Double other = lv == 3 ? rain24h : rain12h;
            String otherName = lv == 3 ? "최근 24시간" : "최근 12시간";
            if (other != null && other >= limit[1]) {
                why.add(text(otherName, other, limit[1]));
            }
            if (!why.isEmpty()) {
                return new Judgement(lv, why);
            }
        }
        return new Judgement(0, List.of());
    }

    // 상류 수위로 본 단계. 기준수위는 한강홍수통제소 공식 값(관측소마다 다름)이고, 어느 단계에 맞출지는 팀이 정한 연결:
    //   관심 수위 이상 → 1 준비 · 경보 수위 이상 → 2 대피 · 심각 수위 이상 → 3 침수 (반월촌교는 주의보 수위가 없음)
    // 수위를 모르면 null. 기준이 없는(null) 칸은 그 조건만 빼고 본다
    public static Judgement judgeWater(String station, Double wl, Double attention, Double alarm, Double serious) {
        if (wl == null) {
            return new Judgement(null, List.of());
        }
        Double[] limit = { serious, alarm, attention };
        String[] name = { "심각", "경보", "관심" };
        for (int i = 0; i < 3; i++) {
            if (limit[i] != null && wl >= limit[i]) {
                return new Judgement(3 - i, List.of("상류 " + station + " 수위 " + fmt(wl) + "m ≥ " + name[i] + " 수위 " + fmt(limit[i]) + "m"));
            }
        }
        return new Judgement(0, List.of());
    }

    // 기상청 호우특보로 본 단계: 호우주의보 → 1 준비 · 호우경보 → 2 대피 (특보 이름이 곧 기준이라 새 숫자는 없다)
    // categories: 캠핑장(인제)에 걸린 특보 이름들. 못 받았으면 null
    // 호우특보가 없으면 level null = 판단 재료로 쓰지 않음 (특보가 없다는 이유만으로 '안전'이라 하지 않는다 — 비·수위로 판단)
    public static Judgement judgeWarnings(List<String> categories) {
        if (categories == null) {
            return new Judgement(null, List.of());
        }
        if (categories.stream().anyMatch(c -> c != null && c.contains("호우경보"))) {
            return new Judgement(2, List.of("기상청 호우경보 발효"));
        }
        if (categories.stream().anyMatch(c -> c != null && c.contains("호우주의보"))) {
            return new Judgement(1, List.of("기상청 호우주의보 발효"));
        }
        return new Judgement(null, List.of());
    }

    // 두 판단 중 높은 쪽. 까닭은 그 단계를 만든 쪽 것만 (둘 다 같으면 둘 다). 둘 다 모르면 null
    public static Judgement higher(Judgement a, Judgement b) {
        if (a.level() == null) {
            return b;
        }
        if (b.level() == null) {
            return a;
        }
        int lv = Math.max(a.level(), b.level());
        List<String> why = new ArrayList<>();
        if (a.level() == lv) {
            why.addAll(a.reasons());
        }
        if (b.level() == lv) {
            why.addAll(b.reasons());
        }
        return new Judgement(lv, why);
    }

    // {3시간 기준, 12시간(3단계는 24시간) 기준}
    private static double[] limits(int level) {
        return switch (level) {
            case 3 -> new double[] { NOW_3H, NOW_24H };
            case 2 -> new double[] { EVAC_3H, EVAC_12H };
            default -> new double[] { READY_3H, READY_12H };
        };
    }

    private static String text(String name, double mm, double limit) {
        return name + " " + fmt(mm) + "mm ≥ " + fmt(limit) + "mm";
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
