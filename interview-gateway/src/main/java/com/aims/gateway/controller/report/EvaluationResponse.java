package com.aims.gateway.controller.report;

import com.aims.core.evaluation.EvaluationDimension;
import com.aims.infra.persistence.entity.EvaluationEntity;
import java.time.Instant;

/** 单条评分响应。 */
public record EvaluationResponse(
        Long id,
        Long sessionId,
        Long roundId,
        String dimension,
        String dimensionLabel,
        int score,
        String comment,
        String evidenceQuote,
        Instant createdAt) {

    public static EvaluationResponse from(EvaluationEntity entity) {
        String dimName = entity.getDimension();
        String label = "";
        try {
            label = EvaluationDimension.valueOf(dimName).getLabel();
        } catch (Exception ignored) {
        }
        return new EvaluationResponse(
                entity.getId(),
                entity.getSessionId(),
                entity.getRoundId(),
                dimName,
                label,
                entity.getScore(),
                entity.getComment(),
                entity.getEvidenceQuote(),
                entity.getCreatedAt());
    }
}
