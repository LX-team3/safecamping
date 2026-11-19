package com.campin.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.campin.route.DirectionsSource;

// DB·카카오 없이 점수 규칙만 확인한다 (mapper·길찾기는 가짜로 대신함)
class SafetyScoreServiceTest {

    @Test
    void roc_사진의_61_28_11() {
        assertEquals(0.611, ScoreRules.roc(1, 3), 0.001);
        assertEquals(0.278, ScoreRules.roc(2, 3), 0.001);
        assertEquals(0.111, ScoreRules.roc(3, 3), 0.001);
    }

    @Test
    void 배점_3등급이면_20_14_6_합100() {
        Map<String, Double> p = ScoreRules.points(List.of(
                List.of("er", "fire", "police"), List.of("pharmacy", "store"), List.of("cctv", "light")));
        // 61.1 ÷ 3 = 20.4, 27.8 ÷ 2 = 13.9, 11.1 ÷ 2 = 5.6 → 반올림하면 사진의 20 · 14 · 6
        assertEquals(20, Math.round(p.get("er")));
        assertEquals(14, Math.round(p.get("pharmacy")));
        assertEquals(6, Math.round(p.get("cctv")));
        assertEquals(100, p.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void 지금_배점은_상75_중25() {
        Map<String, Double> p = ScoreRules.points(ScoreRules.TIERS);
        assertEquals(25, p.get("er"), 1e-9);
        assertEquals(12.5, p.get("store"), 1e-9);
    }

    @Test
    void 응급실_40분이면_20점_중_13점3() {
        assertEquals(13.33, 20 * SafetyScoreService.part(40, 30, 60), 0.01);
        assertEquals(1, SafetyScoreService.part(30, 30, 60));
        assertEquals(0, SafetyScoreService.part(61, 30, 60));
    }

    @Test
    void 경찰은_가장_먼_캠핑장이_0점() {
        // 캠핑장 1: 모두 만점 시간 / 캠핑장 2: 경찰만 20분(가장 멂)
        Map<Long, Map<String, Double>> minutes = Map.of(
                1L, Map.of("er", 10.0, "fire", 3.0, "police", 3.0),
                2L, Map.of("er", 10.0, "fire", 3.0, "police", 20.0));
        Map<Long, Integer> saved = new HashMap<>();
        SafetyScoreService service = service(minutes, saved);

        SafetyScoreService.Summary s = service.recalculateAll();

        assertEquals(2, s.updated());
        assertEquals(100, saved.get(1L));
        // 경찰이 0점. 가짜 데이터에 약국·편의점이 없어 빠지므로 (25 + 25 + 0) ÷ 75 × 100 = 67
        assertEquals(67, saved.get(2L));
    }

    @Test
    void 약국은_가장_빠른_곳_만점_가장_먼_곳_0점_사이는_비례() {
        // 약국 5분 · 15분 · 25분 → 가장 빠른 5분 만점, 가장 먼 25분 0점, 15분은 절반
        Map<Long, Map<String, Double>> minutes = Map.of(
                1L, Map.of("er", 10.0, "pharmacy", 5.0),
                2L, Map.of("er", 10.0, "pharmacy", 15.0),
                3L, Map.of("er", 10.0, "pharmacy", 25.0));
        Map<Long, Integer> saved = new HashMap<>();
        service(minutes, saved).recalculateAll();

        // 응급실 25 + 약국 12.5 × 비율, 배점 합 37.5 로 100점 환산
        assertEquals(100, saved.get(1L));
        assertEquals(83, saved.get(2L));  // (25 + 6.25) ÷ 37.5 × 100 = 83.3
        assertEquals(67, saved.get(3L));  // 25 ÷ 37.5 × 100 = 66.7
    }

    @Test
    void 카카오를_못_부르면_아무것도_저장_안_함() {
        Map<Long, Integer> saved = new HashMap<>();
        ScoreMapper mapper = fakeMapper(Map.of(1L, Map.of("er", 10.0)), saved);
        DirectionsSource down = (a, b, c, d) -> Optional.empty();
        SafetyScoreService service = new SafetyScoreService(mapper, down,
                ScoreRules.points(ScoreRules.TIERS), ScoreRules.TIME, 0);

        assertTrue(service.recalculateAll().stopped());
        assertTrue(saved.isEmpty());
    }

    // ---- 가짜 ----

    // 시설 좌표의 위도 칸에 "분"을 넣어 두고, 가짜 길찾기가 그 값을 초로 돌려준다
    private SafetyScoreService service(Map<Long, Map<String, Double>> minutes, Map<Long, Integer> saved) {
        DirectionsSource fake = (fromLat, fromLng, toLat, toLng) ->
                Optional.of(new DirectionsSource.Result(0, "ok", 0, toLat * 60, List.of()));
        return new SafetyScoreService(fakeMapper(minutes, saved), fake,
                ScoreRules.points(ScoreRules.TIERS), ScoreRules.TIME, 0);
    }

    private ScoreMapper fakeMapper(Map<Long, Map<String, Double>> minutes, Map<Long, Integer> saved) {
        return new ScoreMapper() {
            @Override public List<ScoreCamp> findCamps() {
                return minutes.keySet().stream().sorted().map(id -> new ScoreCamp(id, "캠핑장" + id, 0, 0)).toList();
            }
            @Override public List<ScoreCampFacility> findCampFacilities(Long campId) {
                long[] seq = {campId * 100};
                return minutes.get(campId).entrySet().stream()
                        .map(e -> new ScoreCampFacility(seq[0]++, e.getKey(), e.getValue(), 0)).toList();
            }
            @Override public int updateDriveMin(Long campId, Long facilityId, int driveMin) { return 1; }
            @Override public int updateCampScore(Long campId, int score, Integer erDriveMin) {
                saved.put(campId, score);
                return 1;
            }
        };
    }
}
