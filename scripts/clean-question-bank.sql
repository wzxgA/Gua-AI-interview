-- ============================================================
-- 清理题库（Question Bank）相关数据
-- 涉及表：question_bank
-- 依赖处理：question_bank 无被其他表引用，可直接清理。
-- 说明：仅清理数据库数据，不删除 MinIO 中的文件。
-- 运行：docker exec -i aims-postgres-wzxg psql -U aims -d aims < scripts/clean-question-bank.sql
-- ============================================================

BEGIN;

-- 清空题库表并重置自增序列
TRUNCATE TABLE question_bank RESTART IDENTITY;

COMMIT;

-- 输出结果
SELECT 'question_bank 清理完成，剩余 ' || count(*) || ' 条记录' AS result FROM question_bank;
