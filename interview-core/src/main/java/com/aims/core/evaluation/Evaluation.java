package com.aims.core.evaluation;

import java.time.Instant;

/**
 * 面试评分领域模型。
 *
 * @param id 评分 ID
 * @param sessionId 会话 ID
 * @param roundId 轮次 ID
 * @param dimension 评估维度
 * @param score 分值
 * @param comment 评语
 * @param evidenceQuote 佐证原话
 * @param createdAt 创建时间
 */
public record Evaluation(
        Long id,
        Long sessionId,
        Long roundId,
        EvaluationDimension dimension,
        int score,
        String comment,
        String evidenceQuote,
        Instant createdAt) {}
