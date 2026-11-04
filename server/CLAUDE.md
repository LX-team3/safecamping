# server - 서버

루트 `CLAUDE.md` 의 공통 규칙을 함께 따른다.
상태: ② 설문 · ③ 지역·인원·날짜 API 작성 (DB 없이 자바 안 더미). 스택 Spring Boot 4.0 + MyBatis 4.0, Java 17
<!-- start.spring.io 에서 Boot 3.x 를 더 이상 고를 수 없고, 4.1 은 MyBatis 와 호환되지 않아 4.0 을 씀 -->

## 배운 언어 사용
- Spring Boot + MyBatis 기반 어노테이션을 쓴다면 해당하는 주석 작성

## 패키지 구조 (`com.campin`)
- 기본은 **화면(기능) 하나 = 패키지 하나**. 단, 같은 테이블을 같이 쓰는 화면들은 그 테이블 이름 패키지 하나로 묶는다 (예: 회원가입·로그인 → `member`)
- 패키지 안에 파일을 **바로** 넣는다. 안에 controller/·service/ 같은 하위 폴더를 만들지 않는다
- 파일 이름으로 역할을 구분한다: `XxxController`, `XxxService`, `XxxMapper`, `XxxRequest`/`XxxResponse`(요청·응답), 그 밖은 모델
- 여러 기능이 같이 쓰는 설정·응답·예외는 `common`
- data : DB 연결 전 임시 더미 (`SurveyDummyData`, `RegionDummyData`). DB 를 연결하면 mapper 로 바꾸고 지운다
```
common/   ApiResponse, InvalidRequestException, GlobalExceptionHandler, SecurityConfig
member/   # ①-3 로그인 · ①-4 회원가입 (member 테이블). 내 정보 수정·탈퇴도 여기에
          Member, MemberMapper, MemberResponse,
          SignupController, SignupService, SignupRequest,
          LoginController, LoginService, LoginRequest,
          MemberDetailsService, KeepLoginRememberMeServices
survey/   # ② 캠핑 유형 진단 (만들 때 같은 모양으로)
region/   # ③ 지역 찾기
map/      # ④ 캠핑지도
dbtest/   TestController, TestMapper   # DB 연결 확인용 임시 코드 (본보기 아님)
```

## 상세 의존성 목록
- rombok


## 명령어 (실행·빌드·테스트)
- Maven 설치 없이 Maven Wrapper 를 쓴다 (`backend/` 에서 실행)
- 실행: `.\mvnw.cmd spring-boot:run` → http://localhost:8080
- 테스트: `.\mvnw.cmd test` — 코드를 고친 뒤 반드시 통과 확인
- DB 연결 자동 설정은 `application.properties` 에서 꺼 둠 (DB 연결할 때 그 줄을 지운다)

## API 목록
| 주소 | 하는 일 |
|---|---|
| `GET /api/survey/options` | 설문 10문항 선택지 (id·이름·최대 개수·단독 선택) |
| `POST /api/survey/result` | 설문 답변 검사 → 결과 유형 (지금은 항상 `a`, `fixed: true`) |
| `GET /api/regions/camp-count?sido=32` | 시·군·구별 캠핑장 수 (더미, `dummy: true`). `sido` 없으면 전국 |
| `POST /api/trips/check` | 동행·날짜 검사 → 몇 박인지. 날짜는 `"2026-11-05"` 형식 |
| `GET /api/map/camps?grades=safe,mid` | 캠핑장 목록 (점수 높은 순, 등급 safe 70↑ / mid 40~70 / warn 40↓) |
| `GET /api/map/camps/{id}` | 캠핑장 상세 + 안전점수 항목 5개 |
| `GET /api/map/camps/compare?ids=c1,c2` | 관심 캠핑장 비교 (최대 3곳) |
| `GET /api/map/facilities?types=er,police` | 안전 시설 목록 (police·er·fire·store·toilet, 비우면 전부) |
| `GET /api/map/tours?kw=계곡` · `/api/map/tours/{id}` | 관광지 목록·상세 |
| `GET /api/auth/check-id?loginId=` | 아이디 중복 확인 → `{ available }` |
| `POST /api/auth/signup` | 회원가입 (비밀번호 BCrypt 저장, 연락처는 숫자만) → 201 |
| `POST /api/auth/login` | 로그인 `{ loginId, password, keep }` → 세션 쿠키, `keep` 이면 remember-me 쿠키(14일) |
| `POST /api/auth/logout` | 로그아웃 (SecurityConfig 의 logout 설정이 처리) |
| `GET /api/auth/me` | 지금 로그인한 회원. 로그인 안 했으면 401 |

- 로그인은 Spring Security **세션 방식**. remember-me 서명 키는 환경변수 `REMEMBER_ME_KEY` (없으면 실행마다 임시 키)
- CSRF 켜 둠 (`csrf.spa()`): POST·PUT·PATCH·DELETE 는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 한다 (axios 는 자동, 없으면 403). curl·Postman 으로 시험할 때는 먼저 GET 으로 쿠키를 받는다

- ④ 지도 데이터는 설계 파일 `AppMap.dc.html` 에서 뽑아 `resources/dummy/*.json` 에 둠 (캠핑장 54·시설 373·관광지 10). 길찾기는 카카오 API 연결 때 추가

## 응답 형식 (성공·실패 공통 형태)
- 모든 API 는 `common/ApiResponse` 로 감싸서 돌려준다
- 성공: `{ "success": true, "data": ..., "message": null }`
- 실패: `{ "success": false, "data": null, "message": "사용자에게 보여 줄 문구" }`

## 예외 처리 (에러 코드, 메시지 문구 톤)
- 예외는 `common/GlobalExceptionHandler` 한곳에서 응답으로 바꾼다. 컨트롤러에서 try-catch 하지 않는다
- 사용자 입력이 규칙에 맞지 않으면 서비스에서 `InvalidRequestException` 을 던진다 → 400
- 값 빠짐·형식 틀림 → 400 / 없는 주소 → 404 / 그 밖의 오류 → 500 (내부 내용은 로그에만, 사용자에게는 공통 문구)
- 문구는 화면에 그대로 보일 수 있으므로 짧은 "~니다" 체 (예: "지난 날짜는 고를 수 없습니다.")

## 백엔드 규칙
<!-- 승인·비밀 정보·target/·안전 수치 규칙은 루트 CLAUDE.md 에 있으므로 여기 다시 적지 않는다 -->
- 프론트에서 하는 검사(최대 개수, 단독 선택, 날짜)는 서버에서도 같은 규칙으로 한 번 더 한다
- 요청 JSON 의 참/거짓 값은 `Boolean` 으로 받는다 (Jackson 3 은 `boolean` 값이 빠지면 요청 전체를 오류로 처리)
- `dbtest/` 의 `TestController`/`TestMapper`(연결 확인용 임시 코드)를 새 기능의 본보기로 삼지 않는다
- 보안 설정(SecurityConfig)을 임의로 수정하지 않는다 (지금은 `/api/auth/me` 만 로그인 필요, 나머지는 둘러보기용으로 전체 허용. 변경은 사용자 승인 후)
