package com.campin.brief;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AI 브리핑 한 번 만들기: 7 상황 → 5 임베딩 → 6 자료 검색 → 8 프롬프트 → 9 Claude → 10 검사
// 어느 단계든 실패하면 예외를 던지지 않고 BriefResult.fallback(이유) → 화면은 지금의 문장 틀 브리핑을 보여 준다
//   (안전 정보라 "AI 가 안 되면 아무것도 안 보임"보다 "검증된 기존 브리핑"이 낫다. docs/deploy.md 16-6)
// 다만 일정이 없거나 남의 일정이면(404) 그대로 던진다 — 대체할 일이 아니라 잘못된 요청이라서
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefService {

    private final BriefContextService contextService;
    private final EmbeddingClient embedding;
    private final SafetyDocRepository repo;
    private final BriefAiClient ai;

    public BriefResult make(String loginId, Long planId, BriefSurvey survey) {
        return make(contextService.collect(loginId, planId, survey));
    }

    // 상황을 받은 뒤부터 (테스트에서 DB 없이 상황을 직접 넣으려고 나눔)
    BriefResult make(BriefContext ctx) {
        if (!repo.isEnabled() || !embedding.isEnabled()) {
            return fallback("search-off", ctx);
        }
        Optional<EmbeddingClient.Result> vector = embedding.embed(ctx.question());
        if (vector.isEmpty()) {
            return fallback("search-failed", ctx);
        }
        List<SafetyDocRepository.Chunk> docs;
        try {
            docs = repo.search(vector.get().vector(), ctx.filter());
        } catch (DataAccessException e) {
            // 터널이 꺼짐 · 비밀번호 틀림 등. 메시지에 비밀번호는 들어가지 않는다
            log.warn("AI 브리핑: 자료 검색 실패 ({})", e.getMostSpecificCause().getMessage());
            return fallback("search-failed", ctx);
        }
        if (docs.isEmpty()) {
            return fallback("no-docs", ctx);
        }

        BriefPrompt prompt = BriefPrompt.of(ctx, docs);
        Optional<BriefAnswer> answer = ai.ask(prompt);
        if (answer.isEmpty()) {
            return fallback("ai-failed", ctx);
        }

        BriefCheck.Result checked = BriefCheck.check(answer.get(), BriefPrompt.situationOf(ctx), docs);
        // 버린 줄은 13단계 평가에 쓰려고 로그로 남긴다 (프롬프트를 고칠지, 검사를 고칠지 판단)
        checked.dropped().forEach(d -> log.info("AI 브리핑: 버린 줄 — {}", d));
        if (checked.lines().isEmpty()) {
            return fallback("all-dropped", ctx);
        }
        log.info("AI 브리핑: {}줄 통과 (자료 {})", checked.lines().size(), docs.stream().map(SafetyDocRepository.Chunk::id).toList());
        return new BriefResult(true, checked.lines(), null);
    }

    private static BriefResult fallback(String reason, BriefContext ctx) {
        log.info("AI 브리핑: 대체 브리핑으로 ({}, {})", reason, ctx.placeName());
        return BriefResult.fallback(reason);
    }
}
