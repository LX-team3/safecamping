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
- data : DB 연결 전 임시 더미 (`RegionDummyData`). DB 를 연결하면 mapper 로 바꾸고 지운다 (설문은 더미 없이 `survey/SurveyQuestions` + DB)
```
common/   ApiResponse, InvalidRequestException, GlobalExceptionHandler, SecurityConfig
member/   # ①-3 로그인 · ①-4 회원가입 (member 테이블). 내 정보 수정·탈퇴도 여기에
          Member, MemberMapper, MemberResponse,
          SignupController, SignupService, SignupRequest,
          LoginController, LoginService, LoginRequest,
          MemberDetailsService, KeepLoginRememberMeServices,
          EmergencyContact*(내 정보 · 비상연락처 최대 5명, emergency_contact 테이블),
          Activity*(내 정보 · 여행 취향 최대 3개, member_activity 테이블. 선택지는 ActivityOption)
survey/   # ② 1분 진단 (3단계 5문항, survey_answer 테이블). 질문은 SurveyQuestions 한곳
region/   # ③ 지역 찾기
map/      # ④ 캠핑지도
saved/    # 저장(목록 탭)·관심 캠핑장 비교 (saved_list·saved_camp 테이블). CampDummyData 는 캠핑장 데이터가 생기면 지운다
alert/    # 내 정보 · 알림 (기상특보 + 재난문자). 지금은 AlertDummyData, 지역은 Sido(임시 가장 가까운 시·도)
family/   # 내 캠핑 탭 · 가족 모임방 (family_room·family_member·meeting_place 테이블). 아이 카드·일정·위치도 여기에
tour/     # 관광지 상세 + 관광지 탭 여행 취향 TourPreference* (member_tour_preference 테이블. 선택지는 TourPreferenceOptions)
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
| `GET /api/survey/questions` | 1분 진단 5문항 (새 설계 Survey.dc.html, 3단계). `[{ key, title, type: single·multi·rank, max, exclusive, options: [{ code, label, sub }] }]`. 로그인 없이도 됨 |
| `GET`·`PUT /api/member/survey` | 내 진단 답 `{ campType, transport, heating[], safetyItems[], facilities[] }` (진단 안 했으면 null). PUT 은 덮어씀, 규칙 틀리면 400. 로그인 필요 (안 한 사람의 답은 브라우저에만) |

| `GET /api/regions/camp-count?sido=32` | 시·군·구별 캠핑장 수 (더미, `dummy: true`). `sido` 없으면 전국 |
| `POST /api/trips/check` | 동행·날짜 검사 → 몇 박인지. 날짜는 `"2026-11-05"` 형식 |
| `GET /api/map/camps?grades=safe,mid` | 캠핑장 목록 (점수 높은 순, 등급 safe 70↑ / mid 40~70 / warn 40↓) |
| `GET /api/map/camps/{id}` | 캠핑장 상세 + 안전점수 항목 5개 |
| `GET /api/map/camps/compare?ids=c1,c2` | 관심 캠핑장 비교 (최대 3곳) |
| `GET /api/map/facilities?types=er,police` | 안전 시설 목록 (police·er·fire·store·toilet, 비우면 전부) |
| `GET /api/map/tours?kw=계곡` · `/api/map/tours/{id}` | 관광지 목록·상세 |
| `GET /api/auth/check-id?loginId=` | 아이디 중복 확인 → `{ available }` |
| `POST /api/auth/signup` | 회원가입 (비밀번호 BCrypt 저장, 연락처는 숫자만, 비상연락처 `emergencyPhone` 은 선택 — 넣으면 emergency_contact 첫 번째 사람으로) → 201 |
| `POST /api/auth/login` | 로그인 `{ loginId, password, keep }` → 세션 쿠키, `keep` 이면 remember-me 쿠키(14일) |
| `POST /api/auth/logout` | 로그아웃 (SecurityConfig 의 logout 설정이 처리) |
| `GET /api/auth/me` | 지금 로그인한 회원. 로그인 안 했으면 401 |
| `GET`·`PUT /api/member/emergency-contacts` | 내 비상연락처 (최대 5명). GET → 넣은 순서대로 `[{ name, phone }, …]` (회원가입 때 넣은 번호는 name null). PUT `{ contacts: [...] }` 은 목록을 통째로 바꿈 (다 비운 칸은 건너뜀, 이름·번호 중 하나만 있거나 6명 이상이면 400). 로그인 필요 |
| `GET`·`PUT /api/member/activities` | 내 여행 취향. GET → `{ options: [{ code, label, desc }], selected: [code], max: 3 }`. PUT `{ activities: [code] }` 통째로 바꿈 (없는 code·4개 이상 400). 로그인 필요 |
| `GET /api/tour-preferences/questions` | 관광지 탭 여행 취향 질문 2개. `[{ code, title, max, options: [{ code, label }] }]`. 로그인 없이도 됨 |
| `GET`·`PUT /api/member/tour-preferences` | 내 관광지 여행 취향 `{ activity: [code], nature: [code] }`. PUT 은 통째로 바꿈 (없는 code·최대 개수 초과 400). 로그인 필요 |
| `GET /api/alerts?sido=` · `?lat=&lon=` | 알림 (기상특보 + 재난문자, **더미** `dummy: true`). 지역 → 위치(가장 가까운 시·도) → 없으면 전국. `{ sido, regions, activeWarnings, todayDisasterCount, items: [{ kind: warning·disaster, area, category, message, issuedAt, active }] }`. 로그인 없이 됨 |
| `GET /api/saved/lists` | 내 저장 목록 (`관심 캠핑장` 기본 목록이 없으면 만들어 줌). 이하 `/api/saved/**` 전부 로그인 필요 |
| `POST /api/saved/lists` · `PATCH`·`DELETE /api/saved/lists/{id}` | 목록 만들기 `{ name }`·이름 바꾸기·삭제 (기본 목록은 바꾸기·삭제 불가 400) |
| `POST`·`DELETE /api/saved/lists/{id}/camps/{campId}` | 캠핑장 담기·빼기 (지금 campId 는 더미 `c1`~`c3`) |
| `GET /api/saved/lists/{id}/compare` | 목록 앞의 3곳 비교 (더미, `dummy: true`). `rows: [{ label, kind: score·minutes·yesno, bestLabel, cells: [{ text, value, best }] }]` — value 는 막대 길이용 숫자(막대 없으면 null). 남의 목록 id 는 404 |
| `GET /api/family/room` | 내 가족방 (없으면 `data: null`). 이하 `/api/family/**` 전부 로그인 필요. 한 사람당 방 하나라 주소에 방 번호를 넣지 않는다 |
| `POST /api/family/room` · `POST /api/family/room/join` | 가족방 만들기 `{ name }` → 201 / 초대코드로 들어가기 `{ inviteCode }` (이미 방이 있으면 400) |
| `DELETE /api/family/room/me` | 나가기 (마지막 사람이면 방·모일 장소도 지움) |
| `GET`·`POST /api/family/places` · `DELETE /api/family/places/{id}` | 모일 장소 목록·추가 `{ name, description?, lon?, lat? }`·삭제 (다른 방 장소 id 는 404) |
| `GET`·`POST /api/family/children` · `PUT`·`DELETE /api/family/children/{id}` | 아이 건강 응급 카드 목록·추가·수정·삭제 `{ name, birthDate?, bloodType?, allergy?, disease?, medicine?, memo? }` |
| `GET`·`POST /api/family/schedules` · `DELETE /api/family/schedules/{id}` | 가족 일정 목록(시간 순)·추가 `{ title, startAt: "2026-11-14T06:00:00+09:00", memo? }`·삭제 |

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
- 보안 설정(SecurityConfig)을 임의로 수정하지 않는다 (지금은 `/api/auth/me`·`/api/saved/**`·`/api/family/**`·`/api/member/**` 만 로그인 필요, 나머지는 둘러보기용으로 전체 허용. 변경은 사용자 승인 후)
