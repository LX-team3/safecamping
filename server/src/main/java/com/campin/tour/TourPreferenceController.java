package com.campin.tour;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;
import com.campin.tour.TourPreferenceOptions.Question;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 여행 취향 API. 질문은 비회원도 받고, 저장·불러오기는 /api/member/** 라서 로그인이 필요하다 (SecurityConfig)
@RestController  // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequiredArgsConstructor
public class TourPreferenceController {

    private final TourPreferenceService service;

    @GetMapping("/api/tour-preferences/questions")
    public ApiResponse<List<Question>> questions() {
        return ApiResponse.ok(service.questions());
    }

    @GetMapping("/api/member/tour-preferences")
    public ApiResponse<TourPreferenceResponse> get(Authentication authentication) {
        return ApiResponse.ok(service.get(authentication.getName()));
    }

    // @Valid: TourPreferenceRequest 의 @NotNull 검사를 실행한다
    @PutMapping("/api/member/tour-preferences")
    public ApiResponse<TourPreferenceResponse> save(Authentication authentication,
                                                    @Valid @RequestBody TourPreferenceRequest body) {
        return ApiResponse.ok(service.save(authentication.getName(), body));
    }
}
