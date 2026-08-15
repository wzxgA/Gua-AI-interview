package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 面试防作弊事件持久化实体（仅结构化事件，不存储视频/图片）。 */
@TableName("interview_proctor_event")
public class ProctorEventEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    /** 事件类型：TAB_SWITCH / WINDOW_BLUR / CAMERA_DENIED / CAMERA_OFF / CAMERA_ON。 */
    @TableField("event_type")
    private String eventType;

    @TableField("occurred_at")
    private Instant occurredAt;

    /** 事件持续时间（毫秒，可选）。 */
    @TableField("duration_ms")
    private Long durationMs;

    /** 扩展信息（JSON 字符串，可选）。 */
    private String detail;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
