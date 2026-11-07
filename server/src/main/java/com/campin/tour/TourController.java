package com.campin.tour;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 관광지 상세 화면 API
@RestController
@RequestMapping("/api/tours")
@RequiredArgsConstructor
public class TourController {

    private final TourService tourService;

    // 관광지 상세: GET /api/tours/1?campId=1 (campId: 어느 캠핑장에서 왔는지 → 차로 몇 분)
    @GetMapping("/{id}")
    public ApiResponse<TourDetailResponse> detail(@PathVariable Long id,
                                                  @RequestParam(required = false) Long campId) {
        return ApiResponse.ok(tourService.detail(id, campId));
    }
}
