package com.aims.agent;

import com.aims.core.evaluation.DimensionAggregate;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.report.ReportContext;

/**
 * 报告 Prompt 统一构建器：集中管理报告 Agent 的 Prompt 模板。
 */
public final class ReportPromptBuilder {

    private static final String REPORT_SYSTEM =
            """
            你是资深面试官和人才评估专家。请基于面试评分结果和对话内容，生成一份结构化面试报告。
            要求：
            1. 综合评述应涵盖候选人整体表现、优势与不足
            2. 录用建议基于综合得分和各维度表现综合判断
            3. 综合得分 >= 4.0 强烈推荐；3.5-4.0 推荐；2.5-3.5 中立；< 2.5 不推荐
            4. 只输出符合 ReportResult schema 的 JSON，不要额外说明
            """;

    private ReportPromptBuilder() {}

    /** 报告系统 Prompt。 */
    public static String reportSystem() {
        return REPORT_SYSTEM;
    }

    /** 构造报告用户 Prompt。 */
    public static String reportUser(ReportContext context, DimensionAggregate aggregate, double weightedScore) {
        return """
               候选人：%s
               岗位：%s
               岗位 JD：%s

               各维度评分汇总：
               %s
               综合加权得分：%.2f / 5.0

               对话摘要：
               %s

               请输出报告 JSON，字段说明：
               - summary: 综合评述（200-500 字）
               - dimensions: 各维度评分明细，key 为维度名（PROFESSIONAL/LOGIC/COMMUNICATION/JOB_MATCH/POTENTIAL），value 含 avgScore 和 count
               - recommendation: STRONGLY_RECOMMEND / RECOMMEND / NEUTRAL / NOT_RECOMMEND
               """
                .formatted(
                        safe(context.candidateName()),
                        safe(context.positionTitle()),
                        safe(context.jdText()),
                        EvaluationPromptBuilder.formatDimensionSummary(aggregate),
                        weightedScore,
                        safe(context.conversationSummary()));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}
