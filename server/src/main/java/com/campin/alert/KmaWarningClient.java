package com.campin.alert;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 기상청 기상특보 조회서비스 · 특보현황(getPwnStatus) 호출
// 전국 "지금 발효 중인 특보"를 한 번에 주므로, 10분에 한 번만 부르고 그 사이에는 저장해 둔 결과를 쓴다 (하루 호출 한도 10,000회)
// 키: 환경변수 KMA_SERVICE_KEY (공공데이터포털 "디코딩 키"). 코드·application.properties 에 키를 쓰지 않는다
@Component
public class KmaWarningClient implements WarningSource {

    private static final Logger log = LoggerFactory.getLogger(KmaWarningClient.class);
    private static final String URL = "https://apis.data.go.kr/1360000/WthrWrnInfoService/getPwnStatus";
    private static final Duration CACHE_FOR = Duration.ofMinutes(10);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);  // 실패하면 1분 동안은 다시 부르지 않는다 (기상청 장애 때 매 요청마다 기다리지 않게)

    private final String serviceKey;
    private final RestClient http;

    // 마지막으로 받은 결과와 시각. 여러 요청이 동시에 와도 한 번만 부르도록 activeWarnings 는 synchronized
    private List<Alert> cached;
    private Instant cachedAt;
    private Instant failedAt;

    // ${KMA_SERVICE_KEY:} = 환경변수를 읽고, 없으면 빈 문자열
    public KmaWarningClient(@Value("${KMA_SERVICE_KEY:}") String serviceKey) {
        this.serviceKey = serviceKey.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));  // 기상청이 느려도 알림 화면이 오래 멈추지 않게
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.serviceKey.isEmpty()) {
            log.warn("KMA_SERVICE_KEY 환경변수가 없어 기상특보는 더미(예시)를 씁니다.");
        }
    }

    @Override
    public synchronized Optional<List<Alert>> activeWarnings() {
        if (serviceKey.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cachedAt.plus(CACHE_FOR))) {
            return Optional.of(cached);
        }
        if (failedAt != null && now.isBefore(failedAt.plus(RETRY_AFTER))) {
            return Optional.empty();
        }
        try {
            cached = fetch();
            cachedAt = now;
            failedAt = null;
            return Optional.of(cached);
        } catch (RuntimeException e) {
            // 오래된 특보를 "지금"처럼 보여 주지 않도록 저장해 둔 결과도 버린다 → 화면에는 예시 + 안내
            log.warn("기상청 특보현황을 받지 못했습니다: {}", e.getMessage());
            cached = null;
            failedAt = now;
            return Optional.empty();
        }
    }

    private List<Alert> fetch() {
        // URI 를 직접 만들어 넘긴다: 키의 '+' 같은 글자를 정확히 한 번만 인코딩하기 위해 (두 번 인코딩되면 "등록되지 않은 키" 오류)
        URI uri = URI.create(URL + "?serviceKey=" + encodeKey(serviceKey) + "&dataType=JSON&numOfRows=10&pageNo=1");
        PwnStatusResponse res = http.get().uri(uri).retrieve().body(PwnStatusResponse.class);

        if (res == null || res.response() == null || res.response().header() == null) {
            throw new IllegalStateException("응답 형식이 다릅니다");
        }
        Header header = res.response().header();
        if ("03".equals(header.resultCode())) {
            return List.of();  // NO_DATA = 발효 중인 특보 없음
        }
        if (!"00".equals(header.resultCode())) {
            throw new IllegalStateException(header.resultCode() + " " + header.resultMsg());
        }
        Body body = res.response().body();
        if (body == null || body.items() == null || body.items().item() == null || body.items().item().isEmpty()) {
            return List.of();
        }
        Item item = body.items().item().get(0);  // 전국 현황 한 건
        return KmaWarningParser.parse(item.t6(), item.tmEf());
    }

    // 실수로 "인코딩 키"(%2B 등이 들어 있음)를 넣었으면 그대로 쓰고, 디코딩 키면 여기서 인코딩한다
    private static String encodeKey(String key) {
        return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    // ---- 기상청 응답 모양 (필요한 칸만). 모르는 칸은 무시한다 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PwnStatusResponse(Response response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(Items items) {
    }

    // 결과가 한 건이면 배열이 아니라 객체로 오는 경우가 있어 둘 다 받는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Items(@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<Item> item) {
    }

    // t6: 발효 중인 특보, t7: 예비특보(지금은 안 씀), tmEf: 이 현황의 기준 시각
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String t6, String t7, String tmEf) {
    }
}
