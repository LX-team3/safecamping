package com.campin.member;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// ①-4 회원가입 화면 API
@RestController                 // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/auth")    // 이 클래스의 모든 주소 앞에 /api/auth 를 붙인다
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    // 아이디 중복 확인: GET /api/auth/check-id?loginId=camper01
    @GetMapping("/check-id")
    public ApiResponse<Map<String, Boolean>> checkId(@RequestParam String loginId) {
        return ApiResponse.ok(Map.of("available", signupService.isLoginIdAvailable(loginId)));
    }

    // 회원가입: POST /api/auth/signup
    // @Valid: SignupRequest 의 @NotBlank·@Pattern 검사를 실행한다 (틀리면 GlobalExceptionHandler 가 400)
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(signupService.signup(request));
    }
}
