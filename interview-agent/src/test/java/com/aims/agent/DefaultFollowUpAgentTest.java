package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link DefaultFollowUpAgent} 单元测试：F2 起决策调用走 callWithTools（注册简历交叉验证工具）， 输出解析与兜底逻辑不变。 */
class DefaultFollowUpAgentTest {

    private AiChatFacade aiChatFacade;
    private ResumeCrossCheckTool resumeCrossCheckTool;
    private DefaultFollowUpAgent agent;

    @BeforeEach
    void setUp() {
        aiChatFacade = mock(AiChatFacade.class);
        resumeCrossCheckTool = mock(ResumeCrossCheckTool.class);
        agent = new DefaultFollowUpAgent(aiChatFacade, new ObjectMapper(), resumeCrossCheckTool);
    }

    private FollowUpContext ctx() {
        return new FollowUpContext(
                1L,
                100L,
                "什么是 IoC？",
                "控制反转...",
                "张三",
                "Java 后端工程师",
                "岗位要求",
                "简历摘要",
                List.of(),
                List.of(),
                null);
    }

    @Test
    void evaluate_usesCallWithToolsWithResumeTool() {
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn(
                        "{\"action\":\"CLARIFY\",\"reason\":\"回答与简历矛盾\",\"followUpQuestion\":\"请说明\"}");

        FollowUpDecision decision = agent.evaluate(ctx());

        assertEquals(FollowUpType.CLARIFY, decision.followUpType());
        assertEquals("请说明", decision.followUpQuestion());
        // 决策调用必须走 callWithTools 且注册了 ResumeCrossCheckTool
        verify(aiChatFacade)
                .callWithTools(
                        eq(ModelTier.STANDARD),
                        any(),
                        any(),
                        argThat(tools -> tools != null && tools.contains(resumeCrossCheckTool)));
    }

    @Test
    void evaluate_actionNext_returnsNoFollowUp() {
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn("{\"action\":\"NEXT\",\"reason\":\"回答充分\",\"followUpQuestion\":null}");

        FollowUpDecision decision = agent.evaluate(ctx());

        assertTrue(!decision.shouldFollowUp());
        assertEquals(FollowUpType.NONE, decision.followUpType());
    }

    @Test
    void evaluate_blankResult_defaultsToNoFollowUp() {
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn("");

        FollowUpDecision decision = agent.evaluate(ctx());

        assertTrue(!decision.shouldFollowUp());
    }

    @Test
    void evaluate_exception_defaultsToNoFollowUp() {
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenThrow(new RuntimeException("ai down"));

        FollowUpDecision decision = agent.evaluate(ctx());

        assertTrue(!decision.shouldFollowUp());
    }
}
