package com.campin.alert;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 알림 API (내 정보 · 알림). 로그인 없이도 볼 수 있다 (재난 정보는 누구에게나 필요)
// GET /api/alerts?sido=강원  또는  ?lat=37.7&lon=127.9 (내 위치)  또는  파라미터 없음(전국)
@RestController
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    // @RequestParam(required = false): 값이 없어도 오류가 아니라 null 로 받는다
    @GetMapping("/api/alerts")
    public ApiResponse<AlertResponse> alerts(@RequestParam(required = false) String sido,
                                             @RequestParam(required = false) Double lat,
                                             @RequestParam(required = false) Double lon) {
        return ApiResponse.ok(service.find(sido, lat, lon));
    }
}
