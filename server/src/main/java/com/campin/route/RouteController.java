package com.campin.route;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 캠핑장 → 응급실·소방서·경찰서 경로 API. 로그인 없이도 된다 (안전 정보는 누구에게나 필요)
@RestController              // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    // GET /api/camps/1/routes
    // @PathVariable: 주소의 {id} 자리 값을 받는다
    @GetMapping("/api/camps/{id}/routes")
    public ApiResponse<RouteResponse> routes(@PathVariable Long id) {
        return ApiResponse.ok(routeService.routes(id));
    }
}
