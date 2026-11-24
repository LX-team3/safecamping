package com.campin.family;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 가족 캠핑 API. 회원 전용 (SecurityConfig 의 /api/family/**). 읽기만 있다
@RestController                              // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/family/camp-plans")    // 이 클래스의 모든 주소 앞에 /api/family/camp-plans 를 붙인다
@RequiredArgsConstructor                     // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class FamilyCampPlanController {

    private final FamilyCampPlanService service;

    // 가족 구성원의 캠핑 (나 빼고, 체크인 빠른 순): GET /api/family/camp-plans → 가족방이 없으면 빈 목록
    @GetMapping
    public ApiResponse<List<FamilyCampPlanResponse>> list(Authentication auth) {
        return ApiResponse.ok(service.findOthers(auth.getName()));
    }
}
