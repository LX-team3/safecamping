package com.campin.brief;

import java.time.LocalDate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 임시: 벡터 DB · 임베딩 연결 확인용 (AI 브리핑 4 ~ 5단계). 진짜 브리핑 코드가 생기면 이 파일을 지운다
// 서버를 --vector-ping 인자로 켤 때만 한 번 돈다
//   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--vector-ping"
// ① 벡터 DB (4단계): 개수 120 · 120, JD-001 거리 0, 둘째 줄 MW-002 약 0.508 (docs/deploy.md 16-5)
// ② 임베딩 (5단계, OPENAI_API_KEY 있을 때): OpenAI 를 2번 부른다 (합쳐 수백 토큰, 비용은 아주 적다)
//    ②-1 JD-001 본문을 다시 임베딩 → JD-001 이 1등이면 "같은 모델 · 같은 숫자 지도"
//    ②-2 예시 질문 → 비 · 물 · 계곡 · 어린이 관련 조각이 나오면 뜻으로 찾기가 된다
// ④ ⑤ 7 · 8단계: 상황으로 만든 질문으로 검색 → user 프롬프트를 찍음
// ⑥ 9단계: --vector-ping --ai-brief 로 켜면 ⑤ 프롬프트로 Claude 를 한 번 부른다 (Opus 5.5 기준 몇 센트)
// ③ 검색 (6단계): 질문 2개를 조건 4가지로 거른 상위 5개 + 거르지 않은 5개와 비교 (OpenAI 를 4번 더 부른다)
@Slf4j
@Component
@RequiredArgsConstructor  // final 필드를 받는 생성자를 롬복이 만든다 → 스프링이 넣어 줌
public class VectorPingRunner implements ApplicationRunner {  // ApplicationRunner: 서버가 다 켜진 뒤 한 번 실행된다

    private static final String SAMPLE_QUESTION = "아이와 계곡 옆에서 텐트 캠핑을 하는데 내일 비가 많이 온대요";

    private final SafetyDocRepository repo;
    private final EmbeddingClient embedding;
    private final BriefService brief;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("vector-ping")) return;  // 인자가 없으면 아무것도 안 함
        if (!repo.isEnabled()) {
            log.warn("벡터 DB 확인: VECTOR_DB_URL 이 없어 건너뜁니다.");
            return;
        }
        try {
            // ① 벡터 DB
            long start = System.currentTimeMillis();
            long[] count = repo.countChunks();
            log.info("벡터 DB 확인: 조각 {}개, 임베딩 {}개, 걸린 시간 {}ms",
                    count[0], count[1], System.currentTimeMillis() - start);
            for (SafetyDocRepository.Nearest n : repo.findNearestTo("JD-001", 3)) {
                log.info("  JD-001 과 가까운 조각: {} (거리 {}) {}", n.id(), fmt(n.distance()), n.heading());
            }

            // ② 임베딩
            if (!embedding.isEnabled()) {
                log.warn("임베딩 확인: OPENAI_API_KEY 가 없어 건너뜁니다.");
                return;
            }
            searchByText("임베딩 확인 ②-1 JD-001 본문", repo.findContent("JD-001"));
            searchByText("임베딩 확인 ②-2 예시 질문 \"" + SAMPLE_QUESTION + "\"", SAMPLE_QUESTION);

            // ③ 6단계 검색: 꼬리표로 거르고 상위 5개. 거르기가 눈에 보이는 질문으로 본다
            //   ③-1 · ③-2 같은 겨울 질문을 12월 · 8월로 → 8월에는 winter 조각이 빠져야 한다
            //   ③-3 화상 질문 → 뺄 목록의 JD-058(2016년 화상 응급처치)이 나오지 않아야 한다
            //   ③-4 아이 없이 → child 조각이 빠져야 한다
            String cold = "한겨울에 텐트 안에서 난로를 켜고 자도 되나요";
            String burn = "아이가 캠핑장에서 화상을 입었어요";
            searchFiltered("③-1 겨울 질문 · 12월", cold, SafetyDocFilter.of(true, false, "tent", LocalDate.of(2026, 12, 1)));
            searchFiltered("③-2 겨울 질문 · 8월", cold, SafetyDocFilter.of(true, false, "tent", LocalDate.of(2026, 8, 1)));
            searchFiltered("③-3 화상 질문 · 아이 동반", burn, SafetyDocFilter.of(true, false, "tent", null));
            searchFiltered("③-4 화상 질문 · 아이 없이", burn, SafetyDocFilter.of(false, false, "tent", null));

            // ④ 7단계가 만든 질문으로 검색 (아이 동반 텐트, 12월, 영하 · 눈 예보, 약한 분야 일산화탄소 · 화재)
            var who = new BriefContext.Companions(true, false, false);
            var days = java.util.List.of(new com.campin.weather.DayForecast(LocalDate.of(2026, 12, 5), -5.0, 2.0, 60, 4.0, "흐림", "눈"));
            String q = BriefContext.questionOf(who, "tent", "겨울", java.util.List.of(), days, java.util.List.of("일산화탄소", "화재"));
            SafetyDocFilter filter = SafetyDocFilter.of(true, false, "tent", LocalDate.of(2026, 12, 5));
            searchFiltered("④ 7단계 질문 \"" + q + "\"", q, filter);

            // ⑤ 8단계: 위 상황 + 찾은 조각으로 만든 user 프롬프트를 그대로 찍는다 (Claude 는 부르지 않음)
            var found = embedding.embed(q);
            if (found.isPresent()) {
                var ctx = new BriefContext("인북천 물빛테마공원 캠핑장", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6), false,
                        "겨울", true, LocalDate.of(2026, 12, 2), who, "tent", true, java.util.List.of(),
                        new com.campin.mycamping.ChecklistResponse.Forecast(true, days),
                        new com.campin.mycamping.ChecklistResponse.Safety(71, "mid", 25, false),
                        java.util.List.of("일산화탄소", "화재"), java.util.List.of(), q, filter);
                BriefPrompt p = BriefPrompt.of(ctx, repo.search(found.get().vector(), filter));
                log.info("⑤ 8단계 user 프롬프트 ({}자, system {}자)\n{}", p.user().length(), p.system().length(), p.user());

                // ⑥ 9 · 10단계: --ai-brief 인자도 줄 때만 전체 흐름(검색 → 프롬프트 → Claude → 검사)을 한 번 돈다 (비용이 든다)
                if (args.containsOption("ai-brief")) {
                    BriefResult r = brief.make(ctx);
                    log.info("⑥ 결과: ai={}, fallback={}", r.ai(), r.fallback());
                    r.lines().forEach(l -> log.info("⑥ 대비 행동: {} ← {}", l.text(),
                            l.sources().stream().map(s -> s.id() + " " + s.org() + " " + s.year()).toList()));
                }
            }
        } catch (DataAccessException e) {
            // 터널이 꺼짐 · 비밀번호 틀림 · 테이블 없음 등. 메시지에 비밀번호는 들어가지 않는다
            log.warn("벡터 DB 확인 실패: {}", e.getMostSpecificCause().getMessage());
        }
    }

    // 글 → OpenAI 임베딩 → 벡터 DB 에서 가까운 3개
    private void searchByText(String title, String text) {
        long start = System.currentTimeMillis();
        var result = embedding.embed(text);
        if (result.isEmpty()) {
            log.warn("{}: 임베딩을 받지 못했습니다 (위 경고 참고).", title);
            return;
        }
        long embedMs = System.currentTimeMillis() - start;
        log.info("{}: 벡터 {}개, 토큰 {}, 임베딩 {}ms", title, result.get().vector().length, result.get().tokens(), embedMs);
        for (SafetyDocRepository.Nearest n : repo.findSimilar(result.get().vector(), 3)) {
            log.info("  가까운 조각: {} (거리 {}) {}", n.id(), fmt(n.distance()), n.heading());
        }
    }

    // 질문 → 꼬리표로 거른 상위 5개 (6단계)
    private void searchFiltered(String title, String question, SafetyDocFilter filter) {
        var result = embedding.embed(question);
        if (result.isEmpty()) {
            log.warn("검색 확인 {}: 임베딩을 받지 못했습니다.", title);
            return;
        }
        log.info("검색 확인 {}: 조건 {}", title, filter);
        for (SafetyDocRepository.Chunk c : repo.search(result.get().vector(), filter)) {
            log.info("  {} (거리 {}) [{}] {} — {} {}", c.id(), fmt(c.distance()), c.kind(), c.heading(), c.org(), c.docYear());
        }
        // 비교용: 같은 질문을 거르지 않고 5개 → 위와 다른 줄이 "걸러진 조각"
        for (SafetyDocRepository.Nearest n : repo.findSimilar(result.get().vector(), 5)) {
            log.info("    (거르지 않으면) {} (거리 {}) {}", n.id(), fmt(n.distance()), n.heading());
        }
    }

    private static String fmt(double d) {
        return String.format("%.3f", d);
    }
}
