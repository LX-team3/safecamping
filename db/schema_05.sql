-- =====================================================================
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
