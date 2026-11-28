package com.campin.brief;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 글 → 숫자 1536개(임베딩 벡터). 안전자료 조각을 "뜻으로" 찾을 때 질문을 이 숫자로 바꾼다 (AI 브리핑 5단계)
//
// 왜 OpenAI 인가: 벡터 DB 의 조각 120개가 이미 OpenAI text-embedding-3-small 로 만들어져 있다
//   → 질문도 "같은 모델"로 바꿔야 같은 숫자 지도 위에서 거리를 잴 수 있다. 다른 모델을 쓰면 엉뚱한 조각이 나온다
// 왜 Spring AI 의 OpenAI 부품을 안 쓰나: 대화 모델(Claude)과 둘이 되어 설정이 꼬인다. 한 번 부르는 일이라 RestClient 로 짧게 짠다
//
// 키: 환경변수 OPENAI_API_KEY (작업 PC: application-local.properties, 배포 서버: /etc/campine/campine.env). 코드 · 로그에 키를 남기지 않는다
// 키가 없거나 실패하면 Optional.empty() → 부르는 쪽이 "자료 없이" 대체 문장으로 간다
@Component  // 스프링 빈으로 등록
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final String URL = "https://api.openai.com/v1/embeddings";
    static final String MODEL = "text-embedding-3-small";  // 조각을 만든 모델과 같아야 한다 (safety_doc_chunk.embedding_model)
    static final int DIMS = 1536;                          // 벡터 DB 칸 크기 vector(1536) 와 같아야 한다

    private final String apiKey;
    private final RestClient http;

    // ${OPENAI_API_KEY:} = 환경변수를 읽고, 없으면 빈 문자열
    public EmbeddingClient(@Value("${OPENAI_API_KEY:}") String apiKey) {
        this.apiKey = apiKey.strip();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));   // OpenAI 가 느려도 브리핑이 오래 멈추지 않게
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.apiKey.isEmpty()) {
            log.warn("OPENAI_API_KEY 환경변수가 없어 안전자료를 뜻으로 찾지 않습니다.");
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    // 글 하나 → 벡터. 실패하면 empty (이유는 로그에만)
    public Optional<Result> embed(String text) {
        if (apiKey.isEmpty() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            EmbeddingResponse res = http.post().uri(URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(MODEL, text))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (res == null || res.data() == null || res.data().isEmpty() || res.data().get(0).embedding() == null) {
                log.warn("OpenAI 임베딩 응답 형식이 다릅니다.");
                return Optional.empty();
            }
            float[] vector = res.data().get(0).embedding();
            if (vector.length != DIMS) {  // 크기가 다르면 DB 와 비교할 수 없다 (모델이 바뀐 경우 등)
                log.warn("OpenAI 임베딩 크기가 {}개입니다 (기대 {}개).", vector.length, DIMS);
                return Optional.empty();
            }
            int tokens = res.usage() == null ? 0 : res.usage().promptTokens();
            return Optional.of(new Result(vector, tokens));
        } catch (RestClientResponseException e) {
            // 응답 본문에는 키 일부가 섞일 수 있어 상태 코드만 남긴다 (401 키 · 429 한도/크레딧)
            log.warn("OpenAI 임베딩을 받지 못했습니다: HTTP {}", e.getStatusCode().value());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("OpenAI 임베딩을 받지 못했습니다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // 결과: 벡터 + 쓴 토큰 수(비용 확인용)
    public record Result(float[] vector, int tokens) {}

    // 보내는 JSON: { "model": "...", "input": "..." }
    record EmbeddingRequest(String model, String input) {}

    // 받는 JSON 중 필요한 것만: { "data": [ { "embedding": [...] } ], "usage": { "prompt_tokens": 12 } }
    @JsonIgnoreProperties(ignoreUnknown = true)  // 모르는 칸은 무시
    record EmbeddingResponse(List<Item> data, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(float[] embedding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") int promptTokens) {}  // @JsonProperty: JSON 이름(prompt_tokens)과 자바 이름을 잇는다
}
