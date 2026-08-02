-- ============================================================
-- 清理简历（Resume）相关数据
-- 涉及表：resume
-- 依赖处理：interview_session.resume_id 引用 resume.id（可空），
--           清理前将其置 NULL，以保留面试会话记录。
-- 说明：仅清理数据库数据，不删除 MinIO 中的简历文件。
-- 运行：docker exec -i aims-postgres-wzxg psql -U aims -d aims < scripts/clean-resume.sql
-- ============================================================

BEGIN;

-- 解除面试会话对简历的引用（保留面试会话本身）
UPDATE interview_session SET resume_id = NULL WHERE resume_id IS NOT NULL;

-- 清空简历表并重置自增序列
TRUNCATE TABLE resume RESTART IDENTITY;

COMMIT;

-- 输出结果
SELECT 'resume 清理完成，剩余 ' || count(*) || ' 条记录' AS result FROM resume;
