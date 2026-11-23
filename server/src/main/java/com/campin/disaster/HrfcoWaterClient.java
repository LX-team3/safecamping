package com.campin.disaster;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 한강홍수통제소 Open API — 인북천 반월촌교 수위관측소(1011620, 서화면 심적리, 캠핑장 약 4km 상류)
//  - 기준수위(관심·경보·심각·계획홍수위): /waterlevel/info.json (전체 관측소 목록이라 하루 한 번만 받는다)
//  - 지금 수위(10분 자료 중 가장 최근): /waterlevel/list/10M/1011620.json (10분마다 바뀌므로 5분 저장)
// 키: 환경변수 HRFCO_SERVICE_KEY (한강홍수통제소에서 받은 키, 기상청 키와 다름). 키가 주소 안에 들어가므로 로그에서는 키를 가린다
// 인증서: api.hrfco.go.kr 은 OISTE WISeKey Global Root GB CA 로 이어지는데 JDK 17 기본 신뢰 목록에 없어 "PKIX path building failed" 가 난다.
//   resources/certs 의 그 공개 루트 인증서를 이 클라이언트에서만 추가로 믿는다 (JDK 설치 폴더·다른 호출은 그대로)
@Component
public class HrfcoWaterClient implements WaterLevelSource {

    private static final Logger log = LoggerFactory.getLogger(HrfcoWaterClient.class);
    private static final String BASE = "https://api.hrfco.go.kr/";
    static final String STATION = "1011620";
    private static final Duration INFO_FOR = Duration.ofHours(24);
    private static final Duration LEVEL_FOR = Duration.ofMinutes(5);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);
    private static final DateTimeFormatter YMDHM = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final String serviceKey;
    private final RestClient http;

    private Info info;
    private Instant infoAt;
    private WaterLevel level;
    private Instant levelAt;
    private Instant failedAt;

    public HrfcoWaterClient(@Value("${HRFCO_SERVICE_KEY:}") String serviceKey) {
        this.serviceKey = serviceKey.strip();
        HttpClient.Builder client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3));
        SSLContext ssl = withExtraRoot("/certs/oiste-wisekey-global-root-gb-ca.pem");
        if (ssl != null) {
            client.sslContext(ssl);
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client.build());
        factory.setReadTimeout(Duration.ofSeconds(8));  // info 는 전체 관측소 목록이라 조금 크다
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.serviceKey.isEmpty()) {
            log.warn("HRFCO_SERVICE_KEY 환경변수가 없어 재난지도 상류 수위는 보여 주지 않습니다.");
        }
    }

    @Override
    public synchronized Optional<WaterLevel> current() {
        if (serviceKey.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (level != null && now.isBefore(levelAt.plus(LEVEL_FOR))) {
            return Optional.of(level);
        }
        if (failedAt != null && now.isBefore(failedAt.plus(RETRY_AFTER))) {
            return Optional.ofNullable(level);
        }
        try {
            if (info == null || now.isAfter(infoAt.plus(INFO_FOR))) {
                info = fetchInfo();
                infoAt = now;
            }
            Obs o = fetchLatest();
            level = new WaterLevel(info.obsnm(), LocalDateTime.parse(o.ymdhm().strip(), YMDHM), parseM(o.wl()),
                    parseM(info.attwl()), parseM(info.almwl()), parseM(info.srswl()), parseM(info.pfh()));
            levelAt = now;
        } catch (RuntimeException e) {
            log.warn("한강홍수통제소 수위를 받지 못했습니다: {}", hideKey(e.getMessage()));
            failedAt = now;
        }
        return Optional.ofNullable(level);
    }

    private Info fetchInfo() {
        ListOf<Info> res = http.get().uri(URI.create(BASE + serviceKey + "/waterlevel/info.json")).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<ListOf<Info>>() { });
        if (res == null || res.content() == null) {
            throw new IllegalStateException("관측소 정보 응답이 비어 있습니다");
        }
        return res.content().stream().filter(i -> STATION.equals(i.wlobscd())).findFirst()
                .orElseThrow(() -> new IllegalStateException("관측소 " + STATION + " 정보가 없습니다"));
    }

    private Obs fetchLatest() {
        ListOf<Obs> res = http.get().uri(URI.create(BASE + serviceKey + "/waterlevel/list/10M/" + STATION + ".json")).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<ListOf<Obs>>() { });
        if (res == null || res.content() == null || res.content().isEmpty() || res.content().get(0).ymdhm() == null) {
            throw new IllegalStateException("수위 자료가 비어 있습니다");
        }
        return res.content().get(0);
    }

    // "4.77" → 4.77. 빈칸·공백·읽을 수 없는 값은 null (그 관측소에 그 기준이 없다는 뜻)
    static Double parseM(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // JDK 기본 신뢰 목록 + 루트 인증서 하나. 기본으로 먼저 확인하고, 안 되면 추가한 루트로 확인한다. 못 읽으면 null(기본만)
    private static SSLContext withExtraRoot(String resource) {
        try (InputStream in = HrfcoWaterClient.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("인증서 파일 {} 이 없어 기본 신뢰 목록만 씁니다.", resource);
                return null;
            }
            Certificate root = CertificateFactory.getInstance("X.509").generateCertificate(in);
            X509TrustManager base = trustManager(null);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("extra-root", root);
            X509TrustManager extra = trustManager(ks);
            X509TrustManager both = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    base.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    try {
                        base.checkServerTrusted(chain, authType);
                    } catch (CertificateException e) {
                        extra.checkServerTrusted(chain, authType);
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return base.getAcceptedIssuers();
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { both }, null);
            return ctx;
        } catch (Exception e) {
            log.warn("추가 루트 인증서를 쓰지 못해 기본 신뢰 목록만 씁니다: {}", e.getMessage());
            return null;
        }
    }

    private static X509TrustManager trustManager(KeyStore ks) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager t : f.getTrustManagers()) {
            if (t instanceof X509TrustManager x) {
                return x;
            }
        }
        throw new IllegalStateException("X509TrustManager 가 없습니다");
    }

    private String hideKey(String msg) {
        return msg == null ? "" : msg.replace(serviceKey, "***");
    }

    // ---- 응답 모양 (필요한 칸만) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListOf<T>(List<T> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Info(String wlobscd, String obsnm, String attwl, String almwl, String srswl, String pfh) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Obs(String wlobscd, String ymdhm, String wl) {
    }
}
