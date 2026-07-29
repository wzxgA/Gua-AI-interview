-- 向量模型版本信息
ALTER TABLE resume
    ADD COLUMN embedding_model VARCHAR(100),
    ADD COLUMN embedding_dimension INTEGER;
