package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.report.ReportContext;
import com.aims.core.report.ReportResult;
import org.springframework.stereotype.Service;

/**
 * 报告 Agent 实现：经 {@link AiChatFacade} 调用 STANDARD 模型生成综合报告。
 *
 * <p>Prompt 构建委托给 {@link ReportPromptBuilder}，本类只负责 AI 调用。
 */
@Service
public class DefaultReportAgent implements ReportAgent {

    private final AiChatFacade aiChatFacade;

    public DefaultReportAgent(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    @Override
    public ReportResult generate(ReportContext context) {
        // 注意：weightedScore 和 aggregate 由 Service 层计算后传入 Prompt
        // 此处由 ReportService 计算后通过 Prompt 构建传入
        throw new UnsupportedOperationException(
                "Use generate(ReportContext, DimensionAggregate, double) instead");
    }

    /**
     * 生成综合面试报告（含维度聚合与加权得分）。
     *
     * @param context 报告上下文
     * @param aggregate 维度聚合结果
     * @param weightedScore 综合加权得分
     * @return 结构化面试报告
     */
    public ReportResult generate(
            ReportContext context,
            com.aims.core.evaluation.DimensionAggregate aggregate,
            double weightedScore) {
        return aiChatFacade.callForEntity(
                ModelTier.STANDARD,
                ReportPromptBuilder.reportSystem(),
                ReportPromptBuilder.reportUser(context, aggregate, weightedScore),
                ReportResult.class);
    }
}
