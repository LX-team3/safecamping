package com.campin.survey;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 1분 진단 API
// - 질문은 누구나 (로그인 없이도 진단할 수 있다 → 로그인 안 한 사람의 답은 브라우저에만 둔다)
// - 내 답 읽기·저장은 /api/member/** 라서 SecurityConfig 가 로그인을 요구한다 → authentication 은 null 이 아니다
@RestController
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService service;

    @GetMapping("/api/survey/questions")
    public ApiResponse<List<SurveyQuestion>> questions() {
        return ApiResponse.ok(service.questions());
    }

    // 진단한 적이 없으면 data: null
    @GetMapping("/api/member/survey")
    public ApiResponse<SurveyAnswers> get(Authentication authentication) {
        return ApiResponse.ok(service.get(authentication.getName()));
    }

    @PutMapping("/api/member/survey")
    public ApiResponse<SurveyAnswers> save(Authentication authentication, @RequestBody SurveyAnswers body) {
        return ApiResponse.ok(service.save(authentication.getName(), body));
    }
}
