package com.campin.disaster;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 재난 대피 3D 지도(/disaster) API. 로그인 없이도 볼 수 있다 (재난 정보는 누구에게나 필요)
// GET /api/disaster/now → 인북천 캠핑장의 최근 3·12·24시간 비 + 대피 단계 + 인제군 기상특보
@RestController
@RequiredArgsConstructor
public class DisasterController {

    private final DisasterService service;

    @GetMapping("/api/disaster/now")
    public ApiResponse<DisasterNowResponse> now() {
        return ApiResponse.ok(service.now());
    }
}
