package com.campin.trip;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 탐색 탭 "누구와, 언제 가세요?" 시트 API
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    // 인원·날짜 검사: POST /api/trips/check
    // { "checkIn": "2026-11-14", "checkOut": "2026-11-15", "adults": 2, "kids": 1, "childAges": ["preschool"] }
    @PostMapping("/check")
    public ApiResponse<TripCheckResponse> check(@RequestBody TripCheckRequest request) {
        return ApiResponse.ok(tripService.check(request));
    }
}
