-- ===========================
-- 설문 (② 1분 진단 · 관광지 탭 여행 취향)
-- 두 설문 모두 "설문 → 단계(화면 한 장) → 질문 → 선택지" 모양이라 테이블 하나로 같이 쓴다
-- 질문·선택지 내용은 db/data_survey.sql 로 넣는다
-- ===========================

-- 설문 종류. code 로 화면·API 주소를 나눈다 (/api/surveys/diagnosis, /api/surveys/travel)
CREATE TABLE IF NOT EXISTS survey (
    id            bigserial   PRIMARY KEY,
    code          varchar(20) NOT NULL UNIQUE,   -- diagnosis(1분 진단), travel(여행 취향)
    title         varchar(30) NOT NULL,          -- 화면 위 작은 글씨 (예: 1분 진단)
    duration_text varchar(20),                   -- 단계가 하나인 설문에서 제목 옆에 보일 소요 시간 (예: 약 30초)
    finish_label  varchar(30) NOT NULL,          -- 마지막 버튼 문구
    is_numbered   boolean     NOT NULL DEFAULT true  -- 질문 앞에 번호(1, 2, ...)를 붙일지
);

-- 단계 = 화면 한 장. 큰 제목·설명과 화면 아래 안내 박스(note)를 가진다
CREATE TABLE IF NOT EXISTS survey_step (
    id        bigserial    PRIMARY KEY,
    survey_id bigint       NOT NULL REFERENCES survey (id),
    step      int          NOT NULL,             -- 1부터
    title     varchar(50)  NOT NULL,
    sub       varchar(100),
    note      varchar(300),
    UNIQUE (survey_id, step)
);

CREATE TABLE IF NOT EXISTS survey_question (
    id          bigserial   PRIMARY KEY,
    step_id     bigint      NOT NULL REFERENCES survey_step (id),
    code        varchar(30) NOT NULL,            -- 답변을 주고받을 때 쓰는 이름 (예: camp_type)
    sort_order  int         NOT NULL,
    title       varchar(50) NOT NULL,
    input_type  varchar(10) NOT NULL CHECK (input_type IN ('card', 'chip', 'rank')),  -- 큰 카드 / 알약 / 순서대로 고르는 알약
    max_select  int         NOT NULL DEFAULT 0,  -- 1: 하나만, n: n개까지, 0: 제한 없음
    is_required boolean     NOT NULL DEFAULT true,
    note        varchar(200),                    -- 질문 아래 작은 안내 문구
    UNIQUE (step_id, code),
    UNIQUE (step_id, sort_order)
);

CREATE TABLE IF NOT EXISTS survey_option (
    id           bigserial    PRIMARY KEY,
    question_id  bigint       NOT NULL REFERENCES survey_question (id),
    code         varchar(30)  NOT NULL,
    sort_order   int          NOT NULL,
    label        varchar(30)  NOT NULL,
    description  varchar(50),
    is_exclusive boolean      NOT NULL DEFAULT false,  -- 고르면 같은 질문의 다른 선택을 푼다 (예: 아직 없어요)
    warning_text varchar(200),                         -- 고르면 띄울 위험 안내 (예: 가스난로 → 일산화탄소)
    UNIQUE (question_id, code)
);

-- 로그인 회원이 낸 답변 1건. 다시 내면 새 줄이 생기고, 가장 최근 것을 "내 답변"으로 쓴다
CREATE TABLE IF NOT EXISTS survey_answer (
    id         bigserial   PRIMARY KEY,
    survey_id  bigint      NOT NULL REFERENCES survey (id),
    member_id  bigint      NOT NULL REFERENCES member (id),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- 답변 1건에서 고른 선택지들. rank 는 고른 순서 (순위 질문에서는 곧 1·2·3순위)
CREATE TABLE IF NOT EXISTS survey_answer_item (
    answer_id bigint NOT NULL REFERENCES survey_answer (id) ON DELETE CASCADE,
    option_id bigint NOT NULL REFERENCES survey_option (id),
    rank      int    NOT NULL,
    PRIMARY KEY (answer_id, option_id)
);

-- ===========================
-- 캠핑장 (탐색 탭 목록·지도)
-- ===========================
-- 좌표는 우선 숫자 컬럼(lat·lng, WGS84)으로 둔다. 응급실까지 거리 계산 등 공간 분석을 할 때 PostGIS geography 로 옮긴다
-- safety_score·er_drive_min 은 아직 실제 계산 값이 아니다 → is_dummy 로 구분하고 화면에도 더미라고 표시
CREATE TABLE IF NOT EXISTS camp (
    id           bigserial    PRIMARY KEY,
    name         varchar(50)  NOT NULL,
    sido         varchar(20)  NOT NULL,          -- 예: 강원특별자치도
    sigungu      varchar(20)  NOT NULL,          -- 예: 인제군 (지역 선택에 씀)
    eupmyeon     varchar(20),                    -- 예: 북면
    camp_type    varchar(15)  NOT NULL CHECK (camp_type IN ('auto', 'glamping', 'backpacking')),  -- 1분 진단 camp_type 선택지 code 와 같다
    lat          numeric(9, 6) NOT NULL,         -- 위도
    lng          numeric(9, 6) NOT NULL,         -- 경도
    safety_score int          CHECK (safety_score BETWEEN 0 AND 100),  -- 안전점수. 등급은 서버에서 계산 (70↑ 안전 / 40~69 주의 / 40↓ 위험)
    er_drive_min int,                            -- 가장 가까운 응급실까지 차로 몇 분
    image_url    varchar(300),                   -- 대표 사진 (없으면 화면에 빈 그림)
    is_dummy     boolean      NOT NULL DEFAULT true,
    UNIQUE (name, sigungu)
);
CREATE INDEX IF NOT EXISTS camp_sigungu_idx ON camp (sigungu);

-- ===========================
-- 지역 (탐색 탭 지역 선택 시트)
-- ===========================
-- 시·도 → 시·군·구 2단계. 이름은 행정구역 공식 이름. 데이터는 db/data_region.sql
-- camp 테이블은 아직 이름(sido·sigungu 글자)으로 연결한다 — 전국 데이터를 넣을 때 id 연결로 바꿀지 정한다
CREATE TABLE IF NOT EXISTS sido (
    id         bigserial   PRIMARY KEY,
    name       varchar(20) NOT NULL UNIQUE,   -- 예: 강원특별자치도
    sort_order int         NOT NULL
);

CREATE TABLE IF NOT EXISTS sigungu (
    id              bigserial   PRIMARY KEY,
    sido_id         bigint      NOT NULL REFERENCES sido (id),
    name            varchar(20) NOT NULL,          -- 예: 인제군
    sort_order      int         NOT NULL,
    is_safety_ready boolean     NOT NULL DEFAULT false,  -- 안심(안전점수) 데이터가 준비됐는지 → 화면 "안심 데이터 있음"
    UNIQUE (sido_id, name)
);

-- ===========================
-- 회원의 함께 가는 아이 (누구와 언제 시트의 아이 나이대. 내 정보 › 함께 가는 가족)
-- ===========================
-- 저장할 때는 그 회원의 줄을 지우고 새로 넣는다 (아이 수가 바뀔 수 있어서)
CREATE TABLE IF NOT EXISTS member_child (
    member_id  bigint      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    sort_order int         NOT NULL,              -- 아이 1, 아이 2 ...
    age_group  varchar(15) NOT NULL CHECK (age_group IN ('infant', 'preschool', 'elementary')),  -- 영유아 0~3세 / 미취학 4~7세 / 초등 8~12세
    PRIMARY KEY (member_id, sort_order)
);

-- ===========================
-- 캠핑장 상세 · 시설 상세 · 관광지 상세
-- ===========================
-- 이미 camp 테이블을 만든 DB 에도 들어가도록 ALTER 로 추가한다 (여러 번 실행해도 안전)
ALTER TABLE camp ADD COLUMN IF NOT EXISTS reservation_url varchar(300);    -- 예약 사이트 (없으면 버튼 비활성)

-- 안전 시설 (응급실·소방서 등). 캠핑장과의 거리는 camp_facility 에 둔다
CREATE TABLE IF NOT EXISTS facility (
    id        bigserial     PRIMARY KEY,
    type      varchar(15)   NOT NULL CHECK (type IN ('er', 'fire', 'police', 'store', 'toilet')),  -- 응급실·소방서·지구대·편의점·화장실
    name      varchar(50)   NOT NULL,
    sido      varchar(20)   NOT NULL,
    sigungu   varchar(20)   NOT NULL,
    eupmyeon  varchar(20),
    address   varchar(200),                     -- 상세 주소 (없으면 화면에 "확인 필요")
    phone     varchar(20),                      -- 전화번호 (지어내지 않는다. 없으면 복사 버튼 숨김)
    hours     varchar(100),                     -- 운영 시간 (공공데이터 연동 전에는 비어 있음)
    lat       numeric(9, 6) NOT NULL,
    lng       numeric(9, 6) NOT NULL,
    is_dummy  boolean       NOT NULL DEFAULT true,
    UNIQUE (name, sigungu)
);

-- 캠핑장별 가까운 시설. sort_order 가 화면의 1·2·3 번호
CREATE TABLE IF NOT EXISTS camp_facility (
    camp_id     bigint        NOT NULL REFERENCES camp (id),
    facility_id bigint        NOT NULL REFERENCES facility (id),
    sort_order  int           NOT NULL,
    distance_km numeric(5, 1) NOT NULL,         -- 캠핑장에서 거리
    drive_min   int           NOT NULL,         -- 차로 몇 분
    is_dummy    boolean       NOT NULL DEFAULT true,
    PRIMARY KEY (camp_id, facility_id)
);

-- 관광지. category·nature 는 여행 취향 설문 선택지 code 와 같다 (맞으면 목록 앞으로)
CREATE TABLE IF NOT EXISTS tour (
    id              bigserial     PRIMARY KEY,
    name            varchar(50)   NOT NULL,
    category        varchar(30)   NOT NULL,     -- 예: trekking
    category_label  varchar(30)   NOT NULL,     -- 예: 자연 · 트레킹
    nature          varchar(30),                -- 예: mountain
    popularity_rank int,                        -- 인기 순위
    sido            varchar(20)   NOT NULL,
    sigungu         varchar(20)   NOT NULL,
    eupmyeon        varchar(20),
    lat             numeric(9, 6) NOT NULL,
    lng             numeric(9, 6) NOT NULL,
    is_dummy        boolean       NOT NULL DEFAULT true,
    UNIQUE (name, sigungu)
);

-- 캠핑장별 주변 관광지
CREATE TABLE IF NOT EXISTS camp_tour (
    camp_id   bigint  NOT NULL REFERENCES camp (id),
    tour_id   bigint  NOT NULL REFERENCES tour (id),
    drive_min int     NOT NULL,
    note      varchar(50),                      -- 목록 한 줄 짧은 안내 (예: 연령 제한 확인)
    is_dummy  boolean NOT NULL DEFAULT true,
    PRIMARY KEY (camp_id, tour_id)
);

-- 캠핑장·시설·관광지 상세가 함께 쓰는 정보 줄 ("운영 | 09:00~18:00" 또는 라벨 없는 한 줄)
-- place_type + place_id 로 어느 곳의 줄인지 가리킨다 (세 테이블을 가리켜서 외래키는 걸 수 없음)
CREATE TABLE IF NOT EXISTS place_info (
    id         bigserial   PRIMARY KEY,
    place_type varchar(10) NOT NULL CHECK (place_type IN ('camp', 'facility', 'tour')),
    place_id   bigint      NOT NULL,
    sort_order int         NOT NULL,
    label      varchar(20),                     -- 없으면 값만 한 줄로 보임
    value      varchar(200),                    -- 없으면 화면에 "확인 필요"
    UNIQUE (place_type, place_id, sort_order)
);

-- 상세 화면의 안내 상자 (가기 전에 알아두세요 · 위험할 땐)
CREATE TABLE IF NOT EXISTS place_note (
    id         bigserial    PRIMARY KEY,
    place_type varchar(10)  NOT NULL CHECK (place_type IN ('camp', 'facility', 'tour')),
    place_id   bigint       NOT NULL,
    sort_order int          NOT NULL,
    level      varchar(10)  NOT NULL CHECK (level IN ('danger', 'caution', 'safe')),  -- 위험할 땐 / 주의 / 안전
    body       varchar(200) NOT NULL,
    UNIQUE (place_type, place_id, sort_order)
);
