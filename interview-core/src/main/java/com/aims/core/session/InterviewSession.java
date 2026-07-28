package com.aims.core.session;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 面试会话领域模型。
 *
 * @param id 会话 ID
 * @param candidateId 候选人 ID
 * @param positionId 岗位 ID
 * @param status 会话状态
 * @param planJson 面试计划 JSON
 * @param startedAt 开始时间
 * @param endedAt 结束时间
 * @param totalScore 总评分
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record InterviewSession(
        Long id,
        Long candidateId,
        Long positionId,
        SessionStatus status,
        String planJson,
        Instant startedAt,
        Instant endedAt,
        BigDecimal totalScore,
        Instant createdAt,
        Instant updatedAt) {}
