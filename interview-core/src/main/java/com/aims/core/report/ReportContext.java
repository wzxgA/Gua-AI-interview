package com.aims.core.report;

import java.util.List;

/**
 * 报告上下文，传递给 {@code ReportAgent}。
 *
 * @param sessionId 会话 ID
 * @param candidateName 候选人姓名
 * @param positionTitle 岗位名称
 * @param jdText 岗位 JD
 * @param resumeSummary 简历摘要
 * @param evaluationSummaries 全部评分汇总（每轮每维度）
 * @param conversationSummary 对话摘要（问题+回答精简）
 */
public record ReportContext(
        Long sessionId,
        String candidateName,
        String positionTitle,
        String jdText,
        String resumeSummary,
        List<EvaluationSummary> evaluationSummaries,
        String conversationSummary) {

    /** 单条评分汇总（传递给 ReportAgent 的精简结构）。 */
    public record EvaluationSummary(
            int seq, String dimension, int score, String comment, String evidenceQuote) {}
}
