package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.SupervisorContext;
import com.aims.core.interview.SupervisorDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 默认总指挥实现：STANDARD 档结构化输出；解析失败/空结果兜底为 CONTINUE（不阻断面试）。 */
@Service
public class DefaultSupervisorAgent implements SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultSupervisorAgent.class);

    private final AiChatFacade aiChatFacade;

    public DefaultSupervisorAgent(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    @Override
    public SupervisorDecision supervise(SupervisorContext ctx) {
        try {
            SupervisorDecision decision =
                    aiChatFacade.callForEntity(
                            ModelTier.STANDARD,
                            SupervisorPromptBuilder.system(),
                            SupervisorPromptBuilder.user(ctx),
                            SupervisorDecision.class);
            if (decision == null || decision.action() == null) {
                log.warn("总指挥决策为空，按正常继续 sessionId={}", ctx.sessionId());
                return SupervisorDecision.fallback();
            }
            log.info(
                    "总指挥决策 sessionId={} action={} reason={} elapsedMs={} answered={}/{}",
                    ctx.sessionId(),
                    decision.action(),
                    decision.reason(),
                    ctx.elapsedMs(),
                    ctx.answeredCount(),
                    ctx.totalRounds());
            return decision;
        } catch (Exception e) {
            log.warn("总指挥决策失败，按正常继续 sessionId={}", ctx.sessionId(), e);
            return SupervisorDecision.fallback();
        }
    }
}
