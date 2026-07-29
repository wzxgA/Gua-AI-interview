-- 为三张向量表创建 HNSW 索引，加速 pgvector 余弦距离检索
-- HNSW 适合高维向量（2048维），vector_cosine_ops 对应 <=> 余弦距离运算符
CREATE INDEX IF NOT EXISTS idx_resume_embedding_hnsw
    ON resume USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_question_embedding_hnsw
    ON question_bank USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_position_embedding_hnsw
    ON position USING hnsw (embedding vector_cosine_ops);
