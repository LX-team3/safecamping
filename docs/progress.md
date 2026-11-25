# 진행 상황

## 2026-11-25 배포 문서를 처음 보는 팀원도 따라 하게 보완 (문서만. 코드 · 설정 · 서버 · DB 변경 없음)
- `docs/deploy.md` 1 ~ 14단계: 시작 전 준비물, 내 값으로 바꿀 것, 콘솔에서 누르는 순서(1 ~ 3), 작업 PC 준비(11-0), 올릴 파일 만들기(11-8), 환경변수 모양(12-1), 확인 명령(12-4 · 13-2 · 14-4), 카카오 새 주소 등록(14-5), 마지막 확인(14-6) 추가
- 웹 배포는 14단계까지면 끝. 15(앱) · 16(벡터 DB)은 내용을 그대로 둠
- 확인: 콘솔 메뉴는 네이버 클라우드 공식 가이드와 대조, 작업 PC에서 git 제외 확인 · `.jar` 목록 · 지문 명령 실행
- 확인 못 함: 새 서버에서 1 ~ 14를 처음부터 따라 해 보기, 서버에서 치는 확인 명령, certbot 질문 화면

## 2026-11-25 RAG용 벡터 DB를 배포 서버에 올리는 순서 정리 (서버 · 코드 변경 없음)
- `docs/deploy.md` 16단계에 적음: 안전 자료 조각 CSV → 서버의 새 DB `campine_vector`(pgvector)
- 확인: 작업 PC의 `safety_doc_chunk.csv` 120줄, 벡터 120개(1536), `text-embedding-3-small`
- 확인 못 함: 서버에서 명령 실행, 작업한 팀원의 `01_table.sql`과 테이블이 같은지
- 남은 일: RAG 백엔드 코드. 지금은 벡터 DB를 쓰는 코드가 없어 화면은 그대로

## 2026-11-24 도메인 · HTTPS 적용, 배포 주소로 앱(APK) 빌드 (DB · 서버 코드 변경 없음)
- 주소: `https://campine.duckdns.org` (DuckDNS + Let's Encrypt). 자세한 순서는 `docs/deploy.md` 14 · 15단계
- 앱 이름: `내 캠핑메이트` → `campine` (`capacitor.config.json`, 안드로이드 `strings.xml`. `appId`는 Firebase와 연결되어 그대로 둠)
- `client/capacitor.config.json`: `url`을 배포 주소로 바꾸고 `cleartext` 제거 → `cap sync` → debug APK 빌드
- 화면 이름도 `campine`으로: 브라우저 탭 제목, 모바일 로그인(같은 말이 두 번 나오게 되어 부제는 삭제), 재난지도 `CAMPIN` 2곳. 로고 · `CAMPINE` 표기는 그대로. `npm run build` 통과, 서버에 올림(사이트 첫 화면이 새 빌드와 같음 확인), 실제 폰에 APK 설치(작업자 확인)
- 확인: 인증서 검증, http→https 이동, 화면 · API 200, 다른 주소 조회 요청 403, APK 빌드 성공과 APK 안의 주소
- 확인 못 함: 로그인 · 저장, 카카오 지도(새 주소 등록 필요), 현재 위치, 인증서 자동 갱신 시험, 실제 폰에서의 기능 동작

## 2026-11-24 메타버스 최신 기능을 client 구조로 합침 (브랜치 `myj-metaverse` ← `main`, DB 변경 없음)
- 배경: main 에서 메타버스가 `client/` 안으로 옮겨졌는데(11-22), 옮겨진 것은 소품·침수 미션이 들어가기 전 옛 버전. `myj-metaverse` 의 최신 기능을 그 구조 위에 합침
- 방법: 최신 main 을 `myj-metaverse` 로 가져와(merge) 충돌 9개를 풂. 새 파일(`flood.js`·`props.js`·`minigame.js`·`quiz_points.geojson`)은 `client/` 쪽 새 위치로, `missions.js`·`MetaverseView.vue` 는 양쪽 내용을 합침
- 퀴즈는 DB(`/api/quizzes`)를 쓰지 않고 `client/public/metaverse/data/missions.json` 40문제만 읽음 (메타버스 쪽에서 이미 정한 것)
- 화면: main 의 "← 캠핑 앱으로" 버튼 유지. 세로 휴대폰 화면에서 정보줄 글자가 세로로 꺾이고 안내·미니게임 창과 겹치던 것을 고침
- 확대 막기(`user-scalable=no`)는 `client/index.html` 에 넣지 않음 — 팀 앱 전체에 영향을 줘서. 게임 화면 안은 `touch-action: none` 으로 막음
- 확인: client 개발 서버 `/metaverse` 에서 마시멜로 굽기 → 꼬치 퀴즈 → 주차장 퀴즈 → 침수 대피 시작, 데이터 파일 15개 모두 불러옴, 세로(390×780)·가로(740×360) 화면에서 버튼 겹침 없음, `npm run build` 통과
- 확인 못 함: 실제 휴대폰, 서버(8080)를 켠 상태, 메타버스가 아닌 다른 화면들
- 알아 둘 것: `metaverse/gis/camp.qgz` 는 옛 위치(`metaverse/public/data/terrain.asc`)의 지형 파일을 가리킬 수 있음 → QGIS 에서 열 때 경로를 다시 잡아야 할 수 있음 (확인 못 함)

## 2026-11-24 푸시 토큰 등록 버그 수정 (브랜치 `lyh-aibrief`)
- 증상: 앱에서 로그인한 직후 토큰 등록이 403 으로 실패 → PC 에서 저장하면 "브리핑 푸시 안 보냄: 등록된 폰 없음". 화면을 새로 불러오면(`location.href` 등) 등록됨
- 원인: 로그인 직후 서버가 CSRF 쿠키를 지우는데, 새 쿠키를 받기 전에 POST 를 보냄
- 수정: `utils/pushNotify.js` `registerToken` — GET 으로 새 쿠키를 받은 뒤 등록, 403 이면 한 번만 다시. 서버 · SecurityConfig 변경 없음
- 확인: 쿠키 없이 POST → 같은 403 문구, GET 뒤 POST → CSRF 통과(로그인 안 해서 401), `npm run build` 통과. 확인 못 함: 실제 폰에서 로그인 → 등록 → 알림

## 2026-11-24 PC 웹에서 일정 저장 → 10초 뒤 폰에 브리핑 푸시 — 5번 (브랜치 `lyh-aibrief`)
- 결정: 저장 10초 뒤 발송(시연용), 10초 안에 일정을 지우거나 고쳐도 알림은 그대로 감. DB · SecurityConfig · 라이브러리 변경 없음
- `server` `push/BriefPushService`: 10초 뒤 그 회원의 폰 토큰을 찾아 발송(`path=/app/my-camping`, `brief=일정id`). 등록된 폰이 없거나 실패해도 일정 저장은 그대로 성공. 로그에는 일정 id 만
- `CampPlanRequest` 에 `notifyPhone` 추가, `CampPlanController` 등록(POST)이 성공했을 때만 예약 (고치기 PUT 은 안 보냄)
- `stores/myCamping.js`: 앱이 아닐 때만 `notifyPhone: true` (앱은 로컬 알림이 이미 떠서)
- `MobileMyCampingView`: 내 캠핑 화면이 떠 있을 때 알림을 누르면, 목록에 없는 일정이면 다시 받아 브리핑을 펼침. 지난 캠핑을 거르던 조건이 동작하지 않던 것 수정(`p.past` → `checkOut`)
- 확인: `.\mvnw.cmd test` 208개 통과(새 테스트 8개, 가짜 발송), `npm run build` 통과. 확인 못 함: 실제 Firebase 발송, 실제 폰 수신 · 눌러서 브리핑 열기, 브라우저에서 저장해 보기
- 남음: 폰에서 확인

## 2026-11-24 폰 주소(푸시 토큰) 등록 — 4번 (브랜치 `lyh-aibrief`)
- 결정: 토큰은 서버 메모리에 보관(시연용, 재시작하면 비워짐), 회원당 폰 한 대(나중에 로그인한 폰). DB · SecurityConfig · 라이브러리 변경 없음
- `server` `push/`: `PushTokenStore`(주소록) · `PushTokenController`(`POST`·`DELETE /api/member/push-token`) · `PushTokenRequest`(로그에 토큰이 안 보이게 가림)
- 앱: `utils/pushNotify.js` `linkPushToken`(토큰과 로그인이 모두 준비되면 등록) · `unlinkPushToken`(로그아웃 직전에 삭제, `stores/auth.js`), `main.js` 연결. 토큰을 콘솔에 찍던 줄 삭제. PC 웹 · 모바일 브라우저에서는 아무 일도 안 함
- 알림 테스트 화면에 "서버 등록" 상태 한 줄 추가
- 확인: `.\mvnw.cmd test` 200개 통과(새 테스트 10개), `npm run build` 통과. 실제 키 파일로 "Firebase 연결 준비됨" 로그 확인(서버 전체를 띄우는 테스트에서). 확인 못 함: 실제 폰에서 로그인 · 로그아웃할 때 등록 · 삭제, 로그인 안 한 요청의 401, 실제 발송
- 남음: 5번 일정 저장 뒤 발송 예약

## 2026-11-24 서버에서 Firebase 푸시를 보낼 준비 (브랜치 `lyh-aibrief`)
- 라이브러리 추가(승인): `firebase-admin` 9.12.0 (`server/pom.xml`). DB · 로그인 설정 · `application.properties` 변경 없음
- `server` `push/`: `PushSender`(테스트용 인터페이스) · `FcmPushSender`(Firebase 발송, 채널 `push-alert`). 키가 없거나 잘못돼도 경고만 남기고 서버는 그대로 켜짐
- 키: 환경변수 `GOOGLE_APPLICATION_CREDENTIALS` 에 서비스 계정 키 파일 경로. 키는 프로젝트 폴더 밖에 두고, 만일을 위해 `.gitignore` 에 `*firebase-adminsdk*.json` 추가
- 확인: `.\mvnw.cmd test` 190개 통과(새 테스트 5개, 가짜 키 사용). 확인 못 함: 진짜 키로 서버 켜기 · 실제 폰 발송
- 남음: 토큰 등록 API + 폰 앱이 로그인하면 토큰 보내기, 일정 저장 뒤 발송 예약
- 정리 문서 `docs/push-notification.md` 저장: 개념 설명 · 흐름 · 설정 · 남은 작업

## 2026-11-23 Firebase 푸시 알림(FCM) 받기 — 콘솔 테스트 발송용 (브랜치 `lyh-aibrief`)
- 라이브러리 추가: `@capacitor/push-notifications@^8.1.3` (서버 · DB 변경 없음, 기존 로컬 알림 그대로)
- `utils/pushNotify.js`: 앱 시작 때(`main.js`) 권한 확인 → FCM 등록 → 기기 토큰을 받아 알림 테스트 화면(`/app/test/notify`)에 표시 · 복사 버튼
  - 앱이 켜져 있을 때 받은 푸시는 로컬 알림으로 한 번 더 띄움 (안드로이드는 앱이 앞에 있으면 알림창에 안 띄우므로)
  - 알림을 누르면 맞춤 데이터 `path` 로 이동, 나머지 값은 query (예: `path=/app/disaster`, `demo=warning` → 호우경보 예시 재난지도)
- 안드로이드: `AndroidManifest.xml` 에 기본 알림 채널 `push-alert`(중요도 높음 · 잠금화면 내용 표시), `npx cap sync android` 로 플러그인 반영
- `client/android/app/google-services.json` 은 `android/.gitignore` 로 커밋 제외 → 팀원끼리 따로 전달
- 테스트 방법: 앱 › 알림 테스트 화면에서 토큰 복사 → Firebase 콘솔 › Messaging › 새 캠페인 › 알림 › 테스트 메시지 전송 → 토큰 붙여 넣기
- 정리 문서: `docs/push-notification.md` (구조 · 설정 · 시연 방법 · 서버 발송 계획)
- 확인: `npm run build` 통과, `cap sync` 플러그인 2개 인식. 확인 못 함: 실제 폰에서 토큰 받기 · 푸시 수신 · 알림 눌러 이동 (안드로이드 스튜디오로 다시 설치 필요)

## 2026-11-23 인제군 호우특보 예시 알림 → 재난지도 (시연용 더미, 브랜치 `lyh-aibrief`)
- 더미: `data/rainWarningDemo.js` — 호우주의보(3시간 65mm · 준비) · 호우경보(3시간 95mm · 대피). 기상청 기준(60 · 90mm)을 넘는 값, 실제 특보 아님. 알림 제목에 "[예시]"
- 알림 테스트 화면(`/app/test/notify`)에 버튼 2개 → 5초 뒤 잠금화면 알림(채널 `disaster-alert`, 브리핑과 따로). 잠금화면에는 제목 + 첫 줄, 펼치면 3줄(`largeBody`)
- 누르면 `/app/disaster?demo=advisory|warning` → 지도가 실황을 반영한 뒤 예시 비 양으로 미리 보기(단계 · 빗줄기), 특보 칸은 "호우경보 발효 (예시)" + 실제 특보 아님 안내. "현상황 보기"로 실황 복귀 (`disasterLive.showDemo`)
- 알림 누르기 처리 일반화: `extra: { path, query }` + 누른 시각 `t` (브리핑 알림도 같은 방식으로 바꿈, 내 캠핑 화면은 그대로 `?brief=`)
- 확인: `npm run build` 통과. 확인 못 함: 실제 폰 알림 · 3줄 펼침 · 재난지도 예시 화면

## 2026-11-23 기상특보 더미를 실제 현황으로 교체 (브랜치 `lyh-aibrief`)
- 실제 수신 확인: 로컬 서버 `GET /api/alerts` → `warningDummy: false`, 기상청 특보현황 11/23 16:00 기준 발효 중 7건 (강풍주의보 인천·강원·충남·전북·경북, 건조주의보 강원·경북)
- `alert/AlertDummyData.warnings()` 를 그 7건 그대로로 바꿈 (예전 예시: 강원 호우주의보 · 경기 한파주의보 · 제주 강풍 해제). 더미는 키가 없거나 기상청 호출 실패 때만 쓰이고 화면에 "예시"로 표시됨. 체크리스트 · 브리핑은 여전히 더미를 쓰지 않음(못 받으면 "받지 못했어요")
- `AlertServiceTest` 기대값: 강원 발효 중 더미 1건 → 2건. 확인: 서버 테스트 185개 통과

## 2026-11-23 내 캠핑: 캠핑 전날 브리핑(초인종) + 일정 저장 5초 뒤 앱 알림 (브랜치 `lyh-aibrief`)
- 캠핑 전날(D-1) 카드에만 삭제 옆 검정 원 초인종 버튼. 누르면 카드 안에 "내일 캠핑 브리핑" 3줄이 스르륵 열리고 다시 누르면 닫힘 (grid 행 0fr ↔ 1fr 전환)
- 브리핑 3줄 (`utils/campBrief.js`, RAG 붙이기 전이라 정해진 문장 틀): ① 특보·날씨 — 출발 준비 응답의 특보 + 출발일 예보(기상청 값 그대로, 없으면 "받지 못했어요") ② 진단 — 저장된 1분 진단의 가장 약한 분야 + 그 수칙 제목(진단 없으면 진단 권유) ③ 준비물 — 체크리스트 남은 개수 + 먼저 챙길 항목
- 체크 상태 localStorage 이름을 `checklistStoreKey` 하나로 모아 체크리스트 화면과 브리핑이 같이 씀
- 앱 알림 (`utils/localNotify.js`): `myCamping.add`(일정 등록 시트 · 예약 정보 확인 새 저장 둘 다) 뒤 5초 뒤 로컬 알림(채널 `camp-brief`, 잠금화면 표시, 알림 id = 일정 id). 알림을 누르면 `/app/my-camping?brief=일정id` → 그 카드가 전날 카드면 브리핑을 펼치고 스크롤. 브라우저에서는 아무 동작 안 함. 5초는 시연용(실제는 전날 저녁 등으로 바꿀 자리)
- (이어서) 알림을 누르고 들어오면 그 일정의 브리핑이 항상 열린 상태로: 전날이 아닌 일정도 알림으로 들어오면 초인종 · 브리핑을 보여 줌(제목 "캠핑 브리핑 (D-n)"), 같은 알림을 다시 눌러도 열리도록 주소에 누른 시각 `t` 를 붙임. 먼 일정은 특보·날씨 줄에 "○월 ○일부터 확인할 수 있어요"
- 확인: `npm run build` 통과 (`three` 가 설치 안 돼 있어 `npm install` 후 빌드 → package-lock.json 이 바뀜). 확인 못 함: 실제 안드로이드에서 알림·알림 눌러 이동·브리핑 애니메이션, 브라우저 화면 확인

## 2026-11-23 예약 정보 확인 = 저장한 일정 고치기 (브랜치 `hjs-metaverse-merge`)
- 내 예약 카드의 "예약 정보 확인 ›"(웹 `WebMyBookingView`, 앱 `MobileMyCampingView`) → `booking-check?camp=&plan=일정id` 로 엶
- 예약 확인 화면(웹·앱)이 `?plan=` 이면 저장해 둔 날짜 · 인원 · 동행 · 이동 수단을 채우고 제목은 "예약 정보 확인". 저장하면 새로 등록하지 않고 그 일정을 고침
- 인원 · 동행 · 이동 수단은 지금처럼 memo 글자 (팀 결정: DB 칸은 이 값을 쓰는 기능이 생길 때). 글자 만들기·읽기는 `client/src/utils/bookingMemo.js` 한곳 (최대 48자, memo 100자 안)
- 서버: `PUT /api/member/camp-plans/{id}` `{ checkIn, checkOut, memo }` 추가 (`CampPlanUpdateRequest`, `CampPlanService.update`, `CampPlanMapper.update`). 체크인을 바꿀 때만 지난 날짜·중복 검사. `server/CLAUDE.md` API 목록에 추가
- 확인: 서버 `mvnw test` 181개 통과(고치기 테스트 3개 추가), client `vite build` 통과, memo 만들기→읽기 왕복 확인
- 확인 못 함: 브라우저에서 실제로 고치기 (로그인 계정 없음 · 켜져 있는 서버가 고치기 전 코드라 재시작 필요). 직접 쓴 메모(예: "55")가 있던 일정은 저장하면 메모가 인원·동행·이동 수단 글자로 바뀜

## 2026-11-23 예약 사이트에서 돌아오면 "예약하셨나요?" 팝업 (브랜치 `hjs-metaverse-merge`)
- "예약 사이트 ↗"를 누르면 캠핑장(내 예약 카드면 일정 id까지)과 시각을 기억 (`stores/reservationReturn.js`, localStorage). 누르는 곳: 웹 캠핑장 상세 패널 `WebCampDetailPanel`, 앱 캠핑장 상세 `MobileCampDetailView`, 웹 내 예약 카드 `WebMyBookingView`
- 돌아오면(새 탭에서 돌아옴 = visibilitychange, 같은 탭 뒤로 = 화면 다시 열림) 팝업 `components/common/ReservationReturnPopup.vue` (App.vue 에 한 번). 웹은 `WebConfirmModal`, 앱은 tokens.css 색의 작은 팝업
  - 10초 안에 돌아오면 묻지 않음, 하루 지난 기록은 버림, 예약 확인 · 로그인 화면에서는 안 띄움
  - 예약했어요 → `booking-check?camp=(&plan=)`. 로그인 안 했으면 `login?next=그 주소` → 로그인 뒤 그 화면으로 (`WebLoginView`·`MobileLoginView` 에 next 추가, 우리 사이트 주소만 받음)
  - 아직 안 했어요 · 바깥 누르기 → 기록 지움
- 확인: client `vite build` 통과. 브라우저 웹(1400px)·앱(375px)에서 기록을 넣고 돌아온 것처럼 해 팝업이 뜸, 예약했어요 → 로그인 화면(next 에 `&plan=` 그대로), 3초 만에 돌아오면 안 뜸
- 팝업 설명 줄("예약했다면 ~ 저장해 둘게요.") 삭제 → 제목과 버튼만
- 예약 확인 화면(웹·앱) 인원 +/− 버튼이 안 눌리던 것 고침: 템플릿에서 `bump(adults, …)` 로 ref 를 넘기면 숫자로 풀려서 값이 안 바뀌었음 → `bump('adults', 1)` 처럼 칸 이름으로. 브라우저에서 웹·앱 둘 다 늘리기·줄이기·최소값(성인 1, 어린이 0) 확인
- 어린이(아이) 기준 나이 "※13세 이하" 표시 (팀 결정): 예약 확인 웹·앱 "어린이 ※13세 이하", 웹 누구와·언제 팝업 `WebTripModal` "아이 ※13세 이하", 앱 `MobileTripSheet` "만 13세 미만" → "※13세 이하". 1분 진단의 "어린이 7~12세 · 영유아 0~6세"(`webSurvey.js`)는 다른 질문이라 그대로
- `WebTripModal` 인원 +/− 도 같은 이유(ref 를 넘김)로 안 되던 것 고침 → `bump('adults', 1, 1)`. 브라우저에서 늘리기·줄이기 확인
- 웹 캠핑장 상세 "예약 사이트 ↗" 버튼 테두리: 연한 회색(`--web-line-input`) → 메인 초록 1.5px(`--web-green`). 흰 패널 위 흰 버튼이라 안 보였음. "예약 사이트 없음"은 연한 테두리 그대로. 앱(`.reserve`)은 이미 진한 테두리라 그대로
- DB 저장 확인 (사용자): 예약 확인 화면에서 저장 → `camp_plan` 에 들어감
- 확인 못 함: 실제 외부 예약 사이트를 열었다 돌아오기, 로그인한 상태에서 저장까지 (테스트할 때 8080 서버가 꺼져 있었음)

## 2026-11-23 재난지도 3D 로딩 빠르게 (브랜치 `hjs-metaverse-merge`)
- `client/src/utils/disasterMap.js` (웹·모바일 재난지도 같이 씀)
  - 로딩 화면: 최소 2.5초를 일부러 기다리던 것 없앰. 처음 보이는 타일이 들어오면 바로 걷고, 늦어도 3초 뒤에는 걷음 (예전엔 최대 8초)
  - `maximumScreenSpaceError` 2(Cesium 기본값) → 3: 받는 타일이 줄어드는 대신 아주 약간 흐려짐
  - `data.json`·`criteria.json` 을 차례로가 아니라 동시에 받음
- 작은 물길(OSM 이름 없는 개천)을 인북천과 다르게: 폭 10m → 4m, 비가 와도 넓히지 않음, 물결 없이 옅은 색 띠 (`streamLook`). 폭 10m(비 올 때 최대 약 21m)로 그려서 대피소(서화중앙교회, 개천 가운데선에서 약 14m)가 물 위에 있는 것처럼 보였음. 대피소 좌표는 그대로 (처음부터 바뀐 적 없음)
- 높낮이 그래프 설명을 짧은 문장으로 (`updateProfile`). 예: "큰 홍수가 나면 이 길 중 570m 정도가 물에 잠겨요. 가장 깊은 곳은 1.6m예요. 도착하는 곳은 물 높이와 거의 같아요."
- 막대 114mm 눈금 "캠핑장 잠김" → "침수" (웹에서 "90 경보"와 글자가 겹쳤음): `criteria.json`, `disasterMap.js` DEFAULT_CRIT, 주석(`MobileRainSlider.vue`, 서버 `DisasterRules`·`DisasterNowResponse`)
- 웹(`/web/disaster`)과 앱(`/app/disaster`)은 같은 `disasterMap.js`·`criteria.json` 을 써서 위 수정이 둘 다에 들어감. 옛 단독 앱(`disaster-map/app/`)은 단계·화면이 달라 손대지 않음
- 시험용 `DevTestNav`(보라색 '🧪 시험' 버튼) 삭제: `App.vue` 3줄, `components/DevTestNav.vue`
- 확인: `vite build` 통과. 브라우저 모바일 재난지도에서 대피소 '위치 보기' → 기둥 옆 파란 띠 없어짐. 콘솔 오류는 `/api/auth/me` 401(로그인 안 한 상태) 하나뿐
- 확인 못 함: 비 오는 단계(준비·대피)에서 작은 물길 모습, 브라우저에서 실제로 몇 초 빨라졌는지, 지도 화질 차이. 남은 일: `data.json`(5.5MB) 나눠 받기

## 2026-11-22 메타버스를 client 안으로 합침 (브랜치 `hjs-metaverse-merge`)
- 아래 "client 주소 아래로 연결"(서버 2개 + proxy, 브랜치 `hjs-metaverse-proxy`) 다음 단계. 개발 서버·빌드를 하나로 하려고 메타버스를 client의 화면 하나로 옮김. 느려지면 다시 생각하기로 함 (되돌릴 때는 `hjs-metaverse-proxy`)
- 옮긴 곳: 3D 코드 `client/src/metaverse/`, 화면 `client/src/views/metaverse/MetaverseView.vue`, 데이터 `client/public/metaverse/data/`
- 주소 `/metaverse` (`router/index.js`). 새 layout `full`(`App.vue`) = 웹·모바일 틀 없이 전체 화면. three.js는 이 화면에 들어올 때만 불러옴
- client에 `three` 추가 (`package.json`). client `vite.config.js`의 `/metaverse` proxy 삭제 (원래대로)
- 링크: `webLinks.js`의 `METAVERSE_URL` → `METAVERSE_PATH = '/metaverse'`. PC 3곳·모바일 내 정보 링크와 돌아가기 버튼을 RouterLink로 (새로고침 없이 이동)
- `metaverse/` 폴더: 따로 앱이던 뼈대(Vue 기본 템플릿, index.html, package.json, vite.config.js 등)는 삭제. `gis/`(QGIS 원본), `docs/`, `CLAUDE.md`는 남김. `gis/export_geojson.py`는 `client/public/metaverse/data/`로 내보내게 고침
- 비교(client `npm run build`): 첫 화면 JS 104.5KB → 104.8KB, 빌드 약 3.5초(같음), 메타버스 화면 덩어리 약 652KB(들어갈 때만 받음), 배포 폴더 26MB → 27MB
- 확인: `npm run build` 통과. 브라우저 PC·모바일(375px)에서 링크 → `/metaverse` 3D 캠핑장·지형 나옴, 돌아가기로 PC `/web`·모바일 `/app/explore`, 나갔다 다시 들어와도 3D 화면이 하나만 남음(정리 잘 됨), 재난지도(`/web/disaster`) 열림
- 확인 못 함: 실제 휴대폰·Capacitor 앱에서 열기, 퀴즈 DB(서버에 `/api/quizzes` 없음 → 예비 파일)
- 참고: `MetaverseView.vue`는 원래 hex 색을 직접 써서 client CSS 규칙(`tokens.css` 변수만)과 다름. 옮기기만 하고 색은 안 바꿈

## 2026-11-22 메타버스를 client 주소 아래로 연결 (브랜치 `hjs-metaverse-proxy`)
- 목표: client 개발 서버(5173) 하나로 `/` client · `/api` Spring(8080) · `/metaverse/` 메타버스(5174)를 모두 열기. 주소가 하나라서 로그인 쿠키가 공유되고, 나중에 앱(Capacitor)으로 감쌀 때 메타버스가 앱 안에서 그대로 열림
- 메타버스
  - `metaverse/vite.config.js`: `base: '/metaverse/'`, 포트 5174 고정(`strictPort`). 메타버스만 따로 띄워도 퀴즈를 Spring에서 받도록 `/api` → 8080 proxy 추가
  - `public/` 파일 경로 3곳을 `import.meta.env.BASE_URL` 기준으로 (`geo.js` geojson, `missions.js` 예비 파일, `scene.js` 지형). `/api/quizzes`는 그대로
  - `MetaverseView.vue`: 왼쪽 위 "← 캠핑 앱으로" 링크(`href="/"` → client가 화면 폭을 보고 `/web` 또는 `/app/explore`로 보냄). 좁은 화면에서는 시간·점수 줄과 안 겹치게 정보줄을 한 칸 내림
  - `metaverse/CLAUDE.md` 실행 주소, `metaverse/docs/작업기록.md` 결정 사항 갱신
- client
  - `client/vite.config.js`: proxy에 `/metaverse` → 5174 (`ws: true`, 코드 수정 자동 반영용)
  - `utils/webLinks.js`: `METAVERSE_URL` 기본값을 `http://localhost:5174/metaverse` → `/metaverse/metaverse`
  - PC 메타버스 링크 3곳(`WebMenuDrawer.vue`, `WebMyBookingView.vue`, `WebMyInfoView.vue`)에서 `target="_blank"` 삭제 (앱에서는 새 창이 안 열리거나 밖으로 나감). 내 정보 안내 문구 "새 창에서 열려요" → "3D 캠핑장에서 안전 퀴즈"
  - 모바일 내 정보(`MobileMyInfoView.vue`) 메뉴에 "메타버스 안전 탐험" 추가 (로그인 전·후 모두)
- 지킬 것: `http://localhost:5174` 같은 주소를 코드에 쓰지 않기 · 파일 경로는 `BASE_URL` 기준 · API는 `/api/...` · 새 창 쓰지 않기
- 실행(창 3개): server `.\mvnw.cmd spring-boot:run`(8080) · client `npm run dev`(5173) · metaverse `npm run dev`(5174) → http://localhost:5173/metaverse/metaverse
- 확인: client·metaverse `npm run build` 통과. 브라우저에서 PC·모바일(375px) 링크가 같은 탭에서 열리고 3D 캠핑장·지형이 나옴, 돌아가기 버튼으로 PC는 `/web`, 모바일은 `/app/explore`로 돌아옴
- 확인 못 함 / 남은 것
  - 퀴즈를 DB에서 가져오기: 요청은 client → Spring까지 가지만 Spring에 `/api/quizzes`가 없어 404 → 예비 파일 사용. 퀴즈는 Spring AI + RAG(안전 자료를 pgvector에 저장 → 퀴즈 후보 생성 → 사람이 검토·승인 → quiz 표)로 만들 예정이고, 게임은 승인된 문제만 `/api/quizzes`로 읽기로 함. 응답 모양은 `metaverse/public/data/missions.json`과 같게
  - 배포: 지금은 개발 서버 proxy로만 묶임. 배포할 때는 메타버스 빌드(`dist`, 경로가 `/metaverse/...`)를 client 배포 결과의 `metaverse/` 폴더에 넣거나 같은 주소로 묶는 설정이 따로 필요
  - Capacitor 앱 안에서 열기 (팀원 담당)

## 2026-11-21 재난지도 3단계 · 산 대피 · 단계별 행동요령 · 119 전화 (브랜치 `hjs-disaster-live`)
- 화면 단계는 **안전 · 준비 · 대피** 셋. 114mm(서화 50년 큰비)는 단계 이름을 따로 두지 않고 막대 눈금에 "캠핑장 잠김"으로만 표시 (`criteria.json`, `disasterMap.js` DEFAULT_CRIT). 내부 level 3 은 그대로 둬서 물은 캠핑장까지 차고, 할 일 문장만 더 급하게. 서버 `DisasterRules` 는 바꾸지 않음 (level 3 = 화면에선 "대피")
- 높은 곳 안내를 "남서쪽 산으로 올라가세요"로 (방향은 코드가 계산한 `hDir`). 이유: 도로만 따라 다리 없이 물보다 높은 곳(311m, 100년 범위 밖)까지 가면 1,520m · 약 30분이고 캠핑장(290m)보다 낮은 285m 강가와 범람 범위 안을 약 460m 지나감 → 쓸 수 없음 (계산: 데이터 폴더 `road_route.py`, 그림 `도로경로_비교.png`). 산길은 지형 계산 방향이고 실제 길은 확인 못 함 → 이름표·길안내에 "길 확인 전"
- 아래 시트 행동요령을 단계별 3줄로 (`LV[lv].todo`, 제목 "○○ 단계 행동요령"). 수치 없는 일반 행동만 씀
- 긴급 신고 119 를 `tel:119` 링크로 (휴대폰에서 누르면 전화 걸기 화면)
- 캠핑장 잠김(level 3, 114mm 이상)에서는 대피 경로·목적지 이름표·핀·대피소 칸·경로 고르기·길안내 버튼·높낮이 그래프를 숨김 (`noRoute`). 할 일 문장·행동요령·119 만 남김. 길안내 중에 이 단계가 되면 길안내를 끝냄
- 화면 위 위치 줄을 "인북천 물빛테마공원 캠핑장"만 남김 (모바일). 웹 `WebDisasterPanel.vue` 는 팀원 파일이라 그대로
- 안 쓰는 코드 정리: `state.hasBase`, `state.live.water`, 막대 눈금 `.ref`(기준표에 ref 단계 없음) 삭제. "시뮬레이션"·"즉시 대피"·"심각" 등 옛 이름 주석을 지금 이름(미리 보기 · 캠핑장 잠김)으로 (`disasterMap.js`, `MobileDisasterMapView.vue`, `MobileRainSlider.vue`, 서버 `DisasterRules`·`DisasterNowResponse` 주석, `tokens.css` 주석). 동작은 그대로. `stage.ai` 는 팀원 웹 화면이 써서 남김
- `osmBuildings`(OpenStreetMap 건물 157동) 삭제 (2026-11-22): 지도에서 한 번도 안 그리던 자료라 `client/public/disaster-map/data.json`·옛 단독 앱 `disaster-map/app/data.js` 에서 빼고(각 약 51KB 줄어듦), `disaster-map/qgis/export_data.py` 도 다시 내보낼 때 안 만들게 고침. 건물은 건물통합정보 803동만 (발표 11페이지도 803동). 확인: `npm run build` 통과, 화면 열림·콘솔 오류 없음
- 막대 눈금 글자가 길면(캠핑장 잠김) 왼쪽 맞춤해 90 경보와 안 겹치게 (`MobileRainSlider.vue`)
- 확인: `npm run build` 통과, 브라우저(375px)에서 120mm 때 문구 · 행동요령 · tel:119 · 눈금 확인. 팀원 웹 화면(`WebDisasterPanel.vue`)은 행동요령은 자동으로 따라오고 버튼 글자("높은 곳 (참고)")는 팀원 파일이라 그대로
- 확인 못 함: 실제 휴대폰에서 전화 연결, 서버 켠 실시간 화면

## 2026-11-21 재난지도 화면 단순하게 (브랜치 `hjs-disaster-live`)
- 비 막대(`RainSlider.vue`)
  - 글자를 "최근 3시간 비"에서 "3시간 강수량"으로 바꿈 (기상청 특보 기준과 같은 말이고, 실황이든 미리 보기든 맞는 표현)
  - "실시간"·"실시간 보기"·"시뮬레이션" 표시와 상류 반월촌교 수위 줄(파란 글씨)을 화면에서 뺌
  - PC 같은 넓은 화면에서도 막대 폭이 최대 440px를 넘지 않게 함
- 화면에서만 뺀 것: 수위는 서버 단계 판단에 그대로 씀. "실시간 보기" 버튼이 없어서 막대를 만지면 새로고침 전까지 미리 보기 상태로 남음
- 특보 칸(`DisasterMapView.vue`): 막대로 미리 보는 중이고 실제 특보가 없으면, 그 비에 맞는 특보를 "미리 보기 · 예시"로 보여 줌
  - 안전: 특보 없음
  - 준비(60mm~): 호우주의보
  - 대피(90mm~)와 즉시 대피(114mm~): 호우경보. 특보는 경보가 가장 높음
  - 큰비를 미리 보는데 초록 "특보 없음"이 함께 떠서 헷갈렸던 것을 고침. 실제 특보가 발효 중이면 미리 보기 중에도 그 특보를 보여 줌
  - 이를 위해 `disasterMap.js`의 `state.stage`에 `level`을 더함
- 안내 시트 제목(`disasterMap.js` `LV.head`)을 단계 이름 대신 할 일로 바꿈
  - 안전: 대피소 가는 길만 알아 두세요
  - 준비: 짐을 싸고 대피를 준비하세요
  - 대피: 다리 말고 높은 곳으로 가세요
  - 즉시 대피: 짐 두고 바로 높은 곳으로 가세요
- 높은 곳 길 카메라: 남남서에서 보면 산등성이에 가려져서, 동북동에서 서남서를 내려다보게 바꿈 (heading 250°, pitch -45°). 대피소 길 카메라는 그대로
- 확인: `npm run build` 통과. 휴대폰(390×844)과 PC(1600×900, 폭 제한을 푼 상태) 화면을 0·60·95·120mm에서 찍어 높은 곳 길이 보이는 것을 눈으로 확인함 (서버는 꺼진 상태라 실시간 값은 확인 못 함)

## 2026-11-20 재난지도 실시간 비·기상특보 연결 (브랜치 `hjs-disaster-live`)
- 서버 새 패키지 `disaster/`: `GET /api/disaster/now`
  - 비: 기상청 초단기실황(getUltraSrtNcst) RN1 을 캠핑장 격자로 시간마다 받아 최근 3·12·24시간 합. 지난 시간 값은 저장해 두고 다시 안 부름(처음만 최대 24번, 그다음 한 시간에 1번). 정시 관측은 40분 뒤부터 받음
  - 단계: `DisasterRules` — 준비 3h 60 또는 12h 110 · 대피 3h 90 또는 12h 180 · 즉시 대피 3h 114 또는 24h 310 (criteria.json·rag 문서와 같은 값)
  - 특보: `alert` 의 `WarningSource`(기상청 특보현황, 10분 캐시)를 그대로 써서 강원 중 "인제"가 들어간 구역 또는 강원 전체 특보만. **alert 패키지 파일은 안 고침**
  - 못 받으면 값을 비워 둠 (예시 값·더미 없음). 키: 기존 `KMA_SERVICE_KEY` 그대로
- 화면(`/disaster`): 처음에 실황을 받아 그 단계로 시작, 10분마다 다시 받음. 슬라이더 옆 "실시간"(관측 시각·12/24시간 까닭) ↔ 슬라이더를 만지면 "시뮬레이션", "실시간 보기"로 복귀. 기상특보 칩은 예시 데이터 삭제 → 인제군 실제 특보 / "발효 중인 특보 없음" / "특보 정보 없음"
- 확인: `mvnw test` 161개 통과(새 `DisasterServiceTest` 7개), `npm run build` 통과
- 확인 못 함: 실제 키로 기상청 호출(`/api/disaster/now` 응답), 화면에서 실시간 ↔ 시뮬레이션 전환. 강 수위(원통 관측소)는 아직 (한강홍수통제소 키 따로 필요)
- 높은 곳(2단계 이상) 경로 교체: 캠핑장에서 걸어서 가장 빨리 닿는 높은 곳으로 다시 계산 (10m DEM, 토블러 걷기 속도 최단 시간, 도착 = 지도 범람 범위의 가장 높은 물 높이 311.1m 보다 높고 100년 범람 범위 밖). 약 160m·캠핑장보다 약 20m 높음·계산상 걸어서 3~4분 (예전 188m·+40m 선 대체). 실제 길 아님, 현장 확인 필요. 화면 확인 못 함
- 범람 물 표현: 3D 물 면이 가장자리 높이로 판판해서 둑·둔치·캠핑장이 안 잠긴 것처럼 보이던 문제 → 즉시 대피 단계에서 50년 범람 범위 전체를 땅에 강물로 덮는 층 추가. 넘친 물 색도 강물과 같은 변수(`--color-river`)로. 빌드 통과, 화면 확인 못 함
- 강~캠핑장 사이가 안 잠기던 문제: 범람지도가 강 구역(강~둑 사이 낮은 땅, 284~290m)을 범위에서 빼서 생김 → `data.json` 에 `flood50fill` 추가 (강에서 시작해 가까운 범람 가장자리 물 높이보다 낮은 땅을 이어 채운 10m 칸, 가장자리 300m 안, 사각형 527개). 3D 물은 다 차오르면 강물과 같은 땅 덮개 층으로 바뀜(색 통일). 계산 스크립트는 아직 저장소 밖(임시 폴더)
- 대피(경보, 90mm) 단계부터 강 구역(`flood50fill`)만 물로 덮음 → 즉시 대피(114mm)에서 캠핑장 쪽 범람 범위까지. 경보 때 실제 어디까지 차는지 계산 자료는 없어서 "그림 표현"으로 둠 (수위 자료 붙으면 교체)
- 상류 수위 연결: 한강홍수통제소 Open API 반월촌교(1011620, 서화면 심적리, 캠핑장 약 4km 상류) — `HrfcoWaterClient`, 키 환경변수 `HRFCO_SERVICE_KEY`. 공식 기준수위 관심 6.7 · 경보 8.4 · 심각 9.4m(주의보 없음)를 관심→준비 · 경보→대피 · 심각→즉시 대피로 연결(팀 결정), 최종 단계 = 비·수위 중 높은 쪽. 1시간 넘게 새 자료 없으면 판단 제외. 화면 슬라이더 아래 "상류 반월촌교 수위 4.77m · 관심(6.7m)까지 1.93m" 줄. 로그에 키가 찍히지 않게 가림(기상청 비 클라이언트도). 테스트 165개 통과, 빌드 통과
- 확인함(사용자 터미널): 반월촌교 info·10분 자료 응답 (2026-11-20 14:30 수위 4.77m). 확인 못 함: 서버를 통한 /api/disaster/now 의 water 칸, 화면 표시. 유량(fw) 값은 이상해 보여 안 씀
- 서버에서 수위가 null 이던 원인: JDK 17 기본 신뢰 목록에 api.hrfco.go.kr 의 루트(OISTE WISeKey Global Root GB CA)가 없어 "PKIX path building failed". 공개 루트 인증서를 `server/src/main/resources/certs/` 에 두고 `HrfcoWaterClient` 에서만 추가로 믿게 함 (JDK 폴더·다른 호출은 그대로). Java 로 확인: 기본은 SSLHandshakeException, 루트 추가 시 HTTP 200
- 대피 출발점 변경: 고캠핑 원본 좌표(128.2061766, 38.2258503) → QGIS 위성 사진에서 찍은 **캠핑장 안쪽**(128.2060817, 38.2252365, 땅 290.1m, 50·80·100년 범람 범위 안). 거기서 캠핑장 안 길 150m(QGIS 에서 그림)로 "세 갈래 길 끝"(128.206146, 38.2239962)까지 간 뒤 기존 경로. `data.json` campsite 점·두 경로(대피소 1,516→1,411m, 언덕 924→819m), `HILL_PTS` 다시 계산(108m·+21m·약 3분, 도착 311.2m), 서버 `CAMP_LAT/LNG`(서버 다시 켜야 반영). 계산 스크립트는 데이터 폴더 `hill_fastest.py`·`flood_fill_riverzone.py`, 정리 기록은 데이터 폴더 `레이어_정리_기록.md`. 포스터 이미지는 아직 예전 출발점 기준

## 2026-11-20 웹 내 예약 · 지난 캠핑 후기 (클라이언트 ↔ 서버, 브랜치 `lyh-record`)
- DB: `db/schema_06.sql` — `camp_review`(일정 하나에 후기 하나, 별점 없음, 일정 · 회원 지우면 CASCADE) · `camp_review_photo`(파일 이름 · 순서) — **DB 에 직접 실행해야 함**
- 서버 `mycamping/`: `CampReview*` · `ReviewPhotoStore`(인터페이스) · `LocalReviewPhotoStore`(서버 폴더 `{CAMPIN_UPLOAD_DIR 또는 uploads}/reviews`, `.gitignore` 에 `uploads/`). API 3개(목록 · 작성/수정 multipart POST · 사진 파일) — 표는 `server/CLAUDE.md`
  - 규칙: 체크아웃 지난 일정만 · 글이나 사진 하나는 꼭 · 글 1000자 · 사진 9장 · 한 장 10MB · 이미지는 파일 앞부분으로 판별 · 파일 이름은 UUID
  - 일정 삭제 때 후기 사진 파일도 지움(`CampPlanService.delete`). 업로드 크기 초과는 `GlobalExceptionHandler` 에서 400. `application.properties` 에 multipart 크기 두 줄 추가
- 클라이언트: `components/web/WebReviewModal.vue`(새로 — 이전 브랜치 작업이 커밋 전에 사라져 다시 만듦) · `WebMyBookingView` 지난 캠핑 카드에 후기(사진 줄 → 글 3줄 더보기 → 작성/수정 날짜), 사진 크게 보기, 날짜 줄 "N박" 뺌. 저장 실패 문구는 팝업 안에 보이고 쓰던 내용은 그대로
- 확인: `mvnw test` 166개 통과 (새 `CampReviewServiceTest` 11개, `CampPlanServiceTest` 에 사진 파일 삭제 1개), `npm run build` 통과
- 확인 못 함: 실제 DB · 서버를 켜서 업로드 · 사진 보기 (schema_06 미실행), 브라우저 화면. 회원 탈퇴(CASCADE) 때는 사진 파일이 서버 폴더에 남음 (탈퇴 기능이 생기면 함께 지워야 함)

## 2026-11-19 재난 대피 3D 지도 → 최신 main 위로 합침 (브랜치 `hjs-disaster-map-v2`)
- `hjs-disaster-map` 를 main(b0b8272) 에서 새로 판 브랜치에 merge. 충돌 3곳(tokens.css 색 변수 · router 경로 · 이 파일)은 양쪽을 모두 남김. 차박·가족방 위치·메타버스 작업은 그대로
- 확인: `npm run build` 통과. 확인 못 함: 합친 뒤 `npm run dev` 로 화면 열어 보기

## 2026-11-19 재난 대피 3D 지도 고도화 + 대피 기준 교체 (브랜치 `hjs-disaster-map`)
- 대피 기준(`public/disaster-map/criteria.json`): 출처 없던 120·150mm 단계 삭제 → **60 준비(호우주의보) · 90 대피(호우경보) · 114 즉시 대피(서화 50년에 한 번 큰비, WAMIS 3시간)**. 114mm 부터 50년 홍수 범위에 물. 80·100년은 할 일이 같아 단계 없음. 12·24시간 조건은 설명에만 적음 (화면 슬라이더는 3시간)
- 지도(`utils/disasterMap.js`):
  - 홍수 물을 땅에 칠하던 방식 → 범위 가장자리 땅 높이로 만든 **파란 3D 물**이 아래에서 차오름
  - 물이 차면 대피로를 **물 위에** 그림 (가려져도 보이게)
  - **등고선**: 10m DEM 에서 직접 계산(10m·50m), 강 위·평지는 뺌, 3km 안에서만 보임
  - **해 낮추기 · 산 그림자 · 앰비언트 오클루전**으로 산의 굴곡이 보이게
  - 자전거길·보행로·강을 가로지르는 선 숨김 (강 위 하얀 줄), 캐릭터 작게
  - **나침반 삭제**: 오른쪽 끌기·Ctrl+끌기·두 손가락으로 360도 돌리기, ← → 키
- 새 부품 `components/disaster/RouteProfile.vue`: **대피 길의 높낮이 그래프**(실제 땅 단면 + 홍수 물 높이 + 다리). 안내 시트 안. `MapCompass.vue` 삭제
- 색: `tokens.css` 에 재난지도 색 변수 추가(`--color-dm-*`, `--color-stage-0~3`, 물·강·건물·대피로·등고선, 사용자 승인). 재난지도 화면·부품·엔진의 hex 를 모두 변수로 바꿈 (엔진은 `getComputedStyle` 로 읽어 Cesium 에 넘김)
- 문구: "3시간 강수량" → "최근 3시간 비", 단계 이름 준비·대피·즉시 대피 (RAG 단계 문서의 "판단"과 같게)
- RAG 문서(`disaster-map/rag`): 02·03 에 12시간 조건 추가, 04 를 50년 단일 기준으로, README 표 갱신
- 확인: `npm run build` 통과. `npm run dev` 로 `/disaster` 열어 60mm(준비 · 대피소 길 · 높이 그래프) → 120mm(즉시 대피 · 다리 길 막힘 · 파란 물 · 높은 곳 길 291→330m) 확인
- 확인 못 함: 휴대폰에서 속도(산 그림자·오클루전이 무거움 — 느리면 `disasterMap.js` "해와 그림자"에서 끔), 오른쪽 끌기·두 손가락 돌리기를 실제 기기로

## 2026-11-17 재난 대피 3D 지도 올림 (브랜치 `hjs-disaster-map`)
- 새 폴더 `disaster-map/`: 인북천 물빛테마공원 캠핑장 재난 대피 3D 지도(CesiumJS) 웹앱을 그대로 올림 + QGIS 스크립트(`flood_depth.py`, `export_data.py`)
- 기능: 강수량 슬라이더 5단계 침수 표현, 단계별 대피 경로 2종, 4단계 길안내, 캐릭터 경로 따라가기, 오프라인(PWA)
- `client/`·`server/`·`db/` 는 바꾸지 않음
- 확인: 로컬 서버(`python -m http.server`)로 열어 동작 확인 (어제까지 확인한 상태 그대로)
- 다음: `client/` 에 Vue 컴포넌트로 옮기기 (cesium npm 패키지 추가 필요 → 라이브러리 추가라 팀 승인 후)

## 2026-11-17 재난 대피 3D 지도를 client Vue 화면으로 옮김 (브랜치 `hjs-disaster-map`)
- 새 화면 `/disaster` (`views/disaster/DisasterMapView.vue`). 라우터에 한 줄 추가, 다른 화면에서 들어가는 버튼은 아직 없음
- 부품 `components/disaster/`: `AlertPill`(기상특보, 예시 데이터) · `RainSlider`(강수량 → 침수 단계) · `MapCompass`(돌리기·북쪽) · `EvacSheet`(대피 안내·길안내)
- 지도 엔진 `utils/disasterMap.js`: `disaster-map/app/index.html` 의 Cesium 코드를 옮김. 화면은 reactive state 를 읽고, 버튼은 엔진 함수를 부름. 화면을 떠나면 `destroy()` 로 정리
- 데이터 `public/disaster-map/`: `data.json`(QGIS 내보내기, 5.8MB) · `criteria.json` · 캐릭터 그림
- **라이브러리 추가 (팀 확인 필요)**: `cesium` 1.123.0 (`/disaster` 화면에 들어갈 때만 불러옴, 빌드 크기 약 3.5MB). Cesium 작업 파일은 `scripts/copy-cesium.mjs` 가 `npm run dev/build` 전에 `public/cesium` 으로 복사 (git 제외)
- `package.json` `overrides`: `@zip.js/zip.js` 를 2.7.x 로 고정 — 최신(2.25)은 Cesium 1.123 이 쓰는 파일이 없어 빌드가 깨짐
- 색: 야간용 어두운 색을 `DisasterMapView.vue` 안 CSS 변수(`--dm-*`)로 둠. tokens.css 에 아직 없는 색이라 팀에서 정하면 옮김
- 서버·DB: 바꾼 것 없음. 기상특보는 예시 데이터 그대로(서버 특보 API 연결은 다음)
- 확인: `npm run build` 통과, `npm run dev` 로 `/disaster` 열어 지도·강수량 단계(60→130mm 높은 곳 경로 전환)·길안내 1→2단계·화면 떠날 때 정리 확인
- 확인 못 함: 휴대폰 실기기, 서버(8080)를 켠 상태에서 다른 화면과 함께 동작

## 2026-11-18 가족 모임방 위치 탭 서버+DB(family_location) 연결 (브랜치 `lyh-location`)
- 더미 배열 `client/src/data/familyLocationDummy.js` 삭제. 위치 탭은 `GET /api/family/locations` 결과만 찍는다
- 동의 = DB 에 내 줄이 있음. localStorage 동의 저장은 없앰 (다른 기기에서도 같은 상태)
  - 동의하기·스위치 켜기 → 브라우저 GPS 를 받아 `PUT /api/family/locations/me` (UPSERT) → 목록 다시 불러와 지도에 찍음. GPS 를 못 받으면 저장하지 않고 문구만 보임 (가짜 좌표 안 넣음)
  - 스위치 끄기 → `DELETE /api/family/locations/me` (내 줄 삭제 → 가족에게 안 보임)
  - (같은 날 변경) 위쪽 "가족 위치 확인" 문구·위치 공유 스위치 삭제. 안내창은 "위치 공유에 동의하시겠습니까?" + [동의 안 함] [동의합니다]. "언제든 끌 수 있어요" 문구도 뺌 → **화면에서 공유를 끄는 방법이 없음** (방 나가기로만 삭제, API `DELETE /api/family/locations/me` 는 남겨 둠)
  - 새로고침 → 내 GPS 다시 저장(못 받으면 건너뜀) + 목록 다시 불러오기
  - (2026-11-19 변경) **브라우저 GPS 저장은 일단 뺌.** 위치 탭은 `family_location` 에 SQL 로 넣은 좌표만 찍는다. [동의합니다]는 서버에 저장하지 않고 이번 화면에서만 지도를 보여 줌 (내 줄이 있으면 묻지 않음). 새로고침 = DB 다시 읽기. 서버 PUT API·store `saveMyLocation` 은 남겨 둠
  - 방 나가기(`DELETE /api/family/room/me`) 때도 내 위치 삭제 (11-06 합의 "끄거나 나가면 삭제")
- 백엔드: `LocationRequest` 추가, `LocationMapper.upsert·delete`, `LocationService.saveMine·deleteMine`, `FamilyMapper.deleteLocation`. DB 구조는 그대로 (`schema_05.sql` 주석만 갱신)
— 화면이 열려 있는 동안 자동 갱신은 아직 없음
## 2026-11-23 메타버스: 퀴즈 소품 (브랜치 `myj-metaverse`, DB 변경 없음)
- 퀴즈 자리에 문제 상황을 보여 주는 3D 소품이 나옴 (모닥불, 버너, 난로, 텐트 화재와 소화기, 벌집 등 28가지). 새 파일 `metaverse/src/metaverse/props.js`
- `missions.json` 문제에 `prop`(소품 이름) 칸 추가 — 40문제 중 37문제. 주차장·도로·분수 3문제는 소품 없음
- 데크 위 문제는 텐트·테이블 옆 빈자리에 데크 방향대로 놓임. 밤에는 불·랜턴이 주변을 비춤
- 확인용: 주소 뒤 `?quiz=문제id` 를 붙이면 그 문제만 뜸
- 발표 시연 순서: 아이를 고르면 처음 두 문제 고정("꼬치에 손을 데었어요!" → "주차장에서 차가 뒤로 와요"), 두 번째를 풀면 바로 침수 대피 시작. `metaverse/src/metaverse/scene.js` 의 `DEMO_QUIZ` 로 조절. 개발 서버에서 순서·비 시작 확인함 (대피 뒤 무작위 문제로 돌아가는 것은 확인 못 함)
- 미니게임 "마시멜로 굽기": 꼬치 퀴즈(`c-d-burn`) 고리에 닿으면 1인칭으로 마시멜로를 뒤집으며 굽고 10초 뒤 퀴즈가 뜸. 새 파일 `metaverse/src/metaverse/minigame.js`, `missions.json` 에 `"minigame": "marshmallow"`. 개발 서버에서 낮 화면으로 뒤집기·색 변화·퀴즈로 넘어가기 확인함. 확인 못 함: 밤 화면, 휴대폰(터치)
- 터치 조작(휴대폰 시연용): 왼쪽 아래 조이스틱, 오른쪽 아래 달리기(켜고 끄기)·점프 버튼. 터치 기기에서만 보임. 마시멜로 굽기는 화면 터치로 뒤집힘. 개발 서버의 휴대폰 크기 화면(가로 740×360)에서 이동·달리기·점프·뒤집기 확인함. 확인 못 함: 실제 휴대폰, 세로 화면, 두 손가락 동시 조작
- 낮·밤 직접 바꾸기: 위쪽 정보줄의 ☀️ 낮 / 🌙 밤 을 버튼으로 바꿈. 누르면 바로 전환(퀴즈·침수 대피 중에는 못 누름). 2분 자동 전환은 없앰(버튼으로만 바뀜). 개발 서버에서 낮→밤→낮 전환 확인함
- 결정: AI 가 진단 결과에 맞춰 문제를 만드는 것, 부족한 영역 집중 출제는 하지 않음 (40문제 고정)
- 확인: 개발 서버에서 소품 28가지 모양, 데크 위 자리 4종류(빈 모서리·텐트 옆·테이블 위·텐트 자리), 밤 불빛, 콘솔 오류 없음
- 확인 못 함: `npm run build`, 실제로 걸어가서 퀴즈를 여는 흐름, 휴대폰 속도. 소품은 통과됨(충돌 없음)
- 그 밖: 팀 DB 에 `briefing` 테이블만 만듦 (`db/schema_07.sql` 의 5번 부분). 데크 자리 테이블(`camp_site`·`camp_feature`·`camp_plan.site_id`)은 만들었다가 지움 — 데크 위치는 일단 보류

## 2026-11-22 AI 브리핑(RAG)용 안전 자료 조각 만들기 (저장소 밖 `C:\mylx\rag\조각\`, DB 변경 없음)

- **한 일**: `C:\mylx\rag`의 자료 9종에서 글자 추출 → 다듬기 → 조각 120개 → 꼬리표. 결과: `chunks.jsonl`(DB용 정본), `chunks.csv`(엑셀 검수용), `조각_검수용.pdf`(23쪽), `읽어보기.md`
- **조각 수**: 교육지도서 58 · 소방청 보도자료 17 · 교안(2025) 12 · 소비자원 9 · 행정안전부 보도자료 10 · 성인기 교재 7 · 웹툰 7. 종류: 수칙 61 · 응급처치 25 · 통계 22 · 사례 12
- **꼬리표**: `topic`(주제 19종) · `kind` · `companion`(all/child/pet) · `sleep_type`(all/tent/car) · `season`. `db/schema_06.sql`의 `safety_doc_chunk`에는 아직 이 칸들이 없음 → DB에 넣기 전에 테이블 칸을 맞춰야 함
- **확인 못 함**: 조각 본문을 원문과 한 줄씩 대조하는 검수(사용자가 하기로 함) · PDF 줄바꿈을 이은 자리의 띄어쓰기 · 꼬리표 판단
- **pgvector 연습(저장소 밖 `C:\mylx\rag\pgvector_연습\`)**: 도커 컨테이너 `campin-pgvector`(pgvector/pgvector:pg17, PC 5433 포트, DB `campin_practice`)에 조각 120개를 **가짜 임베딩**(1536 크기)으로 넣고, 꼬리표로 거른 뒤 벡터 거리로 순서를 매기는 검색까지 확인. 팀 DB와 무관한 연습 환경. LLM·임베딩은 OpenAI(`text-embedding-3-small`, 1536)로 쓰기로 함(사용자 결정)
- **최신 main 실행 확인**: `git fetch` 로 받아 보니 `origin/main` 이 내 브랜치보다 27커밋 앞섬. 내 작업 폴더(커밋 안 한 메타버스 작업)는 건드리지 않고, 최신 main 을 `C:\mylx\campin-main` 에 워크트리로 따로 꺼내 서버(8080)·화면(5173)을 실행함 → `/app`, `/web` 화면이 뜨고 캠핑장 목록 API 가 200 응답. 로그인 뒤 기능은 확인 못 함. **main 에 이미 `db/schema_06.sql`(캠핑 후기)이 있어서, 내가 만든 `db/schema_06.sql`(브리핑용)은 번호를 바꿔야 함**
- **실제용 벡터 DB 파일(`db/vector/`, 새 폴더)**: 조각 테이블만 도커 pgvector 에 따로 두고 서버가 두 DB 에 접속하는 방식으로 정함(사용자 결정). `docker-compose.yml`(컨테이너 `campin-vector`, `127.0.0.1:5433`, DB `campin_vector`, 볼륨에 데이터 보관, 비밀번호는 `.env`) · `01_table.sql` · `02_data.sql`(조각 120개 + OpenAI `text-embedding-3-small` 임베딩 1536) · `chunks.jsonl`(조각 원본) · `make_data.py`(조각을 고친 뒤 임베딩 다시 만들기) · `README.md`. 임시 컨테이너에 올려 테이블 생성 · 120줄 적재 · 벡터 검색이 되는 것을 확인. **`.env` 를 만들고 `docker compose up -d` 를 해야 실제로 뜸(아직 안 함)**
- **벡터 DB 실제 가동 + 검수 완료**: 사용자가 `.env` 를 만들고 `docker compose up -d` 로 `campin-vector` 를 띄움 → 조각 120 · 벡터 120 확인. 사용자가 조각 120개를 원문과 대조해 검수 완료(고친 곳 없음) → `chunks.jsonl` · `02_data.sql` · 떠 있는 DB 의 `is_reviewed` 를 모두 true 로 반영
- **`db/schema_06.sql` → `db/schema_07.sql`**: main 의 `schema_06.sql`(캠핑 후기)과 번호가 겹쳐 07 로 옮김. 조각 테이블(`safety_doc_chunk`)은 벡터 DB 로 가서 뺐고, `briefing` 에 쓴 조각 번호를 적는 `chunk_ids text[]` 칸을 더함. 아직 어느 DB 에도 실행 안 함
- **자료가 부족한 영역**: 더위(3개) · 강풍(7개) · 차박(2개)은 조각이 적고, 어르신 동행 · 글램핑에 맞는 조각은 없음

## 2026-11-21 AI 브리핑 기능 설계 · 데크 한 칸씩 데이터 초안 (브랜치 `myj-metaverse`, DB 변경 필요: `db/schema_06.sql` — 아직 어느 DB 에도 실행 안 함)

- **기능 방향(팀 결정)**: 진단 유형 + 데크 위치 + 당일 날씨 + 체크리스트를 모아, 안전수칙 자료를 참고해 LLM이 대비 행동을 브리핑 (재난지도는 넣지 않음)
- **범위(팀 결정)**: 시연은 인북천 물빛테마공원 캠핑장 한 곳만 · 브리핑은 캠핑 출발 전에만 · 데크 번호는 내 캠핑 일정 등록 때 고름 · 메타버스와는 별개(연결은 나중에 검토)
- **퀴즈 출제 방식 변경(팀 결정, 구현 전)**: 유형 진단에서 부족한 부분의 퀴즈를 집중 출제 · 문제를 미리 정해 두지 않고 AI가 안전교육 자료(국민안전교육플랫폼·소방청·행정안전부·한국소비자원 자료)를 바탕으로 계속 만듦. 발표 PPT에 "맞춤 퀴즈" 장으로 넣음(개발 예정 표시)
- **흐름 안**: 단기예보 주기 스케줄러 → 규칙으로 위험 판단(코드) → 바뀌었을 때만 LLM → `briefing` 저장·알림. 기존 `weather/`, `alert/`, `mycamping/`을 재료로 씀
- **테이블(`db/schema_06.sql`, 새 파일)**: `camp_site`(데크 한 칸, 도형 EPSG:5179) · `camp_feature`(개울·강·출구) · 보기 `camp_site_distance`(데크별 거리, 조회 때 계산) · `safety_doc_chunk` · `briefing` · `camp_plan.site_id`. 테이블만 만들고 데이터는 없음. 지우거나 고치는 것 없음. **실행 전 PostGIS 확장 필요. 문법은 DB 에서 돌려 보지 못함(이 PC 에 psql 없음)**
- **데크 초안**: `metaverse/gis/sites_draft.geojson` 35칸(데크 22 · 카라반 13, 주차칸은 뺌). 번호는 현장 배치도와 맞춤(D-1은 따로 떨어진 데크). 메타버스의 배치 계산으로 만든 것이라 QGIS에서 위치를 다듬어야 함
- **확인한 것**: 초안 35칸이 경계 안에 있고 서로 겹치지 않음(계산). **확인 못 함**: 실제 데크 위치와 맞는지, QGIS `sites` 레이어로 내보낸 뒤 메타버스 화면
- **팀 확인 필요**: 진단 문항이 `survey_answer`(예전 5문항)와 다름 · 좌표계 · 위험 판단 기준 숫자의 출처 · Spring AI 추가

## 2026-11-20 메타버스: 돌발 미션 "비와 침수 대피" · 퀴즈 위치를 QGIS 점으로 (브랜치 `myj-metaverse`, DB 변경 없음)
- **침수 대피 미션**(`metaverse/src/metaverse/flood.js`): 탐험 중 무작위로 비가 오면 강물이 차오른 뒤 캠핑장이 강 쪽부터 잠김. 잠기기 전에 출구 고리에 닿으면 성공, 잠기면 "다시 대피하기". 주소 뒤 `?rain=5`로 시작 5초 뒤에 비가 오게 할 수 있음(발표·확인용)
- 출구는 QGIS `quiz_points`의 place `exit` 점 또는 `facilities`의 type `exit` 점. 없으면 강에서 가장 먼 곳을 임시 출구로 씀. 출구가 강 가까이에 있어도 대피할 수 있게 출구 둘레 40m는 가장 늦게 잠김. 선택 레이어 `flood_zones`(폴리곤: 홍수위험지도 100년 빈도에서 가장 깊게 잠기는 구역)가 있으면 그 구역 가운데부터 차서 바깥으로 넓어지고, 없으면 강에서 가까운 곳부터. 따라 그릴 바탕 그림은 `metaverse/gis/floodmap_100yr.tif`
- **차오르는 시간·속도는 게임용 값**(실제 홍수 속도 아님). 캠핑장 안 높낮이는 공개 자료(90m DEM)로 알 수 없어서 넣지 않음
- 퀴즈 위치: QGIS 점 레이어 `quiz_points`(`place`, `label`)가 있으면 문제의 `place`와 같은 점에 뜸. 없으면 임시 위치
- QR 동시접속은 하지 않기로 함
- 메타버스 설명 PPT를 지금 내용으로 다시 만듦: `metaverse/docs/ppt/CAMPIN_메타버스_페이지_1120.pptx` (5장. 6장은 뺌). Pretendard 글꼴이 깔린 PC에서의 모양은 **확인 못 함**
- **침수 미션에서 물 들어오는 곳 변경**: 홍수위험지도 구역(flood_zones)은 쓰지 않음. 강물은 캠핑장 테두리의 북쪽·동쪽 변 전체에서 동시에 넘쳐 들어오고, 반대편 개울은 8m까지만 번지고 멈춤. 들어오는 곳은 게임 설정(실제 홍수 자료로 구한 것 아님). 위 두 줄의 `flood_zones` 설명은 이제 해당 없음. 설명 PPT 5장 그림도 이 방식에 맞게 바꿈
- 안 쓰는 코드 정리: Vue 처음 틀의 안 쓰는 파일 4개 삭제(`HelloWorld.vue`, `assets/base.css`, `main.css`, `logo.svg`). 첫 화면(`/`)과 `/about`의 처음 틀 페이지는 그대로 둠
- **땅 클릭 이동을 없앰**: 이동은 키보드(WASD·방향키)로만. 휴대폰에서는 지금 움직일 방법이 없음
- **점수 제도를 없앰**: 화면에는 푼 문제 수만 보임 (대피 성공 +50점도 없음)
- 확인: `npm run build` 통과, 브라우저에서 비 시작 · 물 차오르기 · 잠김 판정 · 다시 대피하기 · 성공 창. 출구 도착 판정은 따로 떼어 시험
- **확인 못 함**: 캐릭터를 직접 조종해 출구까지 달려가 성공하는 것(자동 조종이 나무에 걸려서 못 함), 밤 화면에서의 비, 휴대폰 화면
- 아직 없음(다음): QGIS에 출구 점과 퀴즈 점 찍기

## 2026-11-19 메타버스: 아이·어른 선택 · 문제 하나씩 · 낮밤 자동 전환 (브랜치 `myj-metaverse`, DB 변경 없음)
- 시작 화면의 낮·밤 선택을 **아이·어른 선택**으로 바꿈. 고른 쪽 문제만 나옴
- 퀴즈 **40문제**(아이·어른 × 낮·밤 각 10문제)를 `metaverse/public/data/missions.json`에 넣음. DB는 쓰지 않음. 문제 근거(소방청 · 한국소비자원 · 행정안전부 통계)와 목록은 `metaverse/docs/ppt/CAMPIN_퀴즈근거.pptx`
- 맵에 문제가 **하나씩 무작위**로 뜨고, 풀면 다음 문제가 다른 곳에 뜸. 낮·밤은 **2분마다 자동 전환**, 낮에는 낮 문제만 밤에는 밤 문제만
- 5분 제한 시간을 없애고 **나가기** 버튼으로 끝냄
- 확인: `npm run build` 통과, 브라우저에서 아이·어른 선택 · 문제 넘어가기 · 2분 뒤 밤 전환 · 나가기 · 처음으로
- **확인 못 함**: 캐릭터가 직접 걸어가 고리에 들어가서 퀴즈가 열리는 것(이 부분 코드는 바꾸지 않음), 휴대폰 화면
- 아직 없음(다음): 문제 위치는 임시(데크 번호 · 시설 · 개울). QGIS 점 레이어 `quiz_points`를 찍으면 문제의 `place` 값과 같은 점에 뜨게 바꿀 예정. 자동차 문제 2개는 통계 근거를 아직 못 찾음

## 2026-11-18 출발 준비 체크리스트 2단계: 캠핑 기간 날씨 예보 (기상청 단기예보, DB 변경 없음)
- 출발 3일 전부터(캠핑 중 포함) 체크리스트 화면에 **"캠핑 기간 날씨"** 카드: 캠핑 날짜(오늘 이후, 최대 7일)마다 하늘 · 최저/최고기온 · 강수확률 · 풍속. 기상청이 주지 않는 날은 "예보 없음". 못 받으면 "날씨 예보를 지금 받지 못했어요" (예시 값 없음)
- 새 패키지 `server/.../weather/`: `KmaGrid`(위·경도 → 기상청 5km 격자, 기상청 공개 공식) · `KmaForecastClient`(getVilageFcst, 격자별 3시간 캐시, 1분 실패 대기, 환경변수 `KMA_SERVICE_KEY` 특보와 같은 키) · `ForecastParser`(시간별 → 하루치) · `ForecastSource`(테스트용 인터페이스) · `DayForecast`
- API: 체크리스트 응답에 `forecast: { available, days: [{ date, minTemp, maxTemp, pop, maxWind, sky, precip }] }` 추가 (near 일 때만, 아니면 null)
- 침낭 항목(계절·한파)에는 "예보 최저 ○°와 침낭 쾌적온도를 비교하세요"를 덧붙임 (침낭을 우리가 고르지 않음)
- **"날씨 대응" 묶음**(특보 바로 뒤): 출처가 있는 기준만 씀 — ① 예보 최저 **-12° 이하** = 기상청 한파주의보 발표 기준(특보는 2일 이상 등 조건이 더 있어 "해당하는 예보가 있다"까지만 말함) → 핫팩·일산화탄소 경보기·침낭 비교, ② 예보 최저 **0° 이하** = 물이 어는 온도(물리적 기준, 기관 기준 아님) → 동파 주의, ③ **비·눈 예보 유무**(수치 없음) → 우비·배수. 특보 항목과 문구가 같으면 한 번만
- **같은 준비물 겹침 정리**(`ChecklistItems.dedupe`): 묶음 순서(특보 → 날씨 → 차박 → 계절 → 공통)에서 앞의 것만 남김. 문구가 같은 것(핫팩·일산화탄소·동파·그늘막·화기)과 문구가 다른 같은 것(침낭 / 내 침낭의 쾌적온도 확인, 마스크, 우비)을 합치고, 호우 특보가 있으면 비 예보 항목은 뺌. 서버 테스트 31개 통과
- **일부러 안 넣은 것(근거 없음)**: 풍속 기준(캠핑용 공식 기준 없음) · 폭염(기상청 기준이 체감온도라 단기예보 기온으로 판정 불가, 특보에 맡김) · "10° 이하" 같은 우리만의 구간. 새 수치는 출처 확인 후에만
- 온도 기준 출처는 Notion 페이지에 정리
- 확인: 서버 테스트 통과(예보 칸 · 범위 밖 날 · 7일 제한 · 먼 일정 호출 없음 · 못 받음 · 침낭 문구 · 응답 변환 · 격자 변환 서울(60,127)·부산(98,76)), `npm run build` 통과
- **확인 못 함**: 기상청 단기예보를 실제로 호출해 본 것(키가 환경변수에 없어 못 함 — 응답 항목명·발표 시각·한 번에 받는 건수는 공개된 사용법 기준), 브라우저 화면
- 아직 없음(다음): **4~7일 앞 중기예보**(지역 코드 표를 기상청 공식 문서로 확인 필요) · 예비특보(`t7`) 표시 · "한파주의보 기준에 가까워요" 문구(기상청 특보 기준표 확인 후)

## 2026-11-18 내 캠핑 "출발 준비" 체크리스트 1단계 (DB 변경 없음)
- 내 캠핑 카드 아래 **출발 준비 ›** 줄 → 체크리스트 화면 `/my-camping/{planId}/checklist` (`views/mycamping/ChecklistView.vue`). 지난 캠핑은 줄 없음
- 화면: 이 곳의 안전 정보(안전점수 · 응급실 분, 차박 자리는 "약") · 특보 배너 · 묶음(특보 대응 / 차박 준비 / 계절 준비물 / 공통 준비물, 제목을 누르면 접고 펼침, 처음엔 공통만 접힘) · 체크는 브라우저(localStorage, 사용자·일정별)에만 저장
- **출발 3일 전부터(캠핑 중 포함)** 그 지역에 지금 발효 중인 기상특보를 확인해 항목에 반영. 먼 일정은 "특보는 ○월 ○일부터 보여요"(기상청 호출 없음)
- 서버 `GET /api/member/camp-plans/{planId}/checklist` (`mycamping/Checklist*`). 특보를 못 받거나 예시(더미)뿐이면 `warningsAvailable:false` 로 비워 둠 — 예시를 진짜처럼 쓰지 않음
- **수치 기준 없음**: 특보는 기상청이 발표한 이름(폭염·한파·강풍·호우·대설·건조·태풍·황사)에 연결만, 침낭은 "내 침낭의 쾌적온도 확인". 항목 문구는 `ChecklistItems` 한 곳(초안, 팀 검토 필요). 기준 출처: 기상청 기상특보 발표기준 (weather.go.kr)
- 서버 테스트 10개 추가 (3일 규칙 · 먼 일정은 기상청 호출 없음 · 특보→항목 · 특보 못 받음 · 차박 자리 · 남의 일정 404 등), 전체 통과
- 2단계(예정): 기상청 단기예보 값 표시 — 활용 신청 · 좌표→격자 변환 · 격자별 캐시 필요. 지금은 예보 값 없음
- 확인 못 함: 브라우저에서 실제 화면 (서버를 다시 켜야 새 API 가 생김). 기상청 특보를 실제로 받는 경우의 화면(키가 없으면 "특보 정보를 받지 못했어요"가 보임)

## 2026-11-18 내 캠핑에 차박 자리도 등록 (DB 변경 필요: `db/schema_05.sql`)
- 차박 판정 카드의 "내 캠핑에 등록하기"가 판정 결과가 나온 **모든 자리**에서 보임 (등록 야영장 · 차박 후보지 · 지도에서 찍은 자리, 불가·주의 자리도). 이름은 "475-3 잡종지"
- DB: `camp_plan.camp_id` NULL 허용 + `lat`·`lng` 추가 + CHECK(캠핑장 번호 또는 좌표) + 같은 자리·체크인 중복 방지 인덱스 → `db/schema_05.sql` (**실행해야 동작**, 지우거나 덮어쓰는 것 없음)
- 서버 `mycamping/`: 요청 `campId` 선택 · `lat`·`lng` 추가, 자리 중복 검사(`existsSpot`), 응답에 `lat`·`lng`. 테스트 3개 추가 (서버 테스트 통과)
- 화면: `PlanSheet` 에 lat·lng, 내 캠핑 카드에 "차박지" 표시 + 이름 누르면 `/chabak?lat=&lng=` 로 그 자리 판정 (`ChabakMap` 이 `ready` 를 알림)
- 확인 못 함: 실제 DB 에 schema_05 실행 · 브라우저 화면 (빌드·서버 테스트만 통과)

## 2026-11-17 시설 상세 지도: 점선 직선 → 카카오 도로 경로
- `FacilityDetailView`: 캠핑장에서 들어오면(`?camp=`) `GET /api/camps/{campId}/routes`(캠핑장 상세와 같은 API, 서버 24시간 저장)를 불러 이 시설의 `path` 로 도로 선을 그림. 못 받으면 예전처럼 점선 직선 + "실제 경로를 불러오지 못해 직선(점선)으로 보여 드려요"
- 시간·거리 카드도 경로를 받았으면 카카오 값, 아니면 DB 값 (캠핑장 상세 경로 지도와 같은 규칙). 지도 아래 안내 한 줄 추가
- 서버·`RouteMap` 은 바꾸지 않음 (`dotted` 를 경로 유무에 따라 켜고 끔)
- 확인: `npm run build` 통과. 확인 못 함: 브라우저에서 카카오 키로 실제 선이 그려지는지

## 2026-11-17 안전점수 다시 계산: 카카오 소요시간 + ROC 배점, 100점 만점 (브랜치 `lyh-scorefix2`)
- 직선거리 기준·`score_weights` 를 계산에서 뺌 → `ScoreWeight`·`ScoreNearest`, `ScoreMapper.findWeights`·`findNearest` 삭제. CCTV·가로등도 제외 (팀 결정)
- 배점: 중요한 순서만 정하고 ROC 공식 `(1/n)·Σ(1/i, i=k..n)` 으로 나눔 (`ScoreRules.TIERS`·`roc`·`points`). 상(응급실·소방서·경찰) / 중(약국·편의점) 2등급 → 상 75점(25씩) · 중 25점(12.5씩). 3등급이면 사진의 20·14·6 이 나오는 것을 테스트로 확인
- 시간 기준(`ScoreRules.TIME`): 응급실 30분 만점·60분 0점, 소방서 5분·15분, 경찰 5분 만점·**점수 매긴 캠핑장 중 가장 먼 곳 0점**. 그 사이는 시간에 비례 (응급실 40분 → 20 × 20/30 = 13.3)
- 계산 순서: 모든 캠핑장 카카오 시간을 먼저 모은 뒤(경찰 0점 기준을 알아야 해서) 점수 저장. 카카오를 못 부르면 아무것도 저장하지 않고 멈춤
- 약국·편의점: 정해진 분 숫자가 없어 캠핑장끼리 비교 (팀 결정) — 가장 빠른 캠핑장 만점, 가장 먼 캠핑장 0점, 그 사이는 카카오 분에 비례
- 확인: `.\mvnw.cmd test` 101개 통과 (새 `SafetyScoreServiceTest` 7개: ROC 값·배점 합 100·40분 예시·경찰 가장 먼 곳·약국 비교 방식·카카오 실패 시 저장 안 함)
- 확인 못 함: 실제 DB·카카오로 `--score` 실행. 편의점 중 상비약 파는 곳만 고르는 것(데이터에 그 칸이 없어 camp_facility 의 가장 가까운 편의점 그대로 씀). 시설 고르기(camp_facility)는 아직 직선으로 가장 가까운 1곳이라, 차로는 다른 시설이 더 빠를 수 있음

## 2026-11-17 차박 서버 코드 정리 (결과 그대로)
- `ChabakService`: 응급실 분 꺼내기를 `erDriveMinOf()` 하나로 (찍은 지점 점수 · 후보지 같이 씀), `score()` 의 가중치 합 중복 계산 제거 (`scoreOf` 한 곳)
- 캐시 이름 `zoneCache` → `layerCache` (구역뿐 아니라 후보지 목록도 들어 있어서)
- 서버 테스트 통과. 후보지 점수 N+1 쿼리(46번 왕복)는 효과가 작아 보류 (첫 요청 한 번뿐, SQL 중복 생김)

## 2026-11-17 차박 화면: 탐색과 같은 목록 + 내 캠핑에 등록
- 아래 시트를 탐색처럼: 검색(지번·캠핑장 이름) · 칩(필터 아이콘=구역 표시 · 차박 후보지 · 등록 야영장 · 주변 안전 ▾) · "차박지 N곳" 목록(안전점수순/응급실 가까운순). 처음엔 작게(지도), 끝까지 올리면 떠 있는 "지도" 버튼
  - 목록 한 줄 `components/chabak/SpotRow.vue`: 야영장은 고캠핑 사진, 후보지는 그 자리 스카이뷰(카카오 정적 지도, 보일 때만 그림) · 판정 딱지 · 안전 배지 · 응급실 분(후보지는 "약")
  - 지도 오른쪽 위 필터 버튼 삭제 (칩 줄로 옮김). `FilterSheet` 에 `only`(avoid·safety) 추가
  - 줄·지도 점을 누르면 판정 카드, "‹ 목록으로"는 보던 시트 높이로 돌아감
- 판정 카드: 등록 야영장을 골랐을 때 "자세히 보기" 아래 **내 캠핑에 등록하기** → 일정 등록 시트 → 내 캠핑에서 보기
  - 시트는 캠핑장 상세에서 꺼내 공통 부품 `components/mycamping/PlanSheet.vue` 로 (상세도 이걸 씀)
  - 차박 후보지는 등록 안 됨: `camp_plan` 이 camp.id 를 꼭 받음 (`CampPlanRequest @NotNull`) → 후보지도 하려면 DB 변경 필요 (팀 결정)
- 서버 `GET /api/chabak/layer` 점 레이어에 `area` · `imageUrl` · `erDriveMin` 추가, 후보지 이름 "475-3 잡" → "475-3 잡종지"
- 남은 것: 후보지 읍·면 이름 (데이터에 없음 — 법정동 코드 이름표 또는 카카오 좌표→주소)
- 확인 못 함: 실제 화면 (빌드·서버 테스트만 통과)

## 2026-11-17 지도 아이콘 교체 (Lucide + Phosphor)
- 시설 5개 → Lucide (ISC): 응급실 cross · 소방서 flame · 경찰서 shield · 편의점 store · 약국 pill. 텐트 → Phosphor tent fill (MIT). 전에는 SVG Repo 그림이 섞여 스타일·라이선스가 제각각이었음 (`utils/facilityIcon.js`)
- 탐색·차박 지도의 "지도 설정" 시설 배지는 흰 바탕 + 진한 아이콘 + 얇은 테두리(`.fac-badge.plain`) → 등급 색 캠핑장 마커와 구별. 내 주변·경로 지도는 등급 색 바탕 그대로 (아이콘만 바뀜)
- 확인 못 함: 실제 화면 (빌드만 통과)

## 2026-11-17 탐색 지도: 범례 빼기 · 지도 설정 레이어가 지도에 보이게
- 오른쪽 위 안전·주의·위험 범례 삭제 (마커 색만으로 알 수 있음)
- 지도 설정의 안전 시설(응급실·약국 등): 처음엔 전부 꺼짐 → 켜면 **지도 화면 안**의 그 종류 시설이 회색 배지로 보임. 지도를 움직이면 다시 받아 옴
  - 전에는 "고른 캠핑장의 가까운 시설"만 그려서, 캠핑장을 안 고르면 켜도 아무것도 안 보였음
  - 새 API `GET /api/facilities?types=&swLat=&swLng=&neLat=&neLng=` (최대 300곳, 화면 가운데에서 가까운 순)
- 차박 지도도 똑같이: 처음엔 전부 꺼짐, 켜면 지도 화면 안의 시설 (전에는 찍은 지점 근처 종류별 3곳만). 판정 카드 "가까운 시설" 목록은 그대로 찍은 지점 기준
- 지도 위 시설 배지를 누르면 말풍선 "종류 · 이름 · 상세 ›" → 누르면 시설 상세(`/facilities/{id}`). 빈 곳을 누르면 닫힘. 탐색·차박 같음 (`facilityPopupEl`, 모양은 base.css `.fac-pop`)
- 지도 설정에서 켠 종류는 `utils/facilityLayers.js` 에 둬서 시설 상세에 갔다 뒤로 와도 유지 (탐색·차박 같이 씀, 새로고침하면 전부 꺼짐)
- 확인 못 함: 실제 화면에서 켜고 끄기·말풍선 (빌드·서버 테스트만 통과)

## 2026-11-17 지도 버튼에 글자 (탐색 · 차박)
- 아이콘만으로는 뜻을 알기 어려워서 지도 위 버튼을 동그라미 44px → 아이콘 + 아래 작은 글자 둥근 네모 48×52 (전역 `.map-btn small`): 차박 `필터 · 지도 · 내 위치`, 탐색 `지도 · 내 위치`. 화면 읽기 프로그램은 기존 aria-label 그대로 읽음
- 필터와 지도 설정은 합치지 않음 (역할이 갈렸고 — 필터: 어떤 차박지를 볼지 / 지도 설정: 지도 모양 — 탐색·내 주변과 같은 "지도 설정"을 유지)

## 2026-11-17 차박 지도: 점 누르기 쉽게 · 후보지 목록
- 후보지·야영장 점: 보이는 점은 16px 그대로, **누르는 범위만 44px** (투명한 버튼 안에 점을 그림) → 옆 땅이 잘못 눌려 엉뚱한 판정이 나오지 않게
- 시트 처음 화면의 "차박 후보지 · 등록 야영장" 줄을 누르면 **목록**: 안전점수 높은 순(점수 없는 곳은 뒤), 주변 안전 필터를 거친 것만, 줄마다 `89점 안전`. 누르면 지도를 옮겨 그 자리 판정 → 판정 카드 위 **"‹ 목록으로"** 로 돌아옴. 지도·점을 직접 눌러 본 판정은 카드 위 **"‹ 차박지 찾기"** 으로 처음 화면에 돌아옴 (판정 실패·범위 밖 카드도 같음). 지도를 직접 누르면 목록 흐름에서 벗어남. 구역(불가·주의)은 목록 없음

## 2026-11-17 차박 지도: 판정한 필지로 확대
- 초록 점(차박 후보지)처럼 축소된 지도에서 누르면 칠한 필지가 점·핀에 가려 안 보이던 것 → 판정 결과가 오면 지도 레벨이 5보다 멀 때만 그 필지가 보이게 확대 (아래 카드에 가리지 않게 아래 여백, 너무 작은 필지는 레벨 3까지만). 이미 확대돼 있으면 지도를 안 움직임

## 2026-11-16 차박 지도: "주변 안전" 필터
- 차박 후보지는 규제(제한 없음)만 통과한 곳이라 응급실이 먼 곳도 섞임 → 시트 처음 화면에 `주변 안전 [전체] [주의 이상] [안전만]` (캠핑장 등급과 같은 기준 40↑ · 70↑)
- 서버 `GET /api/chabak/layer` 가 후보지마다 안전점수(`/score` 와 같은 계산, 46곳 처음 한 번만 계산해 기억) · 야영장은 `camp.safety_score` 를 `score`·`grade` 로 같이 보냄
- 필터를 바꾸면 지도 점과 개수가 바뀜 (지도 범위는 그대로). `ChabakServiceTest` 7개 통과
- (수정) 판정 카드 ✕ 로 처음 화면에 돌아가 필터를 고르던 방식 대신 → 지도 오른쪽 위 버튼 줄 맨 위에 **필터 버튼** → **차박지 필터 시트**(`components/chabak/FilterSheet.vue`): 갈 수 있는 곳(차박 후보지 · 등록 야영장 스위치 + 걸러진 개수) · 주변 안전(전체 · 주의 이상 · 안전만). 판정 카드가 떠 있어도 언제든 열 수 있고, 기본값과 다르면 버튼에 초록 점. "지도 설정"에서는 갈 수 있는 곳을 빼서 지도 종류 · 안전 시설만. 시트 처음 화면에는 "주변 안전 전체 · 필터 ›" 한 줄만
- 필터 시트에 **"피해야 할 곳" 불가 구역 · 주의 구역** 다시 넣음 (처음엔 꺼짐 — 켜면 지도가 빨갛게 덮여 갈 수 있는 곳이 안 보여서). 주변 안전 필터는 점(후보지·야영장)에만 적용, 구역은 개수 표시 안 함. 기본값(갈 수 있는 곳 켜짐 · 피해야 할 곳 꺼짐 · 전체)과 다르면 필터 버튼에 점

## 2026-11-16 차박 지도: "갈 수 있는 곳"만 보여 주기
- 차박 지도를 여는 사람은 "어디로 갈 수 있나"를 찾음 → 들어오면 **차박 후보지(초록 점 46) · 등록 야영장(파란 점)** 을 바로 켜고 둘 다 보이게 범위를 맞춤
- 불가·주의 구역 표시는 화면에서 뺌 (서버 `GET /api/chabak/layer?type=no|caution` 은 남겨 둠). 안전 확인은 지도를 눌러 판정 카드(사유 포함)로
- 시트 처음 화면: "차박할 곳을 찾아보세요" + 점 두 종류 설명·개수. 지도 설정의 칸 이름 "판정 표시" → "갈 수 있는 곳"
- 찍은 지점 안전점수: 새 API `GET /api/chabak/score` — 캠핑장 안전점수와 **같은 계산·같은 기준**(`raw.score_weights`, 직선거리). 지금 화면의 캠핑장 점수도 이 방식이라 서로 비교 가능 (카카오 소요시간 방식은 `ScoreRules.TIME` 분 기준이 비어 있어 아직 안 쓰임). (수정) 처음엔 캠핑장 상세와 같은 큰 점수 카드를 넣었으나, 초록 "제한 없음"과 빨강 "39 위험"이 같은 무게로 붙어 결론이 둘처럼 보여서 → 큰 제목은 판정 하나만 두고, 아래에 **이름표 붙은 두 줄**: `차박 규제 ● 제한 없음` / `주변 안전 ● 39점 위험 · 소방서 약 26분 · 응급실 약 39분 ›`(누르면 시트를 올려 가까운 시설 목록). 판정은 "여기서 자도 되나", 점수는 "도움받기 쉬운가"로 다른 질문이라는 게 보이게. (응답의 `headline` 은 서버에 남아 있지만 차박 화면에서는 안 씀) 자세히 보기의 근거는 점수(100점·0점 나열이 의미가 적어서) 대신 **캠핑장 상세처럼 "가까운 시설"** — 종류마다 가장 가까운 1곳을 번호 · 이름 · 거리 · 차로 약 N분(직선거리 어림)으로, 누르면 시설 상세(`/facilities/{id}`). 카카오 호출 없음 → 한도·캐싱 약관 걱정 없음. `ChabakServiceTest` 6개 통과
- 판정 카드 "근처 등록 야영장"을 누르면 캠핑장 상세(`/camps/{id}`)로. DB 판정 함수에는 이름만 있어서 서버가 이름으로 `camp.id` 를 찾아 붙임 (`ChabakMapper.findCampsByName`, 공용 DB 함수는 안 건드림). 못 찾은 곳은 이름만. `ChabakServiceTest` 5개 통과

## 2026-11-16 차박 DB 파이프라인 레포에 추가 (`db/chabak_pipeline`, 브랜치 `chabak`)
- 원본 SHP → 검사(좌표계·인코딩·건수·PNU) → raw 적재 → 정리 SQL(02·03·05) → 품질 리포트 → dump 를 명령 한 줄로 (`pipeline.py`)
- `load` · `transform` 은 PGHOST 가 localhost 가 아니면 멈춤 (공용 DB 보호). 공용 DB 반영은 README 의 참고용 절차로 사람이 직접 (복원 뒤 teamuser 권한 다시 주기 포함)
- 확인: 이 PC 로컬 `chabak` DB 에서 전체 실행 → 필지 124,276 · 규제구역 845 · 누락 4/856 로 수작업 결과와 같음, 차박 후보지 46곳. 첫 실행에서 버그 3개 고침 (README "실행 결과")
- `.gitignore`: 파이프라인 dump·풀어 놓은 sql 은 올리지 않음 (리포트는 올림)

## 2026-11-15 차박 판정 API (브랜치 `chabak`)
- `GET /api/chabak/verdict?lat=&lng=` 추가 (`chabak/` 패키지: Controller·Service·Mapper·VerdictResponse). 지도에서 찍은 지점이 차박 불가·주의·제한 없음·야영 가능 중 어디인지
- 판정 규칙은 전부 DB `chabak` 스키마 함수(`fn_verdict` → `fn_verdict_json`, 인제군 필지·규제구역)에 있다. 서버는 위치 검사(빠짐·범위 밖 400, 인제군 밖 404)만 하고 함수가 만든 JSON 을 `ChabakVerdictResponse` 로 옮겨 돌려준다 (다른 API 처럼 응답 record. 필지 경계 `parcel` 만 GeoJSON 그대로)
- 공유 DB 확인: 두 함수 search_path = `chabak, extensions, public`, teamuser 의 chabak 스키마·테이블·함수 권한 모두 true
- 확인: `ChabakServiceTest` 3개 통과 (DB 없이 가짜 mapper)
- 확인: 서버를 켜서 실제 DB 로 호출 → 불가 지점(산12-21) `verdict: 불가`, 사유·규제구역·근처 야영장(`distM`)·대안 자리·필지 경계 정상
- 남은 것: 북천·방태천 고시 금지구간, 필지 경계 좌표가 많음(산 필지) → DB 함수에서 `ST_Simplify` 검토

## 2026-11-16 차박 지도 화면 (브랜치 `chabak`)
- 탐색 지도 위쪽 가운데에 `캠핑장 | 차박` 토글 (`components/common/MapModeToggle.vue`, 링크라 뒤로가기로 돌아옴) → `/chabak` (`views/chabak/ChabakView.vue`)
- 차박 지도(`components/chabak/ChabakMap.vue`): 지도를 누르면 `/api/chabak/verdict` → 필지 경계를 판정 색으로 칠하고 핀 표시. 카카오 키가 없으면 시연 지점 버튼 5개로 대신
- 아래 시트: 처음엔 안내 + 판정 색 범례 / 결과는 접힌 카드(배지·한 줄 결론·지번·사유 2줄) → "자세히 보기"로 판정 사유 전부·걸린 규제구역·근처 차박 후보(누르면 그 자리로 이동해 다시 판정)·근처 등록 야영장 / 인제군 밖(404)은 "인제군으로 이동". 참고용 판정 문구는 항상 아래에
- 시트 높이 3단계 (작게: 판정 한 줄 / 카드: 내용 높이에 맞춤, 화면 60%까지 / 전체: 자세히). 손잡이를 끌면 따라오고 놓으면 가까운 단계로 붙음, 누르면 다음 단계. 높이 바뀔 때 0.32초 애니메이션
- 이 끌기·애니메이션을 `utils/dragSheet.js`(`useDragSheet`, `useElementHeight`) + 전역 `.drag-sheet`·`.drag-handle`(styles/base.css)로 빼서 **내 주변 패널**(내림 120 ↔ 펼침 430)과 **탐색 시트**(지도 168 ↔ 목록 전체)에도 적용. 키보드 Enter 로 누르기도 됨. 내 주변 지도는 끄는 동안 다시 맞추지 않고 단계가 정해진 뒤에만 맞춤
- 탐색 화면 처음 상태를 목록 → **지도**(시트 내림)로 바꿈. 1분 진단 안내 팝업도 지도 위에 뜬다 (차박 ↔ 캠핑장 토글로 돌아와도 지도부터)
- 차박 지도 오른쪽 위 "지도에 보일 것" 레이어 버튼 (탐색·내 주변과 같은 LayerSheet). 지도를 찍으면 그 근처 시설을 새 API `GET /api/chabak/facilities`(종류별 3곳, `ChabakFacility`)로 받아 같은 아이콘 배지(회색 — 차박 지도엔 안전점수 등급 없음)로 표시. `ChabakServiceTest` 4개 통과
- 공통 `BottomSheet`(지도에 보일 것 · 지역 · 누구와 언제 · 내 캠핑 일정 등록)에 애니메이션: 열 때 아래에서 올라오고 뒤가 서서히 어두워짐, 닫을 때(X · 바깥 · Esc) 내려간 뒤 닫힘. 손잡이를 아래로 100px 넘게 끌면 닫힘. 움직임 줄이기 설정이면 애니메이션 끔
- 구역 겹침 정리: 국립공원·백두대간·산림보호구역이 같은 땅에 겹쳐 지정돼 반투명 면이 여러 겹 쌓이던 것 → 서버에서 판정별로 한 덩어리로 합침(ST_Union), 주의 구역은 불가 구역과 겹친 부분을 뺌(ST_Difference) → 한 땅에 한 색. 합친 결과는 처음 한 번만 계산해 서버가 기억. 구역 레이어는 개수 표시 안 함(한 덩어리라). `ChabakServiceTest` 5개 통과
- (수정) 지도 위 칩 줄이 거슬려서 없앰 → "지도 설정" 시트에 **"판정 표시"** 스위치 4개(차박 지도에서만). **여러 개 같이 켜짐**, 켜도 시트는 자동으로 안 내려감(직접 닫음). 지도는 레이어마다 따로 그려서 하나를 켜고 끌 때 나머지는 그대로, 새로 켠 것 범위로 맞춤. 겹칠 때 점 > 불가 구역 > 주의 구역 순으로 위. 아래 시트 한 줄에 켠 것들 `● 불가 구역 18 ● 차박 후보지 40` + ✕(모두 끄기). 지도 위에는 토글 · 버튼만 남음
- 차박 지도 판정 단계 칩 (토글 아래 가로 줄, 넘기거나 눌러 하나만 고름, 다시 누르면 끔): **불가 구역 · 주의 구역 · 차박 후보지 · 등록 야영장**. 새 API `GET /api/chabak/layer?type=` (읽기만). 구역은 옅은 면, 후보지·야영장은 점(누르면 그 자리 판정). 고르면 지도 범위를 맞추고 시트를 "작게"로 내려 `● 불가 구역 18곳` 한 줄만 (지도를 가리지 않게). 시트를 올리면 한 줄 설명(무엇은 안 보이는지, 예: 주의 구역엔 사유지·급경사 주의가 안 나옴). 이름에 구역·후보지·야영장을 붙인 것은 판정 단계 전체가 아니라 데이터로 보여 줄 수 있는 것만 보이기 때문. `ChabakServiceTest` 5개 통과
- 지도 위 버튼 정리 (탐색 · 차박): 흩어져 있던 큰 검은 버튼(52px) → **오른쪽 위에 세로로 모은 44px 흰 버튼**(지도 설정 · 내 위치, 전역 `.map-btns` · `.map-btn`). 캠핑장 | 차박 토글은 **왼쪽 위**로 옮기고 같은 높이(44px)로 줄임. 내 위치 버튼이 더 이상 시트를 따라 움직이지 않음. 탐색 지도의 등급 범례는 버튼 아래 그대로. 내 주변 지도는 아직 예전 모양
- 지도 종류를 2개로: `지도`(ROADMAP) · `스카이뷰`(카카오 HYBRID = 위성사진 + 도로·지명). 위성사진만(SKYVIEW)은 뺌. 시트에서 미리보기 그림 카드로 고름 — 글자 없이: 스카이뷰는 카카오 정적 지도의 위성사진만(SKYVIEW, 인제읍 시내), 지도는 정적 지도에 글자 끄는 옵션이 없어 땅·숲·강·도로 간단한 SVG 그림(tokens 색 변수만). 그림의 카카오맵 링크는 눌리지 않게 막음
- 시트 이름 "지도에 보일 것" → **"지도 설정"** (제목 · 세 지도의 버튼 이름)
- 끊김 2차 수정: ① 지도 위 시트(차박·탐색·내 주변)를 높이 애니메이션 → **위치(transform) 애니메이션**으로 (시트 높이는 가장 높은 단계로 고정, 보이지 않는 만큼 아래로 밈 — `useDragSheet().style`). 차박 내 위치 버튼도 transform 으로 시트를 따라감 ② 공통 시트는 화면에 붙은 뒤 한 장면을 먼저 그리고(requestAnimationFrame 두 번) 올라오게 — keyframes → transition
- 공통 시트 애니메이션을 짧고 매끄럽게: 열기 0.22초 · 닫기 0.18초, 움직임은 transform·opacity 만 (어두운 막은 따로 한 장 깔고 투명도만 바꿈) → 다시 그리기 없이 GPU 로 처리
- "지도에 보일 것" 시트: 탭(안전 시설 · 지도 종류 · 날씨·재난) 없앰 → 제목 아래 지도 종류 `기본 / 위성 / 하이브리드` + "안전 시설" 목록. 지도 종류는 `utils/mapType.js` 값 하나를 탐색·내 주변·차박 지도가 같이 봄 (카카오 MapTypeId ROADMAP·SKYVIEW·HYBRID, 키·비용 추가 없음)
- 차박 화면에서 "지도에 보일 것" 시트가 브라우저 전체를 덮던 것 고침 (`.chabak` 에 position: relative)
- "내 위치" 아이콘을 화살표 → 조준점(원 + 가운데 점 + 사방 눈금)으로 바꿈 (차박 지도 · 탐색 지도 CampMap 둘 다)
- 차박 지도 왼쪽 아래 "내 위치" 버튼: 브라우저 위치 → 지도에 초록 점 + 확대 이동 → 그 자리 바로 판정 (인제군 밖이면 "판정 범위 밖"). 권한 거부는 안내 문구. 버튼은 시트 위를 따라 움직이고 시트를 끝까지 올리면 숨김
- 판정 색 4개를 `styles/tokens.css` 에 `--color-verdict-*` 로 추가 (사용자 승인, 나중에 바꿀 수 있음). 지도 다각형은 `utils/chabak.js` 의 `verdictColor()` 가 이 변수를 읽는다
- 카카오 키가 없을 때 `CampMap` 의 "임시 지도" 안내를 토글 아래(top 72px)로 내림
- 화면 시안: claude.ai Design 캔버스 "차박 지도 화면 구성" (기존 구현 스타일 기준)
- 확인: `npm run build` 통과
- 확인 못 함: 브라우저에서 실제로 눌러 보기 (카카오 지도 클릭 · 필지 칠하기 · 시트 올리고 내리기)
- 남은 것: 인제군 경계선 표시(경계 데이터 없음), 차박 지도 위 등록 야영장 마커, 근처 야영장 줄을 캠핑장 상세로 잇기(응답에 id 없음)

## 2026-11-16 가족 모임방 3차 위치 탭 — 1단계: 빈 지도 (브랜치 `lyh-location`)
- 새 파일 `client/src/components/family/LocationTab.vue`: 위치 탭을 누르면 카카오 지도를 띄운다. 가로는 화면 폭, 세로 55vh(최소 280px). 창 크기가 바뀌면 `keepMapSized` 로 다시 맞춤
- `FamilyRoomView.vue`: `TABS` 의 위치 탭 `component: null` → `LocationTab` ("준비 중이에요" 대신 지도)
- 마커 수정: 파란 점(흰 테두리 + 흰 빛) 아래에 파란 이름표(흰 글씨, 테두리 없음). 색 변수 `--color-family-pin` 추가(`tokens.css`, 길 선 파랑 재사용). 더미 인물 짱구·맹구, 좌표는 인제군청 부지 안 대략값(확인 필요). 가까운 두 점이 겹치지 않게 `fitPoints` 최소 확대를 3으로
- 새로고침 버튼(탭 오른쪽 위): 위치를 다시 불러와 마커를 새로 찍고 가족이 모두 보이게 지도를 다시 맞춤. 지도 아래에 "HH:MM 확인" 표시 (확인한 시각이지 위치가 찍힌 시각이 아님). 지금은 더미라 값은 같고, 서버 연결 때 `refresh()` 안의 한 줄만 API 호출로 바꾸면 됨
- 위치 공유 동의(시연용): 위치 탭에 들어오면 동의 안내창("내 위치가 이 가족방 구성원에게 보여요 · 언제든 끌 수 있어요", [나중에] [동의하고 보기]). 동의 전에는 지도를 만들지 않음. 나중에 → "동의하면 가족 위치를 볼 수 있어요" + [동의하기]. 동의는 이 브라우저 localStorage(`campin.locationConsent.<회원 id>`)에만 저장 → 다음부터 안 물음. 지도 아래 [공유 끄기]로 철회. 실제 서비스에서는 DB(`location_consent` 안)에 저장해야 함. 회원가입 화면은 바꾸지 않음
- "나" 마커 구분: 나는 밝은 초록 점·이름표(이름만, "(나)" 표시 없음), 겹치면 위에 표시. 색 변수 `--color-family-me: #16a34a` (사용자 요청으로 새 색 추가).
- 가족방 구성원 목록: "(나)" 글자를 빼고 내 이름 칩을 같은 밝은 초록(`--color-family-me`) + 흰 굵은 글씨로. 마우스를 올리면 "로그인한 나", 화면 읽기 프로그램에는 "이름, 나"
- 탭 순서: 위치 · 모일 곳 · 아이 카드 · 일정 (가족방에 들어오면 위치 탭이 먼저)
- 동의 화면 변경: 화면 전체를 어둡게 덮던 안내창 대신, 지도 칸을 회색(`--color-photo`)으로 두고 가운데에 팝업 카드("내 위치가 이 가족방 구성원에게 보여요. 공유는 언제든 끌 수 있어요." [나중에] [동의하기]). 나중에 → 같은 문구 + [동의하기]만 남고 회색 유지. 동의하면 같은 칸에 지도
- 3단계 DB: `db/schema_05.sql` — `family_location`(member_id UNIQUE · lon · lat · updated_at). 더미 여부 컬럼은 두지 않음(지금은 전부 시드라 구분할 대상이 없음, 시드 주석에 "시연용 가상 좌표"). 시드 INSERT 는 주석으로 넣어 둠(login_id 를 바꿔 따로 실행). **아직 어떤 DB 에도 실행 안 함**
- 서버: `family/` 에 LocationController · LocationService · LocationMapper · LocationResponse · FamilyLocation. `GET /api/family/locations` → `[{ id, name, lat, lng, me, updatedAt }]`. 방 확인은 `requireMyRoom`, 방 구성원 중 위치가 있는 사람만(JOIN family_member → 나간 사람은 안 보임)
- 위치 탭 모양 수정 (2026-11-16): 위쪽에 "위치 공유" on/off 스위치(LayerSheet 스위치와 같은 모양, 켜기 = 동의 · 끄기 = 철회, 아래 "공유 끄기" 글자 버튼은 없앰). 새로고침은 지도 오른쪽 위 동그란 버튼으로 옮김.

## 2026-11-12 DB 정리 3단계: saved_camp.camp_id 숫자 + 캠핑장 외래키 (브랜치 `db-cleanup-2`)
- 새 파일 `db/schema_04.sql`: ① camp 에 없는 캠핑장을 가리키는 `saved_camp` 줄 삭제 (더미 `c1`~`c3`, 덤프 기준 4줄) ② `saved_camp.camp_id` varchar → bigint ③ 외래키 `saved_camp.camp_id` · `camp_plan.camp_id` → `camp(id)`
- 왜: `camp.id` 는 숫자인데 저장 칸만 글자라 `c.id::text = sc.camp_id` 로 비교(인덱스 못 씀), 외래키가 없어 없는 캠핑장도 저장됐음
- `merge_to_public.sql`: `DELETE FROM camp` 를 없애고 camp 를 UPSERT(`ON CONFLICT (id) DO UPDATE`)로. 원본에서 빠진 캠핑장만 지움 → 다시 돌려도 회원이 저장한 캠핑장·일정 유지. 저장된 캠핑장이 원본에서 빠지면 외래키 에러로 merge 전체 취소 (일부러 막음)
- 서버 `saved/`: campId `String` → `Long` (SavedMapper · SavedService · SavedController · CompareCamp · CompareResponse.Camp · FavoritesResponse). 응답 JSON 의 캠핑장 id 가 글자 `"100568"` → 숫자 `100568`
- 프론트: 바꾼 것 없음 (`favorite.js` 는 숫자로 바꿔 쓰고 있었고, `CompareView` 는 id 를 키·주소에만 씀)
- 확인: `mvnw test` 통과 (`SavedServiceTest` id 를 숫자로, 더미 `c1` 케이스 정리), 로컬 DB(`campin_local`)에서 schema_04 실행 → camp_id bigint · 외래키 2개 · saved_camp 1줄(101137)
- 확인: 외래키가 걸린 상태에서 바뀐 merge 를 로컬에서 다시 실행 → COMMIT, 저장한 캠핑장 유지, camp 23곳 · 안전점수 23곳 다시 계산됨. 화면에서 하트로 새로 담은 줄(100568)이 숫자로 저장됨
- 확인 못 함: 비교 화면에서 빼기 · 하트 끄기
- 공유 DB 반영: PR #11 머지 후 백업 덤프(`campin_before_04.dump`) → teamuser 로 schema_04.sql 실행 → `DELETE 4` · COMMIT
- 공유 DB 반영(2단계): PR #10 머지 후 DB 주인이 postgres 로 schema_03.sql 실행 (teamuser 는 스키마 만들기 권한이 없어 실패 → 롤백됨)
- 남은 것: 숫자가 아닌 campId(예: `/camps/c1`)는 지금 500 (listId 와 같은 동작). 공통 예외 처리에 400 을 넣을지 따로 정함

## 2026-11-12 DB 정리 1단계: 쓰지 않는 백업 테이블 삭제 (브랜치 `db-cleanup`)
- 새 파일 `db/schema_03.sql`: `facilities_backup`(9,586줄) · `bak1109_*` 7개를 `DROP` (사용자 승인). 서버 코드·`merge_to_public.sql` 어디서도 읽지 않는 백업
- 지우지 않은 것: 원본 `camps`·`facilities`·`spot`·`score_weights`·`er_detail` — 서버가 직접 안 읽어도 `merge_to_public.sql` 의 재료
- 되돌리기: 2026-11-11 공유 DB 덤프(`campin.dump`)에 그대로 있음 (`pg_restore -t 테이블이름`)
- 확인: 로컬 DB(`campin_local`)에서 pgAdmin 으로 실행 성공
- 확인 못 함: 서버를 켜서 화면이 그대로 나오는지 (지운 테이블을 읽는 코드가 없어 영향 없을 것으로 봄)
- 아직: 공유 DB 반영 안 함 — 팀에 알리고 백업 덤프를 뜬 뒤 실행
- 다음 단계: `saved_camp.camp_id` 숫자 타입 + 외래키 (분석은 노션 "campin DB 정리·최적화 분석")

## 2026-11-12 DB 정리 2단계: 원본 테이블을 raw 스키마로 분리 (브랜치 `db-cleanup`)
- `db/schema_03.sql` 2단계: `camps`·`facilities`·`spot`·`score_weights`·`er_detail` → `raw` 스키마 (`ALTER TABLE … SET SCHEMA`, 데이터·인덱스 그대로). teamuser 가 있으면 `raw` 읽기 권한 부여
- 왜: 원본 `camps` / 화면용 `camp` 처럼 한 글자 차이라 헷갈림 → `raw.camps` / `public.camp`
- 함께 바꾼 코드: `merge_to_public.sql`(`public.camps` → `raw.camps` 등 전부), `saved/SavedMapper` 비교 SQL(`raw.camps`), `dbtest/TestMapper`(`raw.er_detail`)
- 규칙 문서: `db/CLAUDE.md` 에 "스키마 나누기" (raw 는 읽기만, 값 보정은 merge 안에서, 회원 데이터만 직접 수정)
- 새 DB 를 처음부터 만들 때 순서가 바뀜: 원본 덤프 → schema.sql → schema_02.sql → **schema_03.sql → merge_to_public.sql**
- 확인: `mvnw test` 통과, 로컬 DB(`campin_local`)에서 schema_03.sql 전체 실행 → `raw` 에 5개 확인
- 확인 못 함: 비교 화면(전기·반려동물 칸)이 그대로 나오는지, merge 를 raw 에서 다시 돌려 보기
- 아직: 공유 DB 반영 안 함

## 2026-11-11 안전점수: 직선거리 → 카카오 소요시간(분) 기준 (서버 일괄 계산)
- 새 패키지 `server/.../score/`: `SafetyScoreService`(계산) · `ScoreRules`(분 기준 상수 한곳) · `ScoreMapper`(SQL) · `ScoreRunner`(`--score` 인자로 켤 때 한 번) · 모델 `ScoreCamp`·`ScoreCampFacility`·`ScoreWeight`·`ScoreNearest`
- 방식: 캠핑장에 연결된 시설(`camp_facility`, 종류마다 직선으로 가장 가까운 1곳)까지 카카오 소요시간 → `ScoreRules.TIME` 의 분 기준으로 부분점수. 기준이 없는 종류(보안등·CCTV 등)는 예전처럼 `public.score_weights` 거리 기준. 점수 공식 모양은 `merge_to_public.sql` 8 단계와 같음
- 저장: **DB 구조·SQL 파일·application.properties 는 그대로**. 고치는 칸은 `camp.safety_score`·`er_drive_min`, `camp_facility.drive_min` 뿐. **km(`distance_km`)는 직선거리 그대로**
- 한 캠핑장에서 하나라도 길을 못 찾으면 그 캠핑장은 안 고침. 카카오를 아예 못 부르면(키 없음·한도) 그 자리에서 멈춤. 카카오 104(5m 이내)는 0분 → 화면 1분
- 실행: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--score"` (환경변수 `KAKAO_REST_KEY` 필요, 별도 스레드라 서버는 요청을 그대로 받음). 인자 없이 켜면 안 돎
- **`ScoreRules.TIME` 분 기준은 비어 있음 — 팀 결정 필요. 비어 있으면 계산하지 않음**
- 화면: 바꾼 것 없음. 경로 지도(`RouteService`)는 지금처럼 카카오 도로거리 km 를 보여 줌 → 상세 목록(직선 km)과 다를 수 있음
- 확인: `mvnw clean test` 통과 (99개, 새 `SafetyScoreServiceTest` 5개 포함)
- 확인 못 함: 실제 DB·카카오로 돌려 보기 (`public.score_weights`·`public.facilities` 칸 이름은 `merge_to_public.sql` 을 보고 맞춤), 호출 수(캠핑장 수 × 연결 시설 수)가 카카오 하루 한도 안에 드는지
- 주의: `merge_to_public.sql` 을 다시 돌리면 직선 추정값으로 돌아감 → 다시 계산

## 2026-11-09 알림: 재난문자를 행정안전부 긴급재난문자 API 로
- 서버: `alert/SafetyDataDisasterClient` (재난안전데이터 공유플랫폼 `DSSP-IF-00247`) + `DisasterMessageParser` + `DisasterSource` 인터페이스. `AlertService` 가 받으면 실제, 못 받으면 지금처럼 `AlertDummyData` 예시 (`disasterDummy`)
- 키: 환경변수 `SAFETY_DATA_KEY` (`application-local.properties` 에 넣음). **키를 신청할 때 등록한 IP 에서만 불린다** — 다른 IP 면 `resultCode 32` → 예시로 대신. 발표 장소 IP 도 미리 등록 필요
- 호출 수: 하루 한도 99회 → 20분에 한 번만 부르고 결과를 저장 (실패해도 20분 쉼, 하루 최대 72회). 오늘 날짜(`crtDt`)로 요청하고 최근 24시간 문자만 보여 줌
- 지역: 수신 지역 이름(예: `강원특별자치도 인제군,경기도 가평군`)을 시·도별로 나눠 한 건씩. 시·도를 모르는 지역(`전국`)은 어느 지역을 골라도 보이게
- 실제 응답으로 확인함: 성공 `resultCode 00`, 칸 이름 `MSG_CN`·`RCPTN_RGN_NM`·`CRT_DT`(`2026/11/09 14:26:21`)·`DST_SE_NM`(안개·화재·기타 등)·`SN`(숫자). `crtDt=yyyyMMdd` 를 붙이면 그날 문자만 옴 (안 붙이면 2023년 문자부터 오래된 순). 오류는 `30` 키 · `32` 등록 안 된 IP
- 화면: 바꾼 것 없음 (`disasterDummy` 가 false 면 이미 "예시" 표시를 뺌)
- 확인: `mvnw test` 통과 (`DisasterMessageParserTest` 8개 — 실제 응답 모양 포함 · `AlertServiceTest` 재난문자 1개 추가), 브라우저에서 실제 API 응답 확인
- 확인 못 함: 서버를 켜서 알림 화면에 실제 재난문자가 뜨는지
- 실종자 찾기 문자는 뺌 (사용자 결정): 경찰청이 "OOO씨를 찾습니다 … ☎182" 모양으로 보내는 문자. "찾습니다" + (실종·배회·182) 가 있으면 제외 (`DisasterMessageParser.isMissingPerson`)

## 2026-11-09 하트 → 관심 캠핑장 저장 · 비교를 실제 DB 값으로 (더미 삭제)
- 하트(`stores/favorite.js`): 로그인했으면 캠핑장 하트를 서버 '관심 캠핑장' 기본 목록에 담고 뺀다 → 저장 탭·비교 화면에 그대로 나옴. 로그인 안 했거나 관광지 하트는 지금처럼 sessionStorage. 로그인하면 그 전에 누른 캠핑장 하트를 서버로 옮김. 서버 반영이 실패하면 하트를 원래대로 되돌림
- 서버: `GET /api/saved/favorites` 추가 (기본 목록 id + 담긴 캠핑장 id). 캠핑장 담기는 `camp` 테이블에 있는 id 만 허용 (예전 더미 `c1`~`c3` 은 404)
- 비교(`GET /api/saved/lists/{id}/compare`): `camp`(안전점수·응급실 시간) · `camp_facility`(소방서 시간) · 원본 `camps`(전기·반려동물, `camp.source_id` 로 연결)에서 읽음. 값이 없으면 "확인 필요". 대피소는 데이터가 없어 모두 "확인 필요". 응답의 `dummy` 칸 삭제
- 삭제: 서버 `saved/CampDummyData.java`, 클라이언트 `DUMMY_CAMP_IDS` · 비교 화면 "예시 캠핑장 담기 (더미)" 버튼 · "더미 데이터" 안내. 빈 목록에서는 "캠핑장 둘러보기"(탐색) 링크
- DB: 변경 없음. 단 비교 SQL 이 `camp.source_id`·원본 `camps` 를 쓰므로 `merge_to_public.sql` 을 돌린 DB 여야 함
- 확인: `SavedServiceTest` 9개 통과, `npm run build` 통과, 로컬 DB 에 서버(8090)를 띄워 가입 → 담기(없는 id `c1` 은 404) → favorites → compare(실제 점수·시간) → 빼기 확인. 테스트 계정 `cltest01` 이 로컬 DB 에 남아 있음
- 확인 못 함: 브라우저에서 하트 누르기 → 저장 탭 → 비교 화면까지 실제 클릭 흐름
## 2026-11-09 칩 줄 가로 넘기기 (PC 마우스에서도)
- 원인: 칩 줄(탐색 필터, 캠핑장 상세 시설 칩)은 원래 `overflow-x: auto` 라 손가락으로는 넘어갔지만, 막대를 숨겨 두고 마우스 휠은 위아래만 움직여서 PC 에서 옆 칩을 볼 수 없었음
- 새 지시어 `utils/hscroll.js` (`v-hscroll`): 칩 줄 위에서 휠을 굴리면 옆으로 넘김. 끝에 닿으면 원래 화면 스크롤
- 전역 `.hscroll` (`styles/base.css`): 휴대폰은 막대 숨김, 마우스 기기(`hover: hover`·`pointer: fine`)만 얇은 막대 6px
- 캠핑장 상세: 지도 마커로 시설을 고르면 그 칩이 보이게 자동으로 옆으로 넘김 (`scrollIntoView`)
- 확인: `npm run build` 통과
- 확인 못 함: 실제 브라우저(PC 휠·휴대폰 손가락)에서 넘김, 탐색 시트를 접었을 때 막대 6px 때문에 칩 줄이 잘리는지

## 2026-11-09 편의점 아이콘 교체 · 지도 이름표에서 시설 이름 빼기 · 아이콘 키우기
- 편의점: "24" 간판 가게 → 차양 + 창문 + 문 가게 (사용자가 준 그림을 보고 직접 그린 선 SVG, `utils/facilityIcon.js`). 직접 그려서 라이선스 확인 불필요
- 크기 (모든 지도 공통 `styles/base.css`): 배지 30 → 34px, 배지 안 아이콘 58 → 64%. 경로 지도(`RouteMap.vue`) 배지 28 → 32px, 이름표 높이 34 → 38px
- 경로 지도 이름표: "응급실 17분" → "17분" (종류는 아이콘으로). 시설 상세 지도는 글씨 없이 아이콘만. 종류 이름은 `aria-label` 로만 남김 (화면 읽기 프로그램용)
- 캠핑장 상세의 칩·목록, 출발지(캠핑장 이름)는 그대로
- 확인: `npm run build` 통과
- 확인 못 함: 브라우저에서 새 편의점 아이콘 모양·선명도, 커진 마커끼리 겹치는 정도

## 2026-11-09 지도 아이콘 교체: 경찰서(경찰관 → 경찰차) · 캠핑장(텐트 새 그림)
- 사용자가 준 SVG Repo 아이콘 2개(`police-car-9`, `tent-5`)로 `utils/facilityIcon.js` 의 `police` · `camp` 교체. 검정 → `currentColor`
- 크기: 배지(30px)·아이콘 비율(58%)은 그대로. 두 그림이 가로로 길어 512×512 그대로면 작게 보여서 **viewBox 를 그림 있는 부분으로 잘라** 배지 안을 꽉 채움 (경찰차 `0 110 512 292`, 텐트 `0 70 512 362`)
- 바뀌는 곳: 내 주변 · 경로 · 시설 상세 · 탐색 지도의 경찰서 배지, 탐색 지도 캠핑장 마커 텐트, 경로 지도 출발지
- 확인: `npm run build` 통과
- 확인 못 함: 브라우저에서 실제 크기·선명도. 남은 일: 두 아이콘 라이선스 확인 (SVG Repo 페이지)

## 2026-11-09 탐색 지도 레이어 버튼 → 안전 시설 켜고 끄기 (내 주변과 같은 시트)
- 전: 탐색 지도 레이어 버튼 = 안전·주의·위험 등급 캠핑장 켜고 끄기(`MapLayerSheet.vue`) → **삭제**
- 후: 내 주변과 같은 `components/nearby/LayerSheet.vue` 를 연다. 줄 목록은 `GET /api/nearby/layers` (두 화면이 늘 같은 목록)
- 보이는 시설 = **고른 캠핑장의 가까운 시설** (마커를 한 번 누르면 `GET /api/camps/{id}` 의 `facilities` 를 받아 옴). 캠핑장을 고르기 전에는 시설 없음. 서버 변경 없음
- `CampMap.vue`: `facilities` prop 추가, 시설 배지(`facilityBadgeEl`) 그리기 — 색은 고른 캠핑장 등급 색, 번호는 상세 목록 순서. 처음 받아 왔을 때 캠핑장 + 시설이 한 화면에 들어오게 맞춤. 카카오 키 없는 임시 지도에도 같이 그림
- 지도 캠핑장 마커는 이제 등급으로 거르지 않고 전부 보인다 (범례는 그대로)
- 확인: `npm run build` 통과
- 확인 못 함: 브라우저에서 마커 고르기 → 시설 표시 · 레이어 켜고 끄기, 카카오 지도 위 겹침 모양

## 2026-11-09 누구와 언제 시트: 아이 선택 후 "없는 주소입니다" 고침
- 원인: 로그인 + 아이 1명 이상이면 시트가 `PUT /api/members/me/children` 을 부르는데, 이 서버 코드(`MemberChild*`)가 `origin/lyh` 브랜치에만 있고 main 에 합쳐지지 않았음 → 404
- 고침: `origin/lyh` 의 `MemberChildController·Mapper·Request·Service` + 테스트를 가져오고, 주소를 다른 회원 API 와 같은 `/api/member/children` 으로 바꿈 (SecurityConfig 의 `/api/member/**` 로그인 필요 규칙에 그대로 걸림). `TripSheet.vue` 호출 주소도 같이 바꿈
- DB: 기존 `member_child` 테이블 그대로 사용 (schema_02.sql, 1.sql 둘 다 같은 모양). DB 변경 없음
- 확인: `mvnw test` 통과, `npm run build` 통과
- 확인 못 함: 실제 DB·브라우저에서 아이 선택 → 적용 흐름

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

## 2026-11-20 웹(PC) 화면 (브랜치 `bgh-split-layout`, 주소 `/web/...`)
- 구조: 웹은 `router/webRoutes.js` + `views/web/` + `components/web/` + `styles/tokens-web.css`(`--web-*`)로 따로 둔다. 모바일 화면·`tokens.css` 는 그대로. 좁은 화면은 `/web/...` → 모바일 주소로 보낸다
- 설계서(CAMPINE PC 화면설계) 25장 구현: 홈, 로그인·가입, 1분 진단(6유형), 탐색·캠핑장 상세·시설 상세·차박지, 내 주변(카카오 도로 경로), 저장(비교표), 내 예약, 예약 확인, 전체 메뉴
- 설계서에 없던 화면(모바일 기능을 웹 모양으로): 지역·날짜 선택 팝업(`WebTripModal`, 홈 검색칸·탐색 칩), 관광지 상세, 알림, 출발 준비 체크리스트, 모임방(모일 곳·아이 카드·일정), 내 정보
- 모임방의 가족 위치(지도) 탭은 웹에 없음. 내 정보의 비상연락처·여행 취향·가족·공지는 "준비 중"
- 확인: `npm run build` 통과. 확인 못 함: 브라우저에서 새 화면 직접 보기(지도·로그인 화면 포함)

## 2026-11-20 웹·앱 주소 분리: 모바일을 `/app` 으로
- 모바일 라우트를 `router/mobileRoutes.js` 로 옮기고 주소에 `/app` 접두사를 붙임 (`/explore` → `/app/explore`). 화면 파일 위치·라우트 이름은 그대로
- 옛 주소(`/explore`, `/camps/12?tab=tour` 등)는 쿼리를 유지한 채 `/app/...` 으로 자동 이동 (`legacyRedirects`) → 이미 공유한 링크·북마크 보호
- `/` 는 화면 폭으로 `/web` 또는 `/app/explore`. 좁은 화면에서 `/web/...` 로 오면 같은 모바일 화면으로 보냄 (웹에만 있는 이름은 대응표로: alerts→my-info/alerts, family→my-camping/family, my-booking→my-camping, checklist/:id→my-camping/:id/checklist)
- 모바일 화면·부품 25개 파일의 링크 69곳을 `/app/...` 으로 바꿈. 서버에는 화면 주소를 쓰는 곳이 없어 변경 없음
- 확인: `npm run build` 통과, 라우터만 따로 돌려 옛 주소 8개가 `/app` 으로 넘어가는 것 확인
- 확인 못 함: 브라우저에서 모바일 화면을 직접 눌러 보기 (특히 하단 탭, 뒤로가기, 로그인 후 이동)

## 2026-11-20 클라이언트 폴더·이름 규칙 통일 (웹 `Web…` / 모바일 `Mobile…`)
- 모바일 화면을 `views/mobile/Mobile○○View.vue`, 부품을 `components/mobile/Mobile○○.vue` 로 한 폴더에 모음 (웹의 `views/web/Web○○View.vue`, `components/web/Web○○.vue` 와 같은 규칙). `git mv` 로 옮겨 이력 유지, 파일 48개
- 컴포넌트 이름과 `import` 경로, 라우터(`mobileRoutes.js`)를 함께 고침. 화면 주소(`/app/...`)와 라우트 이름은 그대로
- 안 쓰던 Vue 기본 템플릿 파일(HelloWorld · TheWelcome · WelcomeItem · AboutView · HomeView · components/icons)은 삭제
- 위 옛 기록의 `views/camp/…` 같은 경로는 당시 이름이다 → 지금은 `views/mobile/Mobile…View.vue`
- 확인: `npm run build` 통과, 모바일 화면 13개 주소를 실제 브라우저로 열어 정상 표시 확인. 확인 못 함: 로그인 후 화면

## 2026-11-20 앱 비상연락처·함께 가는 가족 화면 삭제 / 웹 메뉴 정리
- 앱에서 `MobileEmergencyContactView`(비상연락처)와 `MobileTravelFamilyView`(함께 가는 가족) 화면, 라우트(`/app/my-info/emergency-contacts`, `/app/my-info/family`), 내 정보 메뉴 항목을 삭제
- `stores/family.js` 의 아이 카드 함수·`children` 목록 삭제 (이 화면만 쓰던 것). 모임방 기능은 그대로
- 문구 수정: 인원·날짜 시트의 "내 정보 › 함께 가는 가족에 저장됩니다" → "아이 나이대는 내 정보에 저장됩니다", 여행 취향 화면의 "함께 가는 가족에서 바꿀 수 있어요" 문장 삭제
- 웹: 전체 메뉴의 "공지사항 · 문의하기", 내 정보의 비상연락처 · 함께 가는 가족 · 공지사항 · 문의 항목 삭제 (최근 본 캠핑장 · 내 안전 설정은 앞서 삭제)
- 서버 API(`/api/member/emergency-contacts`, `/api/family/children`)와 DB 테이블(`emergency_contact`, `child_card`)은 그대로 둠 (삭제는 따로 정할 일)
- 확인: `npm run build` 통과, 삭제한 화면 이름이 코드에 남아 있지 않음. 확인 못 함: 브라우저에서 앱 내 정보 화면 직접 열어 보기

## 2026-11-20 웹 재난 대피 3D 지도 / 앱 공지사항·문의 삭제
- 웹 `/web/disaster` 추가 (`views/web/WebDisasterMapView.vue` + `components/web/WebDisasterPanel.vue`). 지도는 앱과 같은 `utils/disasterMap.js`(CesiumJS)를 그대로 쓰고, 앱에서 지도 위·아래에 흩어져 있던 제목·기상특보·비 슬라이더·대피 안내를 왼쪽 400px 패널 한 칸에 모음. 높낮이 그래프만 앱의 `MobileRouteProfile` 을 그대로 가져다 씀(화면 배치가 없는 그림이라)
- 웹 메뉴·내 정보·내 예약의 "3D 재난 대피 지도" 링크를 `/web/disaster` 로 연결 (`utils/webLinks.js`). 좁은 화면에서 `/web/disaster` 로 오면 기존 규칙대로 `/app/disaster` 로 감
- 앱 내 정보의 "공지사항 · 문의" 항목 삭제 (로그인 전·후 모두)
- 확인: `npm run build` 통과. 실제 Chrome(1400px)에서 지도·패널·비 슬라이더·높낮이 그래프 표시, "대피 길안내 시작" 눌러 단계 안내로 바뀜, 콘솔 오류 없음, 390px 에서 `/app/disaster` 로 이동
- 확인 못 함: 길안내 단계에서 지도 바탕이 하얗게 보인 것이 테스트용 Chrome(소프트웨어 그래픽)의 타일 로딩 때문인지 (앱에도 같은 코드라 코드 문제는 아닐 것으로 보임), 사람 손으로 지도 돌리기·확대, 비 슬라이더로 물 차오르는 모양

## 2026-11-20 1분 진단 안내 팝업: 웹 위치·바탕 닫기, 앱에 같은 디자인 적용
- 웹 홈 팝업(`WebHomeView`): 위에서 150px 띄우던 배치를 가로·세로 정중앙으로 바꿈(화면이 낮으면 스크롤, 아래가 잘리지 않음). 어두운 바탕을 누르면 닫힘(카드 안쪽은 안 닫힘)
- 앱 탐색 화면의 진단 안내 팝업을 웹 디자인으로 교체: 새 `components/mobile/MobileSurveyNudge.vue`(390px 틀에 맞춰 줄임). 문구는 앱 진단이 5문항이라 "5문항 · 약 1분 · 로그인 없이". 버튼은 진단 시작하기 / 오늘 하루 보지 않기 / 닫기, 바탕 누르면 닫힘
- "닫기"·바탕 누르기는 이 탭에서만 안 뜸(sessionStorage), "오늘 하루 보지 않기"는 오늘 하루 안 뜸(localStorage 날짜). 뜨는 조건(진단 전에만)은 그대로
- 색은 `tokens.css` 에 `--color-nudge-*` 7개 추가 (웹 `tokens-web.css` 의 진단 팝업 값과 같음, 사용자 요청)
- 옛 앱 팝업 그림(`assets/illust/camping.svg`)은 이제 쓰는 곳이 없다 (파일은 남겨 둠)
- 확인: `npm run build` 통과, 실제 Chrome 에서 웹(1400×700)·앱(390×800) 팝업 표시와 바탕 클릭으로 닫힘 확인. 확인 못 함: "오늘 하루 보지 않기" 다음 날 다시 뜨는지, 아주 낮은 화면(400px 이하 높이)에서의 스크롤

## 2026-11-20 앱에 지난 캠핑 후기 · 예약 정보 확인 추가 (웹과 맞춤)
- 후기: 내 캠핑(`MobileMyCampingView`)의 지난 캠핑 카드에 "후기 쓰기 / 후기 수정하기" 버튼과 작성한 후기(사진 → 글 3줄 + 더보기 → 작성 날짜, 사진 누르면 크게 보기). 쓰기·수정 화면은 새 `components/mobile/MobileReviewSheet.vue` (390px 틀 전체를 덮음). 서버는 웹과 같은 API (`GET /api/member/camp-plans/reviews`, `POST /api/member/camp-plans/{id}/review`) — 서버 변경 없음
- 후기 규칙은 웹과 같음: 별점 없음, 글이나 사진 중 하나만 있어도 저장, 글 1000자 · 사진 9장 · 한 장 10MB · 이미지만. 쓰던 내용이 있는데 닫으면 확인 창
- 지난 캠핑 카드를 통째로 흐리게 하던 것(opacity)을 이름·날짜만 연하게로 바꿈 (후기가 흐려지지 않게)
- 예약 정보 확인: 새 `views/mobile/MobileBookingCheckView.vue`, 주소 `/app/booking-check?camp=캠핑장id`. 예약 여부 · 날짜 · 인원 · 동행 · 가는 수단을 확인해 내 캠핑에 저장 (웹과 같이 인원·동행·수단은 메모로 저장). 들어가는 길: 내 캠핑의 예약 전·중 카드(캠핑장 일정만)에 "예약 정보 확인 ›"
- 휴대폰 폭에서 `/web/booking-check` 로 들어오면 이제 `/app/booking-check` 로 이동 (전에는 탐색으로 갔음)
- 확인: `npm run build` 통과. 실제 Chrome(390px)에 테스트 계정으로 로그인해 내 캠핑 지난 카드·후기 쓰기 화면·예약 정보 확인 화면 표시와 오류 없음 확인. 저장은 공유 DB 에 글이 남아 누르지 않음
- 확인 못 함: 후기 저장·수정(사진 올리기 포함), 작성한 후기가 카드에 보이는 모양, 예약 정보 저장, 실제 휴대폰 사진 고르기

## 2026-11-22 캠핑 기간 날씨 카드에 날씨별 아이콘 · 색
- 앱 출발 준비(`MobileChecklistView`)의 날씨 카드가 전부 회색이던 것을 하늘·강수 종류별로 아이콘과 카드 바탕색을 다르게: 맑음(노랑) · 구름많음(해+회색 구름) · 흐림(회색) · 비/소나기(파랑) · 눈, 비/눈(청록). 강수가 있으면 하늘보다 강수 우선
- 웹 출발 준비(`WebChecklistView`)는 줄 목록이라 하늘 글씨 앞에 같은 아이콘만 붙임
- 새 부품 `components/common/WeatherIcon.vue`(웹·앱 공용), 종류 정하는 `utils/weather.js`. 색은 `tokens.css` 에 `--color-wx-*` 로 추가 (임시 팔레트 — 디자인 확정되면 값만 바꾸면 됨). 서버 변경 없음
- 확인: `npm run build` 통과. 확인 못 함: 실제 화면에서 아이콘 모양·색 대비 (로그인이 필요한 화면이라 눈으로 못 봄), 눈·비 예보가 있는 날의 표시
- (같은 날) 앱 날씨 카드: 3일을 가로 한 줄로(4일 이상은 옆으로 넘김, 스크롤바 숨김), 카드 밑 "기상청 단기예보 · 캠핑장 위치 기준 · 아침 최저 / 낮 최고" 글 삭제, 앱 틀(`MobileLayout`)의 세로 스크롤바 숨김 (휠·터치 스크롤은 그대로). 웹 날씨 카드의 같은 안내 글은 그대로 둠
- (같은 날) 앱 스크롤바 숨김을 틀 하나가 아니라 `styles/base.css` 의 `.frame, .frame *` 전역 규칙으로 바꿈 — 화면마다 자기 스크롤 영역(`.scroll`)이 따로 있어서 틀만으로는 안 숨겨졌음. 마우스 기기의 가로 칩 줄(`.hscroll`) 막대는 그대로 보임. 확인: 헤드리스 Chrome 에서 스크롤이 생기는 앱 화면의 `.scroll` 막대 폭 0
- (같은 날) 앱 출발 준비 맨 밑 안내 글("체크한 항목은 이 기기(브라우저)에만 저장돼요 · 특보는 기상청 특보 현황 기준 참고용이에요") 삭제. 웹 출발 준비의 같은 글은 그대로 둠
- (같은 날) 앱 출발 준비(`MobileChecklistView`) 디자인 손봄: 맨 위 "준비 3/9" 진행 막대 추가(체크한 항목 수, 지난 캠핑은 숨김), 항목 묶음을 둥근 카드로(제목 옆 종류 색 점 + 체크한 개수 "2/3", 다 하면 초록), 체크칸 네모→동그라미, 특보 배너 왼쪽 색 띠, 안전 정보·안내 상자 테두리 대신 연한 바탕, 날씨 제목 크게·테두리 상자 없앰. 색은 기존 변수만(새 색 없음). 서버·기능 변경 없음. 확인: `npm run build` 통과 — 로그인이 필요한 화면이라 실제 모양은 눈으로 못 봄
- (같은 날) 앱 출발 준비의 특보 배너(건조경보·주의보 등)를 접고 펼 수 있게: 제목("○○ 발효 중")은 늘 보이고 긴 지역 목록은 제목을 눌러 펼침, 처음엔 접힘. 확인: `npm run build` 통과 (화면은 로그인이 필요해 눈으로 못 봄)
- (같은 날) 출발 준비: 특보 배너 왼쪽 색 띠와 묶음 제목 앞 색 점 삭제, 묶음 이름 "특보 대응·날씨 대응" → "특보 대비·날씨 대비" (앱·웹). 계절 기준은 서버 `ChecklistItems.seasonOf` — 체크인 월 기준 봄 3~5 · 여름 6~8 · 가을 9~11 · 겨울 12~2 (기온·지역은 안 봄)

## 2026-11-22 출발 준비: 온도로 정하는 준비물을 계절에서 날씨 묶음으로
- 침낭 · 핫팩 · 보온 매트 · 일산화탄소 경보기 · 물통 동파 주의는 계절(가을·겨울)로 정하지 않고 예보 최저기온으로: 최저 -12° 이하(기상청 한파주의보 발표 기준 온도)면 핫팩 · 일산화탄소 경보기, 0° 이하(물이 어는 온도)면 동파 주의 · 침낭 쾌적온도 확인 (-12° 이하면 둘 다). 출처 있는 두 기준만 쓰고 새 온도 구간은 만들지 않음
- 계절 묶음은 월 기준 그대로 두되 온도 항목만 뺌: 겉옷(일교차) · 벌레 퇴치제 · 마스크 · 그늘막 · 아이스박스 · 우비 · 화기 확인은 계절에 남음. 겨울은 남는 항목이 없어 계절 묶음이 안 보임
- 화면 묶음 이름: "가을 준비물·겨울 준비물" → "계절 대비" (앱·웹). 서버 `ChecklistItems`·`ChecklistServiceTest`(32개 통과, 새 테스트 2개)·`server/CLAUDE.md` 반영. 항목 id 변경: 침낭 `wx-cold-3`·`season-*-bag` 삭제 → `wx-freeze-2` (그 항목에 해 둔 체크는 풀림)
- 달라지는 점: 예보가 없는 먼 일정(출발 3일 전보다 이전)에는 침낭·핫팩·경보기·동파 항목이 아예 안 나옴 (온도를 모르면 권하지 않음). 한파·강풍 등 특보 항목은 그대로
- 확인: `ChecklistServiceTest` 통과, `npm run build` 통과. 확인 못 함: 전체 서버 테스트(`ServerApplicationTests` 는 공유 DB 접속이라 안 돌림), 실제 화면
- (같은 날) 앱 출발 준비: "준비 0/13" 진행 막대 삭제, 안전 정보 줄의 "응급실 차로 ○분" 삭제. 날씨 카드 바탕을 모두 연한 회색으로 돌리고 날씨 색은 작은 동그란 아이콘 배경에만 남김 (특보 배너의 빨강·주황과 비슷해 보이던 것을 구분). 확인: `npm run build` 통과 (화면은 눈으로 못 봄)
- (같은 날) 출발 준비 문구를 "~요"에서 "~니다"체로 통일: 서버가 만드는 항목 설명(`ChecklistItems` — 쌀쌀합니다 · 쉽습니다 · 비교하십시오 · 얼 수 있습니다)과 앱·웹 체크리스트 안내문(로그인 안내, 지난 캠핑, 특보·날씨를 받지 못했습니다, 특보 없음, ○월 ○일부터 보입니다, 웹 맨 밑 안내 등). `ChecklistServiceTest` 문구 검사도 맞춤(32개 통과), `npm run build` 통과. 이 화면 밖의 다른 화면 문구는 그대로
- (같은 날) 출발 준비 항목 아래 작은 글을 단답으로: "11.22 예보 최저 -10° · 0° 이하" · 침낭 "쾌적온도 비교 -10° 이하" · 한파 "11.19 예보 최저 -12° · 한파주의보 기준 이하" · "아침저녁 쌀쌀" · "밀폐되기 쉬움" (문장 끝맺음 없음). 서버 `ChecklistItems`·`ChecklistServiceTest`(32개 통과)·`server/CLAUDE.md` 반영, 서버를 다시 켜야 보임
- (같은 날) 앱 출발 준비: 안전 정보 줄(등급 배지 · 캠핑장 상세 링크)을 키움(글씨 12→15~16px, 배지·여백 확대), 항목 묶음 카드 테두리를 1px 연한 줄에서 1.5px 짙은 줄(`--color-border`)로. 확인: `npm run build` 통과 (화면은 눈으로 못 봄)
- (같은 날) 앱 차박 화면 목록 머리의 "지도를 누르면 그 자리를 판정해요" 글씨를 회색에서 기본 글씨색으로. 확인: `npm run build` 통과
- (같은 날) 앱 차박 화면: 목록 머리의 "안전점수순 / 응급실 가까운순" 정렬 선택 삭제 → 항상 안전점수 높은 순(같으면 응급실 가까운 곳 먼저). 응급실 거리는 안전점수에 이미 들어 있고 후보지 값은 직선 어림이라서. 탐색 화면의 정렬은 그대로. 또 가로 칩 줄(`.hscroll`)의 마우스 기기용 얇은 막대를 삭제해 앱 안의 스크롤 막대를 모두 숨김(탐색·차박·캠핑장 상세 칩 줄 포함, 휠·터치로는 그대로 넘어감). 확인: `npm run build` 통과
- (같은 날) 앱 차박 안전점수 필터: 칩·시트 이름 "주변 안전" → "안전점수"(앱의 다른 곳과 같은 이름), 선택지 "주의 이상" → "위험 제외"(40↑, 주의+안전), "안전만"(70↑)은 그대로. 시트 선택지 앞에 등급 색 점(위험 제외=주황 `--color-caution`, 안전만=초록 `--color-green`, 전체는 점 없음 — 새 색 없음). 판정 카드의 "주변 안전" 줄은 다른 항목이라 그대로. 이 시트는 차박 화면 전용이라 탐색 화면 영향 없음. 확인: `npm run build` 통과
- (같은 날) 앱 차박 안전점수 시트의 안내 문장("갈 수 있는 곳을 안전점수로 걸러요 · 캠핑장 안전점수와 같은 기준") 삭제 (제목·선택지로 충분). "지도에 구역 표시" 시트의 안내 문장은 그대로. 확인: `npm run build` 통과
- (같은 날) 앱 차박 목록: 머리의 "지도를 누르면 그 자리를 판정해요" 삭제, 칩이 모두 꺼졌을 때 안내를 "차박 후보지 · 등록 야영장을 눌러 목록을 확인하세요"로. 확인: `npm run build` 통과
- (같은 날) 앱 차박 목록: 칩이 모두 꺼졌을 때 안내를 "차박 후보지 · 등록 야영장을 눌러 목록 확인"으로(끝맺음 뺌). 차박 후보지 줄의 "응급실 약 ○분" 삭제 — 직선거리 어림값이고 안전점수에 이미 들어 있어서. 등록 야영장 줄의 "응급실 차로 ○분"(카카오 값, 탐색 목록과 같음)은 그대로. 확인: `npm run build` 통과
- (같은 날) 앱 차박 지도: 등록 야영장 표시를 점에서 탐색 지도와 같은 텐트 아이콘 배지(`facilityIcon.js` 의 `camp`)로. 바탕색은 기존 판정 색(야영 가능 파랑) 그대로, 누르는 범위(44px)와 동작은 점과 같음. 차박 후보지는 지금처럼 점(개수가 많아 겹침·속도 때문에 보류). 확인: `npm run build` 통과 (지도는 눈으로 못 봄)
- (같은 날) 앱 차박 지도: 차박 후보지를 지도를 확대했을 때(카카오 레벨 5 이하)만 자동차 아이콘 배지(초록, `facilityIcon.js` 에 Lucide car 추가)로 보이고 멀리서는 지금처럼 점(개수가 최대 1000곳이라 겹침·속도 때문). 확대·축소로 그 기준을 넘나들 때만 후보지를 다시 그림. 등록 야영장 텐트 배지와 함께 아이콘 배지 만드는 코드를 하나(`layerIconEl`)로 합침. 아이콘 모양은 정적 HTML 로 렌더링해 확인(지도 위 겹침은 못 봄)
- (같은 날) 앱 차박 판정 카드 정리: 위 딱지·제목과 겹치던 "차박 규제 ○○" 줄 삭제, "주변 안전" → "안전점수", 시설·근처 후보 안내 글을 짧게(가까운 순 · 시간은 직선거리 어림값 / 공공데이터상 제한 없는 국공유지). 법적 안내 문구(공공데이터 기준 참고용…)는 그대로. 확인: `npm run build` 통과
- (같은 날) 앱 차박 지도 아이콘 배지 키움: 차박 후보지 자동차 26→36px, 등록 야영장 텐트 30→40px, 배지 안 그림 64→70%. 누르는 범위(44px 버튼)는 그대로. 확인: `npm run build` 통과 (지도 위 겹침은 못 봄)
- (같은 날) 앱 차박 지도 아이콘 조정: 등록 야영장 텐트는 원래 크기(30px, 그림 64%)로 되돌림. 차박 후보지는 확대해도 점으로만 보이던 문제라서 아이콘이 나오는 확대 단계를 레벨 5 → 7(화면 폭 2km 안팎)로 넓히고 자동차 배지는 36px·그림 72%. 한 화면에 후보지가 너무 많아 겹치면 기준 숫자(`ICON_LEVEL`)만 조정. 확인: `npm run build` 통과 (지도 위 모습은 못 봄)
- (같은 날) 앱 차박 판정 카드 문구 정리: "제한 없음" 판정 제목 "공공데이터상 제한이 없는 곳이에요" → "공공데이터상 제한 없음", 가까운 시설 목록 위 안내 "가까운 순 · 시간은 직선거리 어림값" 삭제(줄의 "차로 약 ○분"의 "약"은 그대로), 맨 아래 안내를 "공공데이터 기준 참고용 판정이며 법적 경계는 지적측량으로 확정"까지만(뒤의 "지금은 인제군만 판정해요" 삭제). 확인: `npm run build` 통과
- (같은 날) 앱 차박 판정 제목을 짧게 통일: 불가 "차박 불가" · 주의 "확인 필요" · 제한 없음 "공공데이터상 제한 없음" · 야영 가능 "등록된 야영장". 확인: `npm run build` 통과

## 2026-11-22 웹 1분 진단: 침수 예측지도 묶음 삭제, 이동수단만 남김 (10문항 → 8문항)
- `utils/webSurvey.js`: "위급할 때는요?"(침수 예측지도) 묶음과 8번(도착하면 먼저 볼 것)·9번(119에 먼저 말할 것) 삭제. 10번 "위급할 때 차로 이동할 수 있나요?"(`car`)만 안전지도 묶음의 5번째 질문으로 옮김. 메타버스 퀴즈는 3문항(난로·화로·계곡물)
- 점수 분야가 5개 → 3개(일산화탄소 · 화재 · 물·호우): 막대 3개, 유형은 3종 + "든든 대비형", "조심할 것" 카드 3개. "대피 준비"·"신고" 유형·조심할 것 문구와, 그 분야에만 영향을 주던 위험 가중(혼자·어르신·처음 캠핑)은 삭제. 체크리스트의 "소화기·대피로 위치 확인", "사이트 번호 메모", "119 신고 순서"는 그대로(분야가 없어져 "꼭" 표시만 안 붙음). 조심할 것 맨 밑 "위급하면 바로 119" 카드도 그대로
- 문항 수 표기 "10문항" → 웹 진단 화면은 질문 개수를 따라가게(`ALLQ.length`), 웹 홈 말풍선은 "8문항". 앱 진단은 5문항 그대로(웹·앱 불일치는 보류 중 — 서버 저장은 둘을 맞춘 뒤)
- 확인: 샘플 답 2가지로 결과 계산 정상(오류 없음), `npm run build` 통과. 확인 못 함: 실제 화면 모양
- 알아 둘 것: 이동수단(`car`) 답은 지금 결과 화면 어디에도 쓰이지 않음(키워드 앞 3개에만 들어가고 4번째 태그는 안 보임). 추천 캠핑장도 답과 상관없이 안전점수 상위 5곳

## 2026-11-22 앱 1분 진단을 웹과 같은 8문항으로 (클라이언트만, 서버·DB 변경 없음)
- 앱 진단(`MobileSurveyView`)을 웹과 같은 질문 8개로 교체: ① 어떤 캠핑을 가나요? (누구와 · 어디서 · 언제 · 경험 · 이동수단, 아이를 고르면 나이대) ② 이럴 땐 어떻게 할까요? (난로 · 화로 · 계곡물 퀴즈). 질문·선택지·결과 계산은 웹과 같은 `utils/webSurvey.js` 를 공유해 앞으로 웹·앱이 항상 같음 (새 도우미 `answerTags` · `campTypeOf`)
- 답 저장(`stores/survey.js`): 서버 대신 이 브라우저 — 로그인했으면 웹 진단과 같은 사용자별 저장소(`web-survey-saved`)라 웹에서 한 진단이 앱에도 보임, 안 했으면 탭(sessionStorage). 서버 `/api/survey`·`survey_answer`·`SurveyQuestions` 는 지우지 않고 앱이 안 부름. 결과 화면은 만들지 않고 내 정보 카드에 요약(예: "아이 동반 · 영유아 · 오토캠핑 텐트 · 여름 · 첫 캠핑 · 차량 대피")만
- 탐색 추천 연결 유지: "어디서 주무세요?" 답을 캠핑 유형으로 변환해 `/api/camps/recommend?campType=` 에 넘김 (글램핑 → glamping, 오토캠핑·차박 → auto). 노지·백패킹은 진단에 없어져 진단으로는 정해지지 않음. 추천 제목 밑 문구는 옛 답(난방 · 시설 순위)을 읽던 것을 실제 반영하는 것(캠핑 유형 + 안전점수 순)만 말하게 바꿈
- 문구: 앱 팝업·내 정보 카드의 "5문항"·"3단계" → "8문항"·"2단계"
- 영향: 앱에서 이미 서버에 저장해 둔 옛 진단 답은 안 읽혀서 그 사용자에게는 "진단 안 한 상태"(팝업 다시 뜸)
- 확인: `npm run build` 통과, 헤드리스 Chrome(390px)에서 8문항 전체를 눌러 보며 2단계 이동·아이 나이대 입력란·미답 시 안내·마치기 후 탐색 이동·저장값 확인, 화면 캡처 확인(콘솔 오류 없음). 확인 못 함: 로그인 상태의 사용자별 저장·웹↔앱 공유, 탐색 추천 목록이 새 유형으로 거르는 것

## 2026-11-22 웹 홈 "전국 안전 캠핑장 순위"를 실제 데이터로
- 손으로 적어 둔 8곳(`NATION`, 점수 94·92… 더미)을 지우고 서버 `GET /api/camps?sort=score` 의 안전점수 상위 8곳(이름·점수·`imageUrl`)으로 연결. 카드를 누르면 그 캠핑장 상세(`/web/camps/:id`)로 이동(예전엔 탐색). 사진 주소가 없는 캠핑장은 회색 사진 칸 그대로(사진은 DB `camp.image_url` 에 `https://` 주소가 있는 곳만). 못 받으면 예시 점수를 대신 보여 주지 않고 "순위를 불러오지 못했어요"
- 같은 화면의 "내 근처" 캠핑장 3곳·공지사항은 아직 더미. 확인: `npm run build` 통과. 확인 못 함: 실제 순위·사진 표시(공유 DB 를 읽는 서버 호출이라 직접 안 돌림 — 새로고침해서 확인)

## 2026-11-23 웹 1분 진단 문구 · 추천 유형 거름 / 앱 내 캠핑 바로가기
- 웹 진단(`webSurvey.js`, `WebSurveyView.vue`): 퀴즈 묶음의 "메타버스" 칩·문구를 지우고 "선택에 따라 조심해야 할 것이 달라집니다", 첫 묶음 문구는 "주무시는 방식에 맞는 캠핑장을 골라 드려요". 추천 캠핑장을 "어디서 주무세요?" 답으로 거름(`campTypeOf` → `/api/camps?campType=`, 글램핑 2곳·오토 21곳). 다시 진단하면 고른 답은 그대로 두고 추천만 다시 받음. 0곳이면 "찾지 못했어요" 안내
- 앱 내 캠핑(`MobileMyCampingView.vue`): 웹 내 예약 카드처럼 지나지 않은 일정 카드에 "메타버스 안전 탐험"(`/metaverse`) · "3D 재난 대피 지도"(`/app/disaster`) 바로가기
- 진단 유형별 안전점수(지도에서 켜고 끄기)는 규칙 합의 전 — 비교안은 워크스페이스 `Claude outputs/진단유형별_안전점수_방식비교.md`
- 확인: `npm run build` 통과. 확인 못 함: 화면에서 직접 누르기
- 가족 캠핑 공유: 서버 `GET /api/family/camp-plans` (새 `family/FamilyCampPlan*`, `FamilyService.findMyRoomOrNull`) — 같은 가족방 다른 사람의 `camp_plan` 을 읽기만 (DB 구조 변경 없음, 방에 있으면 자동 공유, 방 없으면 빈 목록). 테스트 `FamilyCampPlanServiceTest` 2개. 앱 내 캠핑에 "가족 캠핑" 묶음(지나지 않은 일정만, "○○님이 등록", 삭제·출발 준비 없음, 메타버스·재난지도 바로가기). 확인: 서버 테스트 183개 통과, `npm run build` 통과. 확인 못 함: 실제 DB 로 두 계정 화면 확인, 웹 내 예약에는 아직 없음
- 가족 일정 출발 준비: `GET /api/member/camp-plans/{id}/checklist` 를 같은 가족방 사람도 볼 수 있게 (`CampPlanMapper.findVisible` — 내 것 또는 같은 방 사람 것, 그 밖은 404 그대로). 고치기·지우기·후기는 여전히 본인만(`findById`). 앱 가족 캠핑 카드에 "출발 준비" 줄. 체크 표시는 보는 사람 브라우저에 따로. 테스트 1개 추가, 서버 테스트 184개 통과 · `npm run build` 통과. 확인 못 함: 실제 DB 두 계정
- 웹 내 예약에도 "가족 캠핑" 묶음 (다가오는 캠핑 탭 아래, "○○님의 캠핑", 사진·안전점수·날짜·메모, 안전 점검 체크리스트·주변 안전시설·메타버스·재난지도. 삭제·예약 정보 확인 없음). `npm run build` 통과, 화면 확인 못 함
- 웹 내 예약 "다가오는 캠핑" 탭 맨 아래에 "가족 모임방" 버튼(/web/family, 앱 내 캠핑 하단 버튼과 같은 역할). 내 일정이 없어도 보임. 색은 tokens-web 변수만. `npm run build` 통과, 화면 확인 못 함
- 이름 바꿈: 화면 글자 "가족 모임방"·"가족방" → "모임방", "가족 캠핑" → "모임방 캠핑", 웹 모임방의 "가족 일정" → "모임방 일정" (웹·앱 내 캠핑·내 예약·모임방 화면, 위치 동의 문구, 서버 오류 문구 FamilyRoomRequest·FamilyService). 코드·주소(family, /web/family)는 그대로. 서버 테스트 184개 · `npm run build` 통과
- 앱 모임방 소개 문구에서 "아이 정보" 삭제 (앱에 아이 정보 탭이 없어 문구만 남아 있었음) → "위치, 모일 곳, 일정"
- 웹 화면 글자 "내 예약" → "내 캠핑" (헤더 메뉴·햄버거 메뉴·내 정보 목록·내 캠핑 화면 제목·체크리스트 뒤로가기·예약 정보 확인 버튼·삭제 확인 문구). 앱 이름과 맞춤. 주소(/web/my-booking)·파일 이름(WebMyBookingView)은 그대로. `npm run build` 통과
- 웹 헤더 오른쪽(로그인 앞)에 "앱 화면" 버튼 (시연용 웹 → 앱 전환). 같은 화면의 앱 주소로(내 캠핑 → /app/my-camping, 모임방 → /app/my-camping/family, 알림 → /app/my-info/alerts 등), 없으면 /app/explore. 웹→앱 대응표를 `utils/webLinks.js` 의 `webToAppPath` 한곳으로 옮기고 router/index.js 도 이걸 씀. `npm run build` 통과. 앱 → 웹 버튼은 아직 없음
- 웹 헤더: "앱 화면" 글자 버튼을 없애고, 기능 없던 검색 버튼 자리에 휴대폰 아이콘만 있는 전환 버튼(로그인 · [앱] · 알림 · 메뉴). 검색 버튼 삭제. `npm run build` 통과
- 앱 로그인 화면(로그아웃 상태) 오른쪽 위에 웹 전환 버튼(모니터 아이콘만, /web/login). PC 처럼 넓은 창(768px 이상)에서만 보임 — 좁으면 /web 이 앱으로 되돌아와서. `npm run build` 통과

## 2026-11-23 진단 유형 점수 (응급실 가산점) — 웹·앱 탐색 지도
- 규칙(팀 결정): 진단에서 아이·어르신·차박·겨울·걸어서 중 하나라도 고르면, 응급실이 가까운 캠핑장일수록 최대 +5점 (30분 안 +5 → 60분 넘으면 0, 비례·반올림, 겹쳐도 한 번). 저장된 안전점수·등급은 그대로, 화면에 "79 +5" 로 따로. 봄·여름·가을·경험·퀴즈는 가산점 없음. 이동 답은 가산점에만 쓰고 조심할 것·대비할 것에는 안 씀. 비교 문서: 워크스페이스 `Claude outputs/진단유형별_안전점수_방식비교.md`
- 서버: `ScoreRules.ER_BONUS_MAX`(5) · `erBonus(분)`, `CampResponse.erBonus`, `/api/camps`·`/recommend` 에 `sort=bonus`(안전점수+가산점). DB 변경 없음(`camp.er_drive_min` 사용). 테스트 `ScoreRulesBonusTest` 3 · `CampServiceBonusTest` 2
- 클라이언트: `webSurvey.js` 의 `erBonusReasons`·`needsErBonus`, `stores/survey` 의 `bonusOn`(진단을 마치면 켜짐), 웹 진단을 마치면 답을 `stores/survey` 에도 저장(지도가 읽게). 앱 탐색: "내 유형 점수" 칩 · 안전점수순일 때 bonus 정렬 · 목록/마커에 "+N" · 이유 한 줄. 웹 탐색: "내 유형 점수 켜짐/꺼짐" 버튼 · 추천 TOP 5·목록 bonus 정렬 · 목록/마커/정보 카드에 "+N"
- 캠핑장 상세(웹 패널·앱): `/api/camps/{id}` 에도 `erBonus`, 점수 옆 줄에 "내 유형 점수 +5 · 아이 동반 …" (켜짐 · 해당 답일 때만). 다른 화면(홈 순위·저장 비교·내 캠핑·체크리스트)과 차박 후보지는 기본 점수 그대로
- 확인: 서버 테스트 189개 통과, `npm run build` 통과. 확인 못 함: 실제 화면(서버를 다시 켜야 `erBonus` 가 나옴)
- 캠핑장 상세(웹·앱)의 "내 유형 점수" 줄에 끄기/켜기 버튼 (탐색과 같은 상태). 문구 "…이라" → "…에 해당해" (예: 걸어서 대피이라 → 걸어서 대피에 해당해). `npm run build` 통과
- 캠핑장 상세(웹·앱) 점수 원을 누르면 내 유형 점수 켜기/끄기 (켜면 원에 "76 / 71 +5", 끄면 "71 / 안심", 색은 저장된 등급 그대로, 원 바깥 테두리 + 아래 "내 유형 ON/OFF" 꼬리표). 줄의 끄기/켜기 버튼은 없애고 설명만. 가산점이 없는 사람·캠핑장은 원이 버튼이 아님. `npm run build` 통과
- 웹은 "+5" 를 따로 붙이지 않고 내 유형 점수를 켜면 점수 숫자 자체가 바뀜 (탐색 목록·핀·정보 카드·상세 원: 71 → 76). 등급 색·글자는 저장된 점수 그대로, 상세 설명 줄에 "안전점수 71" 을 남김. 앱은 그대로 "+5" 표시
- 앱도 웹과 같이 "+5" 대신 점수 숫자 자체가 바뀜 (탐색 목록·마커·상세 원 71 → 76, 상세 설명 줄에 "안전점수 71"). MobileCampRow·MobileCampMap 의 +N 표시 제거. `npm run build` 통과
- 앱 탐색 "내 유형 맞춤" · "내 유형 점수" 두 칩을 "내 유형" 하나로 합침: 켜면 추천 TOP 5(유형 거름·위험 뺌) + 내 유형 점수(가산점 더한 숫자·정렬), 끄면 전체 목록 + 기본 점수. 상태는 stores/survey 의 bonusOn 하나(상세 점수 원과 같이 움직임). `npm run build` 통과
- 웹 캠핑장 상세의 "리뷰" 탭 삭제 (기능 없음, "준비 중"만 보였음). 앱 상세엔 원래 없음. 웹 1분 진단 결과의 분야별 막대(일산화탄소·화재·물호우 점수) 삭제 → 유형 카드는 유형 이름·한 줄·해시태그만. 웹 "내 유형 점수" 버튼 구조는 그대로 둠(사용자 결정). `npm run build` 통과

## 2026-11-23 재난지도: 특보 카드 가림 · 거리 요약 줄 · 높낮이 그래프 정리 (앱·웹, 클라이언트만)
- 앱: 특보 알림(`MobileAlertPill`)을 누르면 펼쳐지는 설명 카드가 강수량 막대(z-index 6) 밑에 깔려 가려지던 것 → 펼친 동안만 알림을 막대 위(8)로. `MobileDisasterMapView` 의 `topbar` 에서 z-index 를 빼고 제목(`.place`)과 알림(`.alert`)에 따로 줌(막대 위로 제목·그림자가 덮이지 않게). 웹은 특보가 패널 안에 펼쳐져 있어 해당 없음
- 앱·웹: 단계 제목 밑 작은 글 "서화중앙교회까지 1.41km · 도보 약 21분"(`state.dest.sum`) 삭제 — 아래 대피소 칸에 거리·시간이 이미 있어 겹침
- 앱·웹: 대피 길 높낮이 그래프 삭제. `MobileRouteProfile.vue` 파일과 `disasterMap.js` 의 그래프 계산(`updateProfile`·`profileBase`·`waterAt`·`state.profile`) 함께 지움
- 웹 패널(`WebDisasterPanel.vue`)은 팀원이 만든 파일이지만 사용자 요청으로 위 두 줄만 손댐
- 확인: `npm run build` 통과, 브라우저(앱 375px·웹)에서 85mm로 특보 카드가 막대 위에 보임·요약 줄·그래프 없음 확인. 확인 못 함: 실제 휴대폰(iOS Safari 15.4 미만은 `:has` 미지원이라 카드가 예전처럼 가려질 수 있음)

## 2026-11-23 웹 재난지도를 앱과 똑같이 맞춤 (클라이언트만)
- 웹 화면은 실시간을 받지 않아 특보 칸이 "확인 중"에 멈춰 있었음 → 앱 화면에 있던 실시간·특보 코드(`/api/disaster/now` 10분마다, 특보 칸, 미리 보기 예시 특보)를 `utils/disasterLive.js`(`useDisasterLive`)로 빼서 앱(`MobileDisasterMapView`)·웹(`WebDisasterMapView`)이 같이 씀. 앱 동작은 그대로
- 웹 패널(`WebDisasterPanel.vue`)을 앱 시트·막대와 같게: 장소 줄 "· 인제군 서화면" 삭제, "높은 곳 (참고)" → "산 위 높은 곳", "{단계} 단계 행동요령", 119 `tel:` 링크(눌러서 전화), 길안내 "…가 잠겼어요 · 산으로", 침수(114mm 이상)에는 경로·목적지·길안내 숨김, 막대 글 "3시간 강수량"·관측 시각 줄·긴 눈금 겹침 방지, "AI 판단/기준표" 표시 삭제(앱에 없음), 특보 칸 색(특보 없음 = 안전 색, 못 받음 = 회색)
- 확인: `npm run build` 통과, 브라우저에서 웹(실황 11.23 10:00 관측 0mm 안전 · 95mm 대피 · 120mm 경로 숨김)·앱(375px 실황 표시) 확인. 콘솔 401 은 로그인 안 한 `/api/auth/me` 라 무관

## 2026-11-23 재난지도: 현상황 보기 · 카카오맵 길찾기 · 비 효과 (앱·웹, 클라이언트만)
- 현상황 보기: 막대로 미리 보는 중이고 받아 둔 실황이 있으면 막대 칸의 관측 시각 줄 자리에 "미리 보기 중 [↺ 현상황 보기]". 누르면 마지막으로 받은 `/api/disaster/now` 실황으로 돌아감(`useDisasterLive().backToLive`). 실황을 못 받았으면 버튼 없음. 앱 `MobileRainSlider`·웹 `WebDisasterPanel` 같은 모양(`emit('live')`)
- 카카오맵 길찾기: 대피소 칸 "위치 보기" 옆 버튼. 대피소 경로(안전·준비)일 때만, 산 대피·침수에는 없음(정해진 길이 없어서). `state.dest.navUrl` = `map.kakao.com/link/by/walk/캠핑장,위도,경도/서화중앙교회,위도,경도` (걷기, 새 창). 열어서 도보 18분·1.2km 경로 나오는 것 확인 — 카카오가 고른 길이라 우리 지도의 1.41km 경로와 다를 수 있음
- 비 효과: 지도 위 투명 캔버스에 옅은 빗줄기(`rainFx`, disasterMap.js). 3시간 강수량에 따라 줄기 수만 바뀜(5mm 미만 없음 → 최대 160mm 에서 90개, 투명도 0.22). 움직임 줄이기 설정이면 그리지 않음. 색은 `--color-dm-text`
- 확인: `npm run build` 통과, 브라우저(앱 375px·웹)에서 95·150mm 빗줄기, 현상황 보기로 0mm 실황 복귀, 카카오 링크(40mm 있음·150mm 없음) 확인. 확인 못 함: 실제 휴대폰에서 카카오맵 앱으로 열리는지, 빗줄기가 폰에서 느려지지 않는지

## 2026-11-23 재난지도: 버튼 정리 · 비 효과 진하게 (앱·웹)
- "▶ 대피 경로 따라가기"(캐릭터가 경로를 걷는 미리 보기) 버튼 삭제 → 그 자리에 "카카오맵 길찾기" 버튼(대피소 경로일 때만, 대피 단계에는 없음). 대피소 칸 "위치 보기" 옆의 작은 카카오 버튼은 뺌. 지도 쪽 `toggleTour` 동작은 코드에 남아 있지만 부르는 버튼은 없음
- 큰 버튼 글자 "대피 길안내 시작"·"대피 경로 미리 보기" → 전 단계 "대피경로 보기" (`LV[].cta`)
- 비 효과를 더 잘 보이게: 최대 줄기 90 → 170개, 투명도 0.22 → 0.38, 굵기 1 → 1.3, 길이 조금 길게
- 확인: `npm run build` 통과, 브라우저 웹·앱(375px)에서 70mm(대피경로 보기 + 카카오맵 길찾기)·95mm(대피경로 보기만), 빗줄기 확인
- (이어서) 경로 따라가기 기능을 코드에서도 삭제: `disasterMap.js` 의 `startTour`·`stopTour`·`toggleTour`·`tour` 프레임 처리·`tourSec`, 화면 상태 `tourOn`·`meU`(옛 높낮이 그래프 점), 앱·웹 막대의 `v-show="!state.tourOn"`. 길안내(대피경로 보기 → 단계별 이동·캐릭터 걷기)는 그대로. 확인: 빌드 통과, 앱(375px)·웹에서 대피경로 보기 → 다음 → 길안내 종료 동작, 콘솔 오류 없음

## 2026-11-23 재난지도: 기상청 호우특보로도 단계를 올림 (서버)
- 문제: 단계가 이미 내린 비(실황, 1시간 단위·약 40분 늦게 나옴)와 상류 수위로만 정해져서, 호우경보가 발효돼도 비가 아직 덜 왔으면 화면이 "안전"으로 나올 수 있었음 (특보는 화면에 보여 주기만 함)
- 수정: `DisasterRules.judgeWarnings` 추가 — 인제에 걸린 호우주의보 → 1 준비, 호우경보 → 2 대피. 최종 단계 = 비 · 수위 · 호우특보 중 가장 높은 쪽 (`DisasterService`). 특보는 기상청이 "3시간 60·90mm 이상 예상" 때 내리는 예보 기반 경고라 새 숫자 기준은 없음
- 호우특보가 없을 때는 판단 재료에서 뺌(특보가 없다고 "안전"이라 하지 않음). 한파·건조 등 다른 특보는 단계와 상관없음. 까닭 줄에 "기상청 호우경보 발효"가 나와 앱·웹 막대 아래 관측 줄에 함께 보임 (화면 코드 변경 없음, `disasterLive.js` 주석만)
- 확인: 서버 테스트 185개 통과(재난지도 15개, 새 테스트 4개: 특보만으로 단계 올림 · 비가 더 크면 비를 따름 · 호우 아닌 특보는 무관 · 특보가 없다고 안전이라 하지 않음). 확인 못 함: 실제 호우특보 발효 중 화면(지금은 특보 없음)

## 2026-11-24 차박 서버 주석 정리 (동작 변경 없음)
- "캠핑장 안전점수와 같은 계산" 주석을 바로잡음: 차박 /score·후보지 점수는 직선거리 + raw.score_weights(예전 방식), 지금 camp.safety_score 는 카카오 차 시간 + ScoreRules → 기준이 다름 (ChabakController·Service·Mapper·ScoreResponse·LayerItem, server/CLAUDE.md). layer 응답 칸 목록 갱신, 없는 06_shared_db_after_restore.sql 언급을 README 절차로. 컴파일 확인
- 웹 내 주변·저장 지도의 기상특보 띠(WebWeatherBar, "강원 · 기상특보 · 확인 못 함") 삭제 (특보 조회 코드도 같이 뺌, 부품 파일은 남김). 차박지 찾기 전환 버튼을 공통 부품 `components/web/WebChabakToggle.vue` 로 빼서 검색·내 주변·저장 지도 오른쪽 위에 같이 붙임. `npm run build` 통과
- 웹 저장(로그인 후): 오른쪽 전체를 덮던 비교표를 없애고 지도(관심 캠핑장 핀 · 시설 칩 · 차박 버튼)를 보여 줌. 비교표는 지도 위, 관심 캠핑장 목록 바로 옆 패널(520px)로. ✕ 로 닫으면 "비교 보기 (n곳)" 버튼, 체크를 바꾸면 다시 열림. 패널이 열려 있으면 시설 칩을 패널 오른쪽으로. `npm run build` 통과, 화면 확인 못 함
- 앱 차박 지도: 등록 야영장에 저장(하트) — 목록 줄(MobileSpotRow) 오른쪽 하트, 판정 카드 "관심 캠핑장에 저장/저장됨" 버튼. 저장 탭 관심 캠핑장에 담김 (stores/favorite.js, 서버 변경 없음). 차박 후보지는 camp 표에 없어(saved_camp 외래키) 저장 불가 — 하트 안 보임. 웹 차박지 찾기 목록은 후보지만 있어 해당 없음. `npm run build` 통과
- 웹 차박지 찾기(/web/chabak): ① 핀·목록을 누르면 왼쪽에 차박지 상세(새 `components/web/WebChabakDetailPanel.vue`, 웹 캠핑장 상세와 같은 틀) — 스카이뷰/야영장 사진 · 판정+안전점수 상자 · 판정 사유 · 규제구역 · 땅 정보 · 가까운 시설 · 근처 후보(누르면 그 자리로) · 근처 야영장 · 카카오맵 길찾기 · (야영장이면) 캠핑장 상세. 고른 필지를 지도에 판정 색으로 칠함. ② 앱과 같은 레이어 필터(WebCarPanel): 차박 후보지 · 등록 야영장(새로 목록에 들어감) · 불가 구역 · 주의 구역(지도에 반투명 면) · 안전점수(전체/위험 제외/안전만). WebCampMap 에 zones · parcel 다각형 추가. 하트 저장은 넣지 않음(후보지는 저장 표에 못 담음, 사용자 결정). `npm run build` 통과, API 응답 확인(후보지 46 · 야영장 23 · 구역 · 판정). 화면 확인 못 함
- 웹 차박지 찾기 지도: 후보지 핀 아래 이름표("475-3 잡종지") 숨김 (WebCampMap hideNames). 고른 핀 · 등록 야영장은 이름 보임, 숨긴 핀은 마우스를 올리면 이름(title). 목록 · 상세에는 그대로
- 웹 차박지 찾기: "차박할 땐 이것만 챙겨요" 고정 문구 상자 삭제. 지도 아무 곳이나 누르면 그 자리를 판정해 왼쪽 상세에 보여 줌 (WebCampMap mapclick 이벤트, 종류 "지도에서 찍은 자리", 인제군 밖이면 서버 문구 그대로). 검색 탭에서는 지도 클릭 무시. `npm run build` 통과
- 웹 차박지 찾기: 지도에서 찍은 자리 · 근처 후보 핀에도 안전점수 표시 (상세 패널이 점수를 받으면 score 이벤트로 핀에 반영, 핀 색도 등급대로)
- 웹 차박지 상세에 "내 캠핑에 일정 등록" (웹 캠핑장 상세와 같은 시트: 로그인 안내 → 날짜·메모 → 완료). 야영장은 campId, 후보지·찍은 자리는 lat·lng 로 저장(이름: 후보지 "475-3 잡종지", 찍은 자리 "지번 지목"). 아래 버튼: 카카오맵 길찾기 · (야영장) 캠핑장 상세 · 내 캠핑에 일정 등록. `npm run build` 통과, 실제 등록은 안 눌러 봄(공유 DB)
- 1분 진단 아이 나이: "청소년 13세 이상" 선택지 추가 (고를 수 있지만 결과는 성인 일행과 같음 — 물·호우 +1 · 아이 체크리스트 · 지도 응급실 가산점 모두 12세 이하만, webSurvey.js hasYoungChild). 대비할 것을 나이별로: 영유아 = 상비약·해열제·체온계 / 화로·난로 거리 두기 / 물가 손 닿는 거리 / 구명조끼, 어린이 = 상비약·해열제 / 화로·버너 약속 / 구명조끼 / 연락처 카드·호루라기(근거 자료 없음). 해시태그에 "· 청소년". 웹·앱 같이 적용. `npm run build` 통과
- 진단 해시태그: "#아이 동반 · 영유아" → "#영유아" / "#어린이" / "#청소년" (whoTag)
- 웹 진단 결과 "캠핑장 고르기": 내 유형 점수(응급실 가산점) 해당 답이 있고 켜져 있으면 가산점 순(sort=bonus)으로 고르고 점수도 더한 값으로 표시 (지도 · 상세와 같은 숫자). `npm run build` 통과
- 웹 진단 결과 "대비할 것": "꼭" 배지 대신 색으로 강조 (연한 노랑 바탕 + 왼쪽 띠 + 굵은 갈색 글씨, 토큰 --web-grade-danger-*), 강조 항목을 위로 정렬, 위에 "색이 칠해진 항목은 내 유형에 특히 중요해요" 안내. 체크하면 강조를 거둠. 화면 읽기 프로그램용 숨김 글 "(내 유형에 특히 중요)". `npm run build` 통과
- 진단 첫 묶음 문구: "주무시는 방식에 맞는 캠핑장을 골라 드려요" → "선택에 따라 추천 캠핑장 점수가 달라집니다" (응급실 가산점이 추천 점수에 반영돼서)
- 진단 첫 묶음의 "안전지도" 칩 삭제 (feature 비움 → 웹은 칩을 그리지 않음)
- 진단 "누구와 가세요?" 옆 안내 "여러 개 골라도 돼요" 삭제 (복수 선택은 그대로)
- 진단 유형을 캐릭터 6개로 (webSurvey.js CHARACTERS · AXIS_TYPES · typeOf): 조심할 것 1위 분야 → 답으로 캐릭터. 일산화탄소 = 차박 🐢 등껍질 거북이 / 겨울 🐻 이불 속 곰 / 기본 거북이, 화재 = 오토 🦝 불멍 너구리 / 봄가을 🐿️ 낙엽 다람쥐 / 기본 너구리, 물·호우 = 아이(12세 이하) 🦆 꼬마 오리 가족 / 여름 🦦 계곡 수달 / 기본 수달. 문구 = 성격(trait) + 행동 하나(act). 든든 대비형 등 예전 4유형 삭제, 퀴즈 다 맞히면 👑 배지(allRight). 결과 첫 줄 "{분야}를 가장 조심해야 하는 ‘{캐릭터}’ 유형이에요", 캠핑장 고르기 제목은 실제 기준대로("안전점수(내 유형 점수 포함)가 높은 순으로 골랐어요" — 예전 "하천에서 떨어진 높은 사이트 · 대피로가 가까운 순" 같은 사실이 아닌 문구 제거). 12개 조합을 node 로 돌려 규칙대로 나오는 것 확인, `npm run build` 통과
- 유형 문구: 마침표 삭제, 행동 줄을 "~세요" 로 (열어 두세요 · 끄세요 · 확인하세요 · 다시 한번 보세요 · 계속 보세요 · 가세요)
- 유형: 🦦 계곡 수달 → 🐸 장마 개구리 ("비 오는 날도 캠핑은 못 참는 타입 / 비가 많이 오면 물가 자리는 피하고, 물이 불어나면 바로 높은 곳으로 가세요") — 진단에 계곡 가는지 묻는 질문이 없고 여름 가중치 근거가 장마·집중호우라서. 물·호우 기본 캐릭터도 개구리. 다람쥐 성격 "가을 숲" → "마른 숲" (봄에도 나와서)
- 진단 결과 유형 카드의 "일행과 함께하는 · 불을 다루는 캠퍼" 줄 삭제 (해시태그와 겹침)
- 진단 결과: "👑 상황 퀴즈 모두 정답" 배지 삭제 (퀴즈 정답 여부를 결과에 알리지 않음, allRight 제거)
- 진단 결과 유형 카드: 동물 얼굴(이모지 120px) 크게 + 오른쪽에 이름, 그 아래 말풍선 두 개(성격 / 지켜야 할 행동 — 굵게, 유형 색 테두리, 왼쪽 꼬리). `npm run build` 통과, 화면 확인 못 함
- 진단 결과 유형 카드를 스케치대로: 가운데 동그라미(200px) 안에 캐릭터, 아래 이름, 왼쪽 가운데 말풍선 = 성격(꼬리 → 캐릭터), 오른쪽 위 말풍선 = 행동(굵게 · 유형 색 테두리, 꼬리 ↙ 캐릭터). 좁은 화면(760px 이하)은 세로로 쌓고 꼬리 숨김. `npm run build` 통과, 화면 확인 못 함
- 진단 결과 말풍선 다시: 테두리·삼각 꼬리 없이 그림자로 띄운 둥근 말풍선(캐릭터 쪽 모서리만 뾰족, 메신저 느낌). 성격 = 흰 말풍선, 행동 = 유형 색으로 채운 말풍선 + 흰 글씨. 캐릭터 동그라미 삭제, 이모지 140px + 옅은 그림자. `npm run build` 통과
- 진단 결과 유형 카드 배치를 사용자 시안대로: 위쪽 양옆 말풍선(왼쪽 성격 · 오른쪽 행동), 가운데 캐릭터, 아래 이름, 오른쪽 아래 ⚠ 꼬리표 + 해시태그 (grid-template-areas). 좁은 화면은 캐릭터 → 이름 → 말풍선 → 꼬리표·태그 세로. 캐릭터 그림 비교 예시: 워크스페이스 `Claude outputs/캐릭터_그림_비교.html` (이모지 · Fluent 2D Color · Flat · 3D, MIT 라이선스 확인). `npm run build` 통과
- 진단 캐릭터 그림을 Fluent 3D 이미지로 통일: client/public/survey/characters/{turtle,bear,raccoon,chipmunk,duck,frog}.png (Microsoft Fluent Emoji 3D, 256px, MIT — 같은 폴더 LICENSE-fluentui-emoji.txt). CHARACTERS 에 img, 못 불러오면 이모지로 대신. 성격 말풍선은 유형 색을 옅게 깔고 테두리 · 글씨 색(불: 연한 주황 / 물: 연한 청회색)으로 흰 카드에서 구분되게. `npm run build` 통과, 개발 서버에서 이미지 200
- 진단 결과 카드: 이름 ↔ 꼬리표·해시태그 간격 28 → 56px, 캐릭터 그림 160 → 220px
- 진단 결과 애니메이션(CSS만): A 캐릭터 아래에서 튀어 올라 통통(0.1s~), C 말풍선 캐릭터 쪽 모서리에서 차례로 뿅(성격 0.75s → 행동 1.05s), 이름 · 꼬리표/해시태그 떠오르기, B 등장 뒤 캐릭터 둥실둥실(계속). 움직임 줄이기 설정이면 끔. `npm run build` 통과, 화면 확인 못 함
- 진단 결과 캐릭터 누르기: 통 튀어 한 바퀴(char-jump) + 오른쪽 말풍선이 다음 안전 팁으로 바뀌며 바로 뿅. 팁 = 캐릭터 행동 + 분야별 3개(webSurvey.js AXIS_TIPS, 조심할 것·대비할 것 문장에서 가져옴), 마지막 다음은 처음. 이름 아래 "캐릭터를 눌러 보세요"(한 번 누르면 사라짐). 캐릭터는 버튼(키보드 포커스 표시), 말풍선 aria-live. `npm run build` 통과, 화면 확인 못 함
- 진단 결과: 캐릭터 누르기 반응을 "점프·빙글" → 젤리처럼 눌렸다 출렁 + 반짝이 6개 팡 으로 교체 (팁 바뀌기는 그대로). 줄맞춤: 결과 화면 전체 word-break: keep-all(한글 낱말 중간 줄바꿈 방지), 말풍선은 text-wrap: balance + 가운데 정렬
- 진단 결과 캐릭터 누르기: 반짝이 삭제 (젤리 출렁 + 팁 바뀌기만)
- 진단 유형 행동 말풍선에 캐릭터 말투: 곰 "잊지 말곰" · 다람쥐 "조심해야 합니다람쥐" · 오리 "계속 보세오리" · 개구리 "가야해 개굴" · 너구리 "꼭 확인해야 한다구리" · 거북이 말줄임표로 느릿느릿("차 안에서… 잘 땐…… 시동 끄고……"). 누르면 나오는 다음 팁들은 그대로
- 캐릭터를 누르면 나오는 다음 팁도 캐릭터 말투로 (분야별 공통 AXIS_TIPS → 캐릭터마다 tips 3개, 내용은 조심할 것·대비할 것 문장 그대로 말투만). `npm run build` 통과
- 유형 성격 말풍선 끝 "타입" → 동물 이름 (…시간 가는 줄 모르는 너구리 등)
