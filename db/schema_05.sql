-- =====================================================================
-- 05 가족방 위치 공유 (가족방 3차): family_location 테이블
-- 작성: 2026-11-16
--
-- 실행 순서: schema_04.sql 다음. 새 DB 를 처음부터 만들 때도 schema.sql(member · family_member) 뒤면 된다
-- 꼭 함께 반영: 서버 family/Location* (GET /api/family/locations)
-- 중간에 에러가 나면 전체가 취소된다 (BEGIN ~ COMMIT). 여러 번 실행해도 결과는 같다 (IF NOT EXISTS)
-- 공유 DB 에 넣기 전에 반드시 로컬 DB 에서 먼저 돌려 보고, 공유 DB 는 백업 덤프를 뜬 뒤 실행한다
-- 05 내 캠핑(camp_plan)에 "캠핑장이 아닌 자리"도 등록하기 (차박 후보지 · 지도에서 찍은 자리)
-- 작성: 2026-11-18
--
-- 왜: camp_plan.camp_id 가 NOT NULL + camp 표 외래키라서 camp 표에 없는 차박 후보지는 등록할 수 없었다
-- 바꾸는 것:
--   1. camp_id 를 비워 둘 수 있게 (외래키는 그대로 — 값이 있으면 camp 에 있는 캠핑장이어야 함)
--   2. 자리 좌표 lat · lng 칸 추가 (camp_id 가 없는 줄에만 채움)
--   3. "캠핑장 번호도 좌표도 없는 줄"은 못 들어오게 CHECK
--   4. 같은 자리 · 같은 체크인 중복 등록 방지 (기존 UNIQUE(member_id, camp_id, check_in) 은 camp_id 가 NULL 이면 못 막음)
-- 기존 데이터는 그대로다 (칸만 늘리고 지우는 것 없음). 여러 번 실행해도 결과는 같다. 에러가 나면 전체 취소 (BEGIN ~ COMMIT)
--
-- 실행 순서: schema_04.sql 다음
-- 꼭 함께 반영: 서버 mycamping/ 코드 (campId 선택 · lat·lng 추가) — DB 를 먼저 바꾸고 서버를 다시 켠다
-- teamuser 로 실행 가능 (테이블 주인이 teamuser). 공유 DB 는 백업 덤프를 뜬 뒤 실행한다
-- =====================================================================

BEGIN;

-- 가족방 위치 공유. 회원마다 "마지막 위치" 한 줄만 둔다 (이동 기록은 남기지 않음)
-- 가족방 번호는 따로 저장하지 않는다 → family_member 와 이어서 "내 방 사람들" 위치만 읽는다
--   (방을 나가면 family_member 에서 빠지므로 자연스럽게 안 보인다)
-- 위치 탭에서 공유에 동의하면 서버(PUT /api/family/locations/me)가 브라우저 GPS 값을 넣고, 끄거나 방을 나가면 지운다
-- (테이블 정의는 그대로. 이 주석만 2026-11-18 에 바꿈)
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
-- 좌표: 인제군청(강원 인제군 인제읍) 근처 대략값. 앱에서 동의하기·새로고침을 누르면 그 사람의 실제 GPS 로 덮어써진다
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
-- 1. 캠핑장 번호 비워도 됨 (후보지·찍은 자리)
ALTER TABLE camp_plan ALTER COLUMN camp_id DROP NOT NULL;

-- 2. 자리 좌표 (위도·경도). 캠핑장 등록 줄은 NULL
ALTER TABLE camp_plan ADD COLUMN IF NOT EXISTS lat double precision;
ALTER TABLE camp_plan ADD COLUMN IF NOT EXISTS lng double precision;

-- 3. 캠핑장 번호도 좌표도 없으면 안 됨
ALTER TABLE camp_plan DROP CONSTRAINT IF EXISTS camp_plan_target_ck;
ALTER TABLE camp_plan ADD  CONSTRAINT camp_plan_target_ck
    CHECK (camp_id IS NOT NULL OR (lat IS NOT NULL AND lng IS NOT NULL));

-- 4. 같은 자리 · 같은 체크인 중복 방지 (camp_id 없는 줄만)
CREATE UNIQUE INDEX IF NOT EXISTS camp_plan_spot_uq
    ON camp_plan (member_id, lat, lng, check_in) WHERE camp_id IS NULL;

COMMIT;

-- =====================================================================
-- 실행 후 확인용 (따로 실행)
-- =====================================================================
-- 1. camp_id 가 NULL 허용인지 → is_nullable = YES
-- SELECT is_nullable FROM information_schema.columns WHERE table_name = 'camp_plan' AND column_name = 'camp_id';
-- 2. lat · lng 칸이 생겼는지 → 2줄
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'camp_plan' AND column_name IN ('lat', 'lng');
-- 3. 제약 · 인덱스 → camp_plan_target_ck, camp_plan_spot_uq
-- SELECT conname FROM pg_constraint WHERE conname = 'camp_plan_target_ck';
-- SELECT indexname FROM pg_indexes WHERE indexname = 'camp_plan_spot_uq';
-- 4. 기존 등록은 그대로 → 줄 수가 실행 전과 같아야 함
-- SELECT count(*) FROM camp_plan;
