package com.aims.agent.orchestration.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewPlanGenerator;
import com.aims.agent.InterviewerAgent;
import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.StreamEmitter;
import com.aims.agent.orchestration.node.SummaryNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.interview.QaPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

/**
 * Graph 完整流程集成测试。
 *
 * <p>使用真实 Node + Mock Agent 验证 Graph 拓扑和条件边路由的正确性。采用测试专用 AnswerNode（匿名子类）直接 生成固定 Mock 回答，避免暂停-恢复语义。
 *
 * <p>测试场景：
 *
 * <ol>
 *   <li>8 轮无追问完整流程
 *   <li>PlanNode 异常 → 错误路径路由到 Report
 *   <li>第 1 轮触发追问 → 追问后回到主流程
 *   <li>Summary 阈值：QA 不足 5 条时跳过，足够后生成摘要
 * </ol>
 *
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class InterviewGraphIntegrationTest {

    @Mock private InterviewPlanGenerator planGenerator;
    @Mock private InterviewerAgent interviewerAgent;
    @Mock private FollowUpAgent followUpAgent;
    @Mock private SummaryAgent summaryAgent;

    private InterviewGraphFactory factory;

    @BeforeEach
    void setUp() {
        StreamEmitter emitter = StreamEmitter.NOOP;
        // 使用测试专用 AnswerNode：QuestionNode 会清空 CURRENT_ANSWER，
        // 测试无法做暂停-恢复注入，因此 AnswerNode 直接使用固定 Mock 回答。
        // 追问标记逻辑与真实 AnswerNode 保持一致：PENDING_FOLLOW_UP=true 时构造追问 QaPair
        AnswerNode testAnswerNode =
                new AnswerNode() {
                    @Override
                    public Map<String, Object> apply(InterviewState state) throws Exception {
                        QaPair qaPair =
                                state.pendingFollowUp()
                                        ? new QaPair(
                                                state.currentSeq(),
                                                state.currentQuestion(),
                                                "Mock answer",
                                                state.followUpIndex(),
                                                state.followUpType())
                                        : new QaPair(
                                                state.currentSeq(),
                                                state.currentQuestion(),
                                                "Mock answer");
                        return Map.of(InterviewState.QA_HISTORY, qaPair);
                    }
                };
        factory =
                new InterviewGraphFactory(
                        new PlanNode(planGenerator),
                        new QuestionNode(interviewerAgent, emitter),
                        testAnswerNode,
                        new FollowUpDecisionNode(followUpAgent),
                        new FollowUpNode(followUpAgent, emitter),
                        null, // evaluateNode 已移至 Kafka 链路（FE.04），图内不注册，保留占位
                        new SummaryNode(summaryAgent),
                        new EndCheckNode(),
                        null); // reportNode 已移至 Kafka 链路（FE.04），图内不注册，保留占位
    }

    /** 构建一个 8 题的合法 InterviewPlan（MIN_QUESTION_COUNT=8）。 */
    private InterviewPlan mockPlan() {
        List<PlannedQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            questions.add(
                    new PlannedQuestion(
                            "q" + i, "topic" + i, "BALANCED", List.of("hint" + i), "focus" + i));
        }
        return new InterviewPlan(
                "John",
                "Java Developer",
                List.of(new PlanSection("技术", 8, "技术评估")),
                questions,
                45,
                "1.0");
    }

    @Test
    @DisplayName("完整流程：8 轮提问（无追问），达上限后流程结束")
    void fullFlow_8Rounds_noFollowUp() throws Exception {
        // Plan
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockPlan());

        // Question: 每轮都返回一个固定问题
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("Question"));

        // FollowUpDecision: 每轮都不追问
        when(followUpAgent.evaluate(any())).thenReturn(FollowUpDecision.noFollowUp("sufficient"));

        // Summary: 足够 5 条时返回摘要
        when(summaryAgent.summarize(any())).thenReturn("Summary text");

        // 执行
        CompiledGraph<InterviewState> graph = factory.compileWithoutCheckpoint();
        Map<String, Object> inputs =
                Map.of(
                        InterviewState.SESSION_ID,
                        1L,
                        InterviewState.CANDIDATE_NAME,
                        "John",
                        InterviewState.POSITION_TITLE,
                        "Java Developer",
                        InterviewState.JD_TEXT,
                        "JD",
                        InterviewState.RESUME_SUMMARY,
                        "Resume",
                        InterviewState.PERSONA,
                        InterviewerPersona.FRIENDLY,
                        InterviewState.TOTAL_ROUNDS,
                        8);

        Optional<InterviewState> result = graph.invoke(inputs);

        // 验证：8 轮问答完成、流程结束（FE.04：评估/报告由 Kafka 链路完成，图内不再生成）
        assertTrue(result.isPresent());
        InterviewState finalState = result.get();
        assertTrue(finalState.lastError() == null, "lastError should be null");
        assertEquals(8, finalState.qaHistory().size(), "should have 8 QaPairs");
        assertTrue(
                finalState.currentSeq() >= finalState.totalRounds(),
                "应满足结束条件（currentSeq >= totalRounds）");
    }

    @Test
    @DisplayName("完整流程：PlanNode 抛异常 → LAST_ERROR 非空，流程结束不卡死")
    void fullFlow_errorInPlan_routesToEnd() throws Exception {
        // Plan 抛异常（FaultTolerantNode 会重试 2 次后写入 LAST_ERROR）
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        // 执行
        CompiledGraph<InterviewState> graph = factory.compileWithoutCheckpoint();
        Map<String, Object> inputs =
                Map.of(
                        InterviewState.SESSION_ID,
                        1L,
                        InterviewState.CANDIDATE_NAME,
                        "John",
                        InterviewState.POSITION_TITLE,
                        "Java Developer",
                        InterviewState.TOTAL_ROUNDS,
                        8);

        Optional<InterviewState> result = graph.invoke(inputs);

        // 验证：流程不卡死，LAST_ERROR 非空，满足结束条件（FE.04：错误终止由 endCheck→END 承接）
        assertTrue(result.isPresent());
        InterviewState finalState = result.get();
        assertNotNull(finalState.lastError(), "lastError should not be null");
    }

    @Test
    @DisplayName("完整流程：第 1 轮触发追问 → 追问后回到主流程，达上限后结束")
    void fullFlow_withFollowUp() throws Exception {
        // Plan
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockPlan());

        // Question: 每轮都返回一个固定问题
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("Question"));

        // FollowUpDecision: 第 1 次返回 shouldFollowUp=true，之后返回 false
        when(followUpAgent.evaluate(any()))
                .thenReturn(
                        FollowUpDecision.of(
                                FollowUpType.DEEPEN, "Can you elaborate?", "needs detail"))
                .thenReturn(FollowUpDecision.noFollowUp("sufficient"));

        // FollowUp: 追问流式
        when(followUpAgent.streamFollowUp(any(), any()))
                .thenReturn(Flux.just("Follow-up question"));

        // Summary
        when(summaryAgent.summarize(any())).thenReturn("Summary text");

        // 执行
        CompiledGraph<InterviewState> graph = factory.compileWithoutCheckpoint();
        Map<String, Object> inputs =
                Map.of(
                        InterviewState.SESSION_ID,
                        1L,
                        InterviewState.CANDIDATE_NAME,
                        "John",
                        InterviewState.POSITION_TITLE,
                        "Java Developer",
                        InterviewState.JD_TEXT,
                        "JD",
                        InterviewState.RESUME_SUMMARY,
                        "Resume",
                        InterviewState.PERSONA,
                        InterviewerPersona.FRIENDLY,
                        InterviewState.TOTAL_ROUNDS,
                        8);

        Optional<InterviewState> result = graph.invoke(inputs);

        // 验证
        assertTrue(result.isPresent());
        InterviewState finalState = result.get();
        assertTrue(finalState.lastError() == null, "lastError should be null");
        // 8 条主问题 Q&A + 1 条追问 Q&A（追问回答经 followUp→answer 回环收集）
        assertEquals(9, finalState.qaHistory().size(), "should have 8 main + 1 followUp QaPairs");
        long followUpQaCount = finalState.qaHistory().stream().filter(QaPair::isFollowUp).count();
        assertEquals(1, followUpQaCount, "should have 1 followUp QaPair");
        QaPair followUpQa =
                finalState.qaHistory().stream()
                        .filter(QaPair::isFollowUp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, followUpQa.followUpIndex());
        assertEquals(FollowUpType.DEEPEN, followUpQa.followUpType());
        // 追问问答不影响题数上限判定（qaHistory 9 条 > totalRounds 8，仍以 currentSeq 判定结束）
        assertTrue(
                finalState.currentSeq() >= finalState.totalRounds(),
                "应满足结束条件（currentSeq >= totalRounds）");
    }

    @Test
    @DisplayName("完整流程：QA 不足 5 条时 SummaryNode 跳过，足够后摘要生成")
    void fullFlow_summaryThreshold() throws Exception {
        // Plan
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockPlan());

        // Question
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("Question"));

        // FollowUpDecision: 不追问
        when(followUpAgent.evaluate(any())).thenReturn(FollowUpDecision.noFollowUp("sufficient"));

        // Summary
        when(summaryAgent.summarize(any())).thenReturn("Summary text");

        // 执行
        CompiledGraph<InterviewState> graph = factory.compileWithoutCheckpoint();
        Map<String, Object> inputs =
                Map.of(
                        InterviewState.SESSION_ID,
                        1L,
                        InterviewState.CANDIDATE_NAME,
                        "John",
                        InterviewState.POSITION_TITLE,
                        "Java Developer",
                        InterviewState.JD_TEXT,
                        "JD",
                        InterviewState.RESUME_SUMMARY,
                        "Resume",
                        InterviewState.PERSONA,
                        InterviewerPersona.FRIENDLY,
                        InterviewState.TOTAL_ROUNDS,
                        8);

        Optional<InterviewState> result = graph.invoke(inputs);

        // 验证：摘要应在第 5 轮后生成，最终 runningSummary 非空
        assertTrue(result.isPresent());
        InterviewState finalState = result.get();
        assertTrue(finalState.lastError() == null, "lastError should be null");
        assertNotNull(finalState.runningSummary(), "runningSummary should be set after round 5");
        assertTrue(finalState.lastSummarizedSeq() >= 5, "lastSummarizedSeq should be >= 5");
    }
}
