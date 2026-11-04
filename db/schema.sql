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
