package com.campin.score;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campin.route.DirectionsSource;

// 안전점수 일괄 계산: 카카오 길찾기의 "소요시간(분)"으로 점수를 매긴다 (예전 SQL 은 직선거리로 매겼다)
// 1) 캠핑장에 연결된 시설(camp_facility, 종류마다 직선으로 가장 가까운 1곳)까지 카카오 소요시간을 받는다
// 2) ScoreRules.TIME 에 있는 종류는 그 분으로 부분점수, 없는 종류(보안등·CCTV 등)는 예전처럼 직선거리로 부분점수
// 3) 점수 = Σ(가중치 × 부분점수) ÷ Σ(데이터가 있는 종류의 가중치) × 100  — merge_to_public.sql 8 단계와 같은 모양
// 4) 화면의 분(camp_facility.drive_min, camp.er_drive_min)도 카카오 값으로 고친다. km(distance_km)는 직선거리 그대로
// 한 캠핑장에서 하나라도 길을 못 찾으면 그 캠핑장은 아무것도 고치지 않는다 (추정 값과 카카오 값이 섞이지 않게)
@Service
public class SafetyScoreService {

    private static final Logger log = LoggerFactory.getLogger(SafetyScoreService.class);
    private static final int TOO_CLOSE = 104;  // 카카오: 출발지·도착지가 5m 이내라 경로 없음 → 0분으로 본다

    private final ScoreMapper mapper;
    private final DirectionsSource directions;
    private final Map<String, ScoreRules.TimeRule> timeRules;
    private final long callIntervalMs;

    // 스프링이 쓰는 생성자: 기준은 ScoreRules 상수
    // @Autowired: 생성자가 둘이라 스프링에게 어느 것을 쓸지 알려 준다
    @Autowired
    public SafetyScoreService(ScoreMapper mapper, DirectionsSource directions) {
        this(mapper, directions, ScoreRules.TIME, ScoreRules.CALL_INTERVAL_MS);
    }

    // 테스트용: 기준을 바꿔 넣는다
    SafetyScoreService(ScoreMapper mapper, DirectionsSource directions,
                       Map<String, ScoreRules.TimeRule> timeRules, long callIntervalMs) {
        this.mapper = mapper;
        this.directions = directions;
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

    public Summary recalculateAll() {
        // 분 기준이 없거나 잘못됐으면 시작하지 않는다 (기준값은 팀이 정한 값만 쓴다)
        if (timeRules.isEmpty()) {
            log.error("안전점수: ScoreRules.TIME 분 기준이 비어 있어 계산하지 않습니다.");
            return new Summary(0, 0, 0, false);
        }
        for (var e : timeRules.entrySet()) {
            if (!e.getValue().valid()) {
                log.error("안전점수: {} 의 분 기준이 잘못돼 계산하지 않습니다 (만점 분 < 0점 분 이어야 함).", e.getKey());
                return new Summary(0, 0, 0, false);
            }
        }

        List<ScoreWeight> weights = mapper.findWeights();
        log.info("안전점수: 소요시간 기준 {} / 직선거리 기준 {}", timeRules.keySet(),
                weights.stream().map(ScoreWeight::getType).filter(t -> !timeRules.containsKey(t)).toList());

        List<ScoreCamp> camps = mapper.findCamps();
        int updated = 0;
        int skipped = 0;
        for (ScoreCamp camp : camps) {
            try {
                if (scoreCamp(camp, weights)) updated++;
                else skipped++;
            } catch (KakaoUnavailable e) {
                log.error("안전점수: 카카오 길찾기를 부를 수 없어 멈춥니다 ({} / {} 곳 계산함). 키·한도를 확인하고 다시 실행하세요.",
                        updated, camps.size());
                return new Summary(camps.size(), updated, skipped, true);
            }
        }
        log.info("안전점수: {} 곳 중 {} 곳 계산, {} 곳 건너뜀", camps.size(), updated, skipped);
        return new Summary(camps.size(), updated, skipped, false);
    }

    // 캠핑장 하나. 고쳤으면 true
    boolean scoreCamp(ScoreCamp camp, List<ScoreWeight> weights) {
        // 1) 연결 시설마다 카카오 소요시간(분)
        List<ScoreCampFacility> facilities = mapper.findCampFacilities(camp.getId());
        Map<Long, Double> minutesById = new HashMap<>();
        Map<String, Double> minutesByType = new HashMap<>();  // 종류별로 가장 빠른 분
        for (ScoreCampFacility f : facilities) {
            Double min = minutes(camp, f);
            if (min == null) {
                log.warn("안전점수: {}({}) 시설 {} 길을 찾지 못해 건너뜁니다.", camp.getName(), camp.getId(), f.getFacilityId());
                return false;
            }
            minutesById.put(f.getFacilityId(), min);
            minutesByType.merge(f.getType(), min, Math::min);
        }

        // 2) 거리 기준 종류용 직선거리
        Map<String, Double> distByType = mapper.findNearest(camp.getLat(), camp.getLng()).stream()
                .collect(Collectors.toMap(ScoreNearest::getType, ScoreNearest::getDistM));

        // 3) 부분점수 합
        double got = 0;
        double total = 0;
        for (ScoreWeight w : weights) {
            ScoreRules.TimeRule rule = timeRules.get(w.getType());
            Double v = rule != null ? minutesByType.get(w.getType()) : distByType.get(w.getType());
            if (v == null) continue;  // 데이터가 없는 종류는 빼고 100점으로 환산
            total += w.getWeight();
            got += w.getWeight() * (rule != null
                    ? part(v, rule.fullMin(), rule.zeroMin())
                    : part(v, w.getFullDistM(), w.getZeroDistM()));
        }
        if (total == 0) return false;  // 주변 시설 데이터가 하나도 없음

        // 4) 계산이 다 끝난 뒤에만 쓴다
        minutesById.forEach((id, min) -> mapper.updateDriveMin(camp.getId(), id, toMin(min)));
        Double er = minutesByType.get("er");
        mapper.updateCampScore(camp.getId(), (int) Math.round(got / total * 100), er == null ? null : toMin(er));
        return true;
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

    // v 가 full 이하면 1, zero 이상이면 0, 그 사이는 비례 (작을수록 좋은 값: 분 또는 m)
    static double part(double v, double full, double zero) {
        if (v <= full) return 1;
        if (v >= zero) return 0;
        return (zero - v) / (zero - full);
    }

    // 화면에 보일 분: 반올림, 최소 1분 (예전 SQL 의 drive_min 과 같이 0분으로 보이지 않게)
    static int toMin(double minutes) {
        return Math.max(1, (int) Math.round(minutes));
    }
}
