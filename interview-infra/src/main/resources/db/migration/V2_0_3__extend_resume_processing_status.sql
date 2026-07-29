-- 简历解析与向量化任务状态扩展
ALTER TABLE resume
    ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN parse_error TEXT,
    ADD COLUMN embedding_error TEXT,
    ADD COLUMN parse_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN embedding_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN parsed_at TIMESTAMPTZ,
    ADD COLUMN embedded_at TIMESTAMPTZ;

-- 已有 embedding 数据迁移为已完成状态
UPDATE resume
SET embedding_status = 'COMPLETED',
    embedded_at = COALESCE(updated_at, now())
WHERE embedding IS NOT NULL;

CREATE INDEX idx_resume_embedding_status ON resume (embedding_status);
