# frontend — Vue 3 프론트엔드

루트 `CLAUDE.md` 의 공통 규칙(화면설계, 디자인, 문구 톤, 작업 규칙)을 함께 따른다.

## 명령어
- 설치: `cd frontend && npm install`
- 실행: `cd frontend && npm run dev` → http://localhost:5173 (브라우저 개발자도구에서 모바일 390px 로 보기)
- 빌드 확인: `cd frontend && npm run build` — 화면을 고친 뒤 오류가 없는지 반드시 확인
- 테스트·린트: 아직 없음

## 폴더 구조
(안에 내용은 변경 예정)
```
frontend/src/
├─ main.js, App.vue          # 앱 시작, 390px 폭 틀
├─ router/index.js           # 전체 화면 주소 목록 (SCREENS). 안 만든 화면은 NotReadyView 로 보임
├─ styles/tokens.css         # 디자인 토큰 (색·간격·글꼴) — 색은 여기 변수만 쓴다
├─ styles/base.css           # 기본 스타일
├─ components/common/        # 공통 부품 (AppHeader, BrandLogo, SvgIcon, CheckDot, LoginPromptModal)
├─ components/region/        # 지역 찾기 부품 (RegionHeader, KoreaMap)
├─ components/map/           # 캠핑지도 부품 (MapCanvas 지도 자리 — 카카오맵은 여기만 바꿔 붙임, MapNav 아래 메뉴)
├─ views/<영역>/             # 화면. main/, survey/, region/, map/ 로 나눔
├─ stores/                   # 화면 사이 공유 상태 (auth 로그인, survey 1분 진단 답 — 로그인 안 했을 때만 sessionStorage, trip 지역·인원·날짜, map 관심·찍은 위치)
├─ utils/                    # 날짜 등 도우미
└─ data/                     # 더미 데이터·설계에서 옮긴 내용 (koreaMap.json 지도 모양 등). 1분 진단 질문은 서버(/api/survey/questions)에서 받는다
```

## 화면 구현 방법
- 구현 순서: 뼈대(CSS 변수·공통 부품) → ①-1 Main 시범 → ① → ② → ③ → ④
- 해당 `.dc.html` 을 먼저 읽고 만든다 (루트 "화면설계" 참고) (임시)
- 화면을 새로 만들면 `router/index.js` 의 해당 줄에 `component` 를 연결한다
- 화면 하나를 만들 때마다 해당 설계 파일과 비교해서 빠진 요소가 없는지 확인한다
- 끝나면 `npm run build` 로 확인하고 `docs/progress.md` 를 갱신한다

## 컴포넌트·라우팅 규칙
- 여러 화면에 반복되는 부분은 `components/common/` 으로 뺀다. 한 영역에서만 반복되면 `components/<영역>/`
- 1분 진단은 한 파일(`views/survey/SurveyView.vue`)이 3단계를 처리한다. 결과는 따로 화면 없이 내 정보 카드에 답 요약으로 보인다 (유형 판정 기준이 정해지면 바뀜)
- 로그인은 더미: 로그인 버튼을 누르면 '정민영'(가상 인물)으로 로그인된 것으로 처리. 로그인 후에는 메인 메뉴·결과 저장·재진단 흐름이 바뀐다 (임시)

## CSS 규칙
- 색·간격·글꼴은 `styles/tokens.css` 의 CSS 변수만 쓴다. 컴포넌트에 hex 색을 직접 적지 않는다
- 필요한 색이 변수에 없으면 임의로 만들지 말고 먼저 묻는다

## 데이터·API
- 지금은 `data/` 의 더미 데이터를 쓴다. 안전 수치(점수·거리·시간)가 더미면 화면이나 주석에 더미임을 표시한다 (임시)
- 백엔드 호출은 `utils/api.js` 의 `api()` 를 쓴다 (성공이면 data, 실패면 서버 문구를 담은 Error). 응답 형식은 `backend/CLAUDE.md`
- 개발 중 `/api` 요청은 `vite.config.js` 의 proxy 가 백엔드(8080)로 넘긴다 → 백엔드를 먼저 켜 둔다
- 연결된 화면: ② 1분 진단(답 저장), ③-1 지역별 캠핑장 수, ③-2 인원·날짜 확인, ④-1~10 캠핑지도 (`data/regionDummy.js` 는 더 이상 안 씀)
- ④ 캠핑지도는 틀만 있음: 지도는 `MapCanvas` 의 임시 그림(설계 지도 위 x·y %), 길찾기 경로는 비어 있음 → 카카오 API 연결 때 채움
