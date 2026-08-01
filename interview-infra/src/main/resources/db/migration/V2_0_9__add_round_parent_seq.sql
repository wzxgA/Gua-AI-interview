-- 追问功能：新增 parent_seq 列，标识追问轮次所属的主问题 seq
ALTER TABLE interview_round ADD COLUMN parent_seq INT;

-- 追问轮次索引：按主问题 seq 查询追问次数
CREATE INDEX idx_round_parent_seq ON interview_round (session_id, parent_seq);
