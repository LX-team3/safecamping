\encoding UTF8
-- =====================================================================
-- chabak DB : 규칙 테이블 + 판정 함수
-- 실행: psql -U postgres -d chabak -f 03_rules_and_verdict.sql
-- 전제: 02_public_tables.sql 실행 완료 (cadastre, land_attr, regulation)
-- 규칙 값은 2026-11-14 check.txt 의 실제 값(지목·토지이용상황·지형·도로접면)으로 채움
--
-- 판정 등급
--   불가          : 금지 근거가 데이터로 확인됨
--   주의          : 금지 근거는 없지만 확인 필요
--   제한 없음     : 국공유지 + 어떤 규제·주의 조건에도 안 걸림 (합법 보증 아님)
--   야영 가능     : 등록 야영장 (campsite 테이블)
--
-- 규칙 테이블의 verdict 값
--   불가 / 주의 / 후보   ('후보' = 다른 조건이 다 통과하면 '제한 없음'이 될 수 있음)
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 지목 규칙 (LX맵 jimok 값 기준)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS jimok_rule CASCADE;
CREATE TABLE jimok_rule (
  jimok   VARCHAR(20) PRIMARY KEY,
  verdict VARCHAR(4) CHECK (verdict IN ('불가','주의','후보')),
  reason  TEXT
);
INSERT INTO jimok_rule VALUES
  ('도로',       '불가', '지목 도로: 차량 통행 공간'),
  ('하천',       '불가', '지목 하천: 물길·침수 위험'),
  ('구거',       '불가', '지목 구거: 수로'),
  ('제방',       '불가', '지목 제방: 하천 시설'),
  ('유지',       '불가', '지목 유지: 저수지·연못'),
  ('양어장',     '불가', '지목 양어장'),
  ('공원',       '불가', '지목 공원: 도시공원 야영 금지'),
  ('학교용지',   '불가', '지목 학교용지'),
  ('묘지',       '불가', '지목 묘지'),
  ('종교용지',   '불가', '지목 종교용지'),
  ('수도용지',   '불가', '지목 수도용지'),
  ('주유소용지', '불가', '지목 주유소용지: 화재 위험'),
  ('사적지',     '불가', '지목 사적지'),
  ('공장용지',   '불가', '지목 공장용지'),
  ('주차장',     '주의', '공영주차장 야영·취사 금지(주차장법), 차 안 취침만'),
  ('유원지',     '주의', '유원지: 운영 주체 규칙 확인'),
  ('체육용지',   '주의', '체육시설 부지: 관리 주체 확인'),
  ('창고용지',   '주의', '창고 부지: 소유자 확인'),
  ('대',         '주의', '대지: 건물·주거지 가능성'),
  ('전',         '주의', '농지(밭): 경작지'),
  ('답',         '주의', '농지(논): 경작지'),
  ('과수원',     '주의', '농지(과수원)'),
  ('목장용지',   '주의', '목장 부지'),
  ('임야',       '후보', '임야'),
  ('잡종지',     '후보', '잡종지(공터)');


-- ---------------------------------------------------------------------
-- 2. 토지이용상황 규칙 (토지특성정보 a18 값 기준)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS land_use_rule CASCADE;
CREATE TABLE land_use_rule (
  land_use VARCHAR(50) PRIMARY KEY,
  verdict  VARCHAR(4) CHECK (verdict IN ('불가','주의','후보')),
  reason   TEXT
);
INSERT INTO land_use_rule VALUES
  -- 불가: 도로·하천·건물이 있는 곳·시설
  ('도로등',           '불가', '현황 도로'),
  ('하천등',           '불가', '현황 하천'),
  ('단독',             '불가', '현황 단독주택'),
  ('아파트',           '불가', '현황 아파트'),
  ('다세대',           '불가', '현황 다세대주택'),
  ('연립',             '불가', '현황 연립주택'),
  ('주거기타',         '불가', '현황 주거시설'),
  ('상업용',           '불가', '현황 상업시설'),
  ('주상용',           '불가', '현황 주상복합'),
  ('업무용',           '불가', '현황 업무시설'),
  ('공업용',           '불가', '현황 공업시설'),
  ('전축사',           '불가', '현황 축사'),
  ('답축사',           '불가', '현황 축사'),
  ('전창고',           '불가', '현황 창고'),
  ('답창고',           '불가', '현황 창고'),
  ('공원등',           '불가', '현황 공원'),
  ('운동장등',         '불가', '현황 운동장'),
  ('유해.혐오시설',    '불가', '현황 유해·혐오시설'),
  ('위험시설',         '불가', '현황 위험시설'),
  ('여객자동차터미널', '불가', '현황 터미널'),
  ('콘도미니엄',       '불가', '현황 숙박시설'),
  ('고속도로휴게소',   '불가', '현황 휴게소'),
  ('태양광발전소부지', '불가', '현황 태양광 발전시설'),
  -- 주의: 농지·주차장·기타 확인 필요
  ('전',               '주의', '현황 밭'),
  ('답',               '주의', '현황 논'),
  ('전기타',           '주의', '현황 밭(기타)'),
  ('답기타',           '주의', '현황 논(기타)'),
  ('과수원',           '주의', '현황 과수원'),
  ('목장용지',         '주의', '현황 목장'),
  ('조림',             '주의', '현황 조림지: 식재지 훼손 우려'),
  ('주차장등',         '주의', '현황 주차장: 공영이면 야영·취사 금지'),
  ('상업기타',         '주의', '현황 상업(기타)'),
  ('공업기타',         '주의', '현황 공업(기타)'),
  ('주상기타',         '주의', '현황 주상(기타)'),
  ('특수기타',         '주의', '현황 특수(기타)'),
  ('기타',             '주의', '현황 기타'),
  -- 후보: 임야·나지(공터)
  ('자연림',           '후보', '현황 자연림'),
  ('토지임야',         '후보', '현황 임야'),
  ('임야기타',         '후보', '현황 임야(기타)'),
  ('주거나지',         '후보', '현황 나지(공터)'),
  ('상업나지',         '후보', '현황 나지(공터)'),
  ('공업나지',         '후보', '현황 나지(공터)'),
  ('주상나지',         '후보', '현황 나지(공터)');


-- ---------------------------------------------------------------------
-- 3. 지형높이·도로접면 규칙
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS terrain_rule CASCADE;
CREATE TABLE terrain_rule (
  terrain_h VARCHAR(20) PRIMARY KEY,
  verdict   VARCHAR(4) CHECK (verdict IN ('불가','주의','후보')),
  reason    TEXT
);
INSERT INTO terrain_rule VALUES
  ('평지',         '후보', '평지'),
  ('완경사',       '후보', '완경사'),
  ('고지',         '후보', '고지'),
  ('급경사',       '주의', '급경사: 차량 기울어짐·낙석 위험'),
  ('저지',         '주의', '저지: 침수 위험'),
  ('지정되지않음', '주의', '지형 정보 없음');

DROP TABLE IF EXISTS road_rule CASCADE;
CREATE TABLE road_rule (
  road_side VARCHAR(30) PRIMARY KEY,
  verdict   VARCHAR(4) CHECK (verdict IN ('불가','주의','후보')),
  reason    TEXT
);
INSERT INTO road_rule VALUES
  ('맹지',         '주의', '맹지: 도로에 접하지 않아 차량 진입 불확실'),
  ('세로한면(불)', '주의', '자동차 통행이 어려운 좁은 길에만 접함'),
  ('세로각지(불)', '주의', '자동차 통행이 어려운 좁은 길에만 접함'),
  ('지정되지않음', '주의', '도로접면 정보 없음');
-- 나머지 값(세로한면, 소로한면 등)은 규칙 없음 = 통과


-- ---------------------------------------------------------------------
-- 4. 기간 규칙 (산불조심기간 등)
--    날짜는 매년 산림청·인제군 공고로 확인해서 갱신할 것
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS seasonal_rule CASCADE;
CREATE TABLE seasonal_rule (
  id         SERIAL PRIMARY KEY,
  jimok      VARCHAR(20),
  verdict    VARCHAR(4) CHECK (verdict IN ('불가','주의')),
  reason     TEXT,
  source_url TEXT,
  valid_from DATE,
  valid_to   DATE
);
INSERT INTO seasonal_rule (jimok, verdict, reason, source_url, valid_from, valid_to) VALUES
  ('임야', '주의', '봄철 산불조심기간: 일부 입산통제, 화기 사용 시 과태료',
   'https://www.kukinews.com/article/view/kuk202401300237', '2027-02-01', '2027-05-15'),
  ('임야', '주의', '가을철 산불조심기간: 일부 입산통제, 화기 사용 시 과태료 (기간은 공고로 확인)',
   NULL, '2026-11-01', '2026-12-15');


-- ---------------------------------------------------------------------
-- 5. 예외(조례·고시·현장) 테이블
-- ---------------------------------------------------------------------
-- 다시 실행해도 입력한 데이터가 지워지지 않도록 IF NOT EXISTS
CREATE TABLE IF NOT EXISTS override (
  id         SERIAL PRIMARY KEY,
  pnu        CHAR(19) REFERENCES cadastre(pnu),
  verdict    VARCHAR(5) CHECK (verdict IN ('불가','주의','제한 없음')),
  reason     TEXT,
  source_url TEXT,
  valid_from DATE,
  valid_to   DATE
);


-- ---------------------------------------------------------------------
-- 6. 등록 야영장 (데이터는 나중에 채움)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS campsite (
  id     SERIAL PRIMARY KEY,
  name   TEXT,
  addr   TEXT,
  source TEXT,
  geom   geometry(Point, 5186) NOT NULL
);
CREATE INDEX IF NOT EXISTS campsite_geom_gix ON campsite USING GIST (geom);


-- ---------------------------------------------------------------------
-- 7. 판정 함수
--    SELECT * FROM fn_verdict(38.07, 128.17);   -- (위도, 경도)
-- ---------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_verdict(double precision, double precision);
CREATE FUNCTION fn_verdict(p_lat double precision, p_lng double precision)
RETURNS TABLE (
  pnu            CHAR(19),
  jibun          TEXT,
  jimok          VARCHAR,
  owner_nm       VARCHAR,
  land_use       VARCHAR,
  terrain_h      VARCHAR,
  road_side      VARCHAR,
  verdict        TEXT,
  reasons        TEXT[],
  zones          TEXT[],
  near_boundary  BOOLEAN,
  nearby_camps   JSONB,
  parcel_geojson TEXT
)
LANGUAGE sql STABLE
AS $$
WITH p AS (
  SELECT ST_Transform(ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326), 5186) AS g
),
-- 클릭 지점이 들어 있는 필지. 필지 사이 틈(미등록지·경계 오차)이면 20m 안의 가장 가까운 필지로 판정
pa AS (
  SELECT c.pnu, c.jibun, c.geom,
         a.jimok, a.owner_cd, a.owner_nm, a.land_use, a.terrain_h, a.road_side, a.src_char,
         ST_Intersects(c.geom, p.g)     AS inside,
         ROUND(ST_Distance(c.geom, p.g)) AS gap_m
  FROM cadastre c
  JOIN land_attr a USING (pnu), p
  WHERE ST_DWithin(c.geom, p.g, 20)
  ORDER BY ST_Distance(c.geom, p.g)
  LIMIT 1
),
-- 걸린 규제구역
z AS (
  SELECT r.level, r.category, r.category || ' ' || r.zone_name || COALESCE(' (' || r.label || ')', '') AS txt
  FROM regulation r, p
  WHERE ST_Intersects(r.geom, p.g)
),
-- 클릭 지점이 자기 필지 경계선에서 3m 안쪽인지
nb AS (
  SELECT pa.inside AND ST_Distance(ST_Boundary(pa.geom), p.g) < 3 AS near
  FROM pa, p
),
-- 규칙 결과를 한 줄씩 모음
rules AS (
  SELECT jr.verdict, jr.reason FROM pa JOIN jimok_rule    jr ON jr.jimok     = pa.jimok
  UNION ALL
  SELECT ur.verdict, ur.reason FROM pa JOIN land_use_rule ur ON ur.land_use  = pa.land_use
  UNION ALL
  SELECT tr.verdict, tr.reason FROM pa JOIN terrain_rule  tr ON tr.terrain_h = pa.terrain_h
  UNION ALL
  SELECT rr.verdict, rr.reason FROM pa JOIN road_rule     rr ON rr.road_side = pa.road_side
  UNION ALL
  SELECT s.verdict, s.reason FROM pa JOIN seasonal_rule s
    ON s.jimok = pa.jimok AND CURRENT_DATE BETWEEN s.valid_from AND s.valid_to
  UNION ALL
  SELECT z.level, z.txt FROM z
  UNION ALL
  SELECT '주의', '필지 경계 3m 이내: 옆 필지일 수 있음 (연속지적도 위치 오차)' FROM nb WHERE nb.near
  UNION ALL
  SELECT '주의', '사유지: 소유자 허락 필요' FROM pa WHERE pa.owner_cd NOT IN ('02','04','05')
  UNION ALL
  SELECT '주의', '필지 속성 정보 없음' FROM pa WHERE pa.jimok IS NULL OR NOT pa.src_char
  UNION ALL
  SELECT '주의', '필지 밖 지점: ' || pa.gap_m || 'm 떨어진 가장 가까운 필지 기준' FROM pa WHERE NOT pa.inside
),
ov AS (
  SELECT o.verdict, o.reason FROM override o, pa
  WHERE o.pnu = pa.pnu
    AND CURRENT_DATE BETWEEN COALESCE(o.valid_from, '-infinity') AND COALESCE(o.valid_to, 'infinity')
  LIMIT 1
),
camp AS (
  SELECT cs.name FROM campsite cs, p WHERE ST_DWithin(cs.geom, p.g, 30) LIMIT 1
),
near_camps AS (
  SELECT COALESCE(jsonb_agg(jsonb_build_object('name', x.name, 'dist_m', x.d)), '[]'::jsonb) AS j
  FROM (
    SELECT cs.name, ROUND(ST_Distance(cs.geom, p.g)) AS d
    FROM campsite cs, p
    ORDER BY cs.geom <-> p.g
    LIMIT 3
  ) x
)
SELECT
  pa.pnu, pa.jibun, pa.jimok, pa.owner_nm, pa.land_use, pa.terrain_h, pa.road_side,
  CASE
    WHEN (SELECT verdict FROM ov) IS NOT NULL                 THEN (SELECT verdict FROM ov)
    WHEN EXISTS (SELECT 1 FROM camp)
         AND NOT EXISTS (SELECT 1 FROM z WHERE z.level = '불가' AND z.category <> '고시') THEN '야영 가능'
    WHEN EXISTS (SELECT 1 FROM rules WHERE rules.verdict = '불가') THEN '불가'
    WHEN EXISTS (SELECT 1 FROM rules WHERE rules.verdict = '주의') THEN '주의'
    ELSE '제한 없음'
  END,
  ARRAY(
    SELECT DISTINCT r.reason FROM rules r WHERE r.verdict IN ('불가','주의')
    UNION SELECT reason FROM ov
    UNION SELECT '등록 야영장: ' || name FROM camp
  ),
  ARRAY(SELECT txt FROM z),
  (SELECT near FROM nb),
  (SELECT j FROM near_camps),
  ST_AsGeoJSON(ST_Transform(pa.geom, 4326))
FROM pa;
$$;


-- =====================================================================
-- 테스트: 종류별로 필지 하나씩 골라 그 안의 점으로 판정해 보기
-- =====================================================================
WITH samples AS (
  (SELECT '규제 불가'  AS kind, c.geom FROM cadastre c JOIN regulation r ON ST_Intersects(c.geom, r.geom)
    WHERE r.level = '불가' LIMIT 1)
  UNION ALL
  (SELECT '규제 주의(하천)', c.geom FROM cadastre c JOIN regulation r ON ST_Intersects(c.geom, r.geom)
    WHERE r.category = '하천' LIMIT 1)
  UNION ALL
  (SELECT '국유 잡종지', c.geom FROM cadastre c JOIN land_attr a USING (pnu)
    WHERE a.jimok = '잡종지' AND a.owner_cd = '02' LIMIT 1)
  UNION ALL
  (SELECT '주차장', c.geom FROM cadastre c JOIN land_attr a USING (pnu)
    WHERE a.jimok = '주차장' LIMIT 1)
  UNION ALL
  (SELECT '개인 밭', c.geom FROM cadastre c JOIN land_attr a USING (pnu)
    WHERE a.jimok = '전' AND a.owner_cd = '01' LIMIT 1)
),
pts AS (
  -- 필지 안에서 경계와 가장 먼 점(최대 내접원 중심)으로 테스트
  SELECT kind, ST_Transform((ST_MaximumInscribedCircle(geom)).center, 4326) AS g FROM samples
)
SELECT pts.kind,
       ROUND(ST_Y(pts.g)::numeric, 6) AS lat,
       ROUND(ST_X(pts.g)::numeric, 6) AS lng,
       v.jibun, v.jimok, v.owner_nm, v.verdict, v.reasons
FROM pts, LATERAL fn_verdict(ST_Y(pts.g), ST_X(pts.g)) v;


-- =====================================================================
-- 시연용 '제한 없음' 후보 찾기
--   차 한 대 이상 들어갈 만큼 넓은 곳만: 내접원 반지름 5m 이상 (= 폭 10m 이상 빈 공간)
-- =====================================================================
WITH cand AS (
  SELECT c.pnu, (ST_MaximumInscribedCircle(c.geom)).*
  FROM cadastre c JOIN land_attr a USING (pnu)
  WHERE a.owner_cd IN ('02','04','05')
    AND a.jimok = '잡종지'
    AND a.land_use IN ('자연림','토지임야','임야기타','주거나지','상업나지','공업나지','주상나지')
    AND a.terrain_h IN ('평지','완경사')
    AND a.road_side NOT IN ('맹지','지정되지않음')
    AND NOT EXISTS (SELECT 1 FROM regulation r WHERE ST_Intersects(r.geom, c.geom))
),
pts AS (
  SELECT pnu, ROUND(radius::numeric, 1) AS radius_m, ST_Transform(center, 4326) AS g
  FROM cand WHERE radius >= 5
)
SELECT v.jibun, v.owner_nm, v.land_use, v.road_side, pts.radius_m, v.verdict, v.reasons,
       ROUND(ST_Y(g)::numeric, 6) AS lat, ROUND(ST_X(g)::numeric, 6) AS lng
FROM pts, LATERAL fn_verdict(ST_Y(g), ST_X(g)) v
ORDER BY v.verdict DESC, pts.radius_m DESC;
