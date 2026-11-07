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

## 2026-11-04 설문: 1분 진단 · 여행 취향 (화면 + 서버 + DB)
- 두 설문을 같은 구조(설문 → 단계 → 질문 → 선택지)로 처리. 설문 code: `diagnosis`(1분 진단), `travel`(여행 취향)
- DB: `db/schema.sql` 에 `survey`·`survey_step`·`survey_question`·`survey_option`·`survey_answer`·`survey_answer_item` 추가, 질문 데이터는 `db/data_survey.sql` — **둘 다 DB 에 직접 실행해야 함** (schema → seed 순서)
- 백엔드: `com.campin.survey` — `GET /api/surveys/{code}`, `POST /api/surveys/{code}/answers`(회원만 저장, 비회원은 검사만), `GET /api/surveys/{code}/answers/me`(로그인 필요, `SecurityConfig` 에 추가)
- 프론트: `views/survey/SurveyView.vue` 한 파일이 `/survey/diagnosis/1~3`, `/survey/travel` 처리. `components/survey/`(SurveyLayout·QuestionHead·ChoiceButton), `components/common/CheckDot.vue`, `stores/survey.js`
- 앱을 처음 켜면 `/` → `/survey/diagnosis/1`. 진단 완료나 '나중에 할게요' 후에는 안 띄움 (localStorage `campin.survey.seen`)
- 토큰 추가(임시, 스크린샷에서 뽑은 값): `--color-selected-bg`, `--color-track`, `--color-warn-bg`, `--color-warn-text` → 설계 값 확인 필요
- 확인: `mvnw test` 통과 (16개, 설문 11개 추가), `npm run build` 통과
- 확인 못 함: SQL 실제 실행, 서버 API 실제 응답, 브라우저 화면 (DB 에 테이블이 아직 없음)
- 남은 일: 진단 결과 화면(A/B/C 고정) — 지금은 완료 시 메인으로, 관광지 탭(여행 취향 진입점) — 지금은 주소로만 들어감, "쉬기만" 을 단독 선택으로 할지 결정

## 2026-11-05 탐색 탭: 내 유형 추천 TOP 5 목록 + 캠핑장 지도
- 화면: `/explore` (`views/explore/ExploreView.vue`) — 지도 위에 시트를 올린 한 화면. 손잡이·"지도" 버튼으로 접기/펼치기, 목록 한 줄을 누르면 지도에서 그 캠핑장 선택
- 부품: `components/map/CampMap.vue`(카카오맵, 키 없으면 임시 그림), `components/explore/CampRow.vue`, `components/common/BottomNav.vue`(탐색만 동작), `stores/favorite.js`(하트, 브라우저에만 저장), `utils/kakaoMap.js`
- 1분 진단 마지막 버튼 → `/explore` (진단 답의 캠핑 유형을 추천 조건으로, 난방·안심 시설 1순위를 안내 문구로 씀)
- 백엔드: `com.campin.camp` — `GET /api/camps`, `/api/camps/recommend`, `/api/camps/sigungus`. 추천은 **임시 기준**(위험 제외 · 유형 일치 · 점수/응급실 순)
- DB: `schema.sql` 에 `camp` 테이블(좌표는 lat·lng 숫자 컬럼, PostGIS 는 공간 분석 때), 더미 `db/data_camp.sql` 7곳 (`is_dummy = true`) — **DB 에 직접 실행해야 함**
- 더미 점수: 스크린샷의 71·54 는 문서 등급 기준(70↑ 안전 / 40~69 주의 / 40 미만 위험)과 맞지 않아 65·35 로 넣음 → 등급 기준 확정 필요
- 토큰 추가(임시): `--color-safe-bg`, `--color-caution`, `--color-danger`, `--color-map-bg`
- 확인: `mvnw test` 통과 (23개, 캠핑장 7개 추가), `npm run build` 통과
- 확인 못 함: SQL 실행, API 실제 응답, 카카오 지도 표시, 브라우저 화면
- 남은 일: 날짜 칩(③ 인원·날짜), 상세 필터, 레이어 버튼(2부 재난 레이어), 저장 탭·회원 저장, 캠핑장 상세, 진단 결과 화면

## 2026-11-05 탐색 탭: 지역 선택 · 누구와 언제 시트
- 화면: 탐색 필터 줄의 지역 칩 → `components/explore/RegionSheet.vue`(전국 시·도 → 시·군·구, 검색, "안심 데이터 있음"), 날짜 칩 → `TripSheet.vue`(혼자/성인 동행/아이 동반 · 어른·아이 인원 · 아이 나이대 · 3달 달력). 공통 `components/common/BottomSheet.vue`, `stores/trip.js`, `utils/date.js`
- 적용하면 목록을 펼쳐 그 지역으로 다시 불러옴. 지도 상태(시트 접힘)에서도 칩을 누를 수 있음
- 백엔드: `region/`(`/api/regions/sidos`, `/sidos/{id}/sigungus`, `/sigungus?q=`), `trip/`(`POST /api/trips/check`), `member/` 에 아이 나이대(`GET·PUT /api/members/me/children`, 로그인 필요 → `SecurityConfig`)
- `/api/camps`·`/recommend` 에 `sido` 조건 추가 (시·군·구 이름이 전국에서 겹침, 예: 고성군), 쓰지 않게 된 `/api/camps/sigungus` 삭제
- DB: `schema.sql` 에 `sido`·`sigungu`·`member_child`, 데이터 `db/data_region.sql` (시·도 17 + 강원 18, 인제군만 안심 데이터 있음) — **DB 에 직접 실행해야 함**
- 다른 시·도의 시·군·구는 아직 없음 → 화면에 "준비 중". 전체는 행정표준코드(법정동코드) 파일로 넣을 예정
- 확인: `mvnw test` 통과 (32개, 지역 3·여행 조건 4·아이 나이대 2 추가), `npm run build` 통과
- 확인 못 함: SQL 실행, API 실제 응답, 브라우저 화면
- 남은 일: 동행·아이 나이대를 추천·안전 안내에 반영, 내 정보 › 함께 가는 가족 화면, 전국 시·군·구 데이터

## 2026-11-06 캠핑장 상세 · 시설 상세 · 관광지 상세
- 화면: `/camps/:id`(`views/camp/CampDetailView.vue`, 경로 지도 위 시트, 안전점수·캠핑장 정보·관광지 탭), `/facilities/:id?camp=`, `/tours/:id?camp=`. 공통 부품 `components/detail/`(DetailTopBar·PhotoBox·InfoRows·NoteList), 경로 지도 `components/map/RouteMap.vue`, `utils/place.js`
- 흐름: 탐색 목록 한 줄 → 캠핑장 상세. 지도 마커는 한 번 누르면 이름표, 다시 누르면 상세. 안전점수 탭 시설 → 시설 상세, 관광지 탭 관광지 → 관광지 상세. "경로 보기" → 경로 지도 (칩으로 시설을 고르면 검은 줄)
- 백엔드: `camp/` 에 상세·관광지(`GET /api/camps/{id}`, `/{id}/tours`), `facility/`, `tour/`, 공통 `place/`, `common/DriveTone`
- DB: `schema.sql` 에 `facility`·`camp_facility`·`tour`·`camp_tour`·`place_info`·`place_note`, `camp` 에 `reservation_url` 컬럼, 더미 `db/data_detail.sql` (설하캠핑장만) — **DB 에 직접 실행해야 함**
- 임시로 정한 것: 경로 줄은 두 좌표를 잇는 직선(실제 도로 아님, 화면에 표시), 차로 시간 색 기준 10분(`DriveTone`), 주의·위험 등급 headline 문구. 전화·주소는 지어내지 않고 "확인 필요"
- 더미 시설·관광지의 길찾기는 좌표 대신 카카오맵 이름 검색으로 연다 (더미 좌표로 안내하지 않기 위해)
- 토큰 추가(임시): `--color-caution-bg`
- 확인: `mvnw test` 통과 (42개, 상세 10개 추가), `npm run build` 통과
- 확인 못 함: SQL 실행, API 실제 응답, 카카오 지도 선 표시, 브라우저 화면
- 남은 일: 내 캠핑에 일정 등록(2부, 지금 버튼 비활성), 사진 여러 장, 실제 도로 경로, 다른 캠핑장 6곳의 시설·관광지 데이터, 공공데이터 연동

## 2026-11-06 카카오 지도 실제 키 적용 준비
- 적용 화면: 탐색 지도(`CampMap`), 캠핑장 경로 지도·시설 상세 지도(`RouteMap`). 키만 `client/.env.local` 의 `VITE_KAKAO_MAP_KEY` 에 넣으면 된다
- 보완: 공통 `fitPoints`(점 하나일 때 과확대 방지)·`keepMapSized`(크기 변경 반영), 마커 `clickable`(터치), 시설 상세 210px 높이에 맞춘 여백, 지도를 불러오는 중 화면을 나갔을 때 오류 방지


