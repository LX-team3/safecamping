# 캠핀 메타버스 안전교육 (meta)

인북천 물빛테마공원 캠핑장(강원 인제군 서화면)을 3D로 만든 **어린이 캠핑 안전교육 게임**이에요.
거북이 캐릭터로 캠핑장을 돌아다니며 위험 지점을 찾고, 5분 안에 퀴즈 4개를 풀어요.
팀 프로젝트 "캠핀(CAMPIN)"의 메타버스 파트예요.

> **2026-11-22부터 게임 코드는 client 안에 있어요.** 이 `metaverse/` 폴더에는 QGIS 원본(`gis/`)과 문서(`docs/`, 이 파일)만 남았어요.
> - 3D 코드: `client/src/metaverse/` · 화면: `client/src/views/metaverse/MetaverseView.vue` · 데이터: `client/public/metaverse/data/`
> - 주소: client의 `/metaverse` (라우트는 `client/src/router/index.js`)

## 먼저 읽을 것

- **docs/작업기록.md**: Claude 채팅에서 지금까지 진행한 내용, 결정 사항, 해결한 문제, 남은 할 일이 정리돼 있어요. 새 작업 전에 꼭 읽어 주세요.
- 중요한 결정을 하거나 단계를 끝내면 docs/작업기록.md에 짧게 추가해 주세요.
- **docs/작업일지.md**: 자기소개서용 일자별 기록이에요. 작업한 날마다 그날 항목(한 일, 문제와 해결, 직접 판단한 것)을 꼭 추가해 주세요.

## 작업 방식 (AI에게 부탁할 때 지켜 주세요)

- 설명과 주석은 **한국어**로, 개발 초보도 이해할 수 있게 쉽게 써 주세요.
- 코드를 바꾸면 **무엇을 왜 바꿨는지** 짧게 설명해 주세요.
- 파일을 고칠 때는 필요한 부분만 고치고, 관계없는 코드는 건드리지 마세요.
- 새 기능은 가능하면 **QGIS 데이터(type 값)로 조절**할 수 있게 만들어 주세요. 코드에 좌표를 직접 적지 않아요.
- `gis/camp.gpkg`, `gis/camp.qgz`는 원본 데이터예요. **절대 지우거나 덮어쓰지 마세요.**
- 바꾼 뒤에는 client에서 `npm run dev`로 확인할 수 있게 해 주세요.

## 실행 방법

```bash
cd client
npm install        # 처음 한 번 (three.js도 client에 들어 있어요)
npm run dev        # http://localhost:5173/metaverse
```

- 퀴즈를 DB에서 가져오려면 Spring(8080)도 켜 둬요. 꺼져 있으면 예비 파일(`missions.json`)로 게임이 돼요.
- 데이터 파일은 `` `${import.meta.env.BASE_URL}metaverse/data/...` ``로 불러요. `http://localhost:...` 같은 주소나 새 창(`target="_blank"`)은 쓰지 않아요 (나중에 앱 안에서 안 열려요).

## 기술 구성

- **Vue 3 + Vite** (client와 같은 프로젝트), 3D는 **three.js** (`client/src/metaverse/`)
- 팀 아키텍처: Vue(Firebase Hosting) → Spring Boot(Cloud Run) → PostgreSQL(Cloud SQL, PostGIS)
- 좌표계: 모든 공간 데이터는 **EPSG:5179** (KGD2002 / Unified CS)
- 3D 좌표 변환: `x = X - 중심X`, `z = -(Y - 중심Y)` (중심은 terrain.asc 범위의 가운데, 북쪽 = -z)

## 폴더 구조

```
client/src/
  views/metaverse/MetaverseView.vue   화면(UI): 시작 화면, 점수·타이머, 퀴즈 창, 결과 화면
  metaverse/
    scene.js                총감독: 순서대로 불러오고 매 프레임 업데이트, 다시 하기(restart)
    terrain.js              DEM 지형, 캠핑장 평평하게 깎기, 강바닥 파기, heightAt(x, z)
    campsite.js             캠핑장 전체: 파일을 읽어서 아래 파일들로 순서대로 배치
    ground.js               바닥 영역·도로·바닥분수 (areas, roads)
    river.js                강 (river)
    buildings.js            건물 (buildings): type별 모양
    sites.js                데크·카라반·주차칸, 도로에 붙이기, 충돌 상자
    trees.js                숲과 작은 나무
    cars.js                 달리는 자동차 (car_routes 선을 따라 시작점 → 끝점 반복)
    lamps.js                가로등 (facilities의 lamp 점). 밤 버전에서 불이 켜져요
    shapes.js               모양 도우미: 재질(재사용), 상자, 무늬, 면, 입체, 메모리 정리
    geo.js                  GeoJSON 읽기와 도형 계산 (여러 파일이 같이 써요)
    player.js               거북이 캐릭터 이동, 카메라, 충돌, 시작 위치
    daynight.js             낮·밤 바꾸기: 하늘색, 빛, 별과 달, 캐릭터 손전등 (MODES 설정값)
    missions.js             퀴즈 불러오기(DB → 예비 JSON), 위치(anchor) 찾기, 고리·화살표
client/public/metaverse/data/  QGIS에서 내보낸 GeoJSON, terrain.asc, missions.json(예비 문제)
metaverse/gis/                        QGIS 원본 (camp.gpkg, camp.qgz) — 수정은 QGIS에서만
sql/                        quiz, quiz_choice 테이블 SQL
```

## QGIS 레이어 (camp.gpkg → client/public/metaverse/data/*.geojson)

| 레이어 | 도형 | 주요 필드 / type 값 |
|---|---|---|
| boundary | 폴리곤 | 캠핑장 경계 (이동 범위, 평평하게 깎는 영역). **필수 파일** (나머지는 없어도 게임이 시작돼요) |
| roads | 폴리곤 | name (공놀이 퀴즈는 name `3`) |
| areas | 폴리곤 | type: plaza, parking, fountain, splash, forest, small_tree, road |
| buildings | 폴리곤 | type: office, shower, shower_toilet, toilet, recicle, recicle_toilet, water, car |
| sites | 폴리곤 | type: deck, caravan, parking / name: `B-3` 형식 |
| site_rows | 선 | name: A, B, C, D, F(카라반), P(주차) / count: 개수 |
| river | 폴리곤 | type: bed(하천 전체), water(물길) |
| stream | 선 | 작은 개울 |
| car_routes | 선 | 차량 동선. 그린 방향대로 달려요 / speed: 속도(km/h) / count: 차 대수 |
| facilities | 점 | type: fountain, deck, **start(캐릭터 시작 위치)**, **lamp(가로등)**. 건물 종류 점은 무시돼요 |

GeoJSON 내보내기 설정: EPSG:5179, COORDINATE_PRECISION 1, RFC7946 NO, 같은 이름이면 **파일 덮어쓰기**.

**한 번에 내보내기**: QGIS 파이썬 콘솔(Ctrl+Alt+P) 편집기에서 `gis/export_geojson.py`를 열고 ▶ 실행하면 camp.gpkg의 모든 레이어가 위 설정으로 `client/public/metaverse/data/`에 내보내져요. (gpkg는 읽기만 해요. 레이어를 먼저 저장해야 해요)

## 퀴즈

- 어린이용 13문제. 매 판 4문제(마시멜로 화상 `fire-burn`은 항상 출제).
- 위치(anchor) 예: `{"row":"B","no":3}`, `{"facility":"office"}`, `{"stream":0.5}`, `{"road":"3"}`
- DB: `quiz` + `quiz_choice`(보기 분리, 문제당 정답 1개). API `GET /api/quizzes`가 missions.json과 같은 모양으로 돌려줘요.
- 나중에 할 일: 퀴즈 지점을 PostGIS 점(quiz_point)으로 옮기기, 타임어택 기록·순위.

## 앞으로 할 일

1. 7단계: 무작위 날씨(맑음·비·강풍·폭염)와 돌발상황. (비가 오면 강물이 불어나는 기능은 보류했어요. 관련 코드도 지웠어요)
2. DB 연결 (Spring Boot + PostgreSQL), 배포
