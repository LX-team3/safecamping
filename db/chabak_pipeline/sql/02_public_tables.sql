\encoding UTF8
-- =====================================================================
-- chabak DB : raw -> public 정리 테이블 만들기
-- 실행: psql -U postgres -d chabak -f 02_public_tables.sql
-- 전제: raw 스키마에 8개 테이블 적재 완료 (2026-11-14 확인)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. cadastre : 필지 경계 (연속지적도 2026-10)
--    같은 PNU가 여러 조각으로 들어간 경우를 대비해 PNU별로 합친다.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS cadastre CASCADE;
CREATE TABLE cadastre (
  pnu   CHAR(19) PRIMARY KEY,
  jibun TEXT,
  geom  geometry(MultiPolygon, 5186) NOT NULL
);

INSERT INTO cadastre (pnu, jibun, geom)
SELECT pnu,
       MIN(jibun),
       ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_Union(geom)), 3))
FROM raw.cadastre_src
WHERE pnu IS NOT NULL
GROUP BY pnu;

CREATE INDEX cadastre_geom_gix ON cadastre USING GIST (geom);


-- ---------------------------------------------------------------------
-- 2. land_attr : 필지 속성
--    지목·소유 = LX맵(2026-10, 연속지적도와 기준일 같음)
--    토지이용상황·지형·도로접면 = 토지특성정보(2026-05-20)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS land_attr CASCADE;
CREATE TABLE land_attr (
  pnu           CHAR(19) PRIMARY KEY REFERENCES cadastre(pnu),
  jimok         VARCHAR(20),   -- LX맵 jimok      (예: 임야, 전, 주차장)
  owner_cd      VARCHAR(2),    -- LX맵 owner_cd   (01 개인, 02 국유지, 04 시도유지, 05 군유지 ...)
  owner_nm      VARCHAR(30),
  area_m2       NUMERIC,       -- LX맵 parea
  zoning        VARCHAR(50),   -- 토지특성 a14    (예: 보전관리지역)
  land_use      VARCHAR(50),   -- 토지특성 a18    (예: 토지임야, 하천등)
  terrain_h     VARCHAR(20),   -- 토지특성 a20    (예: 고지, 완경사)
  terrain_shape VARCHAR(20),   -- 토지특성 a22    (예: 부정형)
  road_side     VARCHAR(30),   -- 토지특성 a24    (예: 맹지)
  src_lx        BOOLEAN,       -- LX맵에서 속성을 찾았는지
  src_char      BOOLEAN        -- 토지특성정보에서 찾았는지
);

INSERT INTO land_attr
SELECT c.pnu,
       NULLIF(lx.jimok, ''),
       NULLIF(lx.owner_cd, ''),
       NULLIF(lx.owner_nm, ''),
       lx.parea,
       NULLIF(lc.a14, ''),
       NULLIF(lc.a18, ''),
       NULLIF(lc.a20, ''),
       NULLIF(lc.a22, ''),
       NULLIF(lc.a24, ''),
       lx.pnu IS NOT NULL,
       lc.a1  IS NOT NULL
FROM cadastre c
LEFT JOIN (SELECT DISTINCT ON (pnu) * FROM raw.lx_map    ORDER BY pnu) lx ON lx.pnu = c.pnu
LEFT JOIN (SELECT DISTINCT ON (a1)  * FROM raw.land_char ORDER BY a1)  lc ON lc.a1  = c.pnu;


-- ---------------------------------------------------------------------
-- 3. regulation : 규제구역 폴리곤 (전국 파일 -> 인제 필지와 겹치는 것만)
--    A4(고시 기관 코드)가 아니라 위치로 거른다.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS regulation CASCADE;
CREATE TABLE regulation (
  id        SERIAL PRIMARY KEY,
  category  VARCHAR(20),    -- 자연공원 / 산림보호 / 상수원 / 백두대간 / 하천 / 고시
  zone_code VARCHAR(10),    -- 원본 a5 (예: UMA100)
  zone_name TEXT,           -- 원본 a6 (예: 국립공원)
  label     TEXT,           -- 원본 a8 (예: 설악산국립공원)
  level     VARCHAR(4) CHECK (level IN ('불가','주의')),
  source    TEXT,
  geom      geometry(MultiPolygon, 5186) NOT NULL
);

-- 공통: 인제 필지와 겹치는지 검사하는 조건은 각 INSERT의 WHERE EXISTS 부분

-- 3-1. 자연공원 (국립·군립공원 등) -> 불가
INSERT INTO regulation (category, zone_code, zone_name, label, level, source, geom)
SELECT '자연공원', r.a5, r.a6, NULLIF(r.a8,''), '불가', '자연공원/용도구역 2026-11-09',
       ST_Multi(ST_CollectionExtract(ST_MakeValid(r.geom), 3))
FROM raw.park r
WHERE EXISTS (SELECT 1 FROM cadastre c WHERE ST_Intersects(c.geom, r.geom));

-- 3-2. 산림보호 : 산림유전자원보호구역 -> 불가, 그 밖의 보호구역 -> 주의
INSERT INTO regulation (category, zone_code, zone_name, label, level, source, geom)
SELECT '산림보호', r.a5, r.a6, NULLIF(r.a8,''),
       CASE WHEN r.a6 = '산림유전자원보호구역' THEN '불가' ELSE '주의' END,
       '산림보호/산림보호 2026-11-09',
       ST_Multi(ST_CollectionExtract(ST_MakeValid(r.geom), 3))
FROM raw.forest r
WHERE EXISTS (SELECT 1 FROM cadastre c WHERE ST_Intersects(c.geom, r.geom));

-- 3-3. 상수원보호구역 -> 불가
INSERT INTO regulation (category, zone_code, zone_name, label, level, source, geom)
SELECT '상수원', r.a5, r.a6, NULLIF(r.a8,''), '불가', '수도/상수원보호 2026-11-09',
       ST_Multi(ST_CollectionExtract(ST_MakeValid(r.geom), 3))
FROM raw.water r
WHERE EXISTS (SELECT 1 FROM cadastre c WHERE ST_Intersects(c.geom, r.geom));

-- 3-4. 백두대간 : 핵심구역 -> 불가, 완충구역·보호지역 -> 주의 (미분류 제외)
INSERT INTO regulation (category, zone_code, zone_name, label, level, source, geom)
SELECT '백두대간', r.a5, r.a6, NULLIF(r.a8,''),
       CASE WHEN r.a5 = 'UFO110' THEN '불가' ELSE '주의' END,
       '백두대간보호/용도지역 2026-11-09',
       ST_Multi(ST_CollectionExtract(ST_MakeValid(r.geom), 3))
FROM raw.baekdu r
WHERE r.a5 <> 'UFO000'
  AND EXISTS (SELECT 1 FROM cadastre c WHERE ST_Intersects(c.geom, r.geom));

-- 3-5. 하천 : 하천구역·홍수관리구역 -> 주의 (고시 금지구간은 4단계에서 별도로 '불가' 추가)
INSERT INTO regulation (category, zone_code, zone_name, label, level, source, geom)
SELECT '하천', r.a5, r.a6, NULLIF(r.a8,''), '주의', '하천/용도구역 2026-11-09',
       ST_Multi(ST_CollectionExtract(ST_MakeValid(r.geom), 3))
FROM raw.river r
WHERE r.a5 IN ('UJB100', 'UJB001', 'UJB400')
  AND EXISTS (SELECT 1 FROM cadastre c WHERE ST_Intersects(c.geom, r.geom));

CREATE INDEX regulation_geom_gix ON regulation USING GIST (geom);


-- =====================================================================
-- 확인 쿼리 (결과를 캡처해서 공유)
-- =====================================================================

-- A. 테이블별 건수
SELECT 'cadastre' t, COUNT(*) FROM cadastre
UNION ALL SELECT 'land_attr', COUNT(*) FROM land_attr
UNION ALL SELECT 'regulation', COUNT(*) FROM regulation;

-- B. 속성 매칭 확인 (기준일 차이 점검)
--    lx_miss  : LX맵에 없는 필지 수 (기준일 같아서 거의 0이어야 함)
--    char_miss: 토지특성정보에 없는 필지 수 (2026-05 vs 2026-10 차이)
SELECT COUNT(*) FILTER (WHERE NOT src_lx)   AS lx_miss,
       COUNT(*) FILTER (WHERE NOT src_char) AS char_miss,
       COUNT(*)                             AS total
FROM land_attr;

-- C. 규제구역 종류별 건수
SELECT category, zone_name, level, COUNT(*)
FROM regulation
GROUP BY 1, 2, 3
ORDER BY 1, 3, 2;

-- D. 다음 단계(규칙 테이블)에 쓸 값 목록
SELECT 'jimok' col, jimok v, COUNT(*) FROM land_attr GROUP BY jimok
UNION ALL SELECT 'land_use', land_use, COUNT(*) FROM land_attr GROUP BY land_use
UNION ALL SELECT 'terrain_h', terrain_h, COUNT(*) FROM land_attr GROUP BY terrain_h
UNION ALL SELECT 'road_side', road_side, COUNT(*) FROM land_attr GROUP BY road_side
ORDER BY 1, 3 DESC;
