package com.campin.chabak;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 차박 판정 API. 로그인 없이 된다 (내 주변 탭과 같음)
@RestController              // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/chabak")
@RequiredArgsConstructor
public class ChabakController {

    private final ChabakService chabakService;

    // 지도에서 찍은 지점의 차박 판정: GET /api/chabak/verdict?lat=38.07&lng=128.32
    // 응답 필드는 ChabakVerdictResponse 참고
    @GetMapping("/verdict")
    public ApiResponse<ChabakVerdictResponse> verdict(@RequestParam(required = false) Double lat,
                                         @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(chabakService.verdict(lat, lng));
    }

    // 찍은 지점에서 가까운 안전 시설 (차박 지도 "지도에 보일 것" 레이어): GET /api/chabak/facilities?lat=38.07&lng=128.32
    // 종류별 3곳씩 [{ id, type, name, lat, lng, distanceKm, dummy }], 가까운 순
    @GetMapping("/facilities")
    public ApiResponse<List<ChabakFacility>> facilities(@RequestParam(required = false) Double lat,
                                                        @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(chabakService.facilities(lat, lng));
    }

    // 찍은 지점의 안전점수: GET /api/chabak/score?lat=38.07&lng=128.32
    // 직선거리 + raw.score_weights 기준 (예전 캠핑장 점수 방식). 지금 캠핑장 점수(camp.safety_score)는 카카오 차 시간 + ScoreRules 라 기준이 다르다
    // { score, grade: safe·caution·danger·unknown, erDriveMin(추정), items: [{ type, distM, weight, part }] }
    @GetMapping("/score")
    public ApiResponse<ChabakScoreResponse> score(@RequestParam(required = false) Double lat,
                                                  @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(chabakService.score(lat, lng));
    }

    // 차박 지도 레이어 (위쪽 칩): GET /api/chabak/layer?type=no
    // type: no 불가 구역 · caution 주의 구역 (면: geometry) / open 차박 후보지 · camp 등록 야영장 (점: lat·lng)
    // [{ id, name, lat, lng, score, grade, area, imageUrl, erDriveMin, geometry }] (ChabakLayerItem). 그 밖의 type 은 400
    @GetMapping("/layer")
    public ApiResponse<List<ChabakLayerItem>> layer(@RequestParam(required = false) String type) {
        return ApiResponse.ok(chabakService.layer(type));
    }
}
