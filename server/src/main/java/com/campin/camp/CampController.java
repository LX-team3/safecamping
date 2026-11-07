package com.campin.camp;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 탐색 탭(목록·지도) 캠핑장 API
@RestController              // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/camps")
@RequiredArgsConstructor
public class CampController {

    private final CampService campService;

    // 목록: GET /api/camps?sido=강원특별자치도&sigungu=인제군&campType=auto&sort=score
    // (시·군·구 이름은 전국에서 겹칠 수 있어 시·도도 함께 받는다. 예: 고성군은 강원·경남에 둘 다 있음)
    // @RequestParam(required = false): 값이 없어도 되는 주소 뒤 ?조건 — 없으면 null
    @GetMapping
    public ApiResponse<List<CampResponse>> list(@RequestParam(required = false) String sido,
                                                @RequestParam(required = false) String sigungu,
                                                @RequestParam(required = false) String campType,
                                                @RequestParam(required = false) String sort) {
        return ApiResponse.ok(campService.list(sido, sigungu, campType, sort));
    }

    // 내 유형 추천: GET /api/camps/recommend?sido=강원특별자치도&sigungu=인제군&campType=auto&sort=score&limit=5
    @GetMapping("/recommend")
    public ApiResponse<CampRecommendResponse> recommend(@RequestParam(required = false) String sido,
                                                        @RequestParam(required = false) String sigungu,
                                                        @RequestParam(required = false) String campType,
                                                        @RequestParam(required = false) String sort,
                                                        @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(campService.recommend(sido, sigungu, campType, sort, limit));
    }

    // 캠핑장 상세: GET /api/camps/1
    // @PathVariable: 주소의 {id} 자리 값을 받는다. /recommend 처럼 글자가 정확히 맞는 주소가 먼저 잡힌다
    @GetMapping("/{id}")
    public ApiResponse<CampDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(campService.detail(id));
    }

    // 관광지 탭: GET /api/camps/1/tours?prefer=trekking,mountain
    @GetMapping("/{id}/tours")
    public ApiResponse<List<CampTourResponse>> tours(@PathVariable Long id,
                                                     @RequestParam(required = false) List<String> prefer) {
        return ApiResponse.ok(campService.tours(id, prefer));
    }
}
