-- =====================================================================
-- 03 DB 정리
-- 작성: 2026-11-12
--
-- 실행 순서
--   공유 DB · 공유 DB 덤프를 복원한 DB : 이 파일만 실행 (merge_to_public.sql 은 이미 돌아 있음)
--   새 DB 를 처음부터 만들 때        : 원본 덤프(camp_DB 01_schema · 02_data) → schema.sql → schema_02.sql
--                                      → 이 파일 → merge_to_public.sql  (merge 가 raw 스키마의 원본을 읽기 때문)
-- 중간에 에러가 나면 전체가 취소된다 (BEGIN ~ COMMIT). 여러 번 실행해도 결과는 같다 (IF EXISTS)
-- 공유 DB 에 넣기 전에 반드시 로컬 DB 에서 먼저 돌려 보고, 공유 DB 는 백업 덤프를 뜬 뒤 실행한다
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. 쓰지 않는 백업 테이블 지우기 (2026-11-12 사용자 승인)
--    왜: 서버 코드도 merge_to_public.sql 도 읽지 않는 백업이 public 에 섞여 있어 어떤 테이블이 진짜인지 헷갈린다
--    되돌리기: 2026-11-11 공유 DB 덤프(campin.dump)에 그대로 들어 있다
--      예) pg_restore -d campin -t facilities_backup campin.dump
--
--    facilities_backup : facilities 를 정리하기 전 사본으로 보임 (가옥 house 8,212줄 · 화장실 toilet 136줄 포함, 약국 없음)
--    bak1109_*         : 11/9 merge_to_public.sql 이 더미 데이터를 지우기 전 떠 둔 백업 (가짜 캠핑장 7곳, is_dummy 전부 true)
--
--    원본 테이블(camps · facilities · spot · score_weights · er_detail)은 merge_to_public.sql 의 재료라서 지우지 않는다
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS
    public.facilities_backup,
    public.bak1109_camp,
    public.bak1109_camp_facility,
    public.bak1109_camp_tour,
    public.bak1109_facility,
    public.bak1109_place_info,
    public.bak1109_place_note,
    public.bak1109_tour;

-- ---------------------------------------------------------------------
-- 2. 원본 테이블을 raw 스키마로 나누기
--    왜: 원본(camps · facilities)과 화면용(camp · facility)이 이름 한 글자 차이라 어느 게 진짜인지 헷갈린다
--        raw.camps / public.camp 처럼 스키마로 나누면 이름만 봐도 구분된다
--    규칙: raw 는 읽기만 한다. 값 보정은 merge_to_public.sql 안에서, 화면용(public)은 merge 가 만든다
--    인덱스 · 기본키 · geom 공간 인덱스는 테이블을 따라 같이 옮겨진다
--    함께 바꾼 코드: merge_to_public.sql (public.camps → raw.camps 등), SavedMapper(비교 SQL), dbtest/TestMapper
-- ---------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS raw;

ALTER TABLE IF EXISTS public.camps         SET SCHEMA raw;  -- 고캠핑 캠핑장 원본
ALTER TABLE IF EXISTS public.facilities    SET SCHEMA raw;  -- 강원 안전시설 원본 (보안등 · CCTV 포함, 안전점수 계산에 씀)
ALTER TABLE IF EXISTS public.spot          SET SCHEMA raw;  -- 관광지 원본
ALTER TABLE IF EXISTS public.score_weights SET SCHEMA raw;  -- 안전점수 가중치 · 거리 기준
ALTER TABLE IF EXISTS public.er_detail     SET SCHEMA raw;  -- 응급실 메모

-- 서버가 쓰는 teamuser 계정이 raw 안을 읽을 수 있게 (로컬 복원 DB 에는 teamuser 가 없으므로 있을 때만)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'teamuser') THEN
        GRANT USAGE ON SCHEMA raw TO teamuser;
        GRANT SELECT ON ALL TABLES IN SCHEMA raw TO teamuser;
    END IF;
END $$;

COMMIT;

-- =====================================================================
-- 실행 후 확인용 (따로 실행)
-- =====================================================================
-- 1. 0줄이 나와야 한다
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'public' AND (table_name LIKE 'bak1109%' OR table_name = 'facilities_backup');
-- 2. raw 에 5개 (camps · er_detail · facilities · score_weights · spot)
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'raw' ORDER BY 1;
-- 3. 원본 줄 수 그대로 (23 · 1249 · 26)
-- SELECT (SELECT count(*) FROM raw.camps), (SELECT count(*) FROM raw.facilities), (SELECT count(*) FROM raw.spot);
