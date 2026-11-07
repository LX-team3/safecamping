package com.campin.region;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 탐색 탭 지역 선택 시트 API
@RestController                  // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    // 전국 시·도: GET /api/regions/sidos
    @GetMapping("/sidos")
    public ApiResponse<List<SidoResponse>> sidos() {
        return ApiResponse.ok(regionService.sidos());
    }

    // 한 시·도의 시·군·구: GET /api/regions/sidos/10/sigungus
    @GetMapping("/sidos/{sidoId}/sigungus")
    public ApiResponse<List<SigunguResponse>> sigungus(@PathVariable Long sidoId) {
        return ApiResponse.ok(regionService.sigungus(sidoId));
    }

    // 시·군·구 검색: GET /api/regions/sigungus?q=인제
    @GetMapping("/sigungus")
    public ApiResponse<List<SigunguResponse>> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(regionService.search(q));
    }
}
