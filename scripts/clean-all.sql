-- ============================================================
-- 清理全部业务数据（岗位 + 题库 + 简历 + 面试）
-- 涉及表：position, question_bank, resume,
--         interview_session, interview_round, interview_evaluation, interview_report
-- 依赖处理：一次性 TRUNCATE 全部七张业务表，PostgreSQL 自动处理外键依赖。
--           不影响 sys_user（用户）和 flyway_schema_history（迁移记录）。
-- 说明：仅清理数据库数据，不删除 MinIO 中的文件。
-- 运行：docker exec -i aims-postgres-wzxg psql -U aims -d aims < scripts/clean-all.sql
-- ============================================================

BEGIN;

-- 清空全部业务表并重置自增序列
TRUNCATE TABLE
    interview_evaluation,
    interview_report,
    interview_round,
    interview_session,
    position,
    question_bank,
    resume
RESTART IDENTITY;

COMMIT;

-- 输出结果
SELECT 'position             剩余 ' || count(*) || ' 条' AS result FROM position
UNION ALL
SELECT 'question_bank        剩余 ' || count(*) || ' 条' FROM question_bank
UNION ALL
SELECT 'resume               剩余 ' || count(*) || ' 条' FROM resume
UNION ALL
SELECT 'interview_session    剩余 ' || count(*) || ' 条' FROM interview_session
UNION ALL
SELECT 'interview_round      剩余 ' || count(*) || ' 条' FROM interview_round
UNION ALL
SELECT 'interview_evaluation 剩余 ' || count(*) || ' 条' FROM interview_evaluation
UNION ALL
SELECT 'interview_report     剩余 ' || count(*) || ' 条' FROM interview_report;
