package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.exception.AiOutputParseException;
import com.aims.core.evaluation.EvaluationContext;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.evaluation.RoundEvaluations;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 评估 Agent 实现：经 {@link AiChatFacade} 调用 STANDARD 模型生成五维度评分。
 *
 * <p>Prompt 构建委托给 {@link EvaluationPromptBuilder}，本类只负责 AI 调用和结果校验。
 */
@Service
public class DefaultEvaluatorAgent implements EvaluatorAgent {

    private final AiChatFacade aiChatFacade;

    public DefaultEvaluatorAgent(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    @Override
    public List<RoundEvaluation> evaluate(EvaluationContext context) {
        RoundEvaluations result =
                aiChatFacade.callForEntity(
                        ModelTier.STANDARD,
                        EvaluationPromptBuilder.evaluatorSystem(),
                        EvaluationPromptBuilder.evaluatorUser(context),
                        RoundEvaluations.class);
        if (result == null
                || result.evaluations() == null
                || result.evaluations().size() != 5) {
            throw new AiOutputParseException("评估结果维度数不正确，期望 5 个", null);
        }
        return result.evaluations();
    }
}
