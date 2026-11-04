package com.campin.member;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// ①-3 로그인 화면 API. 로그아웃은 SecurityConfig 의 logout 설정이 처리한다
@RestController                 // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/auth")    // 이 클래스의 모든 주소 앞에 /api/auth 를 붙인다
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final KeepLoginRememberMeServices rememberMeServices;
    // 로그인 순간 세션 id 와 CSRF 토큰을 새로 바꾼다 (SecurityConfig 의 sessionAuthenticationStrategy)
    // → 로그인 전에 알려진 값을 가로채 쓰는 공격(세션 고정)을 막는다
    private final SessionAuthenticationStrategy sessionStrategy;

    // 로그인: POST /api/auth/login
    // 아이디·비밀번호가 틀리면 authenticate() 가 예외를 던지고 GlobalExceptionHandler 가 401 로 바꾼다
    @PostMapping("/login")
    public ApiResponse<MemberResponse> login(@Valid @RequestBody LoginRequest body,
                                             HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));

        sessionStrategy.onAuthentication(authentication, request, response);

        // 로그인 정보를 세션에 저장 → 다음 요청부터 JSESSIONID 쿠키로 로그인 상태가 유지된다
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        if (Boolean.TRUE.equals(body.keep())) {
            request.setAttribute(KeepLoginRememberMeServices.KEEP_ATTR, true);
            rememberMeServices.loginSuccess(request, response, authentication);
        }
        return ApiResponse.ok(loginService.findMember(authentication.getName()));
    }

    // 지금 로그인한 회원: GET /api/auth/me (로그인 안 했으면 SecurityConfig 가 401)
    @GetMapping("/me")
    public ApiResponse<MemberResponse> me(Authentication authentication) {
        return ApiResponse.ok(loginService.findMember(authentication.getName()));
    }
}
