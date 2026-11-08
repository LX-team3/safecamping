package com.campin.mycamping;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 내 캠핑 API. /api/member/** 는 SecurityConfig 에서 로그인 필요 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/camp-plans")
@RequiredArgsConstructor
public class CampPlanController {

    private final CampPlanService service;

    // 내가 등록한 캠핑 (체크인 빠른 순): GET /api/member/camp-plans
    @GetMapping
    public ApiResponse<List<CampPlanResponse>> list(Authentication authentication) {
        return ApiResponse.ok(service.findAll(authentication.getName()));
    }

    // 등록: POST /api/member/camp-plans  {"campId":1,"campName":"설하캠핑장","place":"강원 인제군 북면","checkIn":"2026-11-14","checkOut":"2026-11-16","memo":""}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampPlanResponse> add(Authentication authentication, @Valid @RequestBody CampPlanRequest body) {
        return ApiResponse.ok(service.add(authentication.getName(), body));
    }

    // 삭제: DELETE /api/member/camp-plans/3
    @DeleteMapping("/{planId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long planId) {
        service.delete(authentication.getName(), planId);
        return ApiResponse.ok(null);
    }
}
