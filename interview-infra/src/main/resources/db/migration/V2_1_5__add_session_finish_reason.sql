-- v1.4-A 面试结束原因透明化：记录谁结束、以何种方式结束
ALTER TABLE interview_session
    ADD COLUMN finished_by VARCHAR(20),
    ADD COLUMN finish_reason VARCHAR(30);