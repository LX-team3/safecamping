package com.campin.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PushTokenStoreTest {

    private final PushTokenStore store = new PushTokenStore();

    @Test
    void 등록한_회원의_폰을_찾는다() {
        store.register("a", "phone1");
        assertEquals(Optional.of("phone1"), store.find("a"));
        assertTrue(store.find("b").isEmpty());
    }

    @Test
    void 같은_폰을_다시_등록해도_그대로다() {
        store.register("a", "phone1");
        store.register("a", "phone1");
        assertEquals(Optional.of("phone1"), store.find("a"));
    }

    @Test
    void 다른_폰에서_로그인하면_나중_폰으로_바뀐다() {
        store.register("a", "phone1");
        store.register("a", "phone2");
        assertEquals(Optional.of("phone2"), store.find("a"));
    }

    @Test
    void 같은_폰에서_다른_회원이_로그인하면_앞_회원의_연결은_지워진다() {
        store.register("a", "phone1");
        store.register("b", "phone1");
        assertTrue(store.find("a").isEmpty());
        assertEquals(Optional.of("phone1"), store.find("b"));
    }

    @Test
    void 로그아웃하면_지워진다() {
        store.register("a", "phone1");
        store.remove("a", "phone1");
        assertTrue(store.find("a").isEmpty());
    }

    @Test
    void 예전_폰에서_로그아웃해도_지금_폰은_남는다() {
        store.register("a", "phone1");
        store.register("a", "phone2");
        store.remove("a", "phone1");
        assertEquals(Optional.of("phone2"), store.find("a"));
    }
}
