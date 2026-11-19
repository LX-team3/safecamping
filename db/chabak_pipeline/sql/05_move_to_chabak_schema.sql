\encoding UTF8
-- =====================================================================
-- [내 컴퓨터 chabak DB 에서 실행]
-- 공용 DB 로 옮기기 전 준비
--   1. 정리 테이블·함수를 public -> chabak 스키마로 옮긴다 (공용 DB 의 public 과 섞이지 않게)
--   2. 서버가 쓰기 편한 JSON 함수 fn_verdict_json 을 만든다
--   3. 주변 대안 추천용 '제한 없음' 자리를 미리 계산해 둔다 (open_spot)
-- raw 스키마(SHP 원본)는 옮기지 않는다 → 공용 DB 용량 절약
-- 실행: psql -U postgres -d chabak -P pager=off -f 05_move_to_chabak_schema.sql -o result05.txt
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS chabak;

-- ---------------------------------------------------------------------
-- 1. public -> chabak 로 옮기기 (인덱스·시퀀스도 같이 옮겨진다)
--    이미 옮겼으면 건너뛴다 → 여러 번 실행해도 괜찮다
-- ---------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['cadastre','land_attr','regulation','jimok_rule','land_use_rule',
                           'terrain_rule','road_rule','seasonal_rule','override','campsite']
  LOOP
    IF to_regclass('public.' || t) IS NOT NULL AND to_regclass('chabak.' || t) IS NULL THEN
      EXECUTE format('ALTER TABLE public.%I SET SCHEMA chabak', t);
    END IF;
  END LOOP;

  IF to_regprocedure('public.fn_verdict(double precision,double precision)') IS NOT NULL THEN
    ALTER FUNCTION public.fn_verdict(double precision, double precision) SET SCHEMA chabak;
  END IF;
END $$;

-- 함수 안의 테이블 이름(cadastre 등)을 chabak 에서 찾도록 고정. PostGIS 함수는 public 에 있어서 같이 넣는다
ALTER FUNCTION chabak.fn_verdict(double precision, double precision) SET search_path = chabak, public;


-- ---------------------------------------------------------------------
-- 2. 주변 대안: '제한 없음' 자리를 미리 계산해 둔 테이블
--    판정 함수를 클릭할 때마다 주변 수백 필지에 돌리면 느려서, 한 번 계산해 저장한다
--    규칙을 바꾸면 이 블록만 다시 실행하면 된다
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS chabak.open_spot;
CREATE TABLE chabak.open_spot AS
WITH cand AS (
  SELECT c.pnu, c.jibun, a.owner_nm, a.land_use, (ST_MaximumInscribedCircle(c.geom)).*
  FROM chabak.cadastre c
  JOIN chabak.land_attr a USING (pnu)
  WHERE a.owner_cd IN ('02','04','05')
    AND a.jimok = '잡종지'
    AND a.land_use IN ('자연림','토지임야','임야기타','주거나지','상업나지','공업나지','주상나지')
    AND a.terrain_h IN ('평지','완경사')
    AND a.road_side NOT IN ('맹지','지정되지않음','세로한면(불)','세로각지(불)')
    AND NOT EXISTS (SELECT 1 FROM chabak.regulation r WHERE ST_Intersects(r.geom, c.geom))
)
SELECT cand.pnu, cand.jibun, cand.owner_nm, cand.land_use,
       ROUND(cand.radius::numeric, 1)            AS radius_m,   -- 내접원 반지름 (차 댈 공간 크기)
       cand.center::geometry(Point, 5186)         AS geom
FROM cand,
     LATERAL (SELECT ST_Transform(cand.center, 4326) AS g) p,
     LATERAL chabak.fn_verdict(ST_Y(p.g), ST_X(p.g)) v
WHERE cand.radius >= 5
  AND v.verdict = '제한 없음';

CREATE INDEX ON chabak.open_spot USING GIST (geom);


-- ---------------------------------------------------------------------
-- 3. 서버용 JSON 함수
--    SELECT chabak.fn_verdict_json(38.078121, 128.324378);
--    필지가 없는 곳(인제군 밖 등)이면 NULL
--    alternatives: 가까운 '제한 없음' 자리 3곳 (판정이 '제한 없음'·'야영 가능'이 아닐 때만)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION chabak.fn_verdict_json(p_lat double precision, p_lng double precision)
RETURNS jsonb
LANGUAGE sql STABLE
SET search_path = chabak, public
AS $$
WITH v AS (
  SELECT * FROM chabak.fn_verdict(p_lat, p_lng)
),
p AS (
  SELECT ST_Transform(ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326), 5186) AS g
),
alt AS (
  SELECT COALESCE(jsonb_agg(jsonb_build_object(
           'jibun',    x.jibun,
           'owner',    x.owner_nm,
           'radiusM',  x.radius_m,
           'distM',    x.dist_m,
           'lat',      ROUND(ST_Y(ST_Transform(x.geom, 4326))::numeric, 6),
           'lng',      ROUND(ST_X(ST_Transform(x.geom, 4326))::numeric, 6)
         ) ORDER BY x.dist_m), '[]'::jsonb) AS j
  FROM (
    SELECT o.*, ROUND(ST_Distance(o.geom, p.g)) AS dist_m
    FROM chabak.open_spot o, p
    ORDER BY o.geom <-> p.g
    LIMIT 3
  ) x
)
SELECT jsonb_build_object(
         'pnu',          v.pnu,
         'jibun',        v.jibun,
         'jimok',        v.jimok,
         'owner',        v.owner_nm,
         'landUse',      v.land_use,
         'terrain',      v.terrain_h,
         'roadSide',     v.road_side,
         'verdict',      v.verdict,
         'reasons',      to_jsonb(v.reasons),
         'zones',        to_jsonb(v.zones),
         'nearBoundary', v.near_boundary,
         'nearbyCamps',  v.nearby_camps,
         'alternatives', CASE WHEN v.verdict IN ('제한 없음','야영 가능') THEN '[]'::jsonb ELSE alt.j END,
         'parcel',       v.parcel_geojson::jsonb
       )
FROM v, alt;
$$;


-- =====================================================================
-- 확인
-- =====================================================================
SELECT table_name FROM information_schema.tables WHERE table_schema = 'chabak' ORDER BY 1;
SELECT COUNT(*) AS open_spot_count FROM chabak.open_spot;
SELECT jsonb_pretty(chabak.fn_verdict_json(38.202067, 128.203422));  -- 844-1 (주의: 좁은 길) → alternatives 3곳이 나와야 함
