-- P4 修复：为 interview_report.session_id 添加唯一约束，支持 ON CONFLICT upsert
ALTER TABLE interview_report
    ADD CONSTRAINT uk_report_session UNIQUE (session_id);
