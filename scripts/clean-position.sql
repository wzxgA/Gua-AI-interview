-- ============================================================
-- 清理岗位（Position）相关数据
-- 涉及表：position
-- 依赖处理：interview_session.position_id 引用 position.id（可空），
--           清理前将其置 NULL，以保留面试会话记录。
-- 说明：仅清理数据库数据，不删除 MinIO 中的文件。
-- 运行：docker exec -i aims-postgres-wzxg psql -U aims -d aims < scripts/clean-position.sql
-- ============================================================

BEGIN;

-- 解除面试会话对岗位的引用（保留面试会话本身）
UPDATE interview_session SET position_id = NULL WHERE position_id IS NOT NULL;

-- 清空岗位表并重置自增序列
TRUNCATE TABLE position RESTART IDENTITY;

COMMIT;

-- 输出结果
SELECT 'position 清理完成，剩余 ' || count(*) || ' 条记录' AS result FROM position;
