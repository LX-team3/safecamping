package com.campin.common;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import com.campin.member.KeepLoginRememberMeServices;

import jakarta.servlet.http.HttpServletResponse;

@Configuration       // 스프링: 이 클래스의 @Bean 메서드들을 설정으로 읽는다
@EnableWebSecurity   // 스프링 시큐리티: 아래 filterChain 설정을 켠다
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // 로그인 방식: 세션(쿠키). 로그인 API(LoginController)가 성공하면 세션에 로그인 정보를 저장하고,
    // 브라우저는 JSESSIONID 쿠키로 다음 요청부터 로그인 상태를 알린다
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           KeepLoginRememberMeServices rememberMeServices,
                                           CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
            // CORS는 반드시 Security 안에서 설정
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // CSRF 방어: 세션 쿠키는 다른 사이트에서 보낸 요청에도 브라우저가 자동으로 붙이므로 꼭 켠다
            // spa(): 서버가 XSRF-TOKEN 쿠키를 내려 주고, POST·PUT·DELETE 요청은 그 값을 X-XSRF-TOKEN 헤더로 보내야 통과
            // (axios 는 같은 주소로 보낼 때 이 쿠키를 읽어 헤더에 자동으로 넣는다 → 프론트 코드 수정 없음)
            // 토큰 저장소는 아래 @Bean 으로 따로 만든다 → 로그인할 때 LoginController 가 같은 저장소로 토큰을 새로 바꾼다
            .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))

            // 로그인 전에 가려던 주소를 세션에 저장해 두는 기능(로그인 후 되돌아가기용)은 끈다
            // 화면 이동은 Vue 가 맡으므로 필요 없고, 켜 두면 로그인 안 한 사람에게도 세션이 생긴다
            .requestCache(cache -> cache.disable())

            // REST API + Vue 구조라서 기본 로그인 화면은 쓰지 않는다
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            // 로그인 정보는 세션에 저장한다 (LoginController 도 같은 저장소를 쓴다)
            .securityContext(context -> context.securityContextRepository(securityContextRepository))

            // "로그인 상태 유지" 체크 시 세션이 끝나도 remember-me 쿠키로 다시 로그인된다
            .rememberMe(remember -> remember.rememberMeServices(rememberMeServices))

            // 로그아웃: POST /api/auth/logout → 세션·쿠키를 지우고 JSON 으로 답한다
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) ->
                    writeJson(response, HttpServletResponse.SC_OK,
                        "{\"success\":true,\"data\":null,\"message\":null}"))
            )

            // 로그인이 필요한 주소에 로그인 없이 오면 로그인 화면 대신 401 JSON 을 돌려준다
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeJson(response, HttpStatus.UNAUTHORIZED.value(),
                        "{\"success\":false,\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))
                // CSRF 토큰이 없거나 틀린 요청 등 → 403 JSON (기본값은 빈 화면)
                .accessDeniedHandler((request, response, e) ->
                    writeJson(response, HttpStatus.FORBIDDEN.value(),
                        "{\"success\":false,\"data\":null,\"message\":\"요청을 처리할 수 없습니다. 새로고침 후 다시 시도해 주세요.\"}"))
            )

            // 요청별 권한 설정
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/test/**", "/api/auth/**").permitAll()
                // "로그인 없이 둘러보기"가 있으므로 나머지는 아직 전부 허용
                // 회원 전용 기능(결과 저장 등)을 만들면 그 주소를 위 authenticated() 에 추가한다
                .anyRequest().permitAll()
            );

        return http.build();
    }

    // CSRF 토큰 저장소: XSRF-TOKEN 쿠키 (자바스크립트가 읽어야 하므로 HttpOnly 끔 — spa() 와 같은 설정)
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    // 로그인 성공 순간에 할 일: 세션 id 바꾸기 + CSRF 토큰 새로 발급
    // (시큐리티 기본 로그인이 자동으로 하는 일인데, 우리는 컨트롤러에서 로그인하므로 직접 부른다)
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    // 세션에 로그인 정보를 넣고 꺼내는 저장소
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // 아이디·비밀번호를 확인하는 곳. MemberDetailsService 로 회원을 찾고 BCrypt 로 비밀번호를 비교한다
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    // remember-me 쿠키 서명용 키는 환경변수 REMEMBER_ME_KEY 에서 읽는다 (코드·properties 에 쓰지 않는다)
    // 없으면 실행할 때마다 새로 만든다 → 서버를 다시 켜면 "로그인 상태 유지"가 풀린다 (개발 중에는 괜찮음)
    @Bean
    public KeepLoginRememberMeServices rememberMeServices(
            @Value("${REMEMBER_ME_KEY:}") String key, UserDetailsService userDetailsService) {
        if (key.isBlank()) {
            log.warn("REMEMBER_ME_KEY 환경변수가 없어 임시 키를 씁니다. 서버를 다시 켜면 로그인 상태 유지가 풀립니다.");
            key = UUID.randomUUID().toString();
        }
        return new KeepLoginRememberMeServices(key, userDetailsService);
    }

    // CORS 설정: Vue 개발 서버(5173)에서 오는 요청 허용
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // 회원가입 시 비밀번호 암호화, 로그인 시 비밀번호 비교에 쓴다
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 필터 단계(컨트롤러 밖)에서 ApiResponse 와 같은 모양의 JSON 을 직접 쓴다
    private static void writeJson(HttpServletResponse response, int status, String json) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json);
    }
}
