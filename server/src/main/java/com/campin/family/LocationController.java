package com.campin.family;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 가족방 · 위치 공유 API. 회원 전용 (SecurityConfig 의 /api/family/**)
@RestController                           // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/family/locations")  // 이 클래스의 모든 주소 앞에 /api/family/locations 를 붙인다
@RequiredArgsConstructor                  // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class LocationController {

    private final LocationService locationService;

    // 우리 가족방 사람들의 마지막 위치: GET /api/family/locations
    // → [{"id": 1, "name": "이용효", "lat": 38.0699, "lng": 128.17, "me": true, "updatedAt": "..."}]
    @GetMapping
    public ApiResponse<List<LocationResponse>> list(Authentication auth) {
        return ApiResponse.ok(locationService.findAll(auth.getName()));
    }

    // 내 위치 저장(동의하기 · 새로고침): PUT /api/family/locations/me  {"lat": 38.0699, "lng": 128.17}
    // 주소에 회원 번호 대신 me 를 쓴다 → 로그인한 본인 위치만 바꿀 수 있다
    @PutMapping("/me")
    public ApiResponse<Void> saveMine(Authentication auth, @Valid @RequestBody LocationRequest body) {
        locationService.saveMine(auth.getName(), body);
        return ApiResponse.ok(null);
    }

    // 위치 공유 끄기: DELETE /api/family/locations/me
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMine(Authentication auth) {
        locationService.deleteMine(auth.getName());
        return ApiResponse.ok(null);
    }
}
