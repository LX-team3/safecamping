package com.campin.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// 인원·날짜 규칙 확인. "오늘"은 2026-11-06 (한국 시간)으로 고정한다
class TripServiceTest {

    private final TripService service = new TripService(
            Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneId.of("Asia/Seoul")));

    private TripCheckRequest req(String in, String out, Integer adults, Integer kids, List<String> ages) {
        return new TripCheckRequest(in == null ? null : LocalDate.parse(in), out == null ? null : LocalDate.parse(out),
                adults, kids, ages);
    }

    @Test
    void 몇_박인지_계산한다() {
        TripCheckResponse res = service.check(req("2026-11-14", "2026-11-16", 2, 1, List.of("preschool")));
        assertEquals(2, res.nights());
        assertEquals(List.of("preschool"), res.childAges());
    }

    @Test
    void 오늘_체크인은_된다() {
        assertEquals(1, service.check(req("2026-11-06", "2026-11-07", 1, 0, null)).nights());
    }

    @Test
    void 날짜_규칙() {
        assertThrows(InvalidRequestException.class, () -> service.check(req(null, "2026-11-15", 1, 0, null)));
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-05", "2026-11-07", 1, 0, null))); // 지난 날
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-14", "2026-11-14", 1, 0, null))); // 당일 체크아웃
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-14", "2026-11-13", 1, 0, null)));
    }

    @Test
    void 인원_규칙() {
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-14", "2026-11-15", 0, 1, List.of("infant")))); // 어른 없음
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-14", "2026-11-15", 11, 0, null)));
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-14", "2026-11-15", 2, 2, List.of("infant")))); // 나이대 1개 부족
        assertThrows(InvalidRequestException.class, () -> service.check(req("2026-11-14", "2026-11-15", 2, 1, List.of("adult"))));
    }
}
