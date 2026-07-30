package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/** 面试会话持久化实体。 */
@TableName("interview_session")
public class InterviewSessionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("candidate_id")
    private Long candidateId;

    @TableField("resume_id")
    private Long resumeId;

    @TableField("position_id")
    private Long positionId;

    private String status;

    @TableField("plan_json")
    private String planJson;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("ended_at")
    private Instant endedAt;

    @TableField("total_score")
    private BigDecimal totalScore;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("evaluation_status")
    private String evaluationStatus;

    @TableField("evaluation_error")
    private String evaluationError;

    @TableField("evaluated_rounds")
    private Integer evaluatedRounds;

    @TableField("total_rounds_to_evaluate")
    private Integer totalRoundsToEvaluate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getResumeId() {
        return resumeId;
    }

    public void setResumeId(Long resumeId) {
        this.resumeId = resumeId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getEvaluationStatus() {
        return evaluationStatus;
    }

    public void setEvaluationStatus(String evaluationStatus) {
        this.evaluationStatus = evaluationStatus;
    }

    public String getEvaluationError() {
        return evaluationError;
    }

    public void setEvaluationError(String evaluationError) {
        this.evaluationError = evaluationError;
    }

    public Integer getEvaluatedRounds() {
        return evaluatedRounds;
    }

    public void setEvaluatedRounds(Integer evaluatedRounds) {
        this.evaluatedRounds = evaluatedRounds;
    }

    public Integer getTotalRoundsToEvaluate() {
        return totalRoundsToEvaluate;
    }

    public void setTotalRoundsToEvaluate(Integer totalRoundsToEvaluate) {
        this.totalRoundsToEvaluate = totalRoundsToEvaluate;
    }
}
