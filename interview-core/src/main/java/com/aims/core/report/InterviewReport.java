package com.aims.core.report;

import java.time.Instant;

/**
 * 面试报告领域模型。
 *
 * @param id 报告 ID
 * @param sessionId 会话 ID
 * @param summary 综合评述
 * @param dimensionsJson 各维度评分明细 JSON
 * @param recommendation 录用建议
 * @param reportPdfUrl 报告 PDF 地址
 * @param createdAt 创建时间
 */
public record InterviewReport(
        Long id,
        Long sessionId,
        String summary,
        String dimensionsJson,
        Recommendation recommendation,
        String reportPdfUrl,
        Instant createdAt) {}
