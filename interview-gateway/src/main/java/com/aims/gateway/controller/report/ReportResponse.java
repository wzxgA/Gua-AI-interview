package com.aims.gateway.controller.report;

import com.aims.agent.ReportPromptBuilder;
import com.aims.core.interview.ConflictDetail;
import com.aims.core.report.Recommendation;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.ReportEntity;
import com.aims.infra.persistence.service.ConflictDetailsJson;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Instant createdAt,
        String conflictSummary) {

    private static final Map<Recommendation, String> REC_LABELS =
            Map.of(
                    Recommendation.STRONGLY_RECOMMEND, "强烈推荐",
                    Recommendation.RECOMMEND, "推荐",
                    Recommendation.NEUTRAL, "中立",
                    Recommendation.NOT_RECOMMEND, "不推荐");

    public static ReportResponse from(
            ReportEntity entity, BigDecimal totalScore, List<InterviewRoundEntity> rounds) {
        String recName = entity.getRecommendation();
        String label = "";
        try {
            label = REC_LABELS.get(Recommendation.valueOf(recName));
        } catch (Exception ignored) {
        }
        // v1.1-F4：从轮次读回矛盾点清单
        Map<String, List<ConflictDetail>> conflictsByRound = new LinkedHashMap<>();
        for (InterviewRoundEntity r : rounds) {
            List<ConflictDetail> roundConflicts = ConflictDetailsJson.parse(r.getConflictDetails());
            if (!roundConflicts.isEmpty()) {
                conflictsByRound.put(
                        r.getSeq() != null
                                ? String.valueOf(r.getSeq())
                                : r.getParentSeq() + ":" + r.getFollowUpIndex(),
                        roundConflicts);
            }
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
                entity.getCreatedAt(),
                ReportPromptBuilder.formatConflictsByRound(conflictsByRound));
    }
}
