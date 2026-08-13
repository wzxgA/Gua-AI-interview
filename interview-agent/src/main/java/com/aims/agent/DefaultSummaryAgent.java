package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.SummaryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SummaryAgent 默认实现。
 *
 * <p>使用 ECONOMY 档位模型生成滚动摘要，失败时降级返回 previousSummary。
 */
@Service
public class DefaultSummaryAgent implements SummaryAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultSummaryAgent.class);

    private final AiChatFacade aiChatFacade;
    private final SummaryPromptBuilder summaryPromptBuilder;

    public DefaultSummaryAgent(
            AiChatFacade aiChatFacade, SummaryPromptBuilder summaryPromptBuilder) {
        this.aiChatFacade = aiChatFacade;
        this.summaryPromptBuilder = summaryPromptBuilder;
    }

    @Override
    public String summarize(SummaryContext context) {
        try {
            String systemPrompt = summaryPromptBuilder.buildSystemPrompt();
            String userPrompt = summaryPromptBuilder.buildUserPrompt(context);
            String result = aiChatFacade.call(ModelTier.ECONOMY, systemPrompt, userPrompt);
            if (result != null && !result.isBlank()) {
                log.debug("摘要生成成功，sessionId={}", context.sessionId());
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("摘要生成失败，降级使用旧摘要，sessionId={}", context.sessionId(), e);
        }
        return context.previousSummary();
    }
}
