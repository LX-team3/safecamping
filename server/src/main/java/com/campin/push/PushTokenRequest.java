package com.campin.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 토큰 등록 · 삭제 요청. 회원 아이디는 받지 않는다 (로그인 세션에서 안다 → 남의 이름으로 등록할 수 없게)
// 길이 제한은 잘못된 큰 값을 막으려는 것 (Firebase 가 정한 길이는 아님)
public record PushTokenRequest(
        @NotBlank(message = "기기 토큰이 없습니다.")
        @Size(max = 4096, message = "기기 토큰이 너무 깁니다.")
        String token
) {
    // record 는 로그에 찍으면 값이 그대로 나온다 → 토큰(폰의 주소)이 로그에 남지 않게 가린다
    @Override
    public String toString() {
        return "PushTokenRequest[token=***]";
    }
}
