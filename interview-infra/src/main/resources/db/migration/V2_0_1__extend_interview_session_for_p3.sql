-- P3 面试会话扩展
ALTER TABLE interview_session
    ADD COLUMN IF NOT EXISTS resume_id BIGINT REFERENCES resume (id);

ALTER TABLE interview_round
    ADD CONSTRAINT uk_interview_round_session_seq UNIQUE (session_id, seq);

CREATE INDEX IF NOT EXISTS idx_session_resume ON interview_session (resume_id);
