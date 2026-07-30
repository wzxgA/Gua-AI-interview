package com.aims.agent;

import com.aims.core.evaluation.EvaluationContext;
import com.aims.core.evaluation.RoundEvaluation;
import java.util.List;

/**
 * 评估 Agent：对单轮问答进行五维度 AI 评分。
 *
 * <p>使用 STANDARD 档位模型，温度 0，保证可复现。
 */
public interface EvaluatorAgent {

    /**
     * 对单轮问答进行五维度评分。
     *
     * @param context 评估上下文
     * @return 五维度评分结果
     */
    List<RoundEvaluation> evaluate(EvaluationContext context);
}
