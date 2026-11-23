package com.campin.disaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.campin.alert.Alert;
import com.campin.alert.WarningSource;

// 재난지도 단계 판단 확인. 기상청은 부르지 않고 RainSource·WarningSource 자리에 가짜 결과를 넣는다
class DisasterServiceTest {

    private static final LocalDateTime LATEST = LocalDateTime.of(2026, 7, 15, 15, 0);
    private static final WarningSource NO_WARNING = () -> Optional.of(List.of());
    private static final WaterLevelSource NO_WATER = Optional::empty;

    // 반월촌교 기준수위 (한강홍수통제소 info: 관심 6.7 · 주의보 없음 · 경보 8.4 · 심각 9.4 · 계획홍수위 9.7)
    private static WaterLevelSource water(double wl, LocalDateTime at) {
        return () -> Optional.of(new WaterLevel("인제군(반월촌교)", at, wl, 6.7, 8.4, 9.4, 9.7));
    }

    private static LocalDateTime justNow() {
        return LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusMinutes(10);
    }

    // 최근부터 거꾸로 시간당 비 (0번 = 가장 최근)
    private static RainSource rain(Double... mm) {
        List<HourRain> list = new ArrayList<>();
        for (int i = 0; i < mm.length; i++) {
            list.add(new HourRain(LATEST.minusHours(i), mm[i]));
        }
        return (lat, lng, hours) -> Optional.of(list);
    }

    // 24시간 모두 같은 비
    private static RainSource every(double mm) {
        Double[] a = new Double[24];
        java.util.Arrays.fill(a, mm);
        return rain(a);
    }

    private static Alert warning(String category, String message) {
        return new Alert("w", "warning", "강원", "강원", category, message, OffsetDateTime.parse("2026-07-15T14:00:00+09:00"), true);
    }

    @Test
    void 비가_없으면_안전() {
        DisasterNowResponse res = new DisasterService(every(0), NO_WARNING, NO_WATER).now();
        assertTrue(res.rainAvailable());
        assertEquals(0, res.level());
        assertEquals(0.0, res.rain3h());
        assertEquals(LATEST, res.observedAt());
    }

    @Test
    void 세시간_기준() {
        // 최근 3시간 20+20+20 = 60 → 준비, 30*3 = 90 → 대피, 38*3 = 114 → 즉시 대피
        assertEquals(1, new DisasterService(rain(20.0, 20.0, 20.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), NO_WARNING, NO_WATER).now().level());
        assertEquals(2, new DisasterService(rain(30.0, 30.0, 30.0), NO_WARNING, NO_WATER).now().level());
        assertEquals(3, new DisasterService(rain(38.0, 38.0, 38.0), NO_WARNING, NO_WATER).now().level());
        assertEquals(0, new DisasterService(rain(19.0, 20.0, 20.0), NO_WARNING, NO_WATER).now().level());  // 59mm 는 아직 안전
    }

    @Test
    void 열두시간_스물네시간_기준() {
        // 3시간은 기준 아래지만 12시간 동안 10mm 씩 = 120 ≥ 110 → 준비
        DisasterNowResponse res = new DisasterService(every(10), NO_WARNING, NO_WATER).now();
        assertEquals(30.0, res.rain3h());
        assertEquals(120.0, res.rain12h());
        assertEquals(240.0, res.rain24h());
        assertEquals(1, res.level());
        // 24시간 동안 13mm 씩 = 312 ≥ 310 → 즉시 대피 (12시간 156 은 경보 기준 아래)
        assertEquals(3, new DisasterService(every(13), NO_WARNING, NO_WATER).now().level());
    }

    @Test
    void 중간에_못_받은_시간이_있으면_그_합은_비운다() {
        Double[] a = new Double[24];
        java.util.Arrays.fill(a, 1.0);
        a[5] = null;  // 6시간 전 값 없음 → 3시간은 있고 12·24시간은 모름
        DisasterNowResponse res = new DisasterService(rain(a), NO_WARNING, NO_WATER).now();
        assertEquals(3.0, res.rain3h());
        assertNull(res.rain12h());
        assertNull(res.rain24h());
        assertEquals(0, res.level());
    }

    @Test
    void 기상청을_못_받으면_단계를_정하지_않는다() {
        DisasterNowResponse res = new DisasterService((lat, lng, h) -> Optional.empty(), Optional::empty, Optional::empty).now();
        assertFalse(res.rainAvailable());
        assertNull(res.level());
        assertNull(res.rain3h());
        assertFalse(res.warningAvailable());
        assertTrue(res.warnings().isEmpty());  // 예시 특보를 넣지 않는다
    }

    @Test
    void 인제에_걸린_특보만() {
        WarningSource src = () -> Optional.of(List.of(
                warning("호우주의보", "강원 인제평지, 양구평지에 호우주의보가 발효 중입니다. (기상청 특보 현황 기준)"),
                warning("건조주의보", "강원 동해평지, 삼척평지에 건조주의보가 발효 중입니다. (기상청 특보 현황 기준)"),
                warning("한파주의보", "강원에 한파주의보가 발효 중입니다. (기상청 특보 현황 기준)")));
        DisasterNowResponse res = new DisasterService(every(0), src, NO_WATER).now();
        assertTrue(res.warningAvailable());
        assertEquals(List.of("호우주의보", "한파주의보"), res.warnings().stream().map(DisasterNowResponse.Warning::category).toList());
    }

    // 2026-11-23: 호우특보는 예보 기반이라 비가 오기 전에 단계를 올린다
    @Test
    void 호우특보만으로_단계를_올린다() {
        WarningSource watch = () -> Optional.of(List.of(warning("호우주의보", "강원 인제평지에 호우주의보가 발효 중입니다.")));
        WarningSource alarm = () -> Optional.of(List.of(warning("호우경보", "강원 인제평지에 호우경보가 발효 중입니다.")));
        DisasterNowResponse r1 = new DisasterService(every(0), watch, NO_WATER).now();
        assertEquals(1, r1.level());
        assertEquals(List.of("기상청 호우주의보 발효"), r1.reasons());
        DisasterNowResponse r2 = new DisasterService(every(0), alarm, NO_WATER).now();
        assertEquals(2, r2.level());
        assertEquals(List.of("기상청 호우경보 발효"), r2.reasons());
    }

    @Test
    void 비가_특보보다_크면_비를_따른다() {
        WarningSource alarm = () -> Optional.of(List.of(warning("호우경보", "강원 인제평지에 호우경보가 발효 중입니다.")));
        assertEquals(3, new DisasterService(rain(38.0, 38.0, 38.0), alarm, NO_WATER).now().level());
        DisasterNowResponse same = new DisasterService(rain(30.0, 30.0, 30.0), alarm, NO_WATER).now();  // 둘 다 대피면 까닭도 둘 다
        assertEquals(2, same.level());
        assertEquals(2, same.reasons().size());
    }

    @Test
    void 호우가_아닌_특보는_단계와_상관없다() {
        WarningSource cold = () -> Optional.of(List.of(warning("한파주의보", "강원에 한파주의보가 발효 중입니다.")));
        assertEquals(0, new DisasterService(every(0), cold, NO_WATER).now().level());
    }

    @Test
    void 특보가_없다고_안전이라_하지_않는다() {
        // 비·수위를 모르면 특보가 없어도 단계를 정하지 않는다 (null = 미리 보기)
        assertNull(new DisasterService((lat, lng, h) -> Optional.empty(), NO_WARNING, NO_WATER).now().level());
        // 특보를 못 받아도 비로는 판단한다
        assertEquals(0, new DisasterService(every(0), Optional::empty, NO_WATER).now().level());
    }

    @Test
    void 기상청_값_읽기() {
        assertEquals(0.0, KmaRainClient.parseMm("0"));
        assertEquals(3.5, KmaRainClient.parseMm("3.5"));
        assertEquals(0.0, KmaRainClient.parseMm("강수없음"));
        assertNull(KmaRainClient.parseMm("-998.9"));  // 결측
        assertNull(KmaRainClient.parseMm("abc"));
    }

    @Test
    void 상류_수위_기준() {
        assertEquals(0, new DisasterService(every(0), NO_WARNING, water(4.77, justNow())).now().level());
        assertEquals(1, new DisasterService(every(0), NO_WARNING, water(6.7, justNow())).now().level());   // 관심
        assertEquals(2, new DisasterService(every(0), NO_WARNING, water(8.4, justNow())).now().level());   // 경보
        DisasterNowResponse res = new DisasterService(every(0), NO_WARNING, water(9.5, justNow())).now();  // 심각
        assertEquals(3, res.level());
        assertEquals(3, res.water().level());
        assertEquals(List.of("상류 인제군(반월촌교) 수위 9.5m ≥ 심각 수위 9.4m"), res.reasons());
    }

    @Test
    void 비와_수위_중_높은_단계() {
        // 비는 3시간 90mm(대피 2), 수위는 관심(준비 1) → 2, 까닭은 비 쪽만
        DisasterNowResponse res = new DisasterService(rain(30.0, 30.0, 30.0), NO_WARNING, water(7.0, justNow())).now();
        assertEquals(2, res.level());
        assertEquals(1, res.reasons().size());
        // 비를 못 받아도 수위로 단계를 정한다
        assertEquals(2, new DisasterService((lat, lng, h) -> Optional.empty(), NO_WARNING, water(8.5, justNow())).now().level());
    }

    @Test
    void 오래된_수위는_판단에_안_쓴다() {
        DisasterNowResponse res = new DisasterService(every(0), NO_WARNING, water(9.5, justNow().minusHours(2))).now();
        assertEquals(0, res.level());
        assertTrue(res.water().stale());
        assertNull(res.water().level());
        assertEquals(9.5, res.water().wl());  // 값은 시각과 함께 보여 준다
    }

    @Test
    void 수위_값_읽기() {
        assertEquals(4.77, HrfcoWaterClient.parseM("4.77"));
        assertNull(HrfcoWaterClient.parseM(" "));
        assertNull(HrfcoWaterClient.parseM(null));
        assertNull(HrfcoWaterClient.parseM("-"));
    }
}
