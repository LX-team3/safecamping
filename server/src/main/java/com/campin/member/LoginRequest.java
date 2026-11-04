package com.campin.member;

import jakarta.validation.constraints.NotBlank;

// 로그인 요청. keep 은 "로그인 상태 유지" 체크 여부
// Boolean(대문자)으로 받는다 — 값이 빠져도 요청 전체가 오류가 되지 않게 (backend/CLAUDE.md)
public record LoginRequest(
        @NotBlank(message = "아이디를 입력해 주세요.") String loginId,
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
        Boolean keep
) {
}
