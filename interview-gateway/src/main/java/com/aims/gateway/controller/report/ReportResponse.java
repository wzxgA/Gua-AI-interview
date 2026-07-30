package com.aims.gateway.controller.report;

import com.aims.core.report.Recommendation;
import com.aims.infra.persistence.entity.ReportEntity;
import java.math.BigDecimal;
import java.time.Instant;

/** 面试报告响应。 */
public record ReportResponse(
        Long id,
        Long sessionId,
        String summary,
        String dimensionsJson,
        String recommendation,
        String recommendationLabel,
        BigDecimal totalScore,
        String reportPdfUrl,
        Instant createdAt) {

    private static final java.util.Map<Recommendation, String> REC_LABELS =
            java.util.Map.of(
                    Recommendation.STRONGLY_RECOMMEND, "强烈推荐",
                    Recommendation.RECOMMEND, "推荐",
                    Recommendation.NEUTRAL, "中立",
                    Recommendation.NOT_RECOMMEND, "不推荐");

    public static ReportResponse from(ReportEntity entity, BigDecimal totalScore) {
        String recName = entity.getRecommendation();
        String label = "";
        try {
            label = REC_LABELS.get(Recommendation.valueOf(recName));
        } catch (Exception ignored) {
        }
        return new ReportResponse(
                entity.getId(),
                entity.getSessionId(),
                entity.getSummary(),
                entity.getDimensionsJson(),
                recName,
                label != null ? label : "",
                totalScore,
                entity.getReportPdfUrl(),
                entity.getCreatedAt());
    }
}
