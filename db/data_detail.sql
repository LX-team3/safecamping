-- 캠핑장 상세 · 시설 상세 · 관광지 상세 더미 데이터 (화면설계 스크린샷의 설하캠핑장만)
-- 거리·시간·좌표는 화면설계용 가상 값이다 (is_dummy = true). 전화번호·주소는 지어내지 않고 비워 둔다
-- 인제군보건의료원·원대리 자작나무 숲은 실제 이름이지만 여기 적힌 수치는 실제가 아니다
-- schema.sql → data_camp.sql 을 먼저 실행한 뒤 실행한다. 여러 번 실행해도 이미 있는 줄은 건너뛴다

-- ---------------------------
-- 시설
-- ---------------------------
INSERT INTO facility (type, name, sido, sigungu, eupmyeon, lat, lng, is_dummy) VALUES
    ('fire',   '소방서 · 119안전센터', '강원특별자치도', '인제군', '북면',   38.165000, 128.215000, true),
    ('police', '지구대 · 파출소',      '강원특별자치도', '인제군', '북면',   38.125000, 128.250000, true),
    ('er',     '인제군보건의료원',     '강원특별자치도', '인제군', '인제읍', 38.066000, 128.170000, true),
    ('store',  '24시 편의점',          '강원특별자치도', '인제군', '북면',   38.190000, 128.175000, true)
ON CONFLICT (name, sigungu) DO NOTHING;

INSERT INTO camp_facility (camp_id, facility_id, sort_order, distance_km, drive_min)
SELECT c.id, f.id, v.sort_order, v.distance_km, v.drive_min
  FROM camp c,
       facility f,
       (VALUES
        ('소방서 · 119안전센터', 1,  4.0,  5),
        ('지구대 · 파출소',      2,  6.2,  9),
        ('인제군보건의료원',     3, 12.9, 17),
        ('24시 편의점',          4, 15.8, 22)
       ) AS v (facility_name, sort_order, distance_km, drive_min)
 WHERE c.name = '설하캠핑장' AND c.sigungu = '인제군'
   AND f.name = v.facility_name AND f.sigungu = '인제군'
ON CONFLICT (camp_id, facility_id) DO NOTHING;

-- ---------------------------
-- 편의점 추가 (내 주변 지도 레이어용). 이름·좌표·거리·시간 모두 가상 값 (is_dummy = true)
-- 이름은 실제 가게와 헷갈리지 않게 "지역 + 종류"로만 짓는다
-- ---------------------------
INSERT INTO facility (type, name, sido, sigungu, eupmyeon, lat, lng, is_dummy) VALUES
    ('store', '북면 편의점',   '강원특별자치도', '인제군', '북면',   38.150000, 128.215000, true),
    ('store', '인제읍 편의점', '강원특별자치도', '인제군', '인제읍', 38.070000, 128.175000, true)
ON CONFLICT (name, sigungu) DO NOTHING;

-- 설하캠핑장과 연결해야 지도·상세에 나온다 (기존 1~4 다음 번호)
INSERT INTO camp_facility (camp_id, facility_id, sort_order, distance_km, drive_min)
SELECT c.id, f.id, v.sort_order, v.distance_km, v.drive_min
  FROM camp c,
       facility f,
       (VALUES
        ('북면 편의점',   5,  2.0,  4),
        ('인제읍 편의점', 7, 11.0, 15)
       ) AS v (facility_name, sort_order, distance_km, drive_min)
 WHERE c.name = '설하캠핑장' AND c.sigungu = '인제군'
   AND f.name = v.facility_name AND f.sigungu = '인제군'
ON CONFLICT (camp_id, facility_id) DO NOTHING;

-- ---------------------------
-- 약국 추가 (내 주변 지도 · 경로 지도). 이름·좌표·거리·시간 모두 가상 값 (is_dummy = true)
-- 'pharmacy' 는 schema_02.sql 의 facility.type CHECK 에 들어 있어야 한다 → schema_02.sql 을 먼저 다시 실행
-- 편의점과 따로 둔 이유: CHECK 가 아직 옛날 것이면 이 구간만 실패하고 위 편의점은 그대로 들어간다
-- ---------------------------
INSERT INTO facility (type, name, sido, sigungu, eupmyeon, lat, lng, is_dummy) VALUES
    ('pharmacy', '북면 약국',   '강원특별자치도', '인제군', '북면',   38.155000, 128.225000, true),
    ('pharmacy', '인제읍 약국', '강원특별자치도', '인제군', '인제읍', 38.068000, 128.172000, true)
ON CONFLICT (name, sigungu) DO NOTHING;

INSERT INTO camp_facility (camp_id, facility_id, sort_order, distance_km, drive_min)
SELECT c.id, f.id, v.sort_order, v.distance_km, v.drive_min
  FROM camp c,
       facility f,
       (VALUES
        ('북면 약국',   6,  3.2,  6),
        ('인제읍 약국', 8, 11.5, 16)
       ) AS v (facility_name, sort_order, distance_km, drive_min)
 WHERE c.name = '설하캠핑장' AND c.sigungu = '인제군'
   AND f.name = v.facility_name AND f.sigungu = '인제군'
ON CONFLICT (camp_id, facility_id) DO NOTHING;

-- ---------------------------
-- 관광지
-- ---------------------------
INSERT INTO tour (name, category, category_label, nature, popularity_rank, sido, sigungu, eupmyeon, lat, lng, is_dummy) VALUES
    ('원대리 자작나무 숲', 'trekking', '자연 · 트레킹', 'mountain', 1, '강원특별자치도', '인제군', '인제읍', 38.030000, 128.210000, true),
    ('내린천 래프팅',      'water',    '물놀이 · 레저', 'valley',   2, '강원특별자치도', '인제군', '기린면', 37.990000, 128.280000, true)
ON CONFLICT (name, sigungu) DO NOTHING;

INSERT INTO camp_tour (camp_id, tour_id, drive_min, note)
SELECT c.id, t.id, v.drive_min, v.note
  FROM camp c,
       tour t,
       (VALUES
        ('원대리 자작나무 숲', 25, NULL),
        ('내린천 래프팅',      15, '연령 제한 확인')
       ) AS v (tour_name, drive_min, note)
 WHERE c.name = '설하캠핑장' AND c.sigungu = '인제군'
   AND t.name = v.tour_name AND t.sigungu = '인제군'
ON CONFLICT (camp_id, tour_id) DO NOTHING;

-- ---------------------------
-- 공통 정보 줄
-- ---------------------------
INSERT INTO place_info (place_type, place_id, sort_order, label, value)
SELECT 'camp', c.id, v.sort_order, NULL, v.value
  FROM camp c,
       (VALUES
        (1, '입실 14:00 · 퇴실 11:00'),
        (2, '전기 사용 가능 · 개수대 · 온수 샤워장'),
        (3, '반려동물 동반 불가')
       ) AS v (sort_order, value)
 WHERE c.name = '설하캠핑장' AND c.sigungu = '인제군'
ON CONFLICT (place_type, place_id, sort_order) DO NOTHING;

INSERT INTO place_info (place_type, place_id, sort_order, label, value)
SELECT 'facility', f.id, 1, '진료', '응급실 · 소아 진료 여부 확인 필요'
  FROM facility f
 WHERE f.name = '인제군보건의료원' AND f.sigungu = '인제군'
ON CONFLICT (place_type, place_id, sort_order) DO NOTHING;

-- 값이 NULL 인 줄은 화면에 "확인 필요"로 보인다
INSERT INTO place_info (place_type, place_id, sort_order, label, value)
SELECT 'tour', t.id, v.sort_order, v.label, NULL
  FROM tour t,
       (VALUES (1, '운영'), (2, '걷는 길')) AS v (sort_order, label)
 WHERE t.name = '원대리 자작나무 숲' AND t.sigungu = '인제군'
ON CONFLICT (place_type, place_id, sort_order) DO NOTHING;

-- ---------------------------
-- 공통 안내 상자
-- ---------------------------
INSERT INTO place_note (place_type, place_id, sort_order, level, body)
SELECT 'facility', f.id, 1, 'danger', '직접 운전하기보다 119에 먼저 전화하세요. 구급차가 더 빠르고 안전할 수 있어요.'
  FROM facility f
 WHERE f.name = '인제군보건의료원' AND f.sigungu = '인제군'
ON CONFLICT (place_type, place_id, sort_order) DO NOTHING;

INSERT INTO place_note (place_type, place_id, sort_order, level, body)
SELECT 'tour', t.id, v.sort_order, v.level, v.body
  FROM tour t,
       (VALUES
        (1, 'caution', '겨울엔 산길이 미끄러워요. 아이와 함께라면 아이젠을 챙기세요.'),
        (2, 'safe',    '입구 주차장 근처에 화장실이 있어요.')
       ) AS v (sort_order, level, body)
 WHERE t.name = '원대리 자작나무 숲' AND t.sigungu = '인제군'
ON CONFLICT (place_type, place_id, sort_order) DO NOTHING;
