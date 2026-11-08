package com.campin.route;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오모빌리티 자동차 길찾기 호출 (출발지 → 목적지 한 곳)
// 키: 환경변수 KAKAO_REST_KEY (카카오 개발자 콘솔의 "REST API 키"). 코드·application.properties·프론트에 키를 쓰지 않는다
// 지도용 JavaScript 키와 다른 키다 — REST 키가 브라우저에 노출되면 남이 우리 한도로 호출할 수 있어서 서버에서만 부른다
@Component
public class KakaoDirectionsClient implements DirectionsSource {

    private static final Logger log = LoggerFactory.getLogger(KakaoDirectionsClient.class);
    private static final String URL = "https://apis-navi.kakaomobility.com/v1/directions";
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);  // 실패하면 1분 동안은 다시 부르지 않는다 (카카오 장애 때 매 요청마다 기다리지 않게)

    private final String restKey;
    private final RestClient http;
    private volatile Instant failedAt;  // 여러 요청이 동시에 읽고 쓰므로 volatile

    // ${KAKAO_REST_KEY:} = 환경변수를 읽고, 없으면 빈 문자열
    public KakaoDirectionsClient(@Value("${KAKAO_REST_KEY:}") String restKey) {
        this.restKey = restKey.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));  // 카카오가 느려도 경로 화면이 오래 멈추지 않게
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.restKey.isEmpty()) {
            log.warn("KAKAO_REST_KEY 환경변수가 없어 경로는 직선으로 보여 줍니다.");
        }
    }

    @Override
    public Optional<Result> find(double fromLat, double fromLng, double toLat, double toLng) {
        if (restKey.isEmpty()) {
            return Optional.empty();
        }
        Instant failed = failedAt;
        if (failed != null && Instant.now().isBefore(failed.plus(RETRY_AFTER))) {
            return Optional.empty();
        }
        try {
            // 카카오는 "경도,위도" 순서. Locale.ROOT: 소수점이 쉼표로 바뀌는 지역 설정을 막는다
            URI uri = URI.create(String.format(Locale.ROOT,
                    "%s?origin=%.6f,%.6f&destination=%.6f,%.6f&priority=RECOMMEND",
                    URL, fromLng, fromLat, toLng, toLat));
            DirectionsResponse res = http.get().uri(uri)
                    .header("Authorization", "KakaoAK " + restKey)
                    .retrieve()
                    .body(DirectionsResponse.class);
            Result result = toResult(res);
            failedAt = null;
            return Optional.of(result);
        } catch (RuntimeException e) {
            log.warn("카카오 길찾기를 받지 못했습니다: {}", e.getMessage());
            failedAt = Instant.now();
            return Optional.empty();
        }
    }

    // 카카오 응답 → 필요한 값만. 경로가 여러 구간(sections)·도로(roads)로 나뉘어 오므로 vertexes 를 순서대로 이어 붙인다
    static Result toResult(DirectionsResponse res) {
        if (res == null || res.routes() == null || res.routes().isEmpty()) {
            throw new IllegalStateException("응답 형식이 다릅니다");
        }
        Route route = res.routes().get(0);
        List<Double> vertexes = new ArrayList<>();
        if (route.sections() != null) {
            for (Section s : route.sections()) {
                if (s.roads() == null) continue;
                for (Road r : s.roads()) {
                    if (r.vertexes() != null) vertexes.addAll(r.vertexes());
                }
            }
        }
        double distance = route.summary() == null ? 0 : route.summary().distance();
        double duration = route.summary() == null ? 0 : route.summary().duration();
        return new Result(route.resultCode(), route.resultMsg(), distance, duration, vertexes);
    }

    // ---- 카카오 응답 모양 (필요한 칸만). 모르는 칸은 무시한다 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DirectionsResponse(List<Route> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(@JsonProperty("result_code") int resultCode,
                 @JsonProperty("result_msg") String resultMsg,
                 Summary summary,
                 List<Section> sections) {
    }

    // distance: 미터, duration: 초
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(double distance, double duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Section(List<Road> roads) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Road(List<Double> vertexes) {
    }
}
