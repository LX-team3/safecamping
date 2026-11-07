-- 지역 데이터: 시·도 17곳 + 강원특별자치도 시·군 18곳
-- 다른 시·도의 시·군·구는 아직 없다 (화면에 "준비 중"). 전체는 행정표준코드(법정동코드) 파일로 넣을 예정
-- schema.sql 을 먼저 실행한 뒤 실행한다. 여러 번 실행해도 이미 있는 줄은 건너뛴다
INSERT INTO sido (name, sort_order) VALUES
    ('서울특별시', 1), ('부산광역시', 2), ('대구광역시', 3), ('인천광역시', 4),
    ('광주광역시', 5), ('대전광역시', 6), ('울산광역시', 7), ('세종특별자치시', 8),
    ('경기도', 9), ('강원특별자치도', 10), ('충청북도', 11), ('충청남도', 12),
    ('전북특별자치도', 13), ('전라남도', 14), ('경상북도', 15), ('경상남도', 16),
    ('제주특별자치도', 17)
ON CONFLICT (name) DO NOTHING;

-- 강원특별자치도 7시 11군 (순서는 행정구역 코드 순)
INSERT INTO sigungu (sido_id, name, sort_order, is_safety_ready)
SELECT s.id, v.name, v.sort_order, v.ready
  FROM sido s,
       (VALUES
        ('춘천시', 1, false), ('원주시', 2, false), ('강릉시', 3, false), ('동해시', 4, false),
        ('태백시', 5, false), ('속초시', 6, false), ('삼척시', 7, false), ('홍천군', 8, false),
        ('횡성군', 9, false), ('영월군', 10, false), ('평창군', 11, false), ('정선군', 12, false),
        ('철원군', 13, false), ('화천군', 14, false), ('양구군', 15, false), ('인제군', 16, true),
        ('고성군', 17, false), ('양양군', 18, false)
       ) AS v (name, sort_order, ready)
 WHERE s.name = '강원특별자치도'
ON CONFLICT (sido_id, name) DO NOTHING;
