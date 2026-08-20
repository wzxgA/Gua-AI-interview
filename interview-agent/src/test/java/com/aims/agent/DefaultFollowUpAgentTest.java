package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.ResumeEntityMentionExtractor.ResumeMention;
import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.ConflictDetail;
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
    private ResumeEntityMentionExtractor regexMentionExtractor;
    private ResumeEntityMentionExtractor aiMentionExtractor;
    private DefaultFollowUpAgent agent;

    @BeforeEach
    void setUp() {
        aiChatFacade = mock(AiChatFacade.class);
        resumeCrossCheckTool = mock(ResumeCrossCheckTool.class);
        regexMentionExtractor = mock(ResumeEntityMentionExtractor.class);
        aiMentionExtractor = mock(ResumeEntityMentionExtractor.class);
        agent =
                new DefaultFollowUpAgent(
                        aiChatFacade,
                        new ObjectMapper(),
                        resumeCrossCheckTool,
                        regexMentionExtractor,
                        aiMentionExtractor);
    }

    private FollowUpContext ctx() {
        return new FollowUpContext(
                1L,
                null,
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

    @Test
    void evaluate_bareQuoteInReason_lenientlyExtractsAction() {
        // reason 值内带未转义双引号 → 标准 JSON 解析失败，宽松提取应保住 action=CLARIFY + 追问问题
        String raw =
                "{\"action\":\"CLARIFY\",\"reason\":\"与简历中\"未提及内容\"不符\","
                        + "\"followUpQuestion\":\"请说明这段经历\"}";
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn(raw);

        FollowUpDecision decision = agent.evaluate(ctx());

        assertEquals(FollowUpType.CLARIFY, decision.followUpType());
        assertEquals("请说明这段经历", decision.followUpQuestion());
        assertTrue(decision.shouldFollowUp());
    }

    @Test
    void evaluate_unparseableWithoutAction_defaultsToNoFollowUp() {
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn("{\"broken\": true}");

        FollowUpDecision decision = agent.evaluate(ctx());

        assertTrue(!decision.shouldFollowUp());
    }

    @Test
    void evaluate_conflictProbed_carriedIntoDecisionAndPrompt() {
        // resumeId 非空时决策前经两级通道探测矛盾点（stub 两个提取器 + resumeCrossCheckTool）
        ConflictDetail detail = new ConflictDetail("company", null, "阿里巴巴", "我在阿里巴巴负责电商中台");
        ResumeCrossCheckResult cross =
                new ResumeCrossCheckResult(
                        "张三",
                        0.3,
                        0.0,
                        "片段",
                        List.of("阿里巴巴"),
                        List.of("company"),
                        "ENTITY",
                        List.of(detail));
        when(regexMentionExtractor.extract(any()))
                .thenReturn(List.of(new ResumeMention("阿里巴巴", "阿里巴巴")));
        when(aiMentionExtractor.extract(any()))
                .thenReturn(List.of(new ResumeMention("阿里巴巴", "阿里巴巴")));
        when(resumeCrossCheckTool.crossCheck(eq(1L), any(), eq("阿里巴巴"))).thenReturn(cross);
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn(
                        "{\"action\":\"CLARIFY\",\"reason\":\"回答与简历矛盾\",\"followUpQuestion\":\"请说明\"}");

        FollowUpContext ctxWithResume =
                new FollowUpContext(
                        1L,
                        1L,
                        100L,
                        "什么是 IoC？",
                        "我在阿里巴巴负责电商中台",
                        "张三",
                        "Java 后端工程师",
                        "岗位要求",
                        "简历摘要",
                        List.of(),
                        List.of(),
                        null);
        FollowUpDecision decision = agent.evaluate(ctxWithResume);

        assertEquals(FollowUpType.CLARIFY, decision.followUpType());
        // 探测到的矛盾点随决策带出
        assertEquals(1, decision.conflictDetails().size());
        assertEquals("company", decision.conflictDetails().get(0).conflictField());
        // 决策 prompt 注入矛盾证据
        verify(aiChatFacade)
                .callWithTools(
                        eq(ModelTier.STANDARD),
                        any(),
                        argThat(user -> user.contains("阿里巴巴") && user.contains("简历未提及")),
                        any());
        // 探测时以 AI 确认的真实体作为 companyHint 定向比对
        verify(resumeCrossCheckTool).crossCheck(eq(1L), any(), eq("阿里巴巴"));
    }

    @Test
    void probeConflicts_noRegexCandidate_noAiCallNoConflicts() {
        // 正则无候选 → 不触发 AI、不触发 DB 比对
        when(regexMentionExtractor.extract(any())).thenReturn(List.of());
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn("{\"action\":\"NEXT\",\"reason\":\"回答充分\",\"followUpQuestion\":null}");

        FollowUpContext ctxWithResume =
                new FollowUpContext(
                        1L,
                        1L,
                        100L,
                        "问题",
                        "我对微服务架构很有心得，做过分布式系统",
                        "张三",
                        "Java 后端工程师",
                        "岗位要求",
                        "简历摘要",
                        List.of(),
                        List.of(),
                        null);
        agent.evaluate(ctxWithResume);

        verify(aiMentionExtractor, never()).extract(any());
        verify(resumeCrossCheckTool, never()).crossCheck(any(), any(), any());
    }

    @Test
    void probeConflicts_aiFiltersNonEntity_noConflicts() {
        // AI 判定候选为非真实体（如「CLH等待队列」）→ 不产生矛盾点，也不走 DB
        when(regexMentionExtractor.extract(any()))
                .thenReturn(List.of(new ResumeMention("CLH等待队列", "CLH等待队列")));
        when(aiMentionExtractor.extract(any())).thenReturn(List.of());
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn("{\"action\":\"NEXT\",\"reason\":\"回答充分\",\"followUpQuestion\":null}");

        FollowUpContext ctxWithResume =
                new FollowUpContext(
                        1L,
                        1L,
                        100L,
                        "问题",
                        "我用「CLH等待队列」解决并发",
                        "张三",
                        "Java 后端工程师",
                        "岗位要求",
                        "简历摘要",
                        List.of(),
                        List.of(),
                        null);
        FollowUpDecision decision = agent.evaluate(ctxWithResume);

        assertTrue(decision.conflictDetails().isEmpty());
        verify(resumeCrossCheckTool, never()).crossCheck(any(), any(), any());
    }

    @Test
    void probeConflicts_aiUnavailable_fallsBackToRegexHint() {
        // AI 不可用（null）→ 回退正则提名作为 companyHint 走 DB
        ConflictDetail detail = new ConflictDetail("company", null, "腾讯", "曾就职于腾讯");
        ResumeCrossCheckResult cross =
                new ResumeCrossCheckResult(
                        "张三",
                        0.3,
                        0.0,
                        "片段",
                        List.of("腾讯"),
                        List.of("company"),
                        "ENTITY",
                        List.of(detail));
        when(regexMentionExtractor.extract(any()))
                .thenReturn(List.of(new ResumeMention("腾讯", "腾讯")));
        when(aiMentionExtractor.extract(any())).thenReturn(null);
        when(resumeCrossCheckTool.crossCheck(eq(1L), any(), eq("腾讯"))).thenReturn(cross);
        when(aiChatFacade.callWithTools(eq(ModelTier.STANDARD), any(), any(), any()))
                .thenReturn("{\"action\":\"NEXT\",\"reason\":\"回答充分\",\"followUpQuestion\":null}");

        FollowUpContext ctxWithResume =
                new FollowUpContext(
                        1L,
                        1L,
                        100L,
                        "问题",
                        "曾就职于腾讯",
                        "张三",
                        "Java 后端工程师",
                        "岗位要求",
                        "简历摘要",
                        List.of(),
                        List.of(),
                        null);
        FollowUpDecision decision = agent.evaluate(ctxWithResume);

        assertEquals(1, decision.conflictDetails().size());
        assertEquals("company", decision.conflictDetails().get(0).conflictField());
        verify(resumeCrossCheckTool).crossCheck(eq(1L), any(), eq("腾讯"));
    }
}
