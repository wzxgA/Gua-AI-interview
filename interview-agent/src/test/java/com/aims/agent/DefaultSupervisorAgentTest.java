package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.SupervisorAction;
import com.aims.core.interview.SupervisorContext;
import com.aims.core.interview.SupervisorDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link DefaultSupervisorAgent} 单元测试：正常决策 / 空决策兜底 / 调用异常兜底。 */
class DefaultSupervisorAgentTest {

    private AiChatFacade aiChatFacade;
    private DefaultSupervisorAgent agent;

    @BeforeEach
    void setUp() {
        aiChatFacade = mock(AiChatFacade.class);
        agent = new DefaultSupervisorAgent(aiChatFacade);
    }

    private SupervisorContext ctx() {
        return new SupervisorContext(1L, 2, 8, 2, 3, 1, 3_600_000L, 3.5);
    }

    @Test
    void supervise_returnsDecision() {
        SupervisorDecision d =
                new SupervisorDecision(SupervisorAction.TIGHTEN, "进度偏慢", null, false);
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(SupervisorDecision.class)))
                .thenReturn(d);

        assertEquals(d, agent.supervise(ctx()));
    }

    @Test
    void supervise_nullDecision_fallsBackToContinue() {
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(SupervisorDecision.class)))
                .thenReturn(null);

        SupervisorDecision d = agent.supervise(ctx());
        assertEquals(SupervisorAction.CONTINUE, d.action());
    }

    @Test
    void supervise_exception_fallsBackToContinue() {
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(SupervisorDecision.class)))
                .thenThrow(new RuntimeException("ai down"));

        SupervisorDecision d = agent.supervise(ctx());
        assertEquals(SupervisorAction.CONTINUE, d.action());
    }
}
