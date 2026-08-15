-- 面试防作弊事件表（切屏/失焦等行为事件，仅记录结构化数据，不存储视频/图片）
CREATE TABLE interview_proctor_event (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL REFERENCES interview_session(id),
    event_type   VARCHAR(32) NOT NULL,   -- TAB_SWITCH / WINDOW_BLUR / CAMERA_DENIED / CAMERA_OFF / CAMERA_ON
    occurred_at  TIMESTAMPTZ NOT NULL,
    duration_ms  BIGINT,                 -- 事件持续时间（毫秒，可选）
    detail       JSONB                   -- 扩展信息（可选）
);

CREATE INDEX idx_proctor_session ON interview_proctor_event(session_id, occurred_at);

-- 面试级防作弊开关（生成候选人链接时配置）：{"tabSwitch":false,"gaze":false}
ALTER TABLE interview_session
    ADD COLUMN proctor_json JSONB;
