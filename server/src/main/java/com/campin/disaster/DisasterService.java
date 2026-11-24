package com.campin.disaster;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.campin.alert.Alert;
import com.campin.alert.WarningSource;

import lombok.RequiredArgsConstructor;

// 재난지도 "지금" 정보: 캠핑장 격자의 최근 비(기상청 초단기실황) + 상류 반월촌교 수위(한강홍수통제소) + 인제군 기상특보 → 대피 단계
// 판단은 코드(DisasterRules)로만 한다. 못 받은 값은 비워 두고, 예시 값으로 채우지 않는다
@Service
@RequiredArgsConstructor
public class DisasterService {

    // 인북천 물빛테마공원 캠핑장 (client/public/disaster-map/data.json 의 campsite 점 = 대피 출발점, 캠핑장 안쪽)
    static final String PLACE = "인북천 물빛테마공원 캠핑장";
    static final double CAMP_LAT = 38.2252365;
    static final double CAMP_LNG = 128.2060817;

    private final RainSource rainSource;
    private final WarningSource warningSource;
    private final WaterLevelSource waterSource;

    // 10분마다 오는 수위가 1시간 넘게 안 바뀌면 관측소가 멈춘 것으로 보고 단계 판단에 쓰지 않는다 (화면에는 시각과 함께 보여 줌)
    static final java.time.Duration WATER_STALE = java.time.Duration.ofHours(1);

    public DisasterNowResponse now() {
        Optional<List<HourRain>> hours = rainSource.recentHours(CAMP_LAT, CAMP_LNG, 24);
        boolean rainOk = hours.isPresent() && !hours.get().isEmpty();
        Double r3 = rainOk ? sum(hours.get(), 3) : null;
        Double r12 = rainOk ? sum(hours.get(), 12) : null;
        Double r24 = rainOk ? sum(hours.get(), 24) : null;
        DisasterRules.Judgement rainJ = DisasterRules.judge(r3, r12, r24);

        DisasterNowResponse.Water water = waterSource.current().map(DisasterService::toWater).orElse(null);
        DisasterRules.Judgement waterJ = water == null || water.stale() ? new DisasterRules.Judgement(null, List.of())
                : DisasterRules.judgeWater(water.station(), water.wl(), water.attention(), water.alarm(), water.serious());

        Optional<List<Alert>> warnings = warningSource.activeWarnings();
        List<DisasterNowResponse.Warning> mine = warnings.orElse(List.of()).stream()
                .filter(DisasterService::coversInje)
                .map(a -> new DisasterNowResponse.Warning(a.category(), a.area(), a.message(),
                        a.issuedAt() == null ? null : a.issuedAt().toString()))
                .toList();
        // 호우특보는 비가 오기 전에 내리는 예보 기반 경고 → 비·수위보다 먼저 단계를 올릴 수 있다 (2026-11-23)
        DisasterRules.Judgement warnJ = DisasterRules.judgeWarnings(
                warnings.isPresent() ? mine.stream().map(DisasterNowResponse.Warning::category).toList() : null);
        DisasterRules.Judgement j = DisasterRules.higher(DisasterRules.higher(rainJ, waterJ), warnJ);

        return new DisasterNowResponse(PLACE, rainOk && r3 != null, rainOk ? hours.get().get(0).at() : null,
                r3, r12, r24, j.level(), j.reasons(), warnings.isPresent(), mine, water);
    }

    private static DisasterNowResponse.Water toWater(WaterLevel w) {
        boolean stale = w.observedAt() == null || w.observedAt().isBefore(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minus(WATER_STALE));
        Integer lv = stale ? null : DisasterRules.judgeWater(w.station(), w.wl(), w.attention(), w.alarm(), w.serious()).level();
        return new DisasterNowResponse.Water(w.station(), w.observedAt(), w.wl(), w.attention(), w.alarm(), w.serious(), lv, stale);
    }

    // 최근 n 시간 합. 그 안에 한 시간이라도 값이 없으면 null
    static Double sum(List<HourRain> hours, int n) {
        if (hours.size() < n) {
            return null;
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            Double mm = hours.get(i).mm();
            if (mm == null) {
                return null;
            }
            total += mm;
        }
        return Math.round(total * 10) / 10.0;
    }

    // 캠핑장(강원 인제군)에 걸린 특보인지: 강원 특보 중 구역에 "인제"가 있거나(예: 인제평지), 구역 없이 강원 전체에 낸 것
    // KmaWarningParser 가 만든 message 는 "강원 인제평지, 양구평지에 호우주의보가 …" 처럼 구역 전체를 담는다
    static boolean coversInje(Alert a) {
        if (!"warning".equals(a.kind()) || !a.active() || !"강원".equals(a.sido())) {
            return false;
        }
        String msg = a.message() == null ? "" : a.message();
        return msg.contains("인제") || msg.startsWith("강원에 ");
    }
}
