# 재난 대피 3D 지도 (인북천 물빛테마공원 캠핑장)

캠핑장 주변을 3D로 보여 주고, 강수량에 따라 물이 차오르는 범위와 대피 경로를 안내하는 화면.
메인 기능 ③(재난 대피)의 지도 부분. 담당: 한준서

> `app/` 은 원본(혼자 도는 웹페이지). 팀 앱에는 Vue 화면으로 옮겨 둠 → `client/` 의 `/disaster`
> (`views/disaster/DisasterMapView.vue`, `components/disaster/`, `utils/disasterMap.js`, `public/disaster-map/`)

## 폴더
```
disaster-map/
├─ app/                  # 웹앱 (그대로 열면 동작)
│  ├─ index.html         # 화면 + 지도 코드 전부 (CesiumJS 1.123.0, CDN)
│  ├─ data.js            # QGIS에서 내보낸 지도 데이터 (지형·위성영상·건물·범람 범위·대피 경로·대피소)
│  ├─ criteria.json      # 강수량 → 침수 단계 기준표
│  ├─ char/, character.png  # 경로 따라가기 캐릭터
│  ├─ sw.js, manifest.webmanifest, icon-*.png  # 오프라인·홈 화면 설치(PWA)
└─ qgis/                 # QGIS Python 콘솔에서 돌리는 스크립트
   ├─ flood_depth.py     # 범람 범위 + DEM → 침수 깊이 (FwDET 방식)
   └─ export_data.py     # QGIS 레이어 → app/data.js 로 내보내기
```

## 실행
`file://` 로 열면 data.js 를 못 읽는다. 간단한 서버로 연다.
```
cd disaster-map/app
python -m http.server 8766
```
→ http://127.0.0.1:8766

## 기능
- 강수량 슬라이더(3시간 누적 0~200mm) → 안전·주의·강가 잠김·많이 잠김·넓게 잠김 5단계로 침수 범위·물 높이 변경
  - 60·90mm: 기상청 호우주의보·경보 기준 / 120·150mm: 80·100년 빈도 범람을 보여 주기 위해 팀이 정한 참고 단계 (`criteria.json`)
- 단계별 대피 경로: 평상시·주의보 → 서화중앙교회(양지교 경유 1,516m) / 경보·범람 → 서쪽 언덕(924m)
- 4단계 대피 길안내, 캐릭터가 경로를 따라 걷는 재생, 다리가 잠기면 높은 곳 우회 경로

## 데이터 출처
- 수치지도(국토지리정보원) 등고선 → 10m DEM (TIN 보간)
- 하천범람지도 50·80·100년 빈도
- 건물통합정보(V-world), OpenStreetMap 건물·도로·하천
- 대피소: 주민대피시설 목록

※ 실시간 예측이 아니라 범람 빈도별 범위를 강수량 단계에 연결해 보여 주는 시나리오형 지도.
