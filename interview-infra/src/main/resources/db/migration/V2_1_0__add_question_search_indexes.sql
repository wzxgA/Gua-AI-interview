-- v1.1-B RAG 检索增强：题库检索索引优化
-- 1) (category, difficulty) 复合索引：混合检索按分类/难度过滤时走索引，替代单列索引全扫
CREATE INDEX IF NOT EXISTS idx_question_category_difficulty
    ON question_bank (category, difficulty);

-- 2) content / topic GIN trigram 索引：加速关键词检索（ILIKE '%kw%'），对齐简历侧 raw_text/candidate_name 方案
-- pg_trgm 扩展已在 V2_0_5 启用，无需重复 CREATE EXTENSION
CREATE INDEX IF NOT EXISTS idx_question_content_trgm
    ON question_bank USING gin (content gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_question_topic_trgm
    ON question_bank USING gin (topic gin_trgm_ops);
