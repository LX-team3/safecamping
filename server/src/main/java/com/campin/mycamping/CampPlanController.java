package com.campin.mycamping;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;
import com.campin.push.BriefPushService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 내 캠핑 API. /api/member/** 는 SecurityConfig 에서 로그인 필요 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/camp-plans")
@RequiredArgsConstructor
public class CampPlanController {

    private final CampPlanService service;
    private final BriefPushService briefPush;

    // 내가 등록한 캠핑 (체크인 빠른 순): GET /api/member/camp-plans
    @GetMapping
    public ApiResponse<List<CampPlanResponse>> list(Authentication authentication) {
        return ApiResponse.ok(service.findAll(authentication.getName()));
    }

    // 등록: POST /api/member/camp-plans  {"campId":1,"campName":"설하캠핑장","place":"강원 인제군 북면","checkIn":"2026-11-14","checkOut":"2026-11-16","memo":""}
    // 캠핑장이 아닌 자리(차박): campId 대신 "lat":38.057,"lng":128.166 (campName 은 지번 등 이름)
    // 웹에서 저장하면 "notifyPhone":true 도 보낸다 → 10초 뒤 내 폰으로 브리핑 푸시 (저장에 성공했을 때만)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampPlanResponse> add(Authentication authentication, @Valid @RequestBody CampPlanRequest body) {
        CampPlanResponse saved = service.add(authentication.getName(), body);
        if (Boolean.TRUE.equals(body.notifyPhone())) {
            briefPush.schedule(authentication.getName(), saved.id(), saved.campName());
        }
        return ApiResponse.ok(saved);
    }

    // 고치기: PUT /api/member/camp-plans/3  {"checkIn":"2026-11-24","checkOut":"2026-11-27","memo":"성인 2명 · 어린이 0명 · 친구·연인 · 자동차"}
    // 예약 정보 확인 화면이 이미 등록한 일정을 고칠 때 쓴다 (같은 일정을 두 번 등록하지 않게)
    @PutMapping("/{planId}")
    public ApiResponse<CampPlanResponse> update(Authentication authentication, @PathVariable Long planId,
                                                @Valid @RequestBody CampPlanUpdateRequest body) {
        return ApiResponse.ok(service.update(authentication.getName(), planId, body));
    }

    // 삭제: DELETE /api/member/camp-plans/3
    @DeleteMapping("/{planId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long planId) {
        service.delete(authentication.getName(), planId);
        return ApiResponse.ok(null);
    }
}
