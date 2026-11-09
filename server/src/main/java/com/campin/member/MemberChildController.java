package com.campin.member;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 내 아이 나이대 API (로그인 필요 — SecurityConfig 의 /api/member/**)
@RestController
@RequestMapping("/api/member/children")
@RequiredArgsConstructor
public class MemberChildController {

    private final MemberChildService memberChildService;

    // 불러오기: GET /api/member/children → ["preschool", ...]
    @GetMapping
    public ApiResponse<List<String>> find(Authentication authentication) {
        return ApiResponse.ok(memberChildService.find(authentication.getName()));
    }

    // 저장(통째로 바꾸기): PUT /api/member/children
    @PutMapping
    public ApiResponse<List<String>> save(@Valid @RequestBody MemberChildRequest request,
                                          Authentication authentication) {
        return ApiResponse.ok(memberChildService.save(authentication.getName(), request.ageGroups()));
    }
}
