-- P4 评估进度追踪：为 interview_session 增加评估流程状态与进度字段

ALTER TABLE interview_session
    ADD COLUMN IF NOT EXISTS evaluation_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS evaluation_error TEXT,
    ADD COLUMN IF NOT EXISTS evaluated_rounds INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_rounds_to_evaluate INT;

COMMENT ON COLUMN interview_session.evaluation_status IS '评估流程状态：PENDING/EVALUATING/REPORTING/DONE/FAILED';
COMMENT ON COLUMN interview_session.evaluation_error IS '评估失败时的错误信息';
COMMENT ON COLUMN interview_session.evaluated_rounds IS '已评估完成的轮次数';
COMMENT ON COLUMN interview_session.total_rounds_to_evaluate IS '需评估的总轮次数';
