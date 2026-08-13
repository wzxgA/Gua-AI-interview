package com.aims.gateway.controller.interview;

import jakarta.validation.constraints.NotNull;

/** 创建面试会话请求。 */
public record CreateInterviewRequest(
        @NotNull(message = "候选人 ID 不能为空") Long candidateId,
        Long positionId,
        String persona,
        String accessPassword) {}
