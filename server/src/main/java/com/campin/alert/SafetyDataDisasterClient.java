package com.campin.alert;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 행정안전부 긴급재난문자 (재난안전데이터 공유플랫폼 · DSSP-IF-00247) 호출
// 하루 호출 한도가 99회라서 20분에 한 번만 부르고 그 사이에는 저장해 둔 결과를 쓴다 (실패해도 20분 동안 다시 부르지 않음 → 하루 최대 72회)
// 키: 환경변수 SAFETY_DATA_KEY (공유플랫폼에서 발급). 코드·application.properties 에 키를 쓰지 않는다
// ⚠ 이 API 는 키를 신청할 때 등록한 IP 에서만 불린다 (다른 IP 면 resultCode 32 → 더미로 대신)
@Component
public class SafetyDataDisasterClient implements DisasterSource {

    private static final Logger log = LoggerFactory.getLogger(SafetyDataDisasterClient.class);
    private static final String URL = "https://www.safetydata.go.kr/V2/api/DSSP-IF-00247";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration CACHE_FOR = Duration.ofMinutes(20);
    private static final Duration KEEP = Duration.ofHours(24);  // 화면에는 최근 24시간 문자만
    private static final int ROWS = 300;

    private final String serviceKey;
    private final RestClient http;

    // 마지막으로 받은 결과와 시각. 여러 요청이 동시에 와도 한 번만 부르도록 recentMessages 는 synchronized
    private List<Alert> cached;
    private Instant calledAt;

    // ${SAFETY_DATA_KEY:} = 환경변수(또는 application-local.properties)를 읽고, 없으면 빈 문자열
    public SafetyDataDisasterClient(@Value("${SAFETY_DATA_KEY:}") String serviceKey) {
        this.serviceKey = serviceKey.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));  // 공유플랫폼이 느려도 알림 화면이 오래 멈추지 않게
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.serviceKey.isEmpty()) {
            log.warn("SAFETY_DATA_KEY 환경변수가 없어 재난문자는 더미(예시)를 씁니다.");
        }
    }

    @Override
    public synchronized Optional<List<Alert>> recentMessages() {
        if (serviceKey.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (calledAt != null && now.isBefore(calledAt.plus(CACHE_FOR))) {
            return Optional.ofNullable(cached); // 지난번에 실패했으면 null → 더미
        }
        calledAt = now; // 성공·실패와 상관없이 호출 한 번으로 센다 (하루 한도 99회)
        try {
            cached = fetch();
            return Optional.of(cached);
        } catch (RuntimeException e) {
            // 오래된 문자를 최신처럼 보여 주지 않도록 저장해 둔 결과도 버린다 → 화면에는 예시 + 안내
            log.warn("긴급재난문자를 받지 못했습니다: {}", e.getMessage());
            cached = null;
            return Optional.empty();
        }
    }

    private List<Alert> fetch() {
        // crtDt(생성일, yyyyMMdd)로 오늘 문자만 달라고 한다. ⚠ 변수 이름은 실제 응답으로 확인 전 — 무시되더라도 아래에서 24시간으로 거른다
        String today = LocalDate.now(KST).format(DateTimeFormatter.BASIC_ISO_DATE);
        // URI 를 직접 만든다: 키의 '+' 같은 글자를 정확히 한 번만 인코딩하기 위해 (KmaWarningClient 와 같은 이유)
        URI uri = URI.create(URL + "?serviceKey=" + encodeKey(serviceKey)
                + "&returnType=json&pageNo=1&numOfRows=" + ROWS + "&crtDt=" + today);
        String body = http.get().uri(uri).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("빈 응답");
        }
        OffsetDateTime since = OffsetDateTime.now(KST).minus(KEEP);
        return DisasterMessageParser.parse(body).stream()
                .filter(a -> a.issuedAt().isAfter(since))
                .toList();
    }

    // 실수로 "인코딩된 키"(%2B 등)를 넣었으면 그대로 쓰고, 아니면 여기서 인코딩한다
    private static String encodeKey(String key) {
        return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
}
