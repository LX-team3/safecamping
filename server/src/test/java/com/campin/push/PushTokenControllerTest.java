package com.campin.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.campin.common.GlobalExceptionHandler;

// 이 컨트롤러만 띄워 요청 모양을 확인한다 (서버 전체 · DB 는 쓰지 않음. 로그인 검사는 SecurityConfig 몫이라 여기서는 보지 않는다)
class PushTokenControllerTest {

    private static final String URL = "/api/member/push-token";
    private static final Authentication A =
            UsernamePasswordAuthenticationToken.authenticated("a", null, AuthorityUtils.NO_AUTHORITIES);

    private final PushTokenStore store = new PushTokenStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PushTokenController(store))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void 로그인한_회원의_폰으로_등록하고_지운다() throws Exception {
        String body = "{\"token\":\"phone1\"}";

        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertEquals(Optional.of("phone1"), store.find("a"));

        mvc.perform(delete(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertTrue(store.find("a").isEmpty());
    }

    @Test
    void 본문에_다른_아이디를_넣어도_그_회원으로_등록되지_않는다() throws Exception {
        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"phone1\",\"loginId\":\"b\"}"));
        assertTrue(store.find("b").isEmpty());
    }

    @Test
    void 토큰이_비어_있으면_400() throws Exception {
        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        assertTrue(store.find("a").isEmpty());
    }

    @Test
    void 요청을_로그에_찍어도_토큰이_보이지_않는다() {
        assertFalse(new PushTokenRequest("phone1").toString().contains("phone1"));
    }
}
