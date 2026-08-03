package com.aims.gateway.controller.interview;

import com.aims.infra.persistence.entity.InterviewRoundEntity;
import java.time.Instant;

/** 面试轮次响应。 */
public record RoundResponse(
        Long id,
        Integer seq,
        String question,
        String answer,
        String followUpType,
        Integer parentSeq,
        Integer followUpIndex,
        Instant createdAt) {

    /** 从持久化实体构建响应。 */
    public static RoundResponse from(InterviewRoundEntity entity) {
        return new RoundResponse(
                entity.getId(),
                entity.getSeq(),
                entity.getQuestion(),
                entity.getAnswer(),
                entity.getFollowUpType(),
                entity.getParentSeq(),
                entity.getFollowUpIndex(),
                entity.getCreatedAt());
    }
}
