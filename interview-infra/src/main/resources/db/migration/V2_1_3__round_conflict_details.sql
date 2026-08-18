-- v1.1-F4 评估证据引用：interview_round 增 conflict_details（该轮简历交叉验证矛盾点 JSONB）
-- 存量轮次置空（NULL 语义等同无矛盾点），新轮次由代码层写入 '[]' 或矛盾点数组
ALTER TABLE interview_round
    ADD COLUMN IF NOT EXISTS conflict_details JSONB;
