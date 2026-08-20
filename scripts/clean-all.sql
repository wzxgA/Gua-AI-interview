-- ============================================================
-- 清理全部业务数据（候选人 + 岗位 + 题库 + 简历 + 面试 + 防作弊）
-- 涉及表：candidate, position, question_bank, resume,
--         resume_work_experience, resume_project_experience, resume_project_highlight,
--         interview_session, interview_round, interview_evaluation, interview_report,
--         interview_proctor_event
-- 依赖处理：全部业务表在同一条 TRUNCATE 内一次清空，子表父表同列表示例，
--           序列均归零（BIGSERIAL 自动 RESTART IDENTITY）。
--           不影响 sys_user（用户）和 flyway_schema_history（迁移记录）。
-- 说明：仅清理数据库数据，不删除 MinIO 中的文件。
-- 运行：docker exec -i aims-postgres-wzxg psql -U aims -d aims < scripts/clean-all.sql
-- ============================================================

BEGIN;

-- 清空全部业务表并重置自增序列
-- 注意：必须把外键引用的子表一并纳入同一条 TRUNCATE，
--       否则 PostgreSQL 会因外键约束报 "cannot truncate a table referenced in a foreign key constraint"。
--       关键依赖：resume_work_experience/... 引用 resume；resume.candidate_id 引用 candidate；
--                 interview_proctor_event 引用 interview_session。
TRUNCATE TABLE
    interview_proctor_event,
    interview_evaluation,
    interview_report,
    interview_round,
    interview_session,
    resume_project_highlight,
    resume_project_experience,
    resume_work_experience,
    resume,
    candidate,
    position,
    question_bank
RESTART IDENTITY;

COMMIT;

-- 输出结果
SELECT 'candidate            剩余 ' || count(*) || ' 条' AS result FROM candidate
UNION ALL
SELECT 'position             剩余 ' || count(*) || ' 条' FROM position
UNION ALL
SELECT 'question_bank        剩余 ' || count(*) || ' 条' FROM question_bank
UNION ALL
SELECT 'resume               剩余 ' || count(*) || ' 条' FROM resume
UNION ALL
SELECT 'resume_work_experience 剩余 ' || count(*) || ' 条' FROM resume_work_experience
UNION ALL
SELECT 'resume_project_experience 剩余 ' || count(*) || ' 条' FROM resume_project_experience
UNION ALL
SELECT 'resume_project_highlight 剩余 ' || count(*) || ' 条' FROM resume_project_highlight
UNION ALL
SELECT 'interview_session    剩余 ' || count(*) || ' 条' FROM interview_session
UNION ALL
SELECT 'interview_round      剩余 ' || count(*) || ' 条' FROM interview_round
UNION ALL
SELECT 'interview_evaluation 剩余 ' || count(*) || ' 条' FROM interview_evaluation
UNION ALL
SELECT 'interview_report     剩余 ' || count(*) || ' 条' FROM interview_report
UNION ALL
SELECT 'interview_proctor_event 剩余 ' || count(*) || ' 条' FROM interview_proctor_event;
