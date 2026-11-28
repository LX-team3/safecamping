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
saved/    # 저장(목록 탭)·하트(관심 캠핑장 기본 목록)·관심 캠핑장 비교 (saved_list·saved_camp 테이블, 비교 값은 camp·camp_facility·원본 raw.camps)
alert/    # 내 정보 · 알림 (기상특보 + 재난문자). 기상특보 = KmaWarningClient(기상청 특보현황, 10분 캐시, 환경변수 KMA_SERVICE_KEY) + KmaWarningParser(t6 글 → 시·도별 Alert), 재난문자 = SafetyDataDisasterClient(행정안전부 긴급재난문자 DSSP-IF-00247, 20분 캐시·하루 99회 한도, 환경변수 SAFETY_DATA_KEY, 등록한 IP 에서만 호출됨) + DisasterMessageParser(응답 → 시·도별 Alert, 전국 문자는 모든 지역에 보임). 못 받으면 AlertDummyData. 지역은 Sido(임시 가장 가까운 시·도)
family/   # 내 캠핑 탭 · 가족 모임방 (family_room·family_member·meeting_place 테이블). 아이 카드·일정·위치(Location*, family_location 테이블)도 여기에
tour/     # 관광지 상세 + 관광지 탭 여행 취향 TourPreference* (member_tour_preference 테이블. 선택지는 TourPreferenceOptions)
dbtest/   TestController, TestMapper   # DB 연결 확인용 임시 코드 (본보기 아님)
route/    # 캠핑장 → 응급실·소방서·경찰서·편의점·약국 자동차 경로 (카카오모빌리티 길찾기, 키는 환경변수 KAKAO_REST_KEY). DirectionsSource = 테스트용 인터페이스
nearby/   # 내 주변 탭 (내 위치 → 가장 가까운 캠핑장 기준 안전점수·시설). 레이어 목록은 NearbyLayer 한곳
mycamping/ # 내 캠핑 탭 · 일정 등록한 캠핑 CampPlan* (camp_plan 테이블). 회원 개인 것이라 가족방(family/)과 별개. 출발 준비 체크리스트 Checklist* (ChecklistItems 문구 · ChecklistService 3일 규칙 · ChecklistSource 인터페이스=테스트용, 구현 ChecklistLookup). 지난 캠핑 후기 CampReview* (camp_review·camp_review_photo 테이블, 사진 파일은 ReviewPhotoStore 인터페이스=테스트용, 구현 LocalReviewPhotoStore = 서버 폴더 {CAMPIN_UPLOAD_DIR 또는 uploads}/reviews)
weather/  # 날씨 예보 (화면 없음, 체크리스트가 씀). 기상청 단기예보 KmaForecastClient(getVilageFcst, 격자별 3시간 캐시, 환경변수 KMA_SERVICE_KEY) + KmaGrid(위·경도 → 5km 격자) + ForecastParser(시간별 → 하루치 DayForecast). 못 받으면 empty (예시 값 없음). ForecastSource = 테스트용 인터페이스. 4~7일 앞 중기예보는 아직 없음
chabak/   # 차박 판정 (지도에서 찍은 지점 → 불가·주의·제한 없음·야영 가능). 판정 규칙은 전부 DB chabak 스키마 함수(fn_verdict → fn_verdict_json)에 있고 서버는 그 JSON 을 ChabakVerdictResponse 로 옮겨 전달. 범위 인제군
disaster/ # 재난 대피 3D 지도(/disaster) 실시간 정보. 인북천 캠핑장 격자의 최근 비 = KmaRainClient(기상청 초단기실황 RN1, 시간별로 받아 24시간치 저장, 환경변수 KMA_SERVICE_KEY) + 상류 수위 = HrfcoWaterClient(한강홍수통제소 반월촌교 1011620, 기준수위 하루·수위 5분 캐시, 환경변수 HRFCO_SERVICE_KEY) + 인제군 기상특보(alert 의 WarningSource 재사용) → DisasterRules(비: 3h 60/90/114 · 12h 110/180 · 24h 310 / 수위: 관심→1 · 경보→2 · 심각→3, 둘 중 높은 단계) 로 단계. 못 받으면 비워 둠(예시 값 없음). RainSource · WaterLevelSource = 테스트용 인터페이스
push/     # 폰 푸시 알림 보내기 (화면 없음). FcmPushSender = Firebase(FCM) 발송, 서비스 계정 키 파일의 "경로"를 환경변수 GOOGLE_APPLICATION_CREDENTIALS 로 받음 (키 파일은 프로젝트 폴더 밖). 키가 없거나 잘못되면 경고만 남기고 안 보냄. PushSender = 테스트용 인터페이스. 누구에게 = PushTokenStore(로그인 아이디 → 폰 토큰, 회원당 한 대, **서버 메모리**라 재시작하면 비워짐) + PushToken*(등록 · 삭제 API). 언제 = BriefPushService(일정 저장 10초 뒤, 그 시점의 **같은 모임방 사람 모두**에게 브리핑 푸시, 시연용. 앱에서 저장했으면 본인은 뺌. 예약도 서버 메모리, 일정 삭제·변경은 반영 안 함). 같은 방 사람 = RoommateMapper(읽기만)
brief/    # AI 안전 브리핑 (만드는 중, 설명 docs/ai-brief-spring-ai.md). 벡터 DB(campine_vector, 환경변수 VECTOR_DB_URL·USERNAME·PASSWORD) 조각 검색 = SafetyDocRepository(이 클래스 안에서만 접속) + 조건 SafetyDocFilter(꼬리표 · 뺄 목록) + 질문 임베딩 EmbeddingClient(OpenAI text-embedding-3-small, 환경변수 OPENAI_API_KEY). 상황 모으기 = BriefContextService → BriefContext(체크리스트 응답 + 일정 메모 + 화면이 보낸 진단 BriefSurvey). AiPingRunner · VectorPingRunner 는 확인용 임시(--ai-ping · --vector-ping)
score/    # 안전점수 일괄 계산 (화면 없음). 카카오 소요시간(분) 기준 → camp.safety_score·er_drive_min, camp_facility.drive_min 값만 고침 (DB 구조 그대로, km 는 직선 그대로). 기준은 ScoreRules 한곳: 등급(TIERS) → ROC 배점, 분 기준(TIME). 직선거리·score_weights·보안등·CCTV 는 쓰지 않음. 실행: 서버를 --score 인자로 켤 때 한 번
```

## 상세 의존성 목록
- rombok
- firebase-admin 9.12.0 (폰 푸시 알림 발송, `push/`)
- spring-ai-starter-model-anthropic 2.0.3 (`spring-ai-bom` 으로 버전 맞춤, AI 브리핑 Claude 호출, `brief/`)


## 명령어 (실행·빌드·테스트)
- Maven 설치 없이 Maven Wrapper 를 쓴다 (`backend/` 에서 실행)
- 실행: `.\mvnw.cmd spring-boot:run` → http://localhost:8080
- 테스트: `.\mvnw.cmd test` — 코드를 고친 뒤 반드시 통과 확인
- 안전점수 다시 계산: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--score"` (`server/` 에서)
  - 하는 일: 캠핑장 23곳 → 시설(응급실·소방서·경찰·약국·편의점)까지 카카오 차 소요시간을 받아 `camp.safety_score`·`er_drive_min`, `camp_facility.drive_min` 을 고친다. 기준은 `score/ScoreRules`
  - 필요한 것: 환경변수 `KAKAO_REST_KEY`, DB 연결
  - 언제: `db/merge_to_public.sql` 을 다시 실행한 뒤(점수가 직선 추정값으로 돌아가므로), 캠핑장·시설 데이터나 `ScoreRules` 를 바꾼 뒤
  - 왜 인자로만 켜나: 한 번에 카카오를 100번 넘게 불러 하루 한도를 쓴다. API 로 열면 아무나 부를 수 있고, 평소 실행마다 돌면 한도가 낭비된다.
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
| `GET /api/alerts?sido=` · `?lat=&lon=` | 알림 (기상특보 = 기상청 실제, 못 받으면 더미 `warningDummy: true` / 재난문자 = 더미 `disasterDummy: true`). 지역 → 위치(가장 가까운 시·도) → 없으면 전국. `{ warningDummy, disasterDummy, sido, regions, activeWarnings, todayDisasterCount, items: [{ kind: warning·disaster, area, category, message, issuedAt, active }] }`. 로그인 없이 됨 |
| `GET /api/saved/lists` | 내 저장 목록 (`관심 캠핑장` 기본 목록이 없으면 만들어 줌). 이하 `/api/saved/**` 전부 로그인 필요 |
| `POST /api/saved/lists` · `PATCH`·`DELETE /api/saved/lists/{id}` | 목록 만들기 `{ name }`·이름 바꾸기·삭제 (기본 목록은 바꾸기·삭제 불가 400) |
| `POST`·`DELETE /api/saved/lists/{id}/camps/{campId}` | 캠핑장 담기·빼기 (campId 는 `camp.id` 숫자, 예: 100568. 없는 캠핑장은 404) |
| `GET /api/saved/lists/{id}/compare` | 목록 앞의 3곳 비교 (더미, `dummy: true`). `rows: [{ label, kind: score·minutes·yesno, bestLabel, cells: [{ text, value, best }] }]` — value 는 막대 길이용 숫자(막대 없으면 null). 남의 목록 id 는 404 |
| `GET /api/family/room` | 내 가족방 (없으면 `data: null`). 이하 `/api/family/**` 전부 로그인 필요. 한 사람당 방 하나라 주소에 방 번호를 넣지 않는다 |
| `POST /api/family/room` · `POST /api/family/room/join` | 가족방 만들기 `{ name }` → 201 / 초대코드로 들어가기 `{ inviteCode }` (이미 방이 있으면 400) |
| `DELETE /api/family/room/me` | 나가기 (마지막 사람이면 방·모일 장소도 지움) |
| `GET`·`POST /api/family/places` · `DELETE /api/family/places/{id}` | 모일 장소 목록·추가 `{ name, description?, lon?, lat? }`·삭제 (다른 방 장소 id 는 404) |
| `GET`·`POST /api/family/children` · `PUT`·`DELETE /api/family/children/{id}` | 아이 건강 응급 카드 목록·추가·수정·삭제 `{ name, birthDate?, bloodType?, allergy?, disease?, medicine?, memo? }` |
| `GET`·`POST /api/family/schedules` · `DELETE /api/family/schedules/{id}` | 가족 일정 목록(시간 순)·추가 `{ title, startAt: "2026-11-14T06:00:00+09:00", memo? }`·삭제 |
| `GET /api/family/camp-plans` | 가족 캠핑: 같은 가족방 다른 구성원이 내 캠핑에 등록한 일정 (나 빼고, 체크인 빠른 순, **보기만**) `[{ id, ownerId, ownerName, campId, campName, place, lat, lng, checkIn, checkOut, memo }]`. 방에 들어가 있으면 자동 공유 (동의 절차 없음). 가족방이 없으면 빈 목록(404 아님). 고치기·지우기·후기는 본인만 (`/api/member/camp-plans/**`). 출발 준비 체크리스트는 가족도 봄 |
| `GET /api/family/locations` | 우리 가족방 구성원의 마지막 위치 (위치가 있는 사람만, 들어온 순서) `[{ id: 회원 id, name, lat, lng, me, updatedAt, seq }]`. `me` = 로그인한 나. `seq` = 방에 들어온 순번(1부터, 위치 없는 사람까지 세어 매김 → 구성원 목록 순서와 같음, 화면의 사람별 색). 줄이 있는 사람 = 위치 공유에 동의한 사람 |
| `PUT`·`DELETE /api/family/locations/me` | 내 위치 저장 `{ lat, lng }` (브라우저 GPS, 있으면 덮어씀 · 범위 밖 400) / 위치 공유 끄기 (내 줄 삭제). 가족방 없으면 404. 방을 나가면(`DELETE /api/family/room/me`) 내 위치도 지움 |
| `GET /api/camps?sido=&sigungu=&campType=&sort=` · `/api/camps/recommend?…&limit=` | 탐색 캠핑장 목록 · 내 유형 추천 TOP N(위험 등급 뺌, `{ camps, excluded }`). 한 줄 `{ id, name, sigungu, eupmyeon, campType, lat, lng, safetyScore, grade, erDriveMin, imageUrl, dummy, erBonus }`. `sort`: `score`(안전점수) · `er`(응급실 가까운) · `bonus`(안전점수 + `erBonus`, 100 상한). **`erBonus`** = 진단 유형 가산점 0~5 (`ScoreRules.erBonus`: 응급실 30분 안 5 → 60분 넘으면 0, 비례·반올림). 누구에게 쓸지(아이·어르신·차박·겨울·걸어서)는 화면 `utils/webSurvey.js` 의 `needsErBonus` — 진단 답이 브라우저에만 있어서. `safetyScore`·`grade` 는 가산점 없이 그대로. 상세 `/api/camps/{id}` 에도 `erBonus`. 로그인 없이 됨 |
| `GET /api/camps/{id}/routes` | 캠핑장 → 응급실·소방서·경찰서·편의점·약국 자동차 경로 (화장실 뺌) (카카오모빌리티, 시설마다 1번씩 동시 호출, 전부 성공하면 24시간 메모리 저장). `{ source: kakao·straight, routes: [{ facilityId, type, name, ok, dummy, distanceKm, durationMin, path: [[위도, 경도], ...] }] }`. 키 없음·실패면 `ok: false` + 직선 두 점 + DB 시간. 없는 캠핑장 400. 로그인 없이 됨 |
| `GET /api/nearby?lat=&lng=` | 내 주변 (**더미**). 시설 데이터가 있는 가장 가까운 캠핑장 기준 → `{ dummy, basis: { campId, campName, sido, sigungu, eupmyeon, lat, lng, distanceKm, far }, safetyScore, grade, facilities: [{ no, id, type, name, lat, lng, distanceKm, driveMin, tone, dummy }] }`. `far` 는 10km 초과(임시). 없으면 `basis: null`. 위치 빠짐·범위 밖 400. 로그인 없이 됨 |
| `GET`·`POST /api/member/camp-plans` · `DELETE /api/member/camp-plans/{id}` | 내 캠핑 (캠핑장 상세·차박 판정 카드 "내 캠핑에 일정 등록"). GET → 체크인 빠른 순 `[{ id, campId, campName, place, lat, lng, checkIn, checkOut, memo }]` (`campId` 가 null 이면 캠핑장이 아닌 차박 자리 — `lat`·`lng` 가 있음). POST 두 가지: 캠핑장 `{ campId, campName, place?, checkIn: "2026-11-14", checkOut, memo?, notifyPhone? }` / 자리 `{ campName(지번 등 이름), place?, lat, lng, checkIn, checkOut, memo?, notifyPhone? }` (campId 없이 좌표, 소수 6자리로 맞춰 저장) → 201. 저장 10초 뒤 브리핑 푸시: `notifyPhone: true`(웹) = 나 + 같은 모임방 사람, `false`(앱) = 모임방 사람만, 안 보내면 푸시 없음. 둘 다 없음·위치 범위 밖·지난 체크인·체크아웃 ≤ 체크인·같은 캠핑장(같은 자리)+체크인 중복 400. 남의 id 삭제 404. 로그인 필요. **DB `schema_05.sql` 실행 후** 동작 |
| `PUT /api/member/camp-plans/{id}` | 내 캠핑 고치기 (예약 정보 확인 화면). `{ checkIn, checkOut, memo? }` → 고친 일정. 캠핑장(자리)은 그대로. 인원·동행·이동 수단은 memo 글자("성인 2명 · 어린이 0명 · 친구·연인 · 자동차", 프론트 `utils/bookingMemo.js`). 체크인을 바꿀 때만 지난 날짜·중복 400, 체크아웃 ≤ 체크인 400, 남의 id 404. 로그인 필요 |
| `GET /api/member/camp-plans/{planId}/checklist` | 출발 준비 체크리스트 (내 캠핑 카드 → 출발 준비). `{ plan: { id, campId, name, lat, lng, checkIn, checkOut }, season: 봄·여름·가을·겨울(체크인 월 기준 3~5·6~8·9~11·12~2), past, near, openDate, warningsAvailable, warnings: [{ category, area, message }], safety: { score, grade, erDriveMin, erApprox } | null, items: [{ id, group: 특보·차박·계절·공통, text, why, level: warn·normal }] }`. `near` = 출발 **3일 전**부터 캠핑이 끝날 때까지(캠핑 중 포함) → 이때만 그 지역(가장 가까운 시·도)의 **지금 발효 중인 기상특보**(WarningSource, 10분 캐시)를 확인해 항목에 붙임. 먼 일정은 기상청을 부르지 않고 `openDate`(체크인 3일 전)만 알려 줌. 특보를 못 받았거나 예시(더미)뿐이면 `warningsAvailable: false` + `warnings: []` (예시를 진짜처럼 쓰지 않음). `safety`: 캠핑장은 `camp` 값, 차박 자리(`campId` 없음)는 `/api/chabak/score` 계산(응급실 분은 어림 `erApprox: true`). 항목 문구는 `ChecklistItems` 한 곳 — **수치 기준을 두지 않음**(특보는 기상청 이름에 연결, 침낭은 "내 침낭의 쾌적온도 확인"). `forecast`: `near` 일 때만 `{ available, days: [{ date, minTemp, maxTemp, pop, maxWind, sky, precip }] }` (기상청 단기예보, 캠핑 기간 중 오늘 이후 최대 7일, 기상청이 주지 않는 날은 값이 전부 null). 못 받으면 `available: false` + 빈 `days`(예시 값 없음), 먼 일정은 null. 온도로 정하는 준비물(침낭·핫팩·일산화탄소 경보기·동파 주의)은 계절 묶음이 아니라 `날씨` 묶음에서 예보 최저 -12° 이하(핫팩·경보기) · 0° 이하(동파·침낭)일 때만 나옴(예보가 없는 먼 일정에는 안 나옴). 침낭 항목 `why` 는 "쾌적온도 비교 ○° 이하". 예보로 `group: "날씨"` 항목이 특보 묶음 뒤에 붙음 — 출처 있는 기준만: 최저 -12° 이하(기상청 한파주의보 발표 기준 온도) · 0° 이하(물 어는 온도, 동파) · 비·눈 예보 유무. 풍속·폭염·우리만의 구간은 근거가 없어 쓰지 않음(`ChecklistItems` 주석). 체크 상태는 화면이 브라우저에 저장(서버 저장 없음, 보는 사람마다 따로). **같은 가족방 사람의 일정도 볼 수 있음**(`CampPlanMapper.findVisible`), 그 밖의 남의 일정 id 404. 로그인 필요 |
| `POST /api/member/camp-plans/{planId}/brief` | AI 안전 브리핑 (대비 행동 최대 3줄, `brief/`). 본문 `{ survey: { who[], kid, site, weak[] } \| null }` — 웹 1분 진단 결과는 브라우저에만 있어 화면이 담아 보냄(없으면 본문 없이). 응답 `{ ai, lines: [{ text, sources: [{ id, org, year, heading, source, page }] }], fallback }`. `ai: false`(검색 · Claude 실패, 검사 통과 줄 없음)면 lines 비움 → 화면은 지금의 문장 틀 브리핑. 특보 · 날씨 줄은 AI 가 아니라 화면이 체크리스트 응답으로 만든다. 내 일정 · 같은 가족방 일정만(그 밖 404), 끝난 캠핑 400. 저장 안 함, 부를 때마다 Claude 호출(비용). 로그인 필요 |
| `GET /api/member/camp-plans/reviews` | 내 지난 캠핑 후기 전부 `[{ planId, content, photos: [{ id, url }], createdAt, updatedAt }]` (별점 없음, content 는 사진만 올렸으면 null). 로그인 필요. **DB `schema_06.sql` 실행 후** 동작 |
| `POST /api/member/camp-plans/{planId}/review` | 후기 작성 · 수정(덮어씀, 일정 하나에 후기 하나). **multipart/form-data**: `content`(선택) · `keepPhotoIds`(남길 기존 사진 id, 화면 순서, 여러 번) · `photos`(새 사진 파일, 여러 번). 사진 순서 = 남긴 사진 다음 새 사진. 뺀 사진은 파일까지 지움. 체크아웃이 지나지 않음 · 글·사진 둘 다 없음 · 글 1000자 초과 · 사진 9장 초과 · 한 장 10MB 초과 · jpg·png·webp·gif 아님(파일 앞부분으로 판별) · 이 후기 사진이 아닌 keepPhotoIds 400, 남의 일정 404. PUT 대신 POST 인 이유는 컨트롤러 주석. 업로드 크기 제한은 `application.properties` 의 `spring.servlet.multipart.*` (넘으면 400) |
| `GET /api/member/camp-plans/{planId}/review/photos/{photoId}` | 후기 사진 파일 (그림 자체라 ApiResponse 로 감싸지 않음, `Cache-Control: private, max-age=7일`). 내 일정의 후기 사진만, 아니면 404 JSON. 일정을 지우면(`DELETE /api/member/camp-plans/{id}`) 후기 · 사진 줄은 DB CASCADE, 사진 파일은 서버가 지움 |
| `POST`·`DELETE /api/member/push-token` | 안드로이드 앱의 푸시 기기 토큰 등록 · 삭제 `{ token }` → `data: null`. 회원은 세션에서 안다(본문으로 받지 않음). 회원당 한 대(나중 폰으로 바뀜), 같은 폰에 다른 회원이 로그인하면 앞 회원의 연결은 지움. DELETE 는 등록된 토큰과 같을 때만 지움. 빈 토큰 400. 로그인 필요. 서버 메모리 보관 |
| `GET /api/chabak/verdict?lat=&lng=` | 차박 판정 (DB 함수 `chabak.fn_verdict_json` 결과를 `ChabakVerdictResponse` 로 옮김). `{ pnu, jibun, jimok, owner, landUse, terrain, roadSide, verdict: 불가·주의·제한 없음·야영 가능, reasons[], zones[], nearBoundary, nearbyCamps: [{ name, distM }], alternatives: [{ jibun, owner, radiusM, distM, lat, lng }], parcel: GeoJSON }`. 위치 빠짐·범위 밖 400, 인제군 밖 404. 로그인 없이 됨. 공공데이터 기준 참고용 판정 |
| `GET /api/chabak/layer?type=` | 차박 지도 위쪽 칩 레이어. `no` 불가 구역 · `caution` 주의 구역 (`chabak.regulation` 을 판정별로 **한 덩어리로 합침**(ST_Union, 20m 단순화), 주의 구역은 불가 구역과 겹친 부분을 뺌 → `geometry` GeoJSON 1개. 처음 한 번만 계산해 서버가 기억 — 규제 데이터를 바꾸면 서버 재시작) / `open` 차박 후보지 (`chabak.open_spot`, 넓은 순 1000곳까지, 자리마다 안전점수를 `/score` 와 같은 계산으로 붙임 — 처음 한 번만 계산해 기억) · `camp` 등록 야영장 (`camp`, `id` 로 상세, 점수는 `camp.safety_score`) → `[{ id, name, lat, lng, score, grade, area, imageUrl, erDriveMin, geometry }]` (화면 "주변 안전" 필터 · 차박지 목록용. 후보지 name 은 "475-3 잡종지", area 는 소유(군유지 등), erDriveMin 은 직선 어림 / 야영장 area 는 "인제군 북면", 사진·응급실 분은 camp 값). 그 밖의 type 400. PostGIS 함수는 `extensions.` 를 붙여 부른다 (공유 DB 설치 위치). 로그인 없이 됨 |
| `GET /api/chabak/score?lat=&lng=` | 찍은 지점의 안전점수. **예전 캠핑장 점수 방식**(`merge_to_public.sql` 8단계 — 지금 `camp.safety_score` 는 카카오 차 시간 + `ScoreRules` 라 기준이 다름: 종류별 가장 가까운 `raw.facilities` 까지 직선거리 → `raw.score_weights` 만점·0점 거리로 부분점수 → 가중 평균 ×100). `{ score, grade: safe·caution·danger·unknown, erDriveMin(추정: 직선×1.4÷45km/h), items: [{ type, distM, weight, part }] }`. 카카오 호출 없음. 위치 빠짐·범위 밖 400 |
| `GET /api/chabak/facilities?lat=&lng=` | 차박 지도 "지도에 보일 것" 레이어: 찍은 지점에서 종류별(er·fire·police·store·pharmacy) 가까운 3곳 `[{ id, type, name, lat, lng, distanceKm, dummy }]` 가까운 순. 거리는 위도·경도 어림. 위치 빠짐·범위 밖 400. 로그인 없이 됨 |
| `GET /api/facilities?types=er,pharmacy&swLat=&swLng=&neLat=&neLng=` | 탐색 지도 "지도 설정"에서 켠 종류의 시설 중 지도 화면 안에 있는 것 `[{ id, type, name, lat, lng, dummy }]`. 화면 가운데에서 가까운 순 최대 300곳. 종류는 `NearbyLayer` 중 ready 인 것만 (그 밖은 무시, 비면 빈 목록). 범위 빠짐·남서>북동 400. 로그인 없이 됨 |
| `GET /api/nearby/layers` | "지도에 보일 것" 안전 시설 목록 `[{ code, icon, label, desc, ready, on }]` (응급실·소방서·경찰서·편의점·약국·대피소, 공중화장실 뺌). 대피소는 `ready: false` (데이터 없음). 약국은 더미 데이터가 있어 `ready: true` |
| `GET /api/disaster/now` | 재난지도 인북천 캠핑장 지금 정보 `{ place, rainAvailable, observedAt, rain3h, rain12h, rain24h, level(0~3), reasons[], warningAvailable, warnings[{ category, area, message, issuedAt }], water{ station, observedAt, wl, attention, alarm, serious, level, stale } }`. level = 비·수위 중 높은 단계. 실황을 못 받으면 rain* null, 둘 다 못 받으면 level null, 수위를 못 받으면 water null(1시간 넘게 새 자료 없으면 stale·판단 제외), 특보를 못 받으면 warnings 빈 목록. 로그인 없이 됨 |

- 로그인은 Spring Security **세션 방식**. remember-me 서명 키는 환경변수 `REMEMBER_ME_KEY` (없으면 실행마다 임시 키)
- CSRF 켜 둠 (`csrf.spa()`): POST·PUT·PATCH·DELETE 는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 한다 (axios 는 자동, 없으면 403). curl·Postman 으로 시험할 때는 먼저 GET 으로 쿠키를 받는다

- ④ 지도 데이터는 설계 파일 `AppMap.dc.html` 에서 뽑아 `resources/dummy/*.json` 에 둠 (캠핑장 54·시설 373·관광지 10). 길찾기(경로 선)는 `route/` 가 카카오모빌리티로 받는다

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
