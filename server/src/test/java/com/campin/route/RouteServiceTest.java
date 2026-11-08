package com.campin.route;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// DB·카카오 없이 경로 규칙만 확인한다 (가짜 mapper · 가짜 길찾기)
class RouteServiceTest {

    private static final RouteCamp CAMP = new RouteCamp(1L, "설하캠핑장", 38.14, 128.205);
    private static final List<RouteFacility> FACILITIES = List.of(
            new RouteFacility(11L, "fire", "소방서", 38.165, 128.215, 4.0, 5, true),
            new RouteFacility(12L, "er", "응급실", 38.066, 128.17, 12.9, 17, true));

    private final RouteMapper mapper = new RouteMapper() {
        @Override public RouteCamp findCamp(Long campId) { return campId == 1L ? CAMP : null; }
        @Override public List<RouteFacility> findFacilities(Long campId) { return FACILITIES; }
    };

    // 카카오 형식: [경도, 위도, 경도, 위도]. 12345m, 1530초
    private static final DirectionsSource.Result OK =
            new DirectionsSource.Result(0, "길찾기 성공", 12345, 1530, List.of(128.205, 38.14, 128.21, 38.15));

    @Test
    void 카카오_경로를_위도_경도_순서와_km_분으로_바꾼다() {
        RouteService service = new RouteService(mapper, (a, b, c, d) -> Optional.of(OK));
        RouteResponse res = service.routes(1L);

        assertEquals("kakao", res.source());
        RouteResponse.Route r = res.routes().get(0);
        assertTrue(r.ok());
        assertEquals(12.3, r.distanceKm());
        assertEquals(26, r.durationMin());                       // 1530초 = 25.5분 → 26
        assertArrayEquals(new double[] { 38.14, 128.205 }, r.path().get(0));
        assertArrayEquals(new double[] { 38.15, 128.21 }, r.path().get(1));
    }

    @Test
    void 키가_없거나_호출에_실패하면_직선과_DB_시간() {
        RouteService service = new RouteService(mapper, (a, b, c, d) -> Optional.empty());
        RouteResponse res = service.routes(1L);

        assertEquals("straight", res.source());
        RouteResponse.Route r = res.routes().get(0);
        assertFalse(r.ok());
        assertEquals(5, r.durationMin());
        assertEquals(4.0, r.distanceKm());
        assertEquals(2, r.path().size());
        assertArrayEquals(new double[] { 38.14, 128.205 }, r.path().get(0));   // 캠핑장
        assertArrayEquals(new double[] { 38.165, 128.215 }, r.path().get(1));  // 시설
    }

    @Test
    void 길을_못_찾은_시설만_직선() {
        DirectionsSource.Result fail = new DirectionsSource.Result(104, "출발지와 도착지가 너무 가깝습니다", 0, 0, List.of());
        RouteService service = new RouteService(mapper,
                (a, b, toLat, d) -> Optional.of(toLat == 38.165 ? fail : OK));
        RouteResponse res = service.routes(1L);

        assertEquals("kakao", res.source());
        assertFalse(res.routes().get(0).ok());
        assertTrue(res.routes().get(1).ok());
    }

    @Test
    void 두_번째_요청은_저장해_둔_결과를_쓴다() {
        AtomicInteger calls = new AtomicInteger();
        RouteService service = new RouteService(mapper, (a, b, c, d) -> {
            calls.incrementAndGet();
            return Optional.of(OK);
        });
        service.routes(1L);
        service.routes(1L);
        assertEquals(2, calls.get());  // 시설 2곳 × 처음 한 번만
    }

    @Test
    void 실패한_결과는_저장하지_않고_다시_시도한다() {
        AtomicInteger calls = new AtomicInteger();
        RouteService service = new RouteService(mapper, (a, b, c, d) -> {
            calls.incrementAndGet();
            return Optional.empty();
        });
        service.routes(1L);
        service.routes(1L);
        assertEquals(4, calls.get());
    }

    @Test
    void 없는_캠핑장이면_400() {
        RouteService service = new RouteService(mapper, (a, b, c, d) -> Optional.of(OK));
        assertThrows(InvalidRequestException.class, () -> service.routes(99L));
    }

    @Test
    void 카카오_응답의_구간들을_순서대로_이어_붙인다() {
        var res = new KakaoDirectionsClient.DirectionsResponse(List.of(new KakaoDirectionsClient.Route(0, "성공",
                new KakaoDirectionsClient.Summary(1000, 120),
                List.of(new KakaoDirectionsClient.Section(List.of(
                                new KakaoDirectionsClient.Road(List.of(1.0, 2.0)),
                                new KakaoDirectionsClient.Road(List.of(3.0, 4.0)))),
                        new KakaoDirectionsClient.Section(List.of(
                                new KakaoDirectionsClient.Road(List.of(5.0, 6.0))))))));
        DirectionsSource.Result r = KakaoDirectionsClient.toResult(res);
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), r.vertexes());
        assertEquals(1000, r.distanceM());
        assertEquals(120, r.durationS());
    }
}
