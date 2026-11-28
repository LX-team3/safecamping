package com.campin.brief;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

// AI 브리핑 9단계: 8단계 프롬프트로 Claude 를 부르고 JSON 답을 BriefAnswer 로 읽는다
// 모델 · 키 · 최대 길이는 Spring AI 설정(spring.ai.anthropic.*)에서 온다. 실제로 답한 모델은 로그에 남긴다
//
// 왜 ChatClient 가 아니라 ChatModel 인가:
//   ChatClient 는 글 안의 { } 를 템플릿 자리로 읽을 수 있다. system 규칙에 JSON 예시 {"lines": …} 가 있어서
//   메시지를 그대로 넘기는 ChatModel 을 쓴다 (글을 바꾸지 않음)
//
// 실패하면(키 없음 · 401 · 시간 초과 · JSON 이 아님) Optional.empty() → 부르는 쪽이 지금의 문장 틀 브리핑으로 대신한다
@Slf4j
@Component
public class BriefAiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChatModel chatModel;  // Spring AI 가 설정으로 만들어 준 Claude 창구 (AnthropicChatModel)

    public BriefAiClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Optional<BriefAnswer> ask(BriefPrompt p) {
        long start = System.currentTimeMillis();
        ChatResponse res;
        try {
            res = chatModel.call(new Prompt(List.of(new SystemMessage(p.system()), new UserMessage(p.user()))));
        } catch (RuntimeException e) {
            // 오류 종류와 첫 줄만 (요청 · 응답 본문을 통째로 남기지 않는다)
            log.warn("AI 브리핑: Claude 를 부르지 못했습니다 ({}: {})", e.getClass().getSimpleName(), firstLine(e.getMessage()));
            return Optional.empty();
        }
        long ms = System.currentTimeMillis() - start;

        // 실제로 답한 모델 · 걸린 시간 · 토큰(비용) — 설정이 먹었는지, 얼마나 드는지 확인용
        Usage usage = res.getMetadata().getUsage();
        log.info("AI 브리핑: 모델={}, 걸린 시간={}ms, 입력 토큰={}, 출력 토큰={}",
                res.getMetadata().getModel(), ms, usage.getPromptTokens(), usage.getCompletionTokens());

        String text = res.getResult() == null ? null : res.getResult().getOutput().getText();
        Optional<BriefAnswer> answer = parse(text);
        if (answer.isEmpty()) {
            log.warn("AI 브리핑: 답이 정해진 JSON 모양이 아닙니다: {}", firstLine(text));
        }
        return answer;
    }

    // 답 글 → BriefAnswer. 규칙에 "JSON 하나만"이라고 했어도 ```json 울타리나 앞뒤 글이 붙을 수 있어
    // 처음 { 부터 마지막 } 까지만 읽는다. lines 가 없으면 실패로 본다
    static Optional<BriefAnswer> parse(String text) {
        if (text == null) return Optional.empty();
        int from = text.indexOf('{');
        int to = text.lastIndexOf('}');
        if (from < 0 || to <= from) return Optional.empty();
        try {
            BriefAnswer a = JSON.readValue(text.substring(from, to + 1), BriefAnswer.class);
            return a.lines() == null ? Optional.empty() : Optional.of(a);
        } catch (RuntimeException e) {  // Jackson 3 의 읽기 오류는 RuntimeException
            return Optional.empty();
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String line = s.lines().findFirst().orElse("");
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }
}
