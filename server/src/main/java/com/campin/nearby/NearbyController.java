package com.campin.nearby;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 내 주변 탭 API. 로그인 없이도 된다 (안전 정보는 누구에게나 필요)
@RestController              // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/nearby")
@RequiredArgsConstructor
public class NearbyController {

    private final NearbyService nearbyService;

    // 안전점수 + 가까운 시설: GET /api/nearby?lat=38.14&lng=128.2
    // Double 로 받아서 값이 빠지면 서비스가 알기 쉬운 문구로 400 을 돌려준다
    @GetMapping
    public ApiResponse<NearbyResponse> find(@RequestParam(required = false) Double lat,
                                            @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(nearbyService.find(lat, lng));
    }

    // "지도에 보일 것" 시트의 안전 시설 목록: GET /api/nearby/layers
    @GetMapping("/layers")
    public ApiResponse<List<NearbyLayerResponse>> layers() {
        return ApiResponse.ok(nearbyService.layers());
    }
}
