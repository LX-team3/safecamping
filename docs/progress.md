# 진행 상황

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
