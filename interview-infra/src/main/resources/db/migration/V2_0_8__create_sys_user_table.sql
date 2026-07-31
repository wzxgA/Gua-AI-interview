-- 用户表（鉴权登录）
CREATE TABLE sys_user (
    id           BIGSERIAL    PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL UNIQUE,
    password     VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL DEFAULT 'INTERVIEWER',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sys_user_username ON sys_user (username);
