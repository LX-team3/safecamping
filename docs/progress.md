# 진행 상황

## 2026-11-04 로그인·회원가입 (Spring Security, 세션 방식)
- 백엔드: `/api/auth` (check-id·signup·login·logout·me), `SignupController`·`LoginController`, `MemberMapper`, `MemberDetailsService`, `SecurityConfig` 수정, `ApiResponse`·`GlobalExceptionHandler` 추가
- DB: `db/schema.sql` 에 `member` 테이블 추가 — **DB 에 직접 실행해야 함** (테이블 이름은 단수로 통일)
- 프론트: `/login` (①-3), `/signup` (①-4), `stores/auth.js`, `utils/api.js`, `styles/tokens.css`. 메인은 임시 화면
- 확인: `mvnw test` 통과 (5개), `npm run build` 통과, curl 로 401·400·로그아웃 응답 확인
- 확인 못 함: 실제 가입·로그인 (member 테이블이 아직 DB 에 없음), 브라우저 화면
- 백엔드 패키지 정리: `com.campin` 아래 `member/`(로그인·회원가입), `common/`, `dbtest/`(연결 확인) — 테스트 5개 통과
- CSRF 방어 켬 (`csrf.spa()`) — curl 로 토큰 없으면 403, 있으면 통과 확인
- 로그인할 때 세션 id·CSRF 토큰 새로 발급 (`sessionAuthenticationStrategy`), requestCache 끔 → 로그인 안 한 요청에 세션 안 생김 (curl 확인). 토큰 교체는 member 테이블이 없어 확인 못 함
- 남은 일: 주소 검색 연결(지금은 직접 입력), 오류 문구 색(토큰에 없음 → 정해야 함), ①-1 메인
