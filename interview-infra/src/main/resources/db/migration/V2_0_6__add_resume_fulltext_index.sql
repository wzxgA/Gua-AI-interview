-- 启用 pg_trgm 扩展（用于 ILIKE 模糊匹配加速）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 对 raw_text 和 candidate_name 建 GIN trigram 索引
-- 加速 ILIKE '%keyword%' 查询，用于混合检索的关键词匹配
CREATE INDEX IF NOT EXISTS idx_resume_raw_text_trgm
    ON resume USING gin (raw_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_resume_candidate_name_trgm
    ON resume USING gin (candidate_name gin_trgm_ops);
