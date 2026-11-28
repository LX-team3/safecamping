package com.campin.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

// 9단계: Claude 답 글을 BriefAnswer 로 읽는 부분만 본다 (Claude 는 부르지 않음 → 돈이 들지 않는다)
class BriefAiClientTest {

    @Test
    void JSON_그대로() {
        var a = BriefAiClient.parse("{\"lines\":[{\"text\":\"난로는 끄고 자요\",\"sources\":[\"WT-002\"]}]}").orElseThrow();
        assertEquals(List.of(new BriefAnswer.Line("난로는 끄고 자요", List.of("WT-002"))), a.lines());
    }

    @Test
    void 울타리나_앞뒤_글이_붙어도_읽는다() {
        String text = "답입니다.\n```json\n{\"lines\": [{\"text\": \"화로 불씨를 끝까지 꺼요\", \"sources\": [\"JD-025\"], \"extra\": 1}]}\n```";
        var a = BriefAiClient.parse(text).orElseThrow();
        assertEquals("화로 불씨를 끝까지 꺼요", a.lines().get(0).text());
    }

    @Test
    void 빈_줄_목록도_답으로_본다() {
        // 근거가 부족하면 0줄도 된다는 규칙 → 실패가 아니라 "쓸 줄 없음" (10단계가 대체 브리핑으로 보냄)
        assertEquals(List.of(), BriefAiClient.parse("{\"lines\": []}").orElseThrow().lines());
    }

    @Test
    void JSON_이_아니거나_lines_가_없으면_실패() {
        assertTrue(BriefAiClient.parse(null).isEmpty());
        assertTrue(BriefAiClient.parse("죄송하지만 답할 수 없어요").isEmpty());
        assertTrue(BriefAiClient.parse("{\"lines\": [ 깨짐 }").isEmpty());
        assertTrue(BriefAiClient.parse("{\"answer\": \"x\"}").isEmpty());
    }
}
