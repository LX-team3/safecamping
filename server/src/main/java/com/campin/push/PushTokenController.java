package com.campin.push;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 폰 주소(기기 토큰) 등록 API. 안드로이드 앱만 부른다 (client/src/utils/pushNotify.js)
// /api/member/** 는 SecurityConfig 에서 로그인 필요로 막아 두었다 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/push-token")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenStore store;

    // 등록: POST /api/member/push-token  {"token":"..."}  (로그인했을 때 · 로그인된 앱을 다시 켰을 때)
    @PostMapping
    public ApiResponse<Void> register(Authentication authentication, @Valid @RequestBody PushTokenRequest body) {
        store.register(authentication.getName(), body.token());
        return ApiResponse.ok(null);
    }

    // 삭제: DELETE /api/member/push-token  {"token":"..."}  (로그아웃하기 직전)
    // 토큰을 주소가 아니라 본문으로 받는 이유: 주소는 접속 기록(로그)에 남기 쉽다
    @DeleteMapping
    public ApiResponse<Void> remove(Authentication authentication, @Valid @RequestBody PushTokenRequest body) {
        store.remove(authentication.getName(), body.token());
        return ApiResponse.ok(null);
    }
}
