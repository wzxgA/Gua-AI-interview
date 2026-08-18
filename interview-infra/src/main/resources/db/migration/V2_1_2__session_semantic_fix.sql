-- v1.1-C TD2 语义归位：interview_session.candidate_id 由"简历 ID"改指 candidate 表
-- 1) resume_id 归位：旧 candidate_id 实存 resume.id，复制到 resume_id（启用该列语义）
UPDATE interview_session
SET resume_id = candidate_id
WHERE resume_id IS NULL
  AND candidate_id IS NOT NULL;

-- 2) candidate_id 重映射：经 resume.candidate_id（V2_1_1 已按姓名去重回填）指向真候选人
UPDATE interview_session s
SET candidate_id = r.candidate_id
FROM resume r
WHERE r.id = s.resume_id
  AND r.candidate_id IS NOT NULL;
