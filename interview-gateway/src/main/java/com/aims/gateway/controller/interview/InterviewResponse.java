package com.aims.gateway.controller.interview;

import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import java.math.BigDecimal;
import java.time.Instant;

/** 面试会话详情响应。 */
public record InterviewResponse(
        Long id,
        Long candidateId,
        Long resumeId,
        Long positionId,
        SessionStatus status,
        String persona,
        String planJson,
        Instant startedAt,
        Instant endedAt,
        BigDecimal totalScore,
        String evaluationStatus,
        Integer evaluatedRounds,
        Integer totalRoundsToEvaluate,
        Instant createdAt,
        Instant updatedAt,
        String accessToken,
        String accessPassword,
        String accessMode) {

    /** 从持久化实体构建响应。 */
    public static InterviewResponse from(InterviewSessionEntity entity) {
        return from(entity, null);
    }

    /**
     * 从持久化实体构建响应。
     *
     * @param rawPassword 访问密码明文（仅创建/重置时返回，其余场景传 null）
     */
    public static InterviewResponse from(InterviewSessionEntity entity, String rawPassword) {
        SessionStatus sessionStatus =
                entity.getStatus() == null ? null : SessionStatus.valueOf(entity.getStatus());
        return new InterviewResponse(
                entity.getId(),
                entity.getCandidateId(),
                entity.getResumeId(),
                entity.getPositionId(),
                sessionStatus,
                entity.getPersona(),
                entity.getPlanJson(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getTotalScore(),
                entity.getEvaluationStatus(),
                entity.getEvaluatedRounds(),
                entity.getTotalRoundsToEvaluate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAccessToken(),
                rawPassword,
                entity.getAccessMode());
    }
}
