package com.campin.disaster;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.campin.weather.KmaGrid;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 기상청 단기예보 조회서비스 · 초단기실황(getUltraSrtNcst) 호출 — 5km 격자 하나의 "지난 1시간 동안 내린 비"(RN1)
// 한 번 부르면 한 시간치만 오므로, 최근 24시간을 알려면 시간마다 한 번씩 부른다
//  - 지난 시간의 관측값은 바뀌지 않으므로 한 번 받은 시간은 저장해 두고 다시 부르지 않는다 (처음 한 번만 여러 번 부르고, 그다음은 한 시간에 한 번)
//  - 매시 정시 관측은 약 40분 뒤에 받을 수 있다 → 지금이 15:30 이면 가장 최근은 14:00 관측
//  - 실황은 최근 하루(24시간)치까지만 조회된다
// 키: 환경변수 KMA_SERVICE_KEY (weather·alert 와 같은 공공데이터포털 "디코딩 키"). 코드·application.properties 에 키를 쓰지 않는다
@Component
public class KmaRainClient implements RainSource {

    private static final Logger log = LoggerFactory.getLogger(KmaRainClient.class);
    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration READY_AFTER = Duration.ofMinutes(40);  // 정시 관측을 받을 수 있게 되는 시간
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);   // 실패하면 1분 동안은 다시 부르지 않는다
    private static final int MAX_HOURS = 24;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH00");

    private final String serviceKey;
    private final RestClient http;

    // "nx,ny,관측시각" → 그 시간에 내린 비(mm, 결측이면 null). recentHours 는 synchronized 라 한 번에 한 요청만 부른다
    private final Map<String, HourRain> cache = new HashMap<>();
    private Instant failedAt;

    // ${KMA_SERVICE_KEY:} = 환경변수를 읽고, 없으면 빈 문자열
    public KmaRainClient(@Value("${KMA_SERVICE_KEY:}") String serviceKey) {
        this.serviceKey = serviceKey.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));  // 기상청이 느려도 재난지도 화면이 오래 멈추지 않게
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.serviceKey.isEmpty()) {
            log.warn("KMA_SERVICE_KEY 환경변수가 없어 재난지도 실시간 강수량은 보여 주지 않습니다.");
        }
    }

    @Override
    public synchronized Optional<List<HourRain>> recentHours(double lat, double lng, int hours) {
        if (serviceKey.isEmpty()) {
            return Optional.empty();
        }
        int n = Math.min(Math.max(hours, 1), MAX_HOURS);
        KmaGrid.Point p = KmaGrid.of(lat, lng);
        LocalDateTime latest = LocalDateTime.now(KST).minus(READY_AFTER).truncatedTo(ChronoUnit.HOURS);
        cache.keySet().removeIf(k -> cache.get(k).at().isBefore(latest.minusHours(MAX_HOURS)));  // 하루 지난 것은 버린다

        Instant now = Instant.now();
        boolean waiting = failedAt != null && now.isBefore(failedAt.plus(RETRY_AFTER));
        List<HourRain> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDateTime at = latest.minusHours(i);
            String key = p.nx() + "," + p.ny() + "," + at;
            HourRain hr = cache.get(key);
            if (hr == null && !waiting) {
                try {
                    hr = new HourRain(at, fetch(p, at));
                    cache.put(key, hr);
                } catch (RuntimeException e) {
                    // 한 번 실패하면 이번 요청에서는 더 부르지 않는다 (기상청 장애 때 24번 기다리지 않게)
                    log.warn("기상청 초단기실황({})을 받지 못했습니다: {}", at, hideKey(e.getMessage()));  // 주소에 키가 들어 있어 가린다
                    failedAt = now;
                    waiting = true;
                }
            }
            list.add(hr != null ? hr : new HourRain(at, null));
        }
        if (list.get(0).mm() == null && !cache.containsKey(p.nx() + "," + p.ny() + "," + latest)) {
            return Optional.empty();  // 가장 최근 시간을 못 받았으면 "지금" 정보가 없는 것
        }
        return Optional.of(list);
    }

    // 그 시각 관측의 RN1(mm). 결측이면 null
    private Double fetch(KmaGrid.Point p, LocalDateTime at) {
        // URI 를 직접 만들어 넘긴다: 키의 '+' 같은 글자를 정확히 한 번만 인코딩하기 위해
        URI uri = URI.create(URL + "?serviceKey=" + encodeKey(serviceKey) + "&dataType=JSON&numOfRows=20&pageNo=1"
                + "&base_date=" + at.format(DATE) + "&base_time=" + at.format(HOUR)
                + "&nx=" + p.nx() + "&ny=" + p.ny());
        NcstResponse res = http.get().uri(uri).retrieve().body(NcstResponse.class);
        if (res == null || res.response() == null || res.response().header() == null) {
            throw new IllegalStateException("응답 형식이 다릅니다");
        }
        Header header = res.response().header();
        if (!"00".equals(header.resultCode())) {
            throw new IllegalStateException(header.resultCode() + " " + header.resultMsg());
        }
        Body body = res.response().body();
        if (body == null || body.items() == null || body.items().item() == null) {
            throw new IllegalStateException("관측 항목이 비어 있습니다");
        }
        for (Item it : body.items().item()) {
            if ("RN1".equals(it.category())) {
                return parseMm(it.obsrValue());
            }
        }
        throw new IllegalStateException("RN1(1시간 강수량) 항목이 없습니다");
    }

    // "0" · "3.5" → 숫자. 기상청 결측값(±900 이상)이나 읽을 수 없는 값은 null
    static Double parseMm(String v) {
        if (v == null) {
            return null;
        }
        String s = v.strip();
        if (s.contains("없음")) {
            return 0.0;  // 초단기실황은 숫자로 오지만, 예보처럼 "강수없음"으로 오는 경우도 받아 둔다
        }
        try {
            double mm = Double.parseDouble(s.replace("mm", ""));
            return (mm < 0 || mm >= 900) ? null : mm;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hideKey(String msg) {
        return msg == null ? "" : msg.replace(encodeKey(serviceKey), "***").replace(serviceKey, "***");
    }

    // 실수로 "인코딩 키"(%2B 등이 들어 있음)를 넣었으면 그대로 쓰고, 디코딩 키면 여기서 인코딩한다
    private static String encodeKey(String key) {
        return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    // ---- 기상청 응답 모양 (필요한 칸만). 모르는 칸은 무시한다 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NcstResponse(Response response) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String category, String obsrValue) {
    }
}
