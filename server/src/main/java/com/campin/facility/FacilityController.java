package com.campin.facility;

import java.util.List;

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

    // 지도 화면 안의 시설 (탐색 지도 "지도 설정"에서 켠 종류):
    // GET /api/facilities?types=er,pharmacy&swLat=37.9&swLng=128.0&neLat=38.2&neLng=128.4
    // [{ id, type, name, lat, lng, dummy }] — 화면 가운데에서 가까운 순 최대 300곳
    @GetMapping
    public ApiResponse<List<FacilityPin>> inBounds(@RequestParam(required = false) String types,
                                                   @RequestParam(required = false) Double swLat,
                                                   @RequestParam(required = false) Double swLng,
                                                   @RequestParam(required = false) Double neLat,
                                                   @RequestParam(required = false) Double neLng) {
        return ApiResponse.ok(facilityService.inBounds(types, swLat, swLng, neLat, neLng));
    }

    // 시설 상세: GET /api/facilities/3?campId=1 (campId: 어느 캠핑장에서 왔는지 → 거리·시간)
    @GetMapping("/{id}")
    public ApiResponse<FacilityDetailResponse> detail(@PathVariable Long id,
                                                      @RequestParam(required = false) Long campId) {
        return ApiResponse.ok(facilityService.detail(id, campId));
    }
}
