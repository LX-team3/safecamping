-- =====================================================================
-- 05 가족방 위치 공유 (가족방 3차): family_location 테이블
-- 작성: 2026-11-16
--
-- 실행 순서: schema_04.sql 다음. 새 DB 를 처음부터 만들 때도 schema.sql(member · family_member) 뒤면 된다
-- 꼭 함께 반영: 서버 family/Location* (GET /api/family/locations)
-- 중간에 에러가 나면 전체가 취소된다 (BEGIN ~ COMMIT). 여러 번 실행해도 결과는 같다 (IF NOT EXISTS)
-- 공유 DB 에 넣기 전에 반드시 로컬 DB 에서 먼저 돌려 보고, 공유 DB 는 백업 덤프를 뜬 뒤 실행한다
-- =====================================================================

BEGIN;

-- 가족방 위치 공유. 회원마다 "마지막 위치" 한 줄만 둔다 (이동 기록은 남기지 않음)
-- 가족방 번호는 따로 저장하지 않는다 → family_member 와 이어서 "내 방 사람들" 위치만 읽는다
--   (방을 나가면 family_member 에서 빠지므로 자연스럽게 안 보인다)
-- 지금(시연)은 아래 시드처럼 SQL 로 넣은 좌표를 읽기만 한다. 실제 GPS 로 갱신하는 기능은 아직 없다
CREATE TABLE IF NOT EXISTS family_location (
    id          bigserial         PRIMARY KEY,
    member_id   bigint            NOT NULL UNIQUE REFERENCES member (id) ON DELETE CASCADE,  -- 한 회원당 한 줄. 탈퇴하면 같이 지운다
    lon         double precision  NOT NULL,   -- 경도 (WGS84). meeting_place 와 같은 방식
    lat         double precision  NOT NULL,   -- 위도
    updated_at  timestamptz       NOT NULL DEFAULT now()  -- 마지막으로 위치가 들어온 시각
);

COMMIT;

-- =====================================================================
-- 시연용 가상 좌표 넣기 (따로 실행) — 실제 GPS 위치가 아니다
-- 먼저 가상 회원이 가입하고 같은 가족방에 들어가 있어야 한다. 아래 '아이디' 를 실제 login_id 로 바꿔서 실행
-- ON CONFLICT: 이미 그 회원 줄이 있으면 좌표만 바꾼다 → 여러 번 실행해도 된다
-- 좌표: 인제군청(강원 인제군 인제읍) 근처 대략값 (client/src/data/familyLocationDummy.js 와 같은 값)
-- =====================================================================
-- INSERT INTO family_location (member_id, lon, lat)
-- SELECT m.id, v.lon, v.lat
--   FROM (VALUES ('아이디1', 128.1700, 38.0699),
--                ('아이디2', 128.1706, 38.0695),
--                ('아이디3', 128.1700, 38.0633)) AS v(login_id, lon, lat)
--   JOIN member m ON m.login_id = v.login_id
-- ON CONFLICT (member_id) DO UPDATE SET lon = EXCLUDED.lon, lat = EXCLUDED.lat, updated_at = now();

-- =====================================================================
-- 실행 후 확인용 (따로 실행)
-- =====================================================================
-- 1. 테이블 컬럼 → id · member_id · lon · lat · updated_at
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'family_location' ORDER BY ordinal_position;
-- 2. 들어간 좌표와 회원 이름
-- SELECT m.login_id, m.name, l.lon, l.lat, l.updated_at FROM family_location l JOIN member m ON m.id = l.member_id;
