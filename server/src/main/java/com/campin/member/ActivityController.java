package com.campin.member;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 내 정보 · 여행 취향 API. /api/member/** 는 SecurityConfig 에서 로그인 필요 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService service;

    // 선택지 6개 + 내가 고른 것
    @GetMapping
    public ApiResponse<ActivityResponse> get(Authentication authentication) {
        return ApiResponse.ok(service.get(authentication.getName()));
    }

    @PutMapping
    public ApiResponse<ActivityResponse> update(Authentication authentication, @Valid @RequestBody ActivityRequest body) {
        return ApiResponse.ok(service.update(authentication.getName(), body));
    }
}
