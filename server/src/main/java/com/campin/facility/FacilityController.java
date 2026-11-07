package com.campin.facility;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 시설 상세 화면 API
@RestController                     // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService facilityService;

    // 시설 상세: GET /api/facilities/3?campId=1 (campId: 어느 캠핑장에서 왔는지 → 거리·시간)
    @GetMapping("/{id}")
    public ApiResponse<FacilityDetailResponse> detail(@PathVariable Long id,
                                                      @RequestParam(required = false) Long campId) {
        return ApiResponse.ok(facilityService.detail(id, campId));
    }
}
