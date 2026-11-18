package com.campin.weather;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 기상청 단기예보 조회서비스 · 단기예보(getVilageFcst) 호출 — 위치의 격자(5km) 하나에 대한 며칠치 시간별 예보
// 같은 격자는 3시간 동안 저장해 둔 결과를 쓴다 (예보는 하루 8번 발표되고, 하루 호출 한도 10,000회를 아끼기 위해)
// 키: 환경변수 KMA_SERVICE_KEY (특보와 같은 공공데이터포털 "디코딩 키"). 코드·application.properties 에 키를 쓰지 않는다
@Component
public class KmaForecastClient implements ForecastSource {

    private static final Logger log = LoggerFactory.getLogger(KmaForecastClient.class);
    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration CACHE_FOR = Duration.ofHours(3);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);  // 실패하면 1분 동안은 다시 부르지 않는다
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 4;  // 안전장치: 응답이 이상해도 무한히 부르지 않게

    private record Cached(List<DayForecast> days, Instant at) {
    }

    private final String serviceKey;
    private final RestClient http;

    // 격자(nx,ny) + 발표일을 키로 저장. 여러 요청이 동시에 와도 한 번만 부르도록 daily 는 synchronized
    private final Map<String, Cached> cache = new HashMap<>();
    private final Map<String, Instant> failedAt = new HashMap<>();

    public KmaForecastClient(@Value("${KMA_SERVICE_KEY:}") String serviceKey) {
        this.serviceKey = serviceKey.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));  // 기상청이 느려도 체크리스트 화면이 오래 멈추지 않게
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.serviceKey.isEmpty()) {
            log.warn("KMA_SERVICE_KEY 환경변수가 없어 날씨 예보는 보여 주지 않습니다.");
        }
    }

    @Override
    public synchronized Optional<List<DayForecast>> daily(double lat, double lng) {
        if (serviceKey.isEmpty()) {
            return Optional.empty();
        }
        KmaGrid.Point p = KmaGrid.of(lat, lng);
        LocalDateTime now = LocalDateTime.now(KST);
        // 발표 시각: 02시 발표는 그날 모레 이후까지 가장 멀리 주고, 발표 후 약 10분 뒤부터 받을 수 있다
        // → 02:15 이후면 오늘 0200 발표, 그 전이면 어제 2300 발표
        LocalDate baseDate = now.toLocalTime().isBefore(java.time.LocalTime.of(2, 15)) ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
        String baseTime = now.toLocalTime().isBefore(java.time.LocalTime.of(2, 15)) ? "2300" : "0200";
        String key = p.nx() + "," + p.ny() + "," + baseDate + baseTime;

        Instant t = Instant.now();
        Cached c = cache.get(key);
        if (c != null && t.isBefore(c.at().plus(CACHE_FOR))) {
            return Optional.of(c.days());
        }
        Instant failed = failedAt.get(key);
        if (failed != null && t.isBefore(failed.plus(RETRY_AFTER))) {
            return Optional.empty();
        }
        try {
            List<DayForecast> days = fetch(p, baseDate.format(DateTimeFormatter.BASIC_ISO_DATE), baseTime);
            cache.clear();  // 오래된 발표 결과가 쌓이지 않게 (격자 몇 개 수준이라 통째로 비워도 충분)
            cache.put(key, new Cached(days, t));
            failedAt.remove(key);
            return Optional.of(days);
        } catch (RuntimeException e) {
            // 오래된 예보를 "지금" 것처럼 보여 주지 않도록 저장해 둔 결과도 버린다
            log.warn("기상청 단기예보를 받지 못했습니다: {}", e.getMessage());
            cache.remove(key);
            failedAt.put(key, t);
            return Optional.empty();
        }
    }

    private List<DayForecast> fetch(KmaGrid.Point p, String baseDate, String baseTime) {
        List<ForecastParser.Item> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            // URI 를 직접 만들어 넘긴다: 키의 '+' 같은 글자를 정확히 한 번만 인코딩하기 위해
            URI uri = URI.create(URL + "?serviceKey=" + encodeKey(serviceKey) + "&dataType=JSON&numOfRows=" + PAGE_SIZE
                    + "&pageNo=" + page + "&base_date=" + baseDate + "&base_time=" + baseTime
                    + "&nx=" + p.nx() + "&ny=" + p.ny());
            FcstResponse res = http.get().uri(uri).retrieve().body(FcstResponse.class);
            if (res == null || res.response() == null || res.response().header() == null) {
                throw new IllegalStateException("응답 형식이 다릅니다");
            }
            Header header = res.response().header();
            if ("03".equals(header.resultCode())) {
                break;  // NO_DATA
            }
            if (!"00".equals(header.resultCode())) {
                throw new IllegalStateException(header.resultCode() + " " + header.resultMsg());
            }
            Body body = res.response().body();
            if (body == null || body.items() == null || body.items().item() == null) {
                break;
            }
            for (Item it : body.items().item()) {
                all.add(new ForecastParser.Item(it.category(), it.fcstDate(), it.fcstTime(), it.fcstValue()));
            }
            int total = body.totalCount() == null ? 0 : body.totalCount();
            if (page * PAGE_SIZE >= total) {
                break;
            }
        }
        if (all.isEmpty()) {
            throw new IllegalStateException("예보 항목이 비어 있습니다");  // 예보가 없는 것이 아니라 못 받은 것으로 본다
        }
        return ForecastParser.parse(all);
    }

    // 실수로 "인코딩 키"(%2B 등이 들어 있음)를 넣었으면 그대로 쓰고, 디코딩 키면 여기서 인코딩한다
    private static String encodeKey(String key) {
        return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    // ---- 기상청 응답 모양 (필요한 칸만). 모르는 칸은 무시한다 ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FcstResponse(Response response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(Items items, Integer totalCount) {
    }

    // 결과가 한 건이면 배열이 아니라 객체로 오는 경우가 있어 둘 다 받는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Items(@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String category, String fcstDate, String fcstTime, String fcstValue) {
    }
}
