# 진행 상황

## 2026-11-06 내 정보 · 여행 취향
- 목표: 고른 취향에 맞는 캠핑장 추천 (예: 낚시 → 낚시터 가까운 캠핑장). **추천은 예정** — 캠핑장 데이터(고캠핑 주변시설·입지·테마)가 들어오면 붙임
- 정한 것: 선택지 6개 — 낚시 · 등산·트레킹 · 계곡·물놀이 · 바다·해수욕 · 아이 체험 · 계절 풍경 (수상레저·별 보기는 뺌). **최대 3개, 순서(우선순위) 없음**
- DB: `member_activity` (member_id + activity code, PK 둘 다) — **DB 에 직접 실행해야 함**
- 백엔드: `member/` 에 Activity*(GET·PUT `/api/member/activities`). 선택지는 `ActivityOption` enum 한곳 (code 는 DB 값이라 바꾸지 않음). 없는 값·4개 이상 400
- 프론트: `/my-info/activity` (`views/member/ActivityView.vue`), 내 정보 메뉴에서 연결. 설계 TasteSurvey.dc.html 이 없어 Recheck.dc.html 의 선택 버튼 모양을 따름
- 확인: `mvnw test` 통과 (40개, 새 ActivityServiceTest 5개), `npm run build` 통과. 확인 못 함: 브라우저 화면, 실제 DB

## 2026-11-06 내 정보 · 함께 가는 가족 (설계 Family.dc.html)
- 화면: `/my-info/family` (`views/member/TravelFamilyView.vue`). 내 정보 메뉴에서 연결. 서버 변경 없음 — 가족방 아이 카드 API(`/api/family/children`)와 `stores/family.js` 를 그대로 씀
- 정한 것: 가족방이 없으면 "가족방 만들기" 안내(`/my-camping/family` 로 이동). 이 화면에서는 보기 + 추가(이름 필수·생년월일 선택) + 삭제. 알레르기 등은 가족방에서
- 설계와 다른 점: "태어난 해" 선택 대신 이름·생년월일 입력, "이름과 생일은 받지 않아요" 안내 문구 뺌(사실과 다름), 설계의 "날짜·인원을 고를 때 자동으로 불러와요" 문구 뺌(아직 없는 기능)
- 연령대(영유아 ≤3·미취학 ≤7·초등 ≤12·청소년)와 "이렇게 바뀌어요" 문구는 설계 그대로. 단 응급실 거리 비중·알림 등 **실제로 점수·알림이 바뀌는 기능은 예정** (구상만 있음, 지금은 문구만 보여 줌 — 만들 때 이 문구와 맞출 것)
- 확인: `npm run build` 통과. 확인 못 함: 브라우저 화면, 실제 DB

## 2026-11-06 내 정보 (1차) — 설계 ForYouNew·Me·Family·Recheck.dc.html
- 화면: `/my-info` (`views/member/MyInfoView.vue`). 서버 `member` 패키지와 맞춰 로그인·회원가입(`LoginView`·`SignupView`)도 `views/auth/` 에서 `views/member/` 로 합침. 진단 전(ForYouNew) 모양. 로그인하면 이름 + Me 의 메뉴 5개, 안 했으면 로그인·알림·공지. 아직 없는 메뉴는 "준비 중이에요" 표시. 임시 메인에 링크 추가
- 색 3개 `tokens.css` 에 추가 (`--color-text-light`·`accent-light`·`arrow`, 설계 파일에서)
- 정한 것: **함께 가는 가족은 따로 저장하지 않고 내 캠핑 가족방의 아이 카드(`child_card`)를 같이 쓴다** (내 캠핑에서 저장하면 내 정보에서도 보임).
  주의: 설계 Family 화면은 "이름·생일은 받지 않고 태어난 해만 저장"이라고 안내하는데 아이 카드는 이름·생년월일을 저장함 → 문구 또는 저장 방식을 맞춰야 함. 또 아이 카드는 가족방이 있어야 만들 수 있음
- 정한 것: **비상연락처는 이름 + 번호, 최대 5명** (`emergency_contact` 테이블, 아래 항목). 회원가입 때 비상연락처는 **선택**
- 설계에 없어서 임시로 정한 것: 로그아웃 버튼(메뉴 맨 아래), "1분 진단 시작하기"는 설문 화면이 없어 누를 수 없음
- 확인: `npm run build` 통과
- 남은 일: 함께 가는 가족 화면(아이 카드 연결), 진단 후(Me) 모양(설문 결과 회원별 저장 필요), 다시 진단(Recheck), 여행 취향·알림·공지, 하단 탭바

## 2026-11-06 내 정보 · 비상연락처 (최대 5명)
- 처음엔 2명이라 `member` 에 칸을 붙였다가, **최대 5명 + 추가/빼기**로 바뀌어 `emergency_contact` 테이블로 옮김 (member 에서 emergency_* 칸 삭제)
- DB: `db/schema.sql` 에 `emergency_contact` + 예전 모양 DB 옮기기(`DO $$` 블록: 번호 복사 후 member 칸 삭제, 여러 번 실행해도 됨) — **DB 에 직접 실행해야 함**
- 백엔드: `member/` 에 EmergencyContact*(GET·PUT `/api/member/emergency-contacts`), `EmergencyContactMapper`. SecurityConfig 에 `/api/member/**` 로그인 필요 추가
  - 규칙: PUT 은 목록을 통째로 바꿈(모두 지우고 순서대로 다시 넣음). 다 비운 칸은 건너뜀, 이름·번호 중 하나만 있으면 400, 6명 이상 400
- 회원가입: 비상연락처 선택 (넣으면 이름 없이 첫 번째 사람으로 저장) → 내 정보에서 다시 저장하려면 이름을 넣어야 함
- 프론트: `/my-info/emergency-contacts` (`views/member/EmergencyContactView.vue`). 설계 화면이 없어 Family.dc.html 모양을 따름 ("+ 추가하기" 점선 버튼, 칸 오른쪽 위 X). 추가·빼기는 "저장하기"를 눌러야 반영
- 확인: `mvnw test` 통과 (35개, EmergencyContactServiceTest 6개), `npm run build` 통과
- 확인 못 함: 실제 DB 동작, 브라우저 화면

## 2026-11-06 저장 화면 "가족 공유" 탭 삭제
- 이유: 가족방(내 캠핑)이 가족 기능을 맡고 있고, 후보 캠핑장 공유는 안전 핵심 기능이 아니라 나중으로 미룸. 저장 탭은 `목록`·`차박 위치` 두 개
- 나중에 필요해지면: `saved_list` 에 `shared_to_family` 컬럼 + 공유 켜기/끄기·가족방 공유 목록 조회 API (이야기만 하고 만들지 않음)
- 확인 못 함: 브라우저 화면 (코드만 수정)

## 2026-11-06 가족 모임방 2차 (아이 건강 응급 카드 · 일정 공유) + 내 캠핑 화면 분리
- 화면 구조 변경: `내 캠핑`(`/my-camping`, `views/mycamping/`)은 예약 후 정보 화면 → 가족방은 **버튼을 눌러** `/my-camping/family`(`views/family/FamilyRoomView`)로 들어감. 예약·날씨·위험 알림 카드는 데이터가 없어 자리만 둠
- 탭을 컴포넌트로 분리: `components/family/` (MeetingPlaceTab, ChildCardTab, ScheduleTab, AddButton). 공통 입력칸·버튼 모양은 FamilyRoomView 의 `.family-room` 범위 스타일
- DB: `db/schema.sql` 에 `child_card`·`family_schedule` 추가 — **DB 에 직접 실행해야 함**
- 백엔드: `family/` 에 ChildCard*(목록·추가·수정·삭제), Schedule*(목록·추가·삭제) 추가. 방 확인은 `FamilyService.requireMyRoom` 하나로 (다른 가족방 데이터는 404)
- 아이 카드: 이름만 필수, 비운 칸은 "기록 없음"으로 표시 ("없음"으로 단정하지 않음). 만 나이는 생년월일로 계산. 일정: 한국 시간으로 날짜별 묶어 표시
- 확인: `mvnw test` 통과 (28개, 새 ChildCardServiceTest 4개·ScheduleServiceTest 3개), `npm run build` 통과
- 확인 못 함: 실제 DB 동작(테이블 아직 없음), 브라우저 화면, 날짜(OffsetDateTime) JSON 주고받기
- 남은 일: 일정 수정(지금은 삭제 후 다시 추가), 내 캠핑 예약 정보 카드
- **3차 위치 공유는 보류** (지도 화면이 아직 없어서). 이야기한 방향: 같은 가족방 앱 사용자끼리만 공유, 각자 스위치로 켬(기본 꺼짐),
  화면이 열려 있는 동안만 갱신(웹 한계 → "n분 전" 꼭 표시), 최신 위치 1개만 저장, 끄거나 나가면 삭제. 앱 없는 가족에게 문자 보내기는 범위 밖

## 2026-11-05 내 캠핑 · 가족 모임방 (1차: 방 만들기·초대 + 모일 장소) — 브랜치 `feature/saved` 에서 작업 (분리 필요)
- 위치 결정: 가족방은 하단 탭 `내 캠핑` 안에 둔다 (저장 = 예약 전, 내 정보 = 계정 설정이라 성격이 다름). 저장 화면의 "가족 공유" 탭 계획은 이쪽으로 옮김
- DB: `db/schema.sql` 에 `family_room`(초대코드 6자)·`family_member`(한 회원당 방 1개)·`meeting_place`(좌표 선택) 추가 — **DB 에 직접 실행해야 함**
- 백엔드: `family/` 패키지 (`/api/family/**`, SecurityConfig 에 회원 전용 추가). 방 번호를 주소로 받지 않고 로그인한 회원의 방만 다룸
- 프론트: `/my-camping`(MyCampingView), `stores/family.js`, 임시 메인에 "내 캠핑" 링크. 모일 장소 좌표는 "지금 내 위치 넣기"(브라우저 위치)로만 넣고, 지도는 카카오맵 웹 링크로 연다
- 확인: `mvnw test` 통과 (21개, 새 FamilyServiceTest 9개), `npm run build` 통과
- 확인 못 함: 실제 DB 동작(테이블 아직 없음), 브라우저 화면, 위치 권한 동작
- 하단 탭바는 아직 없음 (요청에 따라 이번엔 화면만)
- 남은 일: 2차 아이 건강 응급 카드·일정 공유 → 3차 위치 공유. 방장 구분 없음(모두 같은 권한), 지도에서 모일 곳 고르기는 카카오맵 SDK 연결 때

## 2026-11-04 저장 (1차: 목록 탭 + 관심 캠핑장 비교) — 브랜치 `feature/saved`
- DB: `db/schema.sql` 에 `saved_list`(회원별 목록, `관심 캠핑장` 기본 목록)·`saved_camp`(담은 캠핑장) 추가 — **DB 에 직접 실행해야 함**
- 백엔드: `saved/` 패키지 (`/api/saved/**`, 회원 전용으로 SecurityConfig 수정), `common/NotFoundException`(404) 추가
- 비교 값은 `saved/CampDummyData` 더미 3곳(설계 Compare.dc.html 값) → 응답 `dummy: true`, 화면에 "더미 데이터" 안내
- 프론트: `/saved`(SavedView), `/saved/lists/:id`(CompareView), `stores/saved.js`. 빈 목록에는 시험용 "예시 캠핑장 3곳 담기 (더미)" 버튼
- 확인: `mvnw test` 통과 (12개, 새 SavedServiceTest 7개), `npm run build` 통과
- 확인 못 함: 실제 DB 동작(테이블 아직 없음), 브라우저 화면, 비로그인 401 응답
- 설계에서 뺀 것: 아래 메뉴(다른 화면 없음), 검색 버튼, 비교 하단 "가장 안심되는 ○○ 보기"(캠핑장 상세 없음), "내 유형 기준" 문구(유형을 계산에 안 씀)
- 남은 일: 차박 위치 탭(PostGIS 좌표), 가족 공유 탭(공유 방식 설계), 최근 본 캠핑장, 목록 이름 바꾸기·삭제 화면(API 는 있음), 캠핑장 상세의 하트 버튼
- 설계 색 5개를 `tokens.css` 에 추가 (`--color-photo`·`fill-soft`·`line`·`divider`·`text-mid`) 하고 저장·비교 화면에 적용
- "가장 좋은 값" 기준은 임시 규칙 유지 (응급실·소방서 = 가장 짧은 시간, 대피소 = 걸어서 갈 수 있는 곳 우선) — 확정 필요

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
