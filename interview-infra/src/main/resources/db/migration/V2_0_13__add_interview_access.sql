-- 面试会话：候选人免登录访问能力（v1.0 update）
-- access_token    公开链接令牌（随机串，候选人通过 /i/{accessToken} 进入）
-- access_password 访问密码 bcrypt 哈希（候选人进入面试前需输入）
-- access_enabled  是否允许候选人访问（false = 面试官已作废该入口）
ALTER TABLE interview_session
    ADD COLUMN access_token     VARCHAR(64)  UNIQUE,
    ADD COLUMN access_password  VARCHAR(100),
    ADD COLUMN access_enabled   BOOLEAN      NOT NULL DEFAULT TRUE;

CREATE INDEX idx_session_access_token ON interview_session (access_token);
