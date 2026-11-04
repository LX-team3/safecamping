package com.campin.member;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import jakarta.servlet.http.HttpServletRequest;

// "로그인 상태 유지" 쿠키(remember-me)를 만들고 읽는다
// 기본 remember-me 는 폼 파라미터로 체크 여부를 보는데, 우리는 JSON 으로 로그인하므로
// 컨트롤러가 request 속성(KEEP_ATTR)에 체크 여부를 넣어 주고 여기서 그 값을 본다
public class KeepLoginRememberMeServices extends TokenBasedRememberMeServices {

    public static final String KEEP_ATTR = "keepLogin";
    private static final int VALID_SECONDS = 14 * 24 * 60 * 60; // 14일

    public KeepLoginRememberMeServices(String key, UserDetailsService userDetailsService) {
        super(key, userDetailsService, RememberMeTokenAlgorithm.SHA256);
        setTokenValiditySeconds(VALID_SECONDS);
    }

    @Override
    protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
        return Boolean.TRUE.equals(request.getAttribute(KEEP_ATTR));
    }
}
