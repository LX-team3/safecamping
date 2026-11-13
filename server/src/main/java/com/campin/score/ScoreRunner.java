package com.campin.score;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 안전점수 계산을 언제 돌릴지: 서버를 켤 때 --score 인자를 주면 한 번 돈다
//   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--score"
// 인자 없이 켜면 아무것도 하지 않는다 (평소 실행)
// API 로 열지 않은 이유: 누구나 부를 수 있으면 아무나 재계산을 돌려 카카오 하루 한도를 써 버릴 수 있다
// db/merge_to_public.sql 을 다시 실행하면 값이 직선 추정으로 돌아가므로 그 뒤에 다시 돌린다
@Component                 // 스프링 빈으로 등록
@RequiredArgsConstructor
public class ScoreRunner implements ApplicationRunner {  // ApplicationRunner: 서버가 다 켜진 뒤 한 번 실행된다

    private final SafetyScoreService service;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("score")) return;
        // 별도 스레드: 계산이 오래 걸려도 그동안 서버는 요청을 받는다
        Thread t = new Thread(service::recalculateAll, "safety-score");
        t.setDaemon(true);  // 서버를 끌 때 계산이 끝나길 기다리지 않는다
        t.start();
    }
}
