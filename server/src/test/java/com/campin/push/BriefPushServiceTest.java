package com.campin.push;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 가짜 PushSender 로 "누구에게 무엇을 보내는지"만 확인한다 (Firebase 로 실제 발송은 하지 않는다)
class BriefPushServiceTest {

    private record Sent(String token, String title, String body, Map<String, String> data) {
    }

    private final BlockingQueue<Sent> sent = new LinkedBlockingQueue<>();
    private final PushTokenStore store = new PushTokenStore();
    private final PushSender fakeSender = (token, title, body, data) -> sent.add(new Sent(token, title, body, data));
    private final BriefPushService service = new BriefPushService(store, fakeSender, 50);

    @AfterEach
    void stop() {
        service.stop();
    }

    @Test
    void 예약하면_조금_뒤_그_회원의_폰으로_보낸다() throws Exception {
        store.register("a", "phoneA");
        store.register("b", "phoneB");

        service.schedule("a", 3L, "설하캠핑장");

        Sent first = sent.poll(5, TimeUnit.SECONDS);
        assertEquals("phoneA", first.token());
        assertTrue(first.body().startsWith("설하캠핑장"));
        assertEquals(Map.of("path", "/app/my-camping", "brief", "3"), first.data());
        assertNull(sent.poll(200, TimeUnit.MILLISECONDS));  // 한 번만, 다른 회원에게는 안 감
    }

    @Test
    void 등록된_폰이_없으면_보내지_않는다() {
        service.send("a", 3L, "설하캠핑장");
        assertTrue(sent.isEmpty());
    }

    @Test
    void 예약한_뒤_로그아웃하면_보내지_않는다() throws Exception {
        BriefPushService slow = new BriefPushService(store, fakeSender, 500);
        try {
            store.register("a", "phoneA");
            slow.schedule("a", 3L, "설하캠핑장");
            store.remove("a", "phoneA");
            assertNull(sent.poll(1500, TimeUnit.MILLISECONDS));
        } finally {
            slow.stop();
        }
    }

    @Test
    void 발송이_실패하거나_서버가_꺼지는_중이어도_예외를_던지지_않는다() {
        store.register("a", "phoneA");
        BriefPushService broken = new BriefPushService(store, (token, title, body, data) -> {
            throw new IllegalStateException("발송 실패");
        }, 50);
        assertDoesNotThrow(() -> broken.send("a", 3L, "설하캠핑장"));
        broken.stop();
        assertDoesNotThrow(() -> broken.schedule("a", 3L, "설하캠핑장"));
    }
}
