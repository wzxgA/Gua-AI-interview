package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 默认面试官实现：经 {@link AiChatFacade} 调用 FLAGSHIP 模型生成问题。
 *
 * <p>Prompt 构建委托给 {@link InterviewPromptBuilder}，本类只负责 AI 调用和结果校验。
 */
@Service
public class DefaultInterviewerAgent implements InterviewerAgent {

    private final AiChatFacade aiChatFacade;

    public DefaultInterviewerAgent(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    @Override
    public String nextQuestion(InterviewContext context) {
        if (context.plan() == null || context.currentRound() > context.totalRounds()) {
            return "请先做一个简单的自我介绍。";
        }
        String result =
                aiChatFacade.call(
                        ModelTier.FLAGSHIP,
                        InterviewPromptBuilder.interviewerSystem(),
                        InterviewPromptBuilder.interviewerUser(context));
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("面试官生成的问题不能为空");
        }
        return result.trim();
    }

    @Override
    public Flux<String> streamQuestion(InterviewContext context) {
        if (context.plan() == null || context.currentRound() > context.totalRounds()) {
            return Flux.just("请先做一个简单的自我介绍。");
        }
        return aiChatFacade.stream(
                        ModelTier.FLAGSHIP,
                        InterviewPromptBuilder.interviewerSystem(),
                        InterviewPromptBuilder.interviewerUser(context))
                .filter(chunk -> chunk != null && !chunk.isBlank());
    }
}
