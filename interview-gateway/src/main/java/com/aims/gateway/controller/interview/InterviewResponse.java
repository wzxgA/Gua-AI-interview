package com.aims.gateway.controller.interview;

import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import java.math.BigDecimal;
import java.time.Instant;

/** 面试会话详情响应。 */
public record InterviewResponse(
        Long id,
        Long candidateId,
        Long positionId,
        SessionStatus status,
        String planJson,
        Instant startedAt,
        Instant endedAt,
        BigDecimal totalScore,
        String evaluationStatus,
        Integer evaluatedRounds,
        Integer totalRoundsToEvaluate,
        Instant createdAt,
        Instant updatedAt) {

    /** 从持久化实体构建响应。 */
    public static InterviewResponse from(InterviewSessionEntity entity) {
        SessionStatus sessionStatus =
                entity.getStatus() == null ? null : SessionStatus.valueOf(entity.getStatus());
        return new InterviewResponse(
                entity.getId(),
                entity.getCandidateId(),
                entity.getPositionId(),
                sessionStatus,
                entity.getPlanJson(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getTotalScore(),
                entity.getEvaluationStatus(),
                entity.getEvaluatedRounds(),
                entity.getTotalRoundsToEvaluate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
