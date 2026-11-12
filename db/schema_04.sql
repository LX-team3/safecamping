-- =====================================================================
-- 04 saved_camp.camp_id 숫자로 바꾸기 + 캠핑장 외래키
-- 작성: 2026-11-12
--
-- 실행 순서: schema_03.sql 다음. 새 DB 를 처음부터 만들 때는 merge_to_public.sql 다음 (camp 가 채워져 있어야 함)
-- 꼭 함께 반영: merge_to_public.sql 의 camp UPSERT 변경 (이 파일 이후로 merge 가 DELETE FROM camp 를 하면 외래키 에러)
--               서버 saved/ 코드 (campId String → Long)
-- 중간에 에러가 나면 전체가 취소된다 (BEGIN ~ COMMIT). 여러 번 실행해도 결과는 같다
-- teamuser 로 실행 가능 (테이블 주인이 teamuser). 공유 DB 는 백업 덤프를 뜬 뒤 실행한다
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. camp 에 없는 캠핑장을 가리키는 저장 줄 지우기 (회원 데이터라 직접 정리)
--    왜: 더미 시절 id 'c1' · 'c2' · 'c3' 가 남아 있다 (2026-11-11 덤프 기준 4줄). 숫자로 바꿀 수 없고 가리킬 캠핑장도 없다
--    양쪽을 글자로 비교 → 아직 글자 칸이어도, 이미 숫자 칸으로 바뀐 뒤 다시 돌려도 동작한다
-- ---------------------------------------------------------------------
DELETE FROM saved_camp sc
 WHERE NOT EXISTS (SELECT 1 FROM camp c WHERE c.id::text = sc.camp_id::text);

-- ---------------------------------------------------------------------
-- 2. saved_camp.camp_id 글자(varchar) → 숫자(bigint)
--    왜: camp.id 는 숫자인데 이 칸만 글자라 서버가 매번 camp.id::text 로 바꿔 비교했다
--        → camp 기본키 인덱스를 못 쓰고, 외래키도 걸 수 없었다
-- ---------------------------------------------------------------------
ALTER TABLE saved_camp ALTER COLUMN camp_id TYPE bigint USING camp_id::bigint;

-- ---------------------------------------------------------------------
-- 3. 외래키: 없는 캠핑장은 저장·일정 등록이 안 되게 DB 가 막는다
--    ON DELETE 는 기본(NO ACTION): 누가 저장한 캠핑장은 camp 에서 지울 수 없다 (에러로 알려 줌)
--    여러 번 돌려도 되게 있으면 지우고 다시 건다
-- ---------------------------------------------------------------------
ALTER TABLE saved_camp DROP CONSTRAINT IF EXISTS saved_camp_camp_id_fkey;
ALTER TABLE saved_camp ADD  CONSTRAINT saved_camp_camp_id_fkey
    FOREIGN KEY (camp_id) REFERENCES camp (id);

ALTER TABLE camp_plan DROP CONSTRAINT IF EXISTS camp_plan_camp_id_fkey;
ALTER TABLE camp_plan ADD  CONSTRAINT camp_plan_camp_id_fkey
    FOREIGN KEY (camp_id) REFERENCES camp (id);

COMMIT;

-- =====================================================================
-- 실행 후 확인용 (따로 실행)
-- =====================================================================
-- 1. camp_id 타입 → bigint
-- SELECT data_type FROM information_schema.columns WHERE table_name = 'saved_camp' AND column_name = 'camp_id';
-- 2. 외래키 2개 → saved_camp_camp_id_fkey · camp_plan_camp_id_fkey
-- SELECT conname FROM pg_constraint WHERE conname IN ('saved_camp_camp_id_fkey', 'camp_plan_camp_id_fkey');
-- 3. 남은 저장 줄 (덤프 기준 1줄: 101137)
-- SELECT * FROM saved_camp;
