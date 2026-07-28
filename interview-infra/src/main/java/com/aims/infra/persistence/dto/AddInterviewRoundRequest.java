package com.aims.infra.persistence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 新增面试轮次请求。 */
public record AddInterviewRoundRequest(
        @NotNull(message = "轮次序号不能为空") Integer seq,
        @NotBlank(message = "问题不能为空") String question,
        String answer,
        String followUpType,
        String audioUrl,
        Integer durationMs) {}
