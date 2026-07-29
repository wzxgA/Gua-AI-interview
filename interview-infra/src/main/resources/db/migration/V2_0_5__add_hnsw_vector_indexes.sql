-- 启用 pg_trgm 扩展（用于 ILIKE 模糊匹配加速）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 将 embedding 列从 vector(2048) 改为 halfvec(2048)
-- pgvector 的 HNSW 索引最多支持 2000 维 vector，但 halfvec 最多支持 4000 维
-- halfvec 使用半精度浮点，存储减半，检索精度损失极小
ALTER TABLE resume ALTER COLUMN embedding TYPE halfvec(2048) USING embedding::halfvec(2048);
ALTER TABLE question_bank ALTER COLUMN embedding TYPE halfvec(2048) USING embedding::halfvec(2048);
ALTER TABLE position ALTER COLUMN embedding TYPE halfvec(2048) USING embedding::halfvec(2048);

-- 为三张向量表创建 HNSW 索引，加速余弦距离检索
CREATE INDEX IF NOT EXISTS idx_resume_embedding_hnsw
    ON resume USING hnsw (embedding halfvec_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_question_embedding_hnsw
    ON question_bank USING hnsw (embedding halfvec_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_position_embedding_hnsw
    ON position USING hnsw (embedding halfvec_cosine_ops);

-- 对 raw_text 和 candidate_name 建 GIN trigram 索引
-- 加速 ILIKE '%keyword%' 查询，用于混合检索的关键词匹配
CREATE INDEX IF NOT EXISTS idx_resume_raw_text_trgm
    ON resume USING gin (raw_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_resume_candidate_name_trgm
    ON resume USING gin (candidate_name gin_trgm_ops);
