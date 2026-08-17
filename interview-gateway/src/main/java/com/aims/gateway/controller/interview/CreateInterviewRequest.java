package com.aims.gateway.controller.interview;

import jakarta.validation.constraints.NotNull;

/** 创建面试会话请求（v1.1-C TD2：入参为简历 ID，候选人由简历归集得出）。 */
public record CreateInterviewRequest(
        @NotNull(message = "简历 ID 不能为空") Long resumeId,
        Long positionId,
        String persona,
        String accessPassword) {}
