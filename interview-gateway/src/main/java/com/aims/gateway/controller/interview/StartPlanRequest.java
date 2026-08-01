package com.aims.gateway.controller.interview;

/** 开始生成面试计划请求（参数可选，不传则使用默认值）。 */
public record StartPlanRequest(
        Integer questionCount, // 题数，范围 1-30，默认 10
        String difficulty // 难度偏好：BASIC/BALANCED/ADVANCED，默认 BALANCED
        ) {}
