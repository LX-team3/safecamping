package com.campin.survey;

import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// ② 1분 진단(code=diagnosis) · 관광지 탭 여행 취향(code=travel) 설문 API
@RestController                 // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/surveys") // 이 클래스의 모든 주소 앞에 /api/surveys 를 붙인다
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    // 설문 내용: GET /api/surveys/diagnosis
    // @PathVariable: 주소의 {code} 자리 값을 받는다
    @GetMapping("/{code}")
    public ApiResponse<SurveyResponse> getSurvey(@PathVariable String code) {
        return ApiResponse.ok(surveyService.getSurvey(code));
    }

    // 답변 제출: POST /api/surveys/diagnosis/answers → { saved: true/false }
    // 비회원도 낼 수 있다 ("로그인 없이 둘러보기"). 회원이면 DB 에 저장
    @PostMapping("/{code}/answers")
    public ApiResponse<Map<String, Boolean>> submit(@PathVariable String code,
                                                    @Valid @RequestBody SurveyAnswerRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(surveyService.submit(code, request, loginIdOf(authentication)));
    }

    // 내 지난 답변: GET /api/surveys/travel/answers/me (로그인 안 했으면 SecurityConfig 가 401)
    @GetMapping("/{code}/answers/me")
    public ApiResponse<Map<String, List<String>>> myAnswers(@PathVariable String code, Authentication authentication) {
        return ApiResponse.ok(surveyService.myAnswers(code, authentication.getName()));
    }

    // 로그인 안 한 요청은 authentication 이 null 이거나 '익명' 이다 → 둘 다 비회원으로 본다
    private String loginIdOf(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
