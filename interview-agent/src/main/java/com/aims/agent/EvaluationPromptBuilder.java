package com.aims.agent;

import com.aims.core.evaluation.DimensionAggregate;
import com.aims.core.evaluation.EvaluationContext;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;

/**
 * 评估 Prompt 统一构建器：集中管理评估 Agent 的 Prompt 模板。
 */
public final class EvaluationPromptBuilder {

    private static final String EVALUATOR_SYSTEM =
            """
            你是资深面试评估专家。请根据面试问答内容和岗位要求，对候选人的回答进行五维度评分。
            评分维度与权重：
            1. PROFESSIONAL（专业能力 40%）：知识点准确性、深度、实践经验真实性
            2. LOGIC（逻辑思维 20%）：条理、结构化表达、问题拆解能力
            3. COMMUNICATION（沟通表达 15%）：清晰度、简洁性、专业术语运用
            4. JOB_MATCH（岗位匹配 15%）：与 JD 要求的契合度
            5. POTENTIAL（学习与潜力 10%）：对未知问题的态度与推理过程

            要求：
            1. 每维度评分 1-5 分（5 分优秀，1 分差）
            2. 每维度必须提供评语，说明评分理由
            3. 每维度必须引用候选人回答中的原话作为证据
            4. 基于事实评分，不主观臆断
            5. 只输出符合 RoundEvaluations schema 的 JSON，不要额外说明
            """;

    private EvaluationPromptBuilder() {}

    /** 评估系统 Prompt。 */
    public static String evaluatorSystem() {
        return EVALUATOR_SYSTEM;
    }

    /** 构造评估用户 Prompt。 */
    public static String evaluatorUser(EvaluationContext context) {
        return """
               岗位名称：%s
               岗位 JD：%s
               简历摘要：%s

               第 %d 轮问答：
               问题：%s
               回答：%s

               请输出五维度评分 JSON，字段说明：
               - evaluations: 评分列表，每项含 dimension/score/comment/evidenceQuote
                 - dimension: PROFESSIONAL / LOGIC / COMMUNICATION / JOB_MATCH / POTENTIAL
                 - score: 1-5 整数
                 - comment: 评语
                 - evidenceQuote: 候选人回答中的原话引用
               """
                .formatted(
                        safe(context.positionTitle()),
                        safe(context.jdText()),
                        safe(context.resumeSummary()),
                        context.seq(),
                        safe(context.question()),
                        safe(context.answer()));
    }

    /** 构造报告用户 Prompt 中各维度评分汇总部分。 */
    public static String formatDimensionSummary(DimensionAggregate aggregate) {
        StringBuilder sb = new StringBuilder();
        for (EvaluationDimension dim : EvaluationDimension.values()) {
            DimensionAggregate.DimensionScore ds = aggregate.get(dim);
            if (ds != null) {
                sb.append("- ")
                        .append(dim.getLabel())
                        .append("：平均 ")
                        .append(String.format("%.1f", ds.avgScore()))
                        .append(" 分（")
                        .append(ds.count())
                        .append(" 轮）\n");
            }
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}
