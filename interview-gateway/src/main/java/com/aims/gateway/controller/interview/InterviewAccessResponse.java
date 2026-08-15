package com.aims.gateway.controller.interview;

/** 候选人访问配置响应（管理端）。 */
public record InterviewAccessResponse(
        String accessToken,
        Boolean accessEnabled,
        Boolean requirePassword,
        String accessPassword,
        String accessMode,
        ProctorConfig proctor) {}
