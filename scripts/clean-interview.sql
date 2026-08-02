-- ============================================================
-- 清理面试（Interview）相关数据
-- 涉及表：interview_evaluation, interview_report, interview_round, interview_session
-- 依赖处理：四张表存在级联外键关系，一次性 TRUNCATE 即可正确处理。
--           不影响 position、resume 等其他表数据。
-- 说明：仅清理数据库数据，不删除 MinIO 中的音频/报告文件。
-- 运行：docker exec -i aims-postgres-wzxg psql -U aims -d aims < scripts/clean-interview.sql
-- ============================================================

BEGIN;

-- 清空面试相关四张表并重置自增序列
-- 顺序无关：PostgreSQL 会自动处理同语句中多表的外键依赖
TRUNCATE TABLE
    interview_evaluation,
    interview_report,
    interview_round,
    interview_session
RESTART IDENTITY;

COMMIT;

-- 输出结果
SELECT 'interview_session  剩余 ' || count(*) || ' 条' AS result FROM interview_session
UNION ALL
SELECT 'interview_round     剩余 ' || count(*) || ' 条' FROM interview_round
UNION ALL
SELECT 'interview_evaluation 剩余 ' || count(*) || ' 条' FROM interview_evaluation
UNION ALL
SELECT 'interview_report    剩余 ' || count(*) || ' 条' FROM interview_report;
