-- 面试入口模式：NONE=未生成链接（默认），CANDIDATE_ONLY=仅候选端，DISABLED=已作废
ALTER TABLE interview_session
    ADD COLUMN access_mode VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- 存量数据迁移：已有有效 access_token 且 access_enabled=true 的标记为 CANDIDATE_ONLY
UPDATE interview_session
SET access_mode = 'CANDIDATE_ONLY'
WHERE access_token IS NOT NULL AND access_enabled = TRUE;

-- 已有 access_token 但 access_enabled=false 的标记为 DISABLED
UPDATE interview_session
SET access_mode = 'DISABLED'
WHERE access_token IS NOT NULL AND access_enabled = FALSE;
