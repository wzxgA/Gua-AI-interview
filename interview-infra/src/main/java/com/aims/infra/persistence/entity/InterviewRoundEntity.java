package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 面试轮次持久化实体。 */
@TableName("interview_round")
public class InterviewRoundEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    private Integer seq;
    private String question;
    private String answer;

    @TableField("follow_up_type")
    private String followUpType;

    @TableField("parent_seq")
    private Integer parentSeq;

    @TableField("follow_up_index")
    private Integer followUpIndex;

    @TableField("audio_url")
    private String audioUrl;

    @TableField("duration_ms")
    private Integer durationMs;

    /** 简历交叉验证矛盾点 JSONB（v1.1-F4）：ConflictDetail 数组字符串；空列表写 "[]"。 */
    @TableField("conflict_details")
    private String conflictDetails;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

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

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getFollowUpType() {
        return followUpType;
    }

    public void setFollowUpType(String followUpType) {
        this.followUpType = followUpType;
    }

    public Integer getParentSeq() {
        return parentSeq;
    }

    public void setParentSeq(Integer parentSeq) {
        this.parentSeq = parentSeq;
    }

    public Integer getFollowUpIndex() {
        return followUpIndex;
    }

    public void setFollowUpIndex(Integer followUpIndex) {
        this.followUpIndex = followUpIndex;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getConflictDetails() {
        return conflictDetails;
    }

    public void setConflictDetails(String conflictDetails) {
        this.conflictDetails = conflictDetails;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
