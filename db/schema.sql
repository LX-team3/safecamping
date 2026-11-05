-- 캠핑메이트 스키마 (PostgreSQL)
-- 명명 규칙은 db/CLAUDE.md. 테이블 이름은 단수로 통일한다 (예: member)
-- 실행할 스키마(search_path)는 각자 DB 에 맞춘다 (현재 임시로 campingsafe 사용 중)

-- 회원. 로그인·회원가입(Spring Security)에서 쓴다
CREATE TABLE IF NOT EXISTS member (
    id              bigserial    PRIMARY KEY,
    login_id        varchar(16)  NOT NULL UNIQUE,  -- 영문·숫자 4~16자
    password_hash   varchar(100) NOT NULL,         -- BCrypt 암호화 값 (원래 비밀번호는 저장하지 않음)
    name            varchar(20)  NOT NULL,
    phone           varchar(11)  NOT NULL,         -- 숫자만 (예: 01012345678)
    emergency_phone varchar(11)  NOT NULL,         -- 위급할 때 위치·일정을 받을 가족 번호
    address         varchar(200) NOT NULL,
    address_detail  varchar(100),
    created_at      timestamptz  NOT NULL DEFAULT now()
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
