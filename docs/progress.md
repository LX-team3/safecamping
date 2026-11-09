# 진행 상황

## 2026-11-09 지도 시설 마커를 종류별 아이콘 + 안전점수 등급 색으로
- 아이콘: 사용자가 준 SVG Repo 아이콘 5개(응급실 병원·소방서 불꽃·경찰서 경찰관·편의점 24 가게·약국 알약)를 `client/src/utils/facilityIcon.js` 한곳에 모음. 검정 → `currentColor` 로 바꿔 CSS 로 흰색 표시. 경찰관(선 아이콘)은 작게 보면 흐려서 선 굵기 1.4 → 1.8
- 모양: 등급 색 동그라미 + 흰 아이콘 + 오른쪽 위 작은 번호(목록·칩 순서). 공통 스타일 `.fac-badge` 는 `styles/base.css` (카카오 지도 위 요소라 scoped 가 안 닿음)
- 색 = **캠핑장 안전점수 등급** (사용자 결정): 안전 70↑ 초록 `--color-green` · 주의 40~70 주황 `--color-caution` · 위험 40↓ 빨강 `--color-danger` · 점수 없음 회색 (`CampService.gradeOf` 기준 그대로). 기존 색 변수만 씀
  - 그래서 한 지도 안의 시설 마커는 모두 같은 색 (시설별 차로 시간 색 `DriveTone` 과는 별개)
- 적용한 지도: 내 주변(`NearbyMap.vue`, 기준 캠핑장 `data.grade`), 캠핑장 상세 경로(`RouteMap.vue`, 흰 이름표 왼쪽에 배지, 고른 시설은 검은 테두리), 시설 상세(`RouteMap.vue` 같이 씀). 카카오 키 없는 임시 지도도 같은 모양
- 서버: 시설 상세 `GET /api/facilities/{id}?campId=` 의 `from` 에 `grade` 추가 (`FacilityMapper` 에서 `c.safety_score` 조회 → `CampService.gradeOf`). DB 변경 없음
- 확인: `mvnw test` 통과 (86개), `npm run build` 통과
- 확인 못 함: 브라우저에서 실제 모양(아이콘 크기·선명도, 특히 편의점·약국처럼 세밀한 아이콘이 18px 정도에서 잘 보이는지), 시설 상세 API 실제 DB 응답
- 남은 일: SVG Repo 아이콘은 아이콘마다 라이선스가 달라 **배포 전 각 아이콘 페이지에서 라이선스·출처 표기 필요 여부 확인**. "지도에 보일 것" 시트(`LayerSheet.vue`)의 글자 아이콘(+·119·P·24·약)도 같은 아이콘으로 맞출지 결정

## 2026-11-09 약국 · 편의점 더미 데이터 추가 (내 주변 지도 레이어)
- DB: `db/data_detail.sql` 에 시설 4곳 추가 — 편의점(`북면 편의점`·`인제읍 편의점`), 약국(`북면 약국`·`인제읍 약국`). 이름은 실제 가게와 헷갈리지 않게 "지역 + 종류"로만. 좌표·거리·시간은 **가상 값** (`is_dummy = true`)
- 설하캠핑장과 `camp_facility` 로 연결 (번호 5~8) → 내 주변 지도·캠핑장 상세 목록에 나옴 (상세 목록이 4곳 → 8곳)
- 스키마: `db/schema_02.sql` facility.type CHECK 에 `pharmacy` 추가 + 이미 만든 DB 용 `ALTER TABLE ... facility_type_check` (DROP IF EXISTS 후 다시 걺) — **schema_02.sql → data_detail.sql 순으로 DB 에 다시 실행해야 함**
- 서버: `NearbyLayer` 약국 `ready: true`, 설명 "상비약 · 해열제 구하기". 대피소만 `ready: false` 로 남음
- 프론트: `utils/place.js` FACILITY_LABEL 에 `pharmacy: '약국'`. 내 주변 점수 아래 "차로 n분" 줄에서 약국도 편의점처럼 뺌 (응급 시설 아님)
- 확인: `mvnw test` 통과 (86개), `npm run build` 통과
- 확인 못 함: 실제 DB 에서 ALTER·INSERT 실행 (제약 이름 `facility_type_check` 은 PostgreSQL 자동 이름으로 가정), 브라우저에서 약국 레이어 켜기·마커
- 참고: 경로 API(`/api/camps/{id}/routes`)는 캠핑장에 연결된 시설을 전부 부르므로 설하캠핑장은 카카오 호출이 4번 → 8번으로 늘어남
- 남은 일: 실제 약국·편의점 공공데이터 연동, 대피소 데이터(CHECK 에 shelter 추가 필요)

## 2026-11-09 캠핑장 상세 경로 지도에 편의점 · 약국 자동차 경로 추가
- 서버: `RouteMapper.findFacilities` 의 시설 종류를 `er·fire·police` → `er·fire·police·store·pharmacy` 로 넓힘 (화장실만 뺌). API 모양은 그대로
- 화면 변경 없음: 경로 지도 칩·"○○ · n km · 차로 n분" 줄은 원래 캠핑장 시설 전부를 보여 주고 있었음. 편의점은 지금까지 경로를 안 받아 선 없이 마커만 나왔는데, 이제 다른 시설처럼 도로를 따라 파란 선 + 카카오 시간·거리
- 주석·문서 정리: `CampDetailView.vue`·`RouteMap.vue`·`RouteService.java`·`server/CLAUDE.md`
- 설하캠핑장은 카카오 호출이 시설 8곳 = 8번 (동시 호출이라 기다리는 시간은 비슷). 24시간 캐시는 그대로
- 확인: `mvnw test` 통과 (86개), `npm run build` 통과
- 확인 못 함: 실제 카카오 호출(키 필요)·실제 DB 응답, 브라우저에서 선 8개가 겹쳐 보이는 모양

## 2026-11-08 캠핑장 상세 · 경로 지도에 카카오 자동차 경로 (응급실·소방서·경찰서)
- 흐름: 탐색(추천 TOP5 · 지도 마커) → 캠핑장 상세 → "경로 보기" → 3곳까지 도로를 따라 **형광 파란 선** + 자차 시간·거리. 고른 시설은 진하고 굵게(빛 + 밝은 심), 나머지는 옅게
- 서버: `route/` — `GET /api/camps/{id}/routes`. 카카오모빌리티 길찾기(`/v1/directions`)를 시설마다 1번씩 동시에 호출, 전부 성공하면 캠핑장별 24시간 메모리 저장, 실패 후 1분은 다시 안 부름. 키는 환경변수 `KAKAO_REST_KEY` (REST API 키)
- 실패 처리: 키 없음·카카오 오류·길 못 찾음 → 그 시설만 직선 + DB 시간 (`ok: false`). 화면 문구 "실제 경로를 불러오지 못해 직선으로 보여 드려요"
- 화면: `RouteMap.vue` 가 시설의 `path`(도로 좌표)를 받아 그 길을 따라 그림 (없으면 직선 — 시설 상세 지도도 같은 파란 점선). `noLine` 으로 편의점은 실제 경로가 있을 때 선 없이 마커만. `CampDetailView.vue` 는 경로를 상세와 따로 불러 칩·아래 줄 시간을 카카오 값으로, 안내 문구를 상황별로 바꿈 ("카카오 길찾기 예상 시간", 더미 시설이면 "예시 위치 기준 경로")
- 색 2개 `tokens.css` 추가 (사용자 요청): `--color-route`(형광 파랑), `--color-route-core`(고른 선 안쪽 밝은 심)
- 안전점수 탭 목록·점수 설명의 "차로 n분"은 그대로 DB 값 → DB 에 카카오 값을 넣으면 서로 맞춰짐
- DB 변경 없음. **시설 좌표가 지금 더미라 경로도 예시 위치 기준** → 설하캠핑장·실제 시설 좌표 데이터 반영 필요 (SQL 초안은 대화에서 공유, 아직 파일 없음)
- **서버가 `server/.env` 를 자동으로 읽지 않음** → 실행 설정(VS Code `launch.json` 의 `envFile` 등)으로 `KAKAO_REST_KEY` 를 넣어야 함
- 확인: `mvnw test` 통과 (80개, RouteServiceTest 7개 포함), `npm run build` 통과
- 확인 못 함: 실제 카카오 호출(키 필요), 실제 DB 로 API 응답, 브라우저 화면(선 색·빛 모양)
- 남은 일: 실제 시설 좌표 데이터, 탐색 지도에서 마커를 한 번 누를 때 바로 경로 보이기(2차), 후보 여러 곳 중 가장 빠른 시설 고르기(다중 목적지), 경로·시간 DB 저장

## 2026-11-08 내 캠핑 일정 등록 (서버 저장) · 탐색 진단 팝업 · 지도 레이어 · 내 정보 정리
- **내 캠핑 일정 등록**: 캠핑장 상세 "내 캠핑에 일정 등록"(비활성이던 버튼) → 시트(체크인·체크아웃·메모) → 내 캠핑 탭(`MyCampingView`)의 "준비 중" 자리에 카드로 보임 (D-day·n박·삭제)
  - DB: `camp_plan` 테이블 추가 (`db/schema.sql`) — **DB 에 직접 실행해야 함**. 캠핑장 이름·지역은 등록 때 값을 복사해 저장, `camp_id` 는 외래키 없이 값만
  - 서버: 새 패키지 `mycamping/` (CampPlan*). `GET`·`POST /api/member/camp-plans`, `DELETE /api/member/camp-plans/{id}`. `/api/member/**` 아래라 **SecurityConfig 수정 없음**
  - 검사(서버·프론트 같음): 지난 체크인, 체크아웃 ≤ 체크인, 같은 캠핑장·같은 체크인 중복 → 400
  - 프론트: `stores/myCamping.js`. 로그아웃하면 목록 비움. 가족방 일정(family_schedule)과는 별개
- **탐색 진단 팝업**: 진단 안 한 사람에게 탐색 화면 가운데 팝업 "내 유형 진단하기"(그림 `assets/illust/camping.svg`, unDraw — 출처 표기 불필요). "지금하기" → `/survey?from=explore` → 끝나면 탐색으로 돌아옴. "나중에하기" → 그 탭(sessionStorage)에서 다시 안 뜸
- **탐색 지도 레이어 버튼**: 비활성 → "지도에 보일 것" 시트(`components/explore/MapLayerSheet.vue`). 안전·주의·위험 등급 마커 켜고 끄기 (기본 모두 켜짐, 목록은 그대로)
- **내 주변 패널**: 손잡이를 누르면 패널이 120px 로 내려가 지도가 넓게 보임 (다시 누르면 올라옴). 지도 마커도 다시 맞춤
- **내 정보**: 메뉴 아래 회색 설명 줄과 "○○님, 안녕하세요" 삭제
- 확인: `mvnw test` 통과 (73개, 새 CampPlanServiceTest 5개), `npm run build` 통과
- 확인 못 함: `camp_plan` 실제 SQL 실행·API 응답, 브라우저 화면 전부 (팝업·시트·패널·일정 등록)
- 남은 일: 탐색 지도에 응급실·소방서 등 시설 레이어, 내 캠핑 카드에 날씨·위험 알림, 캠핑장 상세 하단 "안전 알림을 보내드려요" 문구(알림 기능 없음)

## 2026-11-08 내 주변 탭 (지금 위치 안전점수 · 지도 레이어)
- 화면: `/nearby` (`views/nearby/NearbyView.vue`), 부품 `components/nearby/NearbyMap.vue`(내 위치 + 번호 마커)·`LayerSheet.vue`("지도에 보일 것"). 아래 탭 "내 주변" 연결
- 시설 목록은 **차로 n분만** 보여 줌 (대피소 도보 시간 뺌). 길찾기는 카카오맵으로만 넘김 (더미 시설은 이름 검색, `utils/place.js` directionsUrl)
- 레이어 시트: 응급실·소방서·경찰서·24시 편의점·약국·대피소. **공중화장실 뺌**. 약국·대피소는 줄만 있고 데이터 없음(`ready: false`, 기본 꺼짐). 탭 "지도 종류"·"날씨 · 재난"은 비활성
- 서버: `nearby/` — `GET /api/nearby?lat=&lng=`, `GET /api/nearby/layers`. DB 변경 없음 (기존 camp·facility·camp_facility 사용)
- 임시로 정한 것: "지금 이 위치" 점수를 계산할 방법이 없어 **시설 데이터가 있는 가장 가까운 캠핑장의 점수·시간**을 보여 주고 화면에 "○○ 기준 · 더미 데이터"로 표시. 10km 넘게 떨어지면 "가장 가까운 데이터 ○○(nkm) 기준". 위치 권한이 없으면 예시 위치(설하캠핑장 좌표)
- 위쪽 상태 카드: 지역 + 기상특보 수(`/api/alerts`, 더미). 날씨(기온)는 데이터가 없어 "준비 중"
- 가족에게 내 위치 보내기: 휴대폰 공유 창(`navigator.share`) → 없으면 카카오맵 링크 복사. 가족방으로 바로 보내기는 2부 가족 위치 기능에서
- 확인: `mvnw test` 통과 (65개, 새 NearbyServiceTest 6개), `npm run build` 통과
- 확인 못 함: 실제 DB 에서 SQL(가장 가까운 캠핑장) 실행, 브라우저 화면·위치 권한, 카카오 지도 마커, 휴대폰 공유 창
- 남은 일: 약국·대피소 데이터(facility.type CHECK 에 pharmacy·shelter 추가 필요), 내 위치 기준 실제 거리·시간 계산(PostGIS/경로 API), 날씨


## 2026-11-08 알림 · 기상특보 실제 데이터 (기상청)
- 서버: `alert/KmaWarningClient`(기상청 기상특보 조회서비스 `getPwnStatus` 호출, 10분 캐시, 실패 시 1분간 재시도 안 함), `alert/KmaWarningParser`(t6 "o 종류 : 강원도(구역…), …" → 시·도별 Alert), `alert/WarningSource`(테스트에서 가짜를 넣기 위한 인터페이스)
- 키: 환경변수 `KMA_SERVICE_KEY` (공공데이터포털 디코딩 키). 없거나 호출 실패면 기상특보는 더미 + 화면에 "예시"
- 응답: `dummy` → `warningDummy`·`disasterDummy` 로 나눔. 재난문자는 아직 더미 (`AlertDummyData.disasters()`)
- 화면(`AlertView`): 예시인 쪽만 안내 문구·"예시" 칩. 실제 특보는 "~부터" 대신 "~ 기준" (특보현황에 특보별 시작 시각이 없어 지어내지 않음)
- 정한 것: 바다 구역(풍랑특보 등)은 시·도 알림에서 뺌, 예비특보(t7)는 아직 안 씀, 구역이 많으면 제목에 3곳 + "외 n곳"
- 남은 일: 재난문자 API (행정안전부, 키 신청 필요), 예비특보 표시 여부
- 확인: `mvnw test` 통과 (68개, 알림 9개 추가), `npm run build` 통과. 실제 기상청 호출 확인 (2026-11-08, 강원·경북 건조주의보가 화면에 나옴)
- 주의: Eclipse 에 Lombok 이 없으면 Eclipse 가 `target/classes` 를 깨진 클래스로 덮어써 서버가 안 뜬다 → `mvnw clean spring-boot:run` 또는 Eclipse 에 Lombok 설치

## 2026-11-07 첫 화면 탐색으로 · 하단바 탭 연결
- 라우터: `/` 로 들어오면 `/explore` 로 보낸다 (redirect). 로그인 후·"로그인 없이 둘러보기"도 탐색으로 간다
- 하단바(`components/common/BottomNav.vue`): 내 캠핑 `/my-camping` · 저장 `/saved` · 내 정보 `/my-info` 연결. 내 주변은 화면이 없어 아직 비활성
- `MyInfoView`·`MyCampingView`·`SavedView` 에 하단바 추가. 탐색 화면처럼 `.page`(세로 flex) 안에 내용(스크롤) + 하단바
- 남은 일: 하위 화면(비상연락처·가족방·비교 등)은 하단바 없음 (뒤로가기 화면이라 일부러 뺌). `HomeView` import 는 안 쓰지만 남아 있음
- 확인: `npm run build` 통과. 실제 브라우저 화면은 확인 못 함

## 2026-11-07 관광지 탭 여행 취향 (설문 2문항)
- 화면: `/tour-preference?camp=캠핑장id` (`views/tour/TourPreferenceView.vue`), 스토어 `stores/tourPreference.js`. 관광지 탭 배너에서 들어오고, 저장한 뒤에는 "바꾸기"로 다시 연다
- 서버: `tour/TourPreference*`. `GET /api/tour-preferences/questions`, `GET`·`PUT /api/member/tour-preferences`
- DB: `member_tour_preference` 테이블 (`db/schema.sql`). 1분 진단(survey)과 내 정보 여행 취향(ActivityView·member_activity)은 건드리지 않음
- 비회원은 sessionStorage 에만 저장, 로그인해도 서버로 옮기지 않음
- 설계와 다른 점: 아이 안내 문구. "나이에 맞지 않는 활동은 뒤로 보내드려요"는 연령 데이터가 없어 동작하지 않으므로 "연령 제한을 꼭 확인하세요"로 바꿈
- 남은 일: 내 정보 여행 취향과 합칠지 팀에서 결정, 관광지 더미 데이터 추가(지금 2곳)

## 2026-11-07 탐색·상세 화면을 현재 1분 진단 스토어에 맞춤
- `ExploreView`: `survey.answers` 가 null 이어도 그려지게 하고, 진단 값(`campType`, `fuel`, `facilities`)을 현재 스토어 형식으로 맞춤
- `CampDetailView`: 없는 `survey.answers.travel`·`fetchMine` 제거. 여행 취향 화면이 없어 관광지는 인기순, 취향 배너는 숨김
- 남은 일: 여행 취향 화면(설문 2문항), `MemberChild*` 4개 합치기, `SecurityConfig` 에 `/api/members/me/**` 추가, 관광지 탭 안내 문구 정리
- 확인: 복사본에서 `vite build` 통과, 탐색·상세 3화면 마운트 오류 없음. 서버 API 응답 200 (시·도 17곳, 캠핑장 7곳). 실제 브라우저 화면은 확인 못 함

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


## 2026-11-06 내 정보 · 알림 (기상특보 + 재난문자 모아 보기) — 더미
- 설계 파일 없음 → 예시로 받은 "재난 안전문자 대시보드" 목록 모양 참고. 숫자 카드(전국 건수·사용자 제보) 대신 "지금 내 지역" 카드(발효 중 특보 + 오늘 재난문자 수)
- 화면: `/my-info/alerts` (`views/member/AlertView.vue`), 내 정보 메뉴(로그인 전·후 모두)에서 연결. 로그인 없이 볼 수 있음
  - 필터 칩: 전체 · 기상특보 · 재난문자. 목록은 최신순, 내용 두 줄 → 누르면 전체. 맨 아래 "공식 재난문자를 대신하지 않습니다"
  - 칩 색은 새로 안 만듦: 특보 = 1분 진단 위험 안내 색(danger), 재난문자 = 회색 → **설계 파일이 오면 다시 정함**
- 지역: 브라우저 위치 → 서버가 가장 가까운 시·도 중심으로 정함 (**임시, 경계 근처에서 틀릴 수 있음** → 카카오 좌표→행정구역 API 로 교체). 위치 실패 시 전국 + 시·도 직접 고르기
- 백엔드: `alert/` (AlertDummyData 10건, 시각은 "지금 - n분"). `GET /api/alerts?sido=` 또는 `?lat=&lon=` 또는 없음(전국). 없는 지역·국외 위치 400
- 남은 일: API 키 신청(기상청 기상특보 조회서비스, 행정안전부 재난문자) → 서버가 주기적으로 받아 DB 에 저장하고 더미 삭제. 푸시 알림, 캠핑장 지역 기준, 알림 켜기/끄기 설정
- 확인: `mvnw test` 통과 (51개, 새 AlertServiceTest 5개), `npm run build` 통과. 확인 못 함: 브라우저 화면, 위치 권한 동작

## 2026-11-06 관심 캠핑장 비교 — 표 → 항목별 막대로 변경 (가시성)
- 이유: 390px 에 3칸 표라 글자가 작고(12~13px) 가장 좋은 값이 굵은 글씨로만 구분됨
- 화면(`CompareView`): 위에 비교 중인 캠핑장 3곳(빼기), 아래 항목마다 카드 하나 — 캠핑장별 한 줄(이름 · 막대 · 값 · "가장 가까움" 같은 칩). 설계 Compare.dc.html 의 표 모양과 달라짐
- 막대: 안전점수는 100점 기준(초록), 시간은 그 항목의 가장 긴 값 기준. 대피소는 걸어서·차로가 섞이면 같은 막대로 비교할 수 없어 글자만. 전기·반려동물은 가능/불가 글자만
- 서버 응답 변경: `Row` 에 `bestLabel`, `Cell` 에 `value`(막대용 숫자) 추가, kind 는 score·minutes·yesno. 안전점수도 가장 높은 곳에 칩을 붙임 (표일 때는 안 붙였음)
- 확인: `mvnw test` 통과 (46개, 비교 테스트에 막대 값·칩 확인 추가), `npm run build` 통과. 확인 못 함: 브라우저 화면

## 2026-11-06 1분 진단 (새 설계 Survey.dc.html — 3단계 5문항)
- 예전 설문(5단계 10문항)에서 바뀜: 지역·날짜·동행은 탐색 화면으로, 관광지·체험은 내 정보 · 여행 취향으로 옮김. 남은 5문항: 캠핑 종류 · 이동 · 난방 · 안전 용품 · 안심 시설(최대 3개, 순서 있음)
- 예전 10문항 설문 코드(다른 브랜치)는 **쓰지 않기로 함** → 이번 코드가 정본. CLAUDE.md 의 예전 설문 설명도 고침
- 정한 것: 끝나면 내 정보로 (설계는 탐색 화면인데 아직 없음 → 버튼 문구도 "진단 마치기"로 임시). 누구나 진단 가능 — 로그인했으면 서버 저장, 안 했으면 sessionStorage (로그인 후 내 정보·진단을 열면 서버로 옮겨 저장). 결과는 **유형 이름 없이 답 요약만** (판정 기준 없음)
- DB: `survey_answer` (회원당 한 줄, 여러 개 답은 쉼표로 이음) — **DB 에 직접 실행해야 함**
- 백엔드: `survey/` (SurveyQuestions 에 질문 정본, SurveyService 검사: 빠짐·최대 개수·없는 값·중복·"아직 없어요" 같은 혼자만 고르는 값). `GET /api/survey/questions`(누구나), `GET`·`PUT /api/member/survey`(로그인)
- 프론트: `/survey` (`views/survey/SurveyView.vue`), `stores/survey.js`. 내 정보: 진단 전 버튼 연결, 진단 후 카드(답 요약 + 다시 진단하기). 다시 진단은 이전 답을 채워서 전체 다시 (Recheck 의 "바뀐 부분만"은 아직)
- 색 3개 `tokens.css` 추가 (`--color-track`·`danger`·`danger-soft`, 설계에서)
- 설계에서 뺀 것: 내 정보의 "내 유형 안전수칙" 버튼·캠핑 기록·안전 체크 카드 (데이터·화면 없음)
- 확인: `mvnw test` 통과 (46개, 새 SurveyServiceTest 6개), `npm run build` 통과. 확인 못 함: 브라우저 화면, 실제 DB

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
