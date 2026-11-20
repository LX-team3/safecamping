-- =====================================================================
-- 06 지난 캠핑 후기 (웹 내 예약 → 지난 캠핑 카드의 "후기 쓰기")
-- 작성: 2026-11-20
--
-- 바꾸는 것: 테이블 2개 추가 (기존 테이블 · 데이터는 그대로)
--   camp_review       — 내 캠핑 일정(camp_plan) 하나에 후기 하나. 별점은 두지 않는다 (팀 결정)
--   camp_review_photo — 후기 사진 (최대 9장은 서버가 검사). 파일 자체는 서버 폴더에 두고 여기에는 파일 이름만
--
-- 실행 순서: schema_05.sql 다음 (camp_plan · member 가 있어야 함)
-- 꼭 함께 반영: 서버 mycamping/CampReview* (GET /api/member/camp-plans/reviews 등)
-- 여러 번 실행해도 결과는 같다 (IF NOT EXISTS). 에러가 나면 전체 취소 (BEGIN ~ COMMIT)
-- 공유 DB 는 백업 덤프를 뜬 뒤 실행한다
-- =====================================================================

BEGIN;

-- 일정을 지우거나 회원이 탈퇴하면 후기도 같이 지운다 (CASCADE)
-- member_id 는 camp_plan 에도 있지만, "내 후기만" 조건을 JOIN 없이 걸 수 있게 같이 둔다
CREATE TABLE IF NOT EXISTS camp_review (
    id          bigserial      PRIMARY KEY,
    plan_id     bigint         NOT NULL UNIQUE REFERENCES camp_plan (id) ON DELETE CASCADE,  -- 일정 하나에 후기 하나
    member_id   bigint         NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    content     varchar(1000),                 -- 사진만 올리면 비어 있을 수 있다 (글 · 사진 중 하나는 서버가 검사)
    created_at  timestamptz    NOT NULL DEFAULT now(),
    updated_at  timestamptz    NOT NULL DEFAULT now()   -- 마지막으로 고친 시각
);

-- file_name 은 서버가 만든 무작위 이름(UUID + 확장자). 사용자가 올린 원래 파일 이름은 저장하지 않는다
CREATE TABLE IF NOT EXISTS camp_review_photo (
    id          bigserial      PRIMARY KEY,
    review_id   bigint         NOT NULL REFERENCES camp_review (id) ON DELETE CASCADE,
    file_name   varchar(60)    NOT NULL UNIQUE,
    sort_order  smallint       NOT NULL DEFAULT 0,      -- 화면에 보이는 순서 (0 부터)
    created_at  timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS camp_review_photo_review_idx ON camp_review_photo (review_id, sort_order);

COMMIT;
