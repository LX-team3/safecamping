# 캠핀 메타버스 안전교육 (meta)

인북천 물빛테마공원 캠핑장(강원 인제군 서화면)을 3D로 만든 **어린이 캠핑 안전교육 게임**이에요.
거북이 캐릭터로 캠핑장을 돌아다니며 위험 지점을 찾아 퀴즈를 풀어요. 시작할 때 아이·어른을 고르고, 문제는 하나씩 무작위로 나타나요 (제한 시간 없음, 나가기 버튼으로 끝냄).
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

- 퀴즈 40문제는 `client/public/metaverse/data/missions.json`에서 읽어요. DB와 서버(Spring)는 쓰지 않아서 서버가 꺼져 있어도 게임이 돼요.
- 데이터 파일은 `` `${import.meta.env.BASE_URL}metaverse/data/...` ``로 불러요. `http://localhost:...` 같은 주소나 새 창(`target="_blank"`)은 쓰지 않아요 (나중에 앱 안에서 안 열려요).

## 기술 구성

- **Vue 3 + Vite** (client와 같은 프로젝트), 3D는 **three.js** (`client/src/metaverse/`)
- 팀 아키텍처: Vue(Firebase Hosting) → Spring Boot(Cloud Run) → PostgreSQL(Cloud SQL, PostGIS)
- 좌표계: 모든 공간 데이터는 **EPSG:5179** (KGD2002 / Unified CS)
- 3D 좌표 변환: `x = X - 중심X`, `z = -(Y - 중심Y)` (중심은 terrain.asc 범위의 가운데, 북쪽 = -z)

## 폴더 구조

```
client/src/
  views/metaverse/MetaverseView.vue   화면(UI): 시작 화면(아이·어른 고르기), 위쪽 정보줄, 퀴즈 창, 미니게임 안내, 터치 조이스틱·버튼, 침수 미션 알림, 끝 화면
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
    missions.js             퀴즈 불러오기(missions.json), 다음 문제 고르기, 위치 찾기, 고리·화살표, 소품 놓기
    props.js                퀴즈 소품: 문제 상황을 보여 주는 3D 물건(모닥불, 버너, 난로, 벌집 등 28가지). 맨 아래 PROPS 표에 이름이 있어요
    minigame.js             퀴즈 앞 미니게임 "마시멜로 굽기": 1인칭 화면, Space·클릭으로 뒤집기, 10초 뒤 퀴즈 (맨 위 설정값은 게임용 값)
    flood.js                돌발 미션 "비와 침수 대피": 비, 차오르는 물, 출구 고리, 성공·실패 판정 (맨 위 설정값은 게임용 값)
client/public/metaverse/data/  QGIS에서 내보낸 GeoJSON, terrain.asc, missions.json(퀴즈 40문제)
metaverse/gis/               QGIS 원본 (camp.gpkg, camp.qgz) — 수정은 QGIS에서만
```

## 터치 조작 (휴대폰·태블릿, 2026-11-23)

- 터치가 되는 기기에서는 왼쪽 아래 **조이스틱**, 오른쪽 아래 **🏃 달리기**(누를 때마다 켜짐·꺼짐)와 **⬆️ 점프** 버튼이 나와요. 화면을 끌면 둘러볼 수 있어요.
- 화면(`MetaverseView.vue`)이 `game.setMove(x, y)`, `game.setRun(켜짐)`, `game.jump()`를 부르고, `player.js`가 키보드와 같은 방식으로 움직여요.
- 퀴즈·미니게임·대피 결과 창이 떠 있으면 조이스틱과 버튼은 숨겨져요. 마시멜로 굽기는 화면 아무 곳이나 터치하면 뒤집혀요.

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
| stream | 선 | 작은 개울. 침수 미션 때 이 개울도 넘쳐서 개울 둘레 8m까지 잠겨요 |
| car_routes | 선 | 차량 동선. 그린 방향대로 달려요 / speed: 속도(km/h) / count: 차 대수 |
| facilities | 점 | type: fountain, deck, **start(캐릭터 시작 위치)**, **lamp(가로등)**. 건물 종류 점은 무시돼요 |
| facilities (exit) | 점 | type: **exit(침수 미션 때 도망갈 출구)**. 없으면 가장 늦게 잠기는 곳을 임시 출구로 써요 |
| quiz_points | 점 | 퀴즈가 뜨는 자리. place: fire, tent, path, water, power, rest, nature, office, fountain, parking, road / label: 메모. 문제의 `place`와 같은 점 중 하나에 무작위로 떠요. 없으면 임시 위치(anchor)를 써요 |

GeoJSON 내보내기 설정: EPSG:5179, COORDINATE_PRECISION 1, RFC7946 NO, 같은 이름이면 **파일 덮어쓰기**.

**한 번에 내보내기**: QGIS 파이썬 콘솔(Ctrl+Alt+P) 편집기에서 `gis/export_geojson.py`를 열고 ▶ 실행하면 camp.gpkg의 모든 레이어가 위 설정으로 `client/public/metaverse/data/`에 내보내져요. (gpkg는 읽기만 해요. 레이어를 먼저 저장해야 해요)

## 퀴즈

- 40문제: 아이(child)·어른(adult) × 낮(day)·밤(night) 각 10문제. `client/public/metaverse/data/missions.json`에만 있어요 (DB는 쓰지 않기로 함, 2026-11-19).
- 문제 필드: `audience`, `time`, `place`(장소 종류), `anchor`(임시 위치), `prop`(소품 이름), 그 밖에 제목·상황·질문·보기 4개·정답 번호·해설.
- 소품(2026-11-23): 문제 자리에 그 상황을 보여 주는 3D 소품이 서요. 문제의 `prop` 값이 `props.js`의 PROPS 표에 있는 이름이면 그 소품이 나오고, `prop`이 없는 문제(주차장·도로·분수 3문제)는 고리만 떠요. 문제 자리가 데크 한가운데면 데크의 텐트·테이블 옆 빈자리에 맞춰 놓고, 밤에는 불·랜턴이 주변을 비춰요. 크기는 잘 보이게 실제보다 조금 크게 만든 게임용 크기예요.
- 미니게임(2026-11-23): 문제에 `"minigame": "marshmallow"`가 있으면 고리에 닿았을 때 퀴즈보다 먼저 "마시멜로 굽기"가 열려요 (지금은 `c-d-burn` 하나). 1인칭으로 그릴을 보며 Space·클릭으로 뒤집고, 10초 뒤 "앗 뜨거!" 글자가 나온 다음 퀴즈가 떠요. 빼려면 `missions.json`의 그 한 줄만 지우면 돼요.
- 소품 확인: 주소 뒤에 `?quiz=문제id`(예: `?quiz=c-n-throw`)를 붙이면 그 문제만 떠요.
- 발표 시연 순서(2026-11-23): 아이를 고르면 처음 두 문제가 "꼬치에 손을 데었어요!"(`c-d-burn`) → "주차장에서 차가 뒤로 와요"(`c-d-parking`)로 고정이고, 두 번째를 풀면 바로 침수 대피가 시작돼요. `scene.js` 맨 위 `DEMO_QUIZ`에서 바꿔요 (비우면 처음부터 무작위).
- 진행: 맵에 문제 하나만 뜨고, 풀면 다음 문제가 다른 곳에 무작위로 떠요. 고른 대상(아이/어른)과 지금 시간대(낮/밤)에 맞는 문제만 나와요.
- 낮·밤은 화면 위쪽 ☀️ 낮 / 🌙 밤 버튼을 눌러야 바뀌어요 (`scene.js`의 `toggleTime`. 퀴즈·침수 대피 중에는 못 눌러요). 2분마다 저절로 바뀌던 기능은 없앴어요 (2026-11-23). 새 판은 낮부터 시작해요. 바뀌면 떠 있던 문제도 새 시간대 문제로 바뀌어요.
- 위치: QGIS 점 레이어 `quiz_points`에 문제의 `place`와 같은 값의 점이 있으면 그 점에 떠요 (`missions.js`의 `findMissionPos`). 그런 점이 없는 문제는 임시 위치 anchor를 써요. 예: `{"row":"B","no":3}`, `{"facility":"office"}`, `{"stream":0.5}`, `{"road":"3"}`, `{"tree":true}`(캠핑장 안 작은 나무 하나를 무작위로 골라 그 옆 풀밭. 벌 퀴즈가 써요), `{"car":true}`(달리는 차 한 대를 골라 고리·아이콘이 그 차를 따라다녀요. "캠핑장 안 운전" 퀴즈가 써요), `{"parked":true}`(주차된 차 한 대를 골라, 캐릭터가 다가오면 차가 캐릭터 쪽으로 1m 후진하고 후진등이 깜빡여요. 아이용 주차장 퀴즈가 써요. 거리·빠르기는 `missions.js`의 `BACK_UP`)
- 문제 근거와 목록: `docs/ppt/CAMPIN_퀴즈근거.pptx`

## 돌발 미션: 비와 침수 대피

- 시연 문제 2개(`DEMO_QUIZ`)를 풀면 바로 비가 와요. 그 뒤, 또는 시연 문제가 없을 때(어른)는 탐험 중 무작위로 비가 와요 (`scene.js`의 `FIRST_RAIN`, `NEXT_RAIN`). 주소 뒤에 `?rain=5`를 붙이면 시작 5초 뒤에 와요 (발표·확인용).
- 순서: 강물이 차오름 → 캠핑장이 잠김. 강물은 캠핑장 테두리의 **북쪽과 동쪽 변 전체**에서 동시에 넘쳐 들어와요. 반대편 개울(stream)도 넘치지만 개울에서 8m까지만 잠기고 멈춰요 (`flood.js`의 `STREAM_REACH`). 홍수위험지도 구역(flood_zones)은 쓰지 않아요 (2026-11-20 변경). 들어오는 곳은 게임에서 정한 설정이에요. 떠 있던 퀴즈는 사라지고 초록 화살표가 출구를 가리켜요.
- 점수는 없앴어요 (2026-11-20). 화면에는 푼 문제 수만 보여요.
- 출구 고리에 닿으면 성공(퀴즈 이어서), 물에 잠기면 실패("다시 대피하기"로 비가 시작된 자리에서 다시).
- 출구는 실제 출구 자리 그대로 써요(강 가까이여도 돼요). 대신 출구 둘레 40m는 가장 늦게 잠기게 했어요 (`flood.js`의 `EXIT_KEEP`).
- 차오르는 시간·속도는 게임용 값이에요. 실제 홍수 속도가 아니에요 (`flood.js` 맨 위).
- QR 동시접속은 하지 않기로 했어요 (2026-11-20).

## 앞으로 할 일

1. 7단계: 무작위 날씨(맑음·비·강풍·폭염)와 돌발상황. (비가 오면 강물이 불어나는 기능은 보류했어요. 관련 코드도 지웠어요)
2. DB 연결 (Spring Boot + PostgreSQL), 배포
