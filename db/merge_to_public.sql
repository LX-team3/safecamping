-- =====================================================================
-- campin 원본 테이블(복수형) → 서버 화면 테이블(단수형) 채우기  — 모두 public 스키마 안에서
-- 작성: 2026-11-09
--
-- 실행 전 준비
--   1) public 스키마에 아래가 모두 있어야 한다 (이 순서로 실행)
--        campin 덤프(01_schema.sql, 02_data.sql) — camps · facilities · spot · score_weights · er_detail
--        schema.sql → schema_02.sql → data_region.sql (data_camp.sql · data_detail.sql 더미는 돌리지 않아도 됨)
--   2) pgAdmin 이면 이 파일 전체를 한 번에 실행한다 (Ctrl+A → F5)
--   3) 먼저 테스트용 DB 에서 돌려 보고, 문제없으면 공용 DB 에 넣는다
--
-- 이 파일이 하는 일 (자세한 내용은 MERGE_CHANGELOG.md)
--   0. 거리 계산 함수(임시) 만들기 — PostGIS 없이 위도·경도로 계산
--   1. 화면 테이블의 더미 데이터 지우기 (camp · facility · tour 와 거기 딸린 줄)
--   2. facility.type 허용 값에 pharmacy(약국) 추가, 원본 id 를 기록할 source_id 칸 추가
--   3. 캠핑장 23곳 옮기기       camps      → camp
--   4. 안전시설 옮기기          facilities → facility (응급실·소방서·경찰서·편의점)
--   5. 관광지 26곳 옮기기       spot       → tour
--   6. 캠핑장별 가까운 시설 4곳 계산  → camp_facility
--   7. 캠핑장별 가까운 관광지 5곳 계산 → camp_tour
--   8. 안전점수 · 응급실 시간 계산     → camp.safety_score · camp.er_drive_min
--   9. 상세 화면 정보 줄 · 안내 상자    → place_info · place_note
--  10. id 순번(시퀀스) 맞추기
--
-- 원본 테이블(camps · facilities · spot · score_weights · er_detail)은 하나도 고치지 않는다 (공용 데이터라서). 오타 등은 옮기면서만 바로잡는다
-- 중간에 에러가 나면 전체가 취소된다 (BEGIN ~ COMMIT). 여러 번 실행해도 결과는 같다
-- =====================================================================

BEGIN;
SET LOCAL search_path TO public;

-- ---------------------------------------------------------------------
-- 0. 두 점 사이 직선거리(m). 하버사인 공식. 이 접속 동안만 있는 임시 함수
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.dist_m(lat1 float8, lng1 float8, lat2 float8, lng2 float8)
RETURNS float8 LANGUAGE sql IMMUTABLE AS $$
    SELECT 2 * 6371000 * asin(sqrt(
             power(sin(radians(lat2 - lat1) / 2), 2)
           + cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lng2 - lng1) / 2), 2)))
$$;

-- 차로 걸리는 시간(분) 추정. 실제 도로 경로가 없어서 "직선거리 × 1.4(굽은 길) ÷ 시속 45km" 로 잡는다
CREATE OR REPLACE FUNCTION pg_temp.drive_min(dist_m float8)
RETURNS int LANGUAGE sql IMMUTABLE AS $$
    SELECT greatest(1, ceil(dist_m * 1.4 / 1000 / 45 * 60))::int
$$;

-- 주소에서 읍·면 뽑기 (예: '강원 인제군 북면 만해로 197' → '북면')
CREATE OR REPLACE FUNCTION pg_temp.eupmyeon(addr text)
RETURNS varchar LANGUAGE sql IMMUTABLE AS $$
    SELECT (regexp_match(addr, '\s(\S+[읍면])\s'))[1]
$$;

-- ---------------------------------------------------------------------
-- 1. 더미 데이터 지우기
-- ---------------------------------------------------------------------
DELETE FROM place_info WHERE place_type = 'camp'     AND place_id IN (SELECT id FROM camp);
DELETE FROM place_note WHERE place_type = 'camp'     AND place_id IN (SELECT id FROM camp);
DELETE FROM place_info WHERE place_type = 'facility' AND place_id IN (SELECT id FROM facility);
DELETE FROM place_note WHERE place_type = 'facility' AND place_id IN (SELECT id FROM facility);
DELETE FROM place_info WHERE place_type = 'tour'     AND place_id IN (SELECT id FROM tour);
DELETE FROM place_note WHERE place_type = 'tour'     AND place_id IN (SELECT id FROM tour);
DELETE FROM camp_facility;
DELETE FROM camp_tour;
DELETE FROM camp;
DELETE FROM facility;
DELETE FROM tour;

-- ---------------------------------------------------------------------
-- 2. 스키마 조금 바꾸기
-- ---------------------------------------------------------------------
-- 약국(pharmacy) 허용. 화장실(toilet)은 안 쓰기로 했지만 서버 코드가 쓸 수 있어 남겨 둔다
ALTER TABLE facility DROP CONSTRAINT IF EXISTS facility_type_check;
ALTER TABLE facility ADD  CONSTRAINT facility_type_check
    CHECK (type IN ('er', 'fire', 'police', 'store', 'pharmacy', 'toilet'));

-- 원본(public) id 를 남겨 두는 칸. 나중에 원본이 바뀌면 이 칸으로 찾아 다시 맞출 수 있다
ALTER TABLE camp     ADD COLUMN IF NOT EXISTS source_id varchar(20);  -- public.camps.content_id
ALTER TABLE facility ADD COLUMN IF NOT EXISTS source_id varchar(60);  -- public.facilities.id
ALTER TABLE tour     ADD COLUMN IF NOT EXISTS source_id varchar(20);  -- public.spot.spot_id

-- ---------------------------------------------------------------------
-- 3. 캠핑장 23곳: public.camps → camp
--    id 는 고캠핑 번호(content_id)를 그대로 쓴다 (예: 101289) → 원본과 바로 맞춰 볼 수 있다
-- ---------------------------------------------------------------------
INSERT INTO camp (id, source_id, name, sido, sigungu, eupmyeon, camp_type, lat, lng,
                  safety_score, er_drive_min, image_url, reservation_url, is_dummy)
SELECT c.content_id::bigint,
       c.content_id,
       replace(c.name, '캠핌장', '캠핑장'),                         -- 오타 바로잡기 (아이호 캠핌장)
       '강원특별자치도',                                             -- 주소의 '강원'·'강원도' 표기를 공식 이름으로 통일
       '인제군',
       pg_temp.eupmyeon(c.address),
       CASE WHEN c.camp_types LIKE '%글램핑%' THEN 'glamping' ELSE 'auto' END,  -- 일반·자동차야영장 → auto
       round(c.lat, 6),
       round(c.lng, 6),
       NULL, NULL,                                                   -- 8 단계에서 계산해 채운다
       CASE WHEN c.image_url ~* '^https?://' AND c.image_url !~* 'blog\.naver\.com'
             AND length(c.image_url) <= 300 THEN c.image_url END,    -- 블로그 글 주소는 사진이 아니라 뺀다
       CASE WHEN c.homepage ~* '^https?://' AND length(c.homepage) <= 300 THEN c.homepage END,  -- '전화예약' 같은 글자는 뺀다
       false
  FROM public.camps c
 WHERE c.address LIKE '%인제군%';

-- ---------------------------------------------------------------------
-- 4. 안전시설: public.facilities → facility
--    화면(캠핑장 상세 · 시설 상세)에 보여 줄 응급실·소방서·경찰서·편의점만 옮긴다
--    보안등·CCTV 는 옮기지 않는다 → 안전점수 계산(8 단계)에서 public.facilities 를 직접 읽는다
-- ---------------------------------------------------------------------
INSERT INTO facility (source_id, type, name, sido, sigungu, eupmyeon, address, phone, hours, lat, lng, is_dummy)
SELECT f.id,
       f.type,
       f.name,
       '강원특별자치도',
       r.sigungu,
       pg_temp.eupmyeon(f.address),
       f.address,
       f.phone,
       CASE f.hours WHEN '24h' THEN '24시간' ELSE f.hours END,
       round(f.lat::numeric, 6),
       round(f.lng::numeric, 6),
       false
  FROM public.facilities f
  JOIN (VALUES ('51110', '춘천시'), ('51130', '원주시'), ('51150', '강릉시'), ('51170', '동해시'),
               ('51190', '태백시'), ('51210', '속초시'), ('51230', '삼척시'), ('51720', '홍천군'),
               ('51730', '횡성군'), ('51750', '영월군'), ('51760', '평창군'), ('51770', '정선군'),
               ('51780', '철원군'), ('51790', '화천군'), ('51800', '양구군'), ('51810', '인제군'),
               ('51820', '고성군'), ('51830', '양양군')) AS r (code, sigungu)
    ON r.code = f.region_code
 WHERE f.type IN ('er', 'fire', 'police', 'store', 'pharmacy')
   AND f.is_active
   AND f.lat IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5. 관광지: public.spot → tour (spot 이 있는 만큼, 덤프 기준 26곳)
--    spot.themes 표기가 DB 마다 달라서(예: 덤프 '트레킹' / 공용 DB '산행·트레킹', '숲·호수 산책')
--    값이 정확히 같은지가 아니라 "키워드가 들어 있는지"로 고른다. 여러 개가 맞으면 prio 가 작은 것 하나
--    category · nature 는 서버 TourPreferenceOptions 의 code 만 쓴다
--      category: trekking · water · activity · food · culture · stars · festival · rest
--      nature  : mountain · valley · lake · sea · field
--    어느 키워드에도 안 맞으면 마지막 줄(빈 키워드)로 '관광지'(culture) — 관광지가 통째로 빠지지 않게
-- ---------------------------------------------------------------------
INSERT INTO tour (source_id, name, category, category_label, nature, popularity_rank,
                  sido, sigungu, eupmyeon, lat, lng, is_dummy)
SELECT DISTINCT ON (s.spot_id)
       s.spot_id::text,
       s.name,
       m.category,
       m.category_label,
       m.nature,
       NULL,                                   -- 인기 순위는 근거 데이터가 없어 비워 둔다
       '강원특별자치도',
       '인제군',
       pg_temp.eupmyeon(s.address),
       round(s.latitude, 6),
       round(s.longitude, 6),
       false
  FROM public.spot s
  JOIN (VALUES
        ( 1, '박물관',   'culture',  '문화 · 전시',      NULL),
        ( 2, '미술관',   'culture',  '문화 · 전시',      NULL),
        ( 3, '전시관',   'culture',  '문화 · 전시',      NULL),
        ( 4, '사찰',     'culture',  '역사 · 유적',      'mountain'),
        ( 5, '역사',     'culture',  '역사 · 유적',      NULL),
        ( 6, '유적',     'culture',  '역사 · 유적',      NULL),
        ( 7, 'DMZ',      'trekking', '생태 · 탐방',      NULL),
        ( 8, '생태',     'trekking', '생태 · 탐방',      NULL),
        ( 9, '레저',     'activity', '레저 · 액티비티',  NULL),
        (10, '액티비티', 'activity', '레저 · 액티비티',  NULL),
        (11, '휴양림',   'trekking', '자연 · 휴양림',    'mountain'),
        (12, '산행',     'trekking', '자연 · 트레킹',    'mountain'),
        (13, '트레킹',   'trekking', '자연 · 트레킹',    'mountain'),
        (14, '호수',     'trekking', '숲 · 호수 산책',   'lake'),
        (15, '계곡',     'water',    '물놀이 · 계곡',    'valley'),
        (16, '마을',     'culture',  '마을 · 체험',      NULL),
        (17, '체험',     'activity', '레저 · 체험',      NULL),
        (18, '자연',     'trekking', '자연 · 트레킹',    'mountain'),
        (19, '숲',       'trekking', '숲 · 산책',        'mountain'),
        (20, '절',       'culture',  '역사 · 유적',      'mountain'),  -- 덤프의 '절' (다른 낱말에 섞일 수 있어 뒤로 둠)
        (99, '',         'culture',  '관광지',           NULL)         -- 어느 것에도 안 맞을 때
       ) AS m (prio, keyword, category, category_label, nature)
    ON coalesce(s.themes, '') LIKE '%' || m.keyword || '%'
 ORDER BY s.spot_id, m.prio;

-- ---------------------------------------------------------------------
-- 6. 캠핑장별 가까운 시설: 종류(응급실·소방서·경찰서·편의점)마다 가장 가까운 1곳 → 최대 4줄
--    sort_order 는 가까운 순서 (화면의 1·2·3·4 번호)
--    distance_km 는 직선거리, drive_min 은 추정값 (0 단계 공식)
-- ---------------------------------------------------------------------
INSERT INTO camp_facility (camp_id, facility_id, sort_order, distance_km, drive_min, is_dummy)
SELECT camp_id, facility_id,
       row_number() OVER (PARTITION BY camp_id ORDER BY d),
       round((d / 1000)::numeric, 1),
       pg_temp.drive_min(d),
       false
  FROM (SELECT DISTINCT ON (c.id, f.type)
               c.id AS camp_id, f.id AS facility_id,
               pg_temp.dist_m(c.lat::float8, c.lng::float8, f.lat::float8, f.lng::float8) AS d
          FROM camp c CROSS JOIN facility f
         ORDER BY c.id, f.type, d) nearest;

-- ---------------------------------------------------------------------
-- 7. 캠핑장별 가까운 관광지: 직선 40km 안에서 가까운 5곳
-- ---------------------------------------------------------------------
INSERT INTO camp_tour (camp_id, tour_id, drive_min, note, is_dummy)
SELECT camp_id, tour_id, pg_temp.drive_min(d), NULL, false
  FROM (SELECT c.id AS camp_id, t.id AS tour_id,
               pg_temp.dist_m(c.lat::float8, c.lng::float8, t.lat::float8, t.lng::float8) AS d,
               row_number() OVER (PARTITION BY c.id
                                  ORDER BY pg_temp.dist_m(c.lat::float8, c.lng::float8, t.lat::float8, t.lng::float8)) AS rn
          FROM camp c CROSS JOIN tour t) x
 WHERE rn <= 5 AND d <= 40000;

-- ---------------------------------------------------------------------
-- 8. 안전점수 · 응급실 시간
--    종류마다 가장 가까운 시설까지 거리 d 를 재서
--      d ≤ full_dist_m → 만점,  d ≥ zero_dist_m → 0점,  그 사이 → 거리에 비례해 줄어듦
--    점수 = (받은 가중치 합 ÷ 데이터가 있는 종류의 가중치 합) × 100
--      → 데이터가 하나도 없는 종류(지금은 약국)는 계산에서 빼고 100점 만점으로 환산
--    쓰는 시설: 운영 중(is_active)이고 좌표가 있는 것. CCTV 는 방범용(role='safety')만
--    public.facilities 를 직접 읽으므로 보안등·CCTV 도 들어간다
-- ---------------------------------------------------------------------
WITH src AS (
    SELECT type, lat, lng
      FROM public.facilities
     WHERE is_active AND lat IS NOT NULL
       AND type IN (SELECT facility_type FROM public.score_weights)
       AND NOT (type = 'cctv' AND role <> 'safety')
),
w AS (   -- 데이터가 실제로 있는 종류만
    SELECT sw.* FROM public.score_weights sw
     WHERE EXISTS (SELECT 1 FROM src WHERE src.type = sw.facility_type)
),
nearest AS (
    SELECT c.id AS camp_id, w.facility_type, w.weight, w.full_dist_m, w.zero_dist_m,
           min(pg_temp.dist_m(c.lat::float8, c.lng::float8, s.lat, s.lng)) AS d
      FROM camp c
      CROSS JOIN w
      JOIN src s ON s.type = w.facility_type
     GROUP BY c.id, w.facility_type, w.weight, w.full_dist_m, w.zero_dist_m
),
scored AS (
    SELECT camp_id,
           sum(weight * CASE WHEN d <= full_dist_m THEN 1
                             WHEN d >= zero_dist_m THEN 0
                             ELSE (zero_dist_m - d) / (zero_dist_m - full_dist_m) END)
             / sum(weight) * 100 AS score,
           max(d) FILTER (WHERE facility_type = 'er') AS er_d
      FROM nearest
     GROUP BY camp_id
)
UPDATE camp c
   SET safety_score = round(s.score)::int,
       er_drive_min = pg_temp.drive_min(s.er_d)
  FROM scored s
 WHERE s.camp_id = c.id;

-- ---------------------------------------------------------------------
-- 9. 상세 화면 정보 줄 (값이 NULL 이면 화면에 "확인 필요") · 안내 상자
-- ---------------------------------------------------------------------
-- 9-1. 캠핑장 정보 줄
INSERT INTO place_info (place_type, place_id, sort_order, label, value)
SELECT 'camp', c.content_id::bigint, v.sort_order, v.label, left(v.value, 200)
  FROM public.camps c
 CROSS JOIN LATERAL (VALUES
        (1, '주소',     c.address),
        (2, '전화',     CASE WHEN c.tel ~ '^507-' THEN '0' || c.tel ELSE c.tel END),  -- 앞자리 0 빠진 번호 바로잡기
        (3, '운영',     c.status),
        (4, '가격',     c.price_min),
        (5, '반려동물', CASE WHEN c.pet_allowed IS NULL THEN NULL
                             WHEN c.pet_allowed THEN coalesce(c.pet_note, '가능')
                             ELSE '불가' END),
        (6, '편의시설', c.facilities_raw),
        (7, 'CO 경보기', CASE WHEN c.has_co_alarm IS NULL THEN NULL
                              WHEN c.has_co_alarm THEN '있음' ELSE '없음' END)
       ) AS v (sort_order, label, value)
 WHERE EXISTS (SELECT 1 FROM camp WHERE camp.id = c.content_id::bigint);

-- 9-2. 캠핑장 안내 상자
INSERT INTO place_note (place_type, place_id, sort_order, level, body)
SELECT 'camp', id, row_number() OVER (PARTITION BY id ORDER BY ord), level, body
  FROM (
        SELECT content_id::bigint AS id, 1 AS ord, 'danger' AS level,
               '지금은 휴장 중이에요. 가기 전에 꼭 전화로 확인하세요.' AS body
          FROM public.camps WHERE status = '휴장'
        UNION ALL
        SELECT content_id::bigint, 2, 'caution',
               '일산화탄소 경보기가 확인되지 않았어요. 텐트 안 난방을 한다면 개인 경보기를 꼭 챙기세요.'
          FROM public.camps WHERE has_co_alarm IS DISTINCT FROM true
        UNION ALL
        SELECT content_id::bigint, 3, 'safe',
               '일산화탄소 경보기가 있는 캠핑장이에요.'
          FROM public.camps WHERE has_co_alarm
       ) n
 WHERE EXISTS (SELECT 1 FROM camp WHERE camp.id = n.id);

-- 9-3. 시설 정보 줄
INSERT INTO place_info (place_type, place_id, sort_order, label, value)
SELECT 'facility', f.id, v.sort_order, v.label, v.value
  FROM facility f
  LEFT JOIN public.er_detail e ON e.id = f.source_id
 CROSS JOIN LATERAL (VALUES
        (1, '운영', f.hours),
        (2, '구분', (SELECT pf.category FROM public.facilities pf WHERE pf.id = f.source_id)),
        (3, '진료', CASE WHEN f.type = 'er' THEN coalesce(e.er_note, '소아 진료 여부 확인 필요') END)
       ) AS v (sort_order, label, value)
 WHERE v.sort_order <> 3 OR f.type = 'er';

-- 9-4. 응급실 안내 상자 (더미에 있던 문구를 모든 응급실에 붙인다)
INSERT INTO place_note (place_type, place_id, sort_order, level, body)
SELECT 'facility', id, 1, 'danger',
       '직접 운전하기보다 119에 먼저 전화하세요. 구급차가 더 빠르고 안전할 수 있어요.'
  FROM facility WHERE type = 'er';

-- 9-5. 관광지 정보 줄
INSERT INTO place_info (place_type, place_id, sort_order, label, value)
SELECT 'tour', t.id, v.sort_order, v.label, nullif(left(v.value, 200), '')
  FROM tour t
  JOIN public.spot s ON s.spot_id::text = t.source_id
 CROSS JOIN LATERAL (VALUES
        (1, '운영', s.open_hours),
        (2, '휴무', s.closed_days),
        (3, '요금', s.fee),
        (4, '전화', s.tel),
        (5, '주소', s.address)
       ) AS v (sort_order, label, value);

-- 9-6. 관광지 안내 상자 (spot.safety_info 가 있는 곳만)
INSERT INTO place_note (place_type, place_id, sort_order, level, body)
SELECT 'tour', t.id, 1, 'caution', left(s.safety_info, 200)
  FROM tour t
  JOIN public.spot s ON s.spot_id::text = t.source_id
 WHERE nullif(trim(s.safety_info), '') IS NOT NULL;

-- ---------------------------------------------------------------------
-- 10. id 순번 맞추기 (camp 는 id 를 직접 넣었으므로 다음 번호가 겹치지 않게)
-- ---------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('camp', 'id'), (SELECT max(id) FROM camp));

-- 인제군 안심 데이터 준비 완료 표시 (이미 true 면 그대로)
UPDATE sigungu SET is_safety_ready = true WHERE name = '인제군';

COMMIT;

-- =====================================================================
-- 실행 후 확인용 (따로 실행)
-- =====================================================================
-- SELECT name, eupmyeon, camp_type, safety_score, er_drive_min FROM camp ORDER BY safety_score DESC;
-- SELECT CASE WHEN safety_score >= 70 THEN '안전' WHEN safety_score >= 40 THEN '주의' ELSE '위험' END AS 등급, count(*)
--   FROM camp GROUP BY 1;
-- SELECT c.name, f.type, f.name, cf.distance_km, cf.drive_min
--   FROM camp_facility cf JOIN camp c ON c.id = cf.camp_id JOIN facility f ON f.id = cf.facility_id
--  ORDER BY c.name, cf.sort_order;
