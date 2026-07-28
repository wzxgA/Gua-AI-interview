package com.aims.infra.persistence.dto;

import jakarta.validation.constraints.NotNull;

/** 创建面试会话请求。 */
public record CreateInterviewSessionRequest(
        Long candidateId,
        @NotNull(message = "简历 ID 不能为空") Long resumeId,
        @NotNull(message = "岗位 ID 不能为空") Long positionId,
        String planJson) {}
