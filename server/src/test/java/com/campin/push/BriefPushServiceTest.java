package com.campin.push;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
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
    private final Map<String, List<String>> rooms = new HashMap<>();  // 로그인 아이디 → 같은 방 사람들
    private final RoommateMapper fakeRoommates = loginId -> rooms.getOrDefault(loginId, List.of());
    private final BriefPushService service = new BriefPushService(store, fakeSender, fakeRoommates, 50);

    @AfterEach
    void stop() {
        service.stop();
    }

    @Test
    void 예약하면_조금_뒤_그_회원의_폰으로_보낸다() throws Exception {
        store.register("a", "phoneA");
        store.register("b", "phoneB");

        service.schedule("a", 3L, "설하캠핑장", true);

        Sent first = sent.poll(5, TimeUnit.SECONDS);
        assertEquals("phoneA", first.token());
        assertTrue(first.body().startsWith("설하캠핑장"));
        assertEquals(Map.of("path", "/app/my-camping", "brief", "3"), first.data());
        assertNull(sent.poll(200, TimeUnit.MILLISECONDS));  // 한 번만, 같은 방이 아닌 회원에게는 안 감
    }

    @Test
    void 등록된_폰이_없으면_보내지_않는다() {
        service.send("a", 3L, "설하캠핑장", true);
        assertTrue(sent.isEmpty());
    }

    @Test
    void 예약한_뒤_로그아웃하면_보내지_않는다() throws Exception {
        BriefPushService slow = new BriefPushService(store, fakeSender, fakeRoommates, 500);
        try {
            store.register("a", "phoneA");
            slow.schedule("a", 3L, "설하캠핑장", true);
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
        }, fakeRoommates, 50);
        assertDoesNotThrow(() -> broken.send("a", 3L, "설하캠핑장", true));
        broken.stop();
        assertDoesNotThrow(() -> broken.schedule("a", 3L, "설하캠핑장", true));
    }

    private List<String> sentTokens() {
        return sent.stream().map(Sent::token).toList();
    }

    @Test
    void 같은_모임방_사람_모두에게_보낸다() {
        rooms.put("a", List.of("b", "c"));
        store.register("a", "phoneA");
        store.register("b", "phoneB");
        store.register("c", "phoneC");
        store.register("d", "phoneD");  // 다른 방

        service.send("a", 3L, "설하캠핑장", true);

        assertEquals(List.of("phoneA", "phoneB", "phoneC"), sentTokens());
        assertEquals(List.of("캠핑 브리핑이 도착했어요", "모임방 캠핑 브리핑이 도착했어요", "모임방 캠핑 브리핑이 도착했어요"),
                sent.stream().map(Sent::title).toList());
        assertTrue(sent.stream().allMatch(s -> "3".equals(s.data().get("brief"))));
    }

    @Test
    void 앱에서_저장했으면_나를_빼고_모임방_사람에게만_보낸다() {
        rooms.put("a", List.of("b"));
        store.register("a", "phoneA");
        store.register("b", "phoneB");

        service.send("a", 3L, "설하캠핑장", false);

        assertEquals(List.of("phoneB"), sentTokens());
    }

    @Test
    void 예약한_뒤_들어온_사람은_받고_나간_사람은_받지_않는다() throws Exception {
        BriefPushService slow = new BriefPushService(store, fakeSender, fakeRoommates, 500);
        try {
            store.register("b", "phoneB");
            store.register("c", "phoneC");
            rooms.put("a", List.of("c"));
            slow.schedule("a", 3L, "설하캠핑장", false);
            rooms.put("a", List.of("b"));  // 보내기 전에 c 가 나가고 b 가 들어옴

            Sent first = sent.poll(5, TimeUnit.SECONDS);
            assertEquals("phoneB", first.token());
            assertNull(sent.poll(200, TimeUnit.MILLISECONDS));
        } finally {
            slow.stop();
        }
    }

    @Test
    void 한_사람의_폰이_없거나_실패해도_다음_사람에게_보낸다() {
        rooms.put("a", List.of("b", "c", "d"));
        store.register("a", "phoneA");
        store.register("c", "phoneC");  // b 는 폰 없음, c 는 발송 실패
        store.register("d", "phoneD");
        BriefPushService partly = new BriefPushService(store, (token, title, body, data) -> {
            if (token.equals("phoneC")) {
                throw new IllegalStateException("발송 실패");
            }
            return sent.add(new Sent(token, title, body, data));
        }, fakeRoommates, 50);

        partly.send("a", 3L, "설하캠핑장", true);
        partly.stop();

        assertEquals(List.of("phoneA", "phoneD"), sentTokens());
    }

    @Test
    void 모임방을_못_찾아도_본인에게는_보낸다() {
        store.register("a", "phoneA");
        BriefPushService noDb = new BriefPushService(store, fakeSender, loginId -> {
            throw new IllegalStateException("DB 오류");
        }, 50);

        assertDoesNotThrow(() -> noDb.send("a", 3L, "설하캠핑장", true));
        noDb.stop();

        assertEquals(List.of("phoneA"), sentTokens());
    }
}
