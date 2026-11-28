package com.campin.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.campin.common.GlobalExceptionHandler;
import com.campin.common.InvalidRequestException;
import com.campin.mycamping.ChecklistResponse;
import com.campin.mycamping.ChecklistService;

// 11단계 API: 본문(진단)을 서비스에 그대로 넘기는지, 응답 모양, 끝난 캠핑 400 (가짜 서비스라 DB · Claude 는 안 씀)
class BriefControllerTest {

    private static final String URL = "/api/member/camp-plans/3/brief";
    private static final Authentication A =
            UsernamePasswordAuthenticationToken.authenticated("a", null, AuthorityUtils.NO_AUTHORITIES);

    private final List<BriefSurvey> received = new ArrayList<>();
    private boolean past = false;

    private final BriefService fakeService = new BriefService(null, null, null, null) {
        @Override public BriefResult make(String loginId, Long planId, BriefSurvey survey) {
            if (past) throw new InvalidRequestException("끝난 캠핑은 브리핑을 만들지 않습니다.");
            received.add(survey);
            return new BriefResult(true, List.of(new BriefResult.Line("텐트 위 눈을 수시로 치워요",
                    List.of(new BriefResult.Source("JD-031", "행정안전부", 2016, "겨울철 안전 ②", "교육지도서", "12")))), null);
        }
    };
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new BriefController(fakeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void 진단을_담아_보내면_서비스에_넘기고_대비_행동을_돌려준다() throws Exception {
        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survey\":{\"who\":[\"child\"],\"kid\":\"baby\",\"site\":\"auto\",\"weak\":[\"일산화탄소\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ai").value(true))
                .andExpect(jsonPath("$.data.lines[0].text").value("텐트 위 눈을 수시로 치워요"))
                .andExpect(jsonPath("$.data.lines[0].sources[0].org").value("행정안전부"));
        assertEquals(new BriefSurvey(List.of("child"), "baby", "auto", List.of("일산화탄소")), received.get(0));
    }

    @Test
    void 본문이_없거나_survey_가_null_이면_진단_없이() throws Exception {
        mvc.perform(post(URL).principal(A)).andExpect(status().isOk());
        mvc.perform(post(URL).principal(A).contentType(MediaType.APPLICATION_JSON).content("{\"survey\":null}"))
                .andExpect(status().isOk());
        assertNull(received.get(0));
        assertNull(received.get(1));
    }

    @Test
    void 끝난_캠핑은_400() throws Exception {
        past = true;
        mvc.perform(post(URL).principal(A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("끝난 캠핑은 브리핑을 만들지 않습니다."));
    }

    // 끝난 캠핑 검사가 실제로 BriefContextService 에 있는지 (체크리스트 응답의 past 로 판단, 메모 조회 전에 멈춤)
    @Test
    void 상황_모으기에서_끝난_캠핑을_막는다() {
        ChecklistService fakeChecklist = new ChecklistService(null, null, null) {
            @Override public ChecklistResponse find(String loginId, Long planId) {
                return new ChecklistResponse(null, "가을", true, false, LocalDate.of(2026, 11, 1),
                        false, List.of(), null, null, List.of());
            }
        };
        var contextService = new BriefContextService(fakeChecklist, null, null);
        var e = assertThrows(InvalidRequestException.class, () -> contextService.collect("a", 3L, null));
        assertEquals("끝난 캠핑은 브리핑을 만들지 않습니다.", e.getMessage());
    }
}
