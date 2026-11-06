-- 캠핑메이트 스키마 (PostgreSQL)
-- 명명 규칙은 db/CLAUDE.md. 테이블 이름은 단수로 통일한다 (예: member)
-- 실행할 스키마(search_path)는 각자 DB 에 맞춘다 (현재 임시로 campingsafe 사용 중)
-- 서버는 application.properties 의 currentSchema 스키마 안에서만 테이블을 찾는다. 이름이 다르면 아래 두 줄의 이름을 같이 바꾼다
-- pgAdmin 은 이 파일 전체를 한 번에 실행해야 한다 (Ctrl+A 로 전체 선택 후 F5). 일부만 선택해 실행하면 SET 이 빠져 public 에 만들어진다
CREATE SCHEMA IF NOT EXISTS campingsafe;
SET search_path TO campingsafe;

-- 회원. 로그인·회원가입(Spring Security)에서 쓴다
CREATE TABLE IF NOT EXISTS member (
    id              bigserial    PRIMARY KEY,
    login_id        varchar(16)  NOT NULL UNIQUE,  -- 영문·숫자 4~16자
    password_hash   varchar(100) NOT NULL,         -- BCrypt 암호화 값 (원래 비밀번호는 저장하지 않음)
    name            varchar(20)  NOT NULL,
    phone           varchar(11)  NOT NULL,         -- 숫자만 (예: 01012345678)
    address         varchar(200) NOT NULL,
    address_detail  varchar(100),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

-- 비상연락처 (위급할 때 위치·일정을 받을 가족). 한 회원당 최대 5명 (서버에서 막음). 넣은 순서(id)대로 보여 준다
-- 회원가입 때는 번호만 받을 수 있어 name 이 비어 있을 수 있다. 내 정보 화면에서 저장할 때는 이름도 받는다
CREATE TABLE IF NOT EXISTS emergency_contact (
    id          bigserial    PRIMARY KEY,
    member_id   bigint       NOT NULL REFERENCES member (id) ON DELETE CASCADE,  -- 탈퇴하면 같이 지운다
    name        varchar(20),
    phone       varchar(11)  NOT NULL,   -- 숫자만
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS emergency_contact_member_idx ON emergency_contact (member_id);

-- 예전 모양(member 안의 emergency_phone 칸)으로 만든 DB 를 옮긴다: 번호를 emergency_contact 로 복사하고 칸을 지운다
-- emergency_phone 칸이 없으면(새 DB 이거나 이미 옮겼으면) 아무것도 하지 않는다 → 여러 번 실행해도 괜찮다
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'member' AND column_name = 'emergency_phone') THEN
        -- 잠깐 있었던 2명용 칸도 함께 옮기기 위해, 없으면 빈 칸으로 만들어 둔다
        ALTER TABLE member ADD COLUMN IF NOT EXISTS emergency_name   varchar(20);
        ALTER TABLE member ADD COLUMN IF NOT EXISTS emergency_name2  varchar(20);
        ALTER TABLE member ADD COLUMN IF NOT EXISTS emergency_phone2 varchar(11);
        INSERT INTO emergency_contact (member_id, name, phone)
            SELECT id, emergency_name, emergency_phone FROM member WHERE emergency_phone IS NOT NULL ORDER BY id;
        INSERT INTO emergency_contact (member_id, name, phone)
            SELECT id, emergency_name2, emergency_phone2 FROM member WHERE emergency_phone2 IS NOT NULL ORDER BY id;
        ALTER TABLE member DROP COLUMN emergency_name, DROP COLUMN emergency_phone,
                           DROP COLUMN emergency_name2, DROP COLUMN emergency_phone2;
    END IF;
END $$;

-- 여행 취향 (내 정보). 고른 취향에 맞는 캠핑장을 추천하는 데 쓴다 (추천은 캠핑장 데이터가 들어오면 붙임)
-- 한 회원당 최대 3개, 순서 없음 (서버에서 막음). activity 값은 서버 ActivityOption 의 code (fishing·hiking 등)
-- 예전 이름(member_taste · taste 칸)으로 만든 DB 는 이름만 바꾼다 (새 DB 이거나 이미 바꿨으면 아무것도 안 함)
DO $$
BEGIN
    IF to_regclass('member_taste') IS NOT NULL AND to_regclass('member_activity') IS NULL THEN
        ALTER TABLE member_taste RENAME TO member_activity;
        ALTER TABLE member_activity RENAME COLUMN taste TO activity;
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS member_activity (
    member_id   bigint       NOT NULL REFERENCES member (id) ON DELETE CASCADE,  -- 탈퇴하면 같이 지운다
    activity    varchar(20)  NOT NULL,
    PRIMARY KEY (member_id, activity)  -- 같은 취향은 한 번만
);

-- 저장 목록 (저장 화면 "목록" 탭). 예: 관심 캠핑장, 11월 가족 캠핑 후보
CREATE TABLE IF NOT EXISTS saved_list (
    id          bigserial    PRIMARY KEY,
    member_id   bigint       NOT NULL REFERENCES member (id) ON DELETE CASCADE,  -- 탈퇴하면 목록도 지운다
    name        varchar(30)  NOT NULL,
    is_default  boolean      NOT NULL DEFAULT false,  -- '관심 캠핑장' 기본 목록. 지우거나 이름을 바꿀 수 없다
    created_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (member_id, name)  -- 한 회원 안에서 같은 이름의 목록은 하나만
);

-- 목록에 담은 캠핑장
CREATE TABLE IF NOT EXISTS saved_camp (
    id             bigserial    PRIMARY KEY,
    saved_list_id  bigint       NOT NULL REFERENCES saved_list (id) ON DELETE CASCADE,  -- 목록을 지우면 담은 것도 지운다
    camp_id        varchar(20)  NOT NULL,  -- 캠핑장 테이블이 아직 없어 더미 id(c1 등)를 문자열로 저장. camp 테이블이 생기면 외래키로 바꾼다
    created_at     timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (saved_list_id, camp_id)  -- 같은 목록에 같은 캠핑장은 한 번만
);

-- 가족 모임방 (내 캠핑 탭). 가족이 한 방에서 모일 곳·아이 카드·일정·위치를 함께 본다
-- 1차: 방 만들기·초대코드 입장·모일 장소. 아이 카드·일정·위치 테이블은 다음 단계에서 추가
CREATE TABLE IF NOT EXISTS family_room (
    id           bigserial    PRIMARY KEY,
    name         varchar(20)  NOT NULL,          -- 예: 우리 가족
    invite_code  varchar(6)   NOT NULL UNIQUE,   -- 가족에게 알려 줄 입장 코드 (영문 대문자·숫자 6자)
    created_at   timestamptz  NOT NULL DEFAULT now()
);

-- 가족방 구성원. 한 회원은 가족방 하나에만 들어갈 수 있다 (member_id UNIQUE)
-- 방장 구분은 아직 없다 — 구성원은 모두 같은 권한. 마지막 사람이 나가면 방도 지운다 (서비스에서 처리)
CREATE TABLE IF NOT EXISTS family_member (
    id              bigserial    PRIMARY KEY,
    family_room_id  bigint       NOT NULL REFERENCES family_room (id) ON DELETE CASCADE,
    member_id       bigint       NOT NULL UNIQUE REFERENCES member (id) ON DELETE CASCADE,  -- 탈퇴하면 방에서도 빠진다
    joined_at       timestamptz  NOT NULL DEFAULT now()
);

-- 비상시 모일 장소. 좌표는 선택 (아직 지도에서 고르는 화면이 없어 "지금 내 위치"로만 넣을 수 있음)
CREATE TABLE IF NOT EXISTS meeting_place (
    id              bigserial         PRIMARY KEY,
    family_room_id  bigint            NOT NULL REFERENCES family_room (id) ON DELETE CASCADE,  -- 방을 지우면 같이 지운다
    name            varchar(30)       NOT NULL,          -- 예: 관리동 앞 주차장
    description     varchar(200),                        -- 예: 계곡 물이 불면 여기로
    lon             double precision,                    -- 경도 (WGS84). PostGIS 거리 계산이 필요해지면 geometry 로 바꾼다
    lat             double precision,                    -- 위도
    created_by      bigint            REFERENCES member (id) ON DELETE SET NULL,  -- 누가 지정했는지 (탈퇴하면 비움)
    created_at      timestamptz       NOT NULL DEFAULT now(),
    CHECK ((lon IS NULL) = (lat IS NULL))  -- 경도·위도는 둘 다 있거나 둘 다 없다
);

-- 아이 건강 응급 카드 (가족방 2차). 위급할 때 구조대·병원에 바로 보여 줄 정보. 같은 가족방 사람만 본다
CREATE TABLE IF NOT EXISTS child_card (
    id              bigserial     PRIMARY KEY,
    family_room_id  bigint        NOT NULL REFERENCES family_room (id) ON DELETE CASCADE,  -- 방을 지우면 같이 지운다
    name            varchar(20)   NOT NULL,
    birth_date      date,                     -- 약 용량·나이 확인용 (선택)
    blood_type      varchar(10),              -- 예: A+, O-, 모름 (선택)
    allergy         varchar(200),             -- 알레르기 (음식·약)
    disease         varchar(200),             -- 지병 (천식 등)
    medicine        varchar(200),             -- 먹고 있는 약
    memo            varchar(300),             -- 그 밖에 응급 때 알려 줄 것
    updated_by      bigint        REFERENCES member (id) ON DELETE SET NULL,  -- 마지막으로 고친 사람
    updated_at      timestamptz   NOT NULL DEFAULT now()
);

-- 가족 일정 공유 (가족방 2차). 예: 11/14 06:00 출발
CREATE TABLE IF NOT EXISTS family_schedule (
    id              bigserial     PRIMARY KEY,
    family_room_id  bigint        NOT NULL REFERENCES family_room (id) ON DELETE CASCADE,
    title           varchar(50)   NOT NULL,
    start_at        timestamptz   NOT NULL,   -- 화면에는 한국 시간으로 보여 준다
    memo            varchar(200),
    created_by      bigint        REFERENCES member (id) ON DELETE SET NULL,
    created_at      timestamptz   NOT NULL DEFAULT now()
);
