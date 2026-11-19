package com.campin.score;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campin.route.DirectionsSource;

// 안전점수 일괄 계산: 카카오 길찾기 차 "소요시간(분)"만으로 점수를 매긴다 (직선거리·score_weights 는 쓰지 않는다)
// 1) 모든 캠핑장에서 연결 시설(camp_facility, 종류마다 1곳)까지 카카오 소요시간을 받는다
// 2) 기준이 "가장 빠른·가장 먼 캠핑장"인 종류(경찰·약국·편의점)는 1) 이 다 끝나야 그 값을 알 수 있다
// 3) 점수 = Σ(배점 × 시간 비율) ÷ Σ(계산한 종류의 배점) × 100  — 배점은 ScoreRules 의 ROC 가중치
// 4) 화면의 분(camp_facility.drive_min, camp.er_drive_min)도 카카오 값으로 고친다. km(distance_km)는 그대로
// 한 캠핑장에서 하나라도 길을 못 찾으면 그 캠핑장은 건너뛴다 (추정 값과 카카오 값이 섞이지 않게)
// 카카오를 아예 부를 수 없게 되면 아무것도 쓰지 않고 멈춘다 (가장 먼 곳 기준이 틀어지지 않게)
@Service
public class SafetyScoreService {

    private static final Logger log = LoggerFactory.getLogger(SafetyScoreService.class);
    private static final int TOO_CLOSE = 104;  // 카카오: 출발지·도착지가 5m 이내라 경로 없음 → 0분으로 본다

    private final ScoreMapper mapper;
    private final DirectionsSource directions;
    private final Map<String, Double> points;               // 종류 → 배점 (ROC)
    private final Map<String, ScoreRules.TimeRule> timeRules;
    private final long callIntervalMs;

    // 스프링이 쓰는 생성자: 기준은 ScoreRules 상수
    // @Autowired: 생성자가 둘이라 스프링에게 어느 것을 쓸지 알려 준다
    @Autowired
    public SafetyScoreService(ScoreMapper mapper, DirectionsSource directions) {
        this(mapper, directions, ScoreRules.points(ScoreRules.TIERS), ScoreRules.TIME, ScoreRules.CALL_INTERVAL_MS);
    }

    // 테스트용: 기준을 바꿔 넣는다
    SafetyScoreService(ScoreMapper mapper, DirectionsSource directions, Map<String, Double> points,
                       Map<String, ScoreRules.TimeRule> timeRules, long callIntervalMs) {
        this.mapper = mapper;
        this.directions = directions;
        this.points = points;
        this.timeRules = timeRules;
        this.callIntervalMs = callIntervalMs;
    }

    // 계산 결과 요약 (로그·테스트용)
    // stopped: 카카오를 부를 수 없어서(키 없음·호출 실패·한도 초과) 중간에 멈췄는지
    public record Summary(int camps, int updated, int skipped, boolean stopped) {
    }

    // 카카오를 부를 수 없을 때 → 남은 캠핑장도 다 실패할 것이므로 멈춘다
    static class KakaoUnavailable extends RuntimeException {
    }

    // 캠핑장 하나의 카카오 소요시간 (1단계 결과)
    private record CampTimes(ScoreCamp camp, Map<Long, Double> byFacility, Map<String, Double> byType) {
    }

    public Summary recalculateAll() {
        // 기준이 잘못됐으면 시작하지 않는다 (기준값은 팀이 정한 값만 쓴다)
        for (var e : timeRules.entrySet()) {
            if (!e.getValue().valid() || !points.containsKey(e.getKey())) {
                log.error("안전점수: {} 의 기준이 잘못돼 계산하지 않습니다 (만점 분 < 0점 분, 배점이 있어야 함).", e.getKey());
                return new Summary(0, 0, 0, false);
            }
        }
        List<String> noRule = points.keySet().stream().filter(t -> !timeRules.containsKey(t)).toList();
        if (noRule.size() == points.size()) {
            log.error("안전점수: ScoreRules.TIME 분 기준이 비어 있어 계산하지 않습니다.");
            return new Summary(0, 0, 0, false);
        }
        if (!noRule.isEmpty()) {
            log.warn("안전점수: 분 기준이 없어 빼고 계산합니다 (남은 배점으로 100점 환산): {}", noRule);
        }
        log.info("안전점수: 배점 {}", points);

        // 1) 카카오 소요시간 모으기
        List<ScoreCamp> camps = mapper.findCamps();
        List<CampTimes> times = new ArrayList<>();
        int skipped = 0;
        for (ScoreCamp camp : camps) {
            try {
                CampTimes t = collect(camp);
                if (t == null) skipped++;
                else times.add(t);
            } catch (KakaoUnavailable e) {
                log.error("안전점수: 카카오 길찾기를 부를 수 없어 멈춥니다 ({} / {} 곳 받음, 아무것도 저장 안 함). 키·한도를 확인하고 다시 실행하세요.",
                        times.size(), camps.size());
                return new Summary(camps.size(), 0, skipped, true);
            }
        }

        // 2) 정해진 숫자가 없는 기준은 모은 시간 중 가장 짧은(만점)·가장 긴(0점) 값
        Map<String, Double> fullMin = limits(times, true);
        Map<String, Double> zeroMin = limits(times, false);
        log.info("안전점수: 만점 분 기준 {} / 0점 분 기준 {}", fullMin, zeroMin);

        // 3) 점수 매기고 저장
        int updated = 0;
        for (CampTimes t : times) {
            Double score = score(t.byType(), fullMin, zeroMin);
            if (score == null) {
                skipped++;
                continue;
            }
            t.byFacility().forEach((id, min) -> mapper.updateDriveMin(t.camp().getId(), id, toMin(min)));
            Double er = t.byType().get("er");
            mapper.updateCampScore(t.camp().getId(), (int) Math.round(score), er == null ? null : toMin(er));
            updated++;
        }
        log.info("안전점수: {} 곳 중 {} 곳 계산, {} 곳 건너뜀", camps.size(), updated, skipped);
        return new Summary(camps.size(), updated, skipped, false);
    }

    // 캠핑장 하나의 시설별 카카오 소요시간. 하나라도 길을 못 찾으면 null
    private CampTimes collect(ScoreCamp camp) {
        Map<Long, Double> byFacility = new HashMap<>();
        Map<String, Double> byType = new HashMap<>();  // 종류별로 가장 빠른 분
        for (ScoreCampFacility f : mapper.findCampFacilities(camp.getId())) {
            Double min = minutes(camp, f);
            if (min == null) {
                log.warn("안전점수: {}({}) 시설 {} 길을 찾지 못해 건너뜁니다.", camp.getName(), camp.getId(), f.getFacilityId());
                return null;
            }
            byFacility.put(f.getFacilityId(), min);
            byType.merge(f.getType(), min, Math::min);
        }
        return new CampTimes(camp, byFacility, byType);
    }

    // 종류 → 만점 분(full=true) 또는 0점 분(full=false)
    // 정해진 값이 있으면 그 값, 없으면(null) 캠핑장들 중 가장 짧은(만점)·가장 긴(0점) 시간
    private Map<String, Double> limits(List<CampTimes> times, boolean full) {
        Map<String, Double> result = new HashMap<>();
        timeRules.forEach((type, rule) -> {
            Double fixed = full ? rule.fullMin() : rule.zeroMin();
            if (fixed != null) {
                result.put(type, fixed);
                return;
            }
            var values = times.stream().map(t -> t.byType().get(type)).filter(v -> v != null);
            (full ? values.min(Double::compare) : values.max(Double::compare))
                    .ifPresent(v -> result.put(type, v));
        });
        return result;
    }

    // 100점 만점 점수. 계산할 수 있는 종류가 하나도 없으면 null
    Double score(Map<String, Double> minutesByType, Map<String, Double> fullMin, Map<String, Double> zeroMin) {
        double got = 0;
        double total = 0;
        for (var e : points.entrySet()) {
            String type = e.getKey();
            Double v = minutesByType.get(type);
            Double full = fullMin.get(type);
            Double zero = zeroMin.get(type);
            if (v == null || full == null || zero == null) continue;  // 기준·데이터가 없는 종류는 빼고 100점 환산
            total += e.getValue();
            got += e.getValue() * part(v, full, zero);
        }
        return total == 0 ? null : got / total * 100;
    }

    // 카카오 소요시간(분). 길을 못 찾으면 null, 카카오를 아예 부를 수 없으면 KakaoUnavailable
    private Double minutes(ScoreCamp camp, ScoreCampFacility f) {
        pause();
        DirectionsSource.Result r = directions.find(camp.getLat(), camp.getLng(), f.getLat(), f.getLng())
                .orElseThrow(KakaoUnavailable::new);
        if (r.resultCode() == 0) return r.durationS() / 60.0;
        if (r.resultCode() == TOO_CLOSE) return 0.0;
        return null;
    }

    private void pause() {
        if (callIntervalMs == 0) return;
        try {
            Thread.sleep(callIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KakaoUnavailable();
        }
    }

    // 시간 비율: full 분 이하면 1, zero 분 이상이면 0, 그 사이는 시간에 비례
    // (캠핑장끼리 비교하는데 모두 같은 시간이거나 모두 만점 분 안이면 zero ≤ full → 전부 1)
    static double part(double v, double full, double zero) {
        if (v <= full) return 1;
        if (v >= zero) return 0;
        return (zero - v) / (zero - full);
    }

    // 화면에 보일 분: 반올림, 최소 1분 (0분으로 보이지 않게)
    static int toMin(double minutes) {
        return Math.max(1, (int) Math.round(minutes));
    }
}
