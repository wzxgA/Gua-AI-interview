-- 追问不再占用 seq：seq 改为可空
ALTER TABLE interview_round ALTER COLUMN seq DROP NOT NULL;

-- 追问序号字段：同一 parentSeq 下的第几次追问（1-based）
ALTER TABLE interview_round ADD COLUMN follow_up_index INT;

-- 列表按时间排序：createdAt 索引
CREATE INDEX IF NOT EXISTS idx_round_session_created ON interview_round (session_id, created_at);
