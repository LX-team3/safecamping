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

// 내 정보 · 비상연락처 API. 로그인한 회원 자신의 것만 다룬다 (주소에 회원 번호를 받지 않음)
// /api/member/** 는 SecurityConfig 에서 로그인 필요로 막아 두었다 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/emergency-contacts")
@RequiredArgsConstructor
public class EmergencyContactController {

    private final EmergencyContactService service;

    @GetMapping
    public ApiResponse<List<EmergencyContact>> get(Authentication authentication) {
        return ApiResponse.ok(service.get(authentication.getName()));
    }

    // @Valid: EmergencyContactRequest 의 @NotNull·@Size 검사를 실행한다 (틀리면 400)
    @PutMapping
    public ApiResponse<List<EmergencyContact>> update(Authentication authentication,
                                                      @Valid @RequestBody EmergencyContactRequest body) {
        return ApiResponse.ok(service.update(authentication.getName(), body));
    }
}
