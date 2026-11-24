package com.campin.mycamping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.campin.common.GlobalExceptionHandler;
import com.campin.common.InvalidRequestException;
import com.campin.push.BriefPushService;
import com.campin.push.PushTokenStore;

// 일정을 저장할 때 폰 알림을 예약하는지 확인한다 (가짜 서비스라 DB · Firebase 는 쓰지 않음)
class CampPlanControllerTest {

    private static final String URL = "/api/member/camp-plans";
    private static final Authentication A =
            UsernamePasswordAuthenticationToken.authenticated("a", null, AuthorityUtils.NO_AUTHORITIES);

    private final List<String> scheduled = new ArrayList<>();
    private boolean saveFails = false;

    private final CampPlanService fakeService = new CampPlanService(null, null, null, null) {
        @Override public CampPlanResponse add(String loginId, CampPlanRequest req) {
            if (saveFails) {
                throw new InvalidRequestException("이미 같은 날짜로 등록한 캠핑입니다.");
            }
            return saved(req.campName());
        }
        @Override public CampPlanResponse update(String loginId, Long planId, CampPlanUpdateRequest req) {
            return saved("설하캠핑장");
        }
    };
    private final BriefPushService fakePush = new BriefPushService(new PushTokenStore(), (token, title, body, data) -> true) {
        @Override public void schedule(String loginId, Long planId, String campName) {
            scheduled.add(loginId + ":" + planId + ":" + campName);
        }
    };
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CampPlanController(fakeService, fakePush))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static CampPlanResponse saved(String campName) {
        return new CampPlanResponse(3L, 1L, campName, null, null, null,
                LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 27), null);
    }

    // notify: "true" · "false" 또는 null(값을 안 보냄)
    private static String body(String notify) {
        return "{\"campId\":1,\"campName\":\"설하캠핑장\",\"checkIn\":\"2026-11-26\",\"checkOut\":\"2026-11-27\""
                + (notify == null ? "" : ",\"notifyPhone\":" + notify) + "}";
    }

    @Test
    void 웹에서_저장하면_로그인한_회원의_폰_알림을_예약한다() throws Exception {
        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content(body("true")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(3));
        assertEquals(List.of("a:3:설하캠핑장"), scheduled);
    }

    @Test
    void 앱에서_저장하거나_값이_없으면_예약하지_않는다() throws Exception {
        for (String notify : new String[] { "false", null }) {
            mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content(body(notify)))
                    .andExpect(status().isCreated());
        }
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void 저장에_실패하면_예약하지_않는다() throws Exception {
        saveFails = true;
        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content(body("true")))
                .andExpect(status().isBadRequest());
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void 일정을_고칠_때는_예약하지_않는다() throws Exception {
        mvc.perform(put(URL + "/3").principal(A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkIn\":\"2026-11-26\",\"checkOut\":\"2026-11-28\"}"))
                .andExpect(status().isOk());
        assertTrue(scheduled.isEmpty());
    }
}
