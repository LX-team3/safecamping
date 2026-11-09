package com.campin.route;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

// 캠핑장 상세 · 경로 지도: 캠핑장 → 응급실·소방서·경찰서 자동차 경로
// 카카오 길찾기를 시설마다 한 번씩 (동시에) 부르고, 결과는 캠핑장별로 하루 동안 저장해 둔다
// (캠핑장·시설 위치는 거의 안 바뀌고, 카카오 하루 호출 한도를 아끼기 위해. DB 데이터를 고쳤으면 서버를 다시 켠다)
@Service
@RequiredArgsConstructor
public class RouteService {

    static final Duration CACHE_FOR = Duration.ofHours(24);

    private final RouteMapper routeMapper;
    private final DirectionsSource directions;

    // 캠핑장 id → 저장해 둔 결과. 전부 성공한 결과만 저장한다 (실패한 것은 다음에 다시 시도)
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(RouteResponse response, Instant at) {
    }

    public RouteResponse routes(Long campId) {
        Cached c = cache.get(campId);
        if (c != null && Instant.now().isBefore(c.at().plus(CACHE_FOR))) {
            return c.response();
        }

        RouteCamp camp = routeMapper.findCamp(campId);
        if (camp == null) {
            throw new InvalidRequestException("없는 캠핑장입니다.");
        }
        List<RouteFacility> facilities = routeMapper.findFacilities(campId);

        // 시설들을 동시에 부른다 → 기다리는 시간은 한 번 부를 때와 비슷하다
        List<CompletableFuture<RouteResponse.Route>> jobs = facilities.stream()
                .map(f -> CompletableFuture.supplyAsync(() -> routeOf(camp, f)))
                .toList();
        List<RouteResponse.Route> routes = jobs.stream().map(CompletableFuture::join).toList();

        boolean anyOk = routes.stream().anyMatch(RouteResponse.Route::ok);
        RouteResponse res = new RouteResponse(anyOk ? "kakao" : "straight", routes);
        if (!routes.isEmpty() && routes.stream().allMatch(RouteResponse.Route::ok)) {
            cache.put(campId, new Cached(res, Instant.now()));
        }
        return res;
    }

    private RouteResponse.Route routeOf(RouteCamp camp, RouteFacility f) {
        DirectionsSource.Result r = directions.find(camp.getLat(), camp.getLng(), f.getLat(), f.getLng())
                .orElse(null);
        List<double[]> path = r == null ? List.of() : toPath(r.vertexes());

        // 키 없음 · 호출 실패 · 길 못 찾음(result_code ≠ 0) · 좌표 없음 → 직선 + DB 시간
        if (r == null || r.resultCode() != 0 || path.size() < 2) {
            List<double[]> straight = List.of(new double[] { camp.getLat(), camp.getLng() },
                    new double[] { f.getLat(), f.getLng() });
            return new RouteResponse.Route(f.getId(), f.getType(), f.getName(), false, f.isDummy(),
                    f.getDistanceKm(), f.getDriveMin(), straight);
        }
        return new RouteResponse.Route(f.getId(), f.getType(), f.getName(), true, f.isDummy(),
                Math.round(r.distanceM() / 100.0) / 10.0,     // m → km, 소수 한 자리
                (int) Math.round(r.durationS() / 60.0),        // 초 → 분 (반올림)
                path);
    }

    // 카카오 [경도, 위도, 경도, 위도, ...] → [[위도, 경도], ...]
    static List<double[]> toPath(List<Double> vertexes) {
        List<double[]> path = new ArrayList<>();
        if (vertexes == null) return path;
        for (int i = 0; i + 1 < vertexes.size(); i += 2) {
            path.add(new double[] { vertexes.get(i + 1), vertexes.get(i) });
        }
        return path;
    }
}
