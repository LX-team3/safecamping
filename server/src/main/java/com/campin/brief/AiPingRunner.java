package com.campin.brief;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// 임시: Claude 연결 확인용 (AI 브리핑 3단계). 확인이 끝나면 이 파일을 지운다
// 서버를 --ai-ping 인자로 켤 때만 한 번 부른다 → 평소 실행이나 테스트에서는 돈이 나가지 않는다
@Slf4j
@Component
public class AiPingRunner implements ApplicationRunner {

    private final ChatClient chatClient;

    // ChatClient.Builder 는 Spring AI 가 서버를 켤 때 만들어 준다 (②③의 모델 · 키 설정이 들어 있음)
    public AiPingRunner(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ai-ping")) return; // 인자가 없으면 아무것도 안 함

        long start = System.currentTimeMillis();
        ChatResponse res = chatClient.prompt()
                .system("너는 캠핑 안전 안내자다. 한 문장으로 쉽게 답한다.")
                .user("캠핑 전날 꼭 확인할 것 하나만 알려줘.")
                .call()
                .chatResponse();
        long ms = System.currentTimeMillis() - start;

        // 실제로 어떤 모델이 답했는지, 시간 · 토큰(비용)을 남긴다. 키는 로그에 남기지 않는다
        Usage usage = res.getMetadata().getUsage();
        log.info("AI 확인: 모델={}, 걸린 시간={}ms, 입력 토큰={}, 출력 토큰={}",
                res.getMetadata().getModel(), ms, usage.getPromptTokens(), usage.getCompletionTokens());
        log.info("AI 답: {}", res.getResult().getOutput().getText());
    }
}