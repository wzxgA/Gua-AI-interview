-- 将岗位、题库、简历 embedding 统一切换为 2048 维。
-- 旧的 1024 维向量无法直接转换，必须由应用按原始文本重新生成。
ALTER TABLE position
    ALTER COLUMN embedding TYPE vector(2048)
        USING NULL::vector(2048);

ALTER TABLE question_bank
    ALTER COLUMN embedding TYPE vector(2048)
        USING NULL::vector(2048);

ALTER TABLE resume
    ALTER COLUMN embedding TYPE vector(2048)
        USING NULL::vector(2048);
