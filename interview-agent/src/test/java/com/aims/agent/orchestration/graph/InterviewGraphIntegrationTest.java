package com.aims.agent.orchestration.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.aims.agent.DefaultReportAgent;
import com.aims.agent.EvaluatorAgent;
import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewPlanGenerator;
import com.aims.agent.InterviewerAgent;
import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.EvaluateNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.ReportNode;
import com.aims.agent.orchestration.node.StreamEmitter;
import com.aims.agent.orchestration.node.SummaryNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.DimensionAggregate;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.interview.QaPair;
import com.aims.core.report.Recommendation;
import com.aims.core.report.ReportResult;
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
    @Mock private EvaluatorAgent evaluatorAgent;
    @Mock private SummaryAgent summaryAgent;
    @Mock private DefaultReportAgent reportAgent;

    private InterviewGraphFactory factory;

    @BeforeEach
    void setUp() {
        StreamEmitter emitter = StreamEmitter.NOOP;
        // 使用测试专用 AnswerNode：QuestionNode 会清空 CURRENT_ANSWER，
        // 测试无法做暂停-恢复注入，因此 AnswerNode 直接使用固定 Mock 回答
        AnswerNode testAnswerNode =
                new AnswerNode() {
                    @Override
                    public Map<String, Object> apply(InterviewState state) throws Exception {
                        QaPair qaPair =
                                new QaPair(
                                        state.currentSeq(), state.currentQuestion(), "Mock answer");
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
                        new EvaluateNode(evaluatorAgent),
                        new SummaryNode(summaryAgent),
                        new EndCheckNode(),
                        new ReportNode(reportAgent));
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

    private RoundEvaluation mockEvaluation() {
        return new RoundEvaluation(EvaluationDimension.PROFESSIONAL, 4, "good", "evidence");
    }

    private ReportResult mockReport() {
        return new ReportResult("Final summary", Map.of(), Recommendation.NEUTRAL);
    }

    @Test
    @DisplayName("完整流程：8 轮提问（无追问），最终生成报告")
    void fullFlow_8Rounds_noFollowUp() throws Exception {
        // Plan
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockPlan());

        // Question: 每轮都返回一个固定问题
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("Question"));

        // FollowUpDecision: 每轮都不追问
        when(followUpAgent.evaluate(any())).thenReturn(FollowUpDecision.noFollowUp("sufficient"));

        // Evaluate: 每轮返回 1 个评估
        when(evaluatorAgent.evaluate(any())).thenReturn(List.of(mockEvaluation()));

        // Summary: 足够 5 条时返回摘要
        when(summaryAgent.summarize(any())).thenReturn("Summary text");

        // Report
        when(reportAgent.generate(any(), any(DimensionAggregate.class), anyDouble()))
                .thenReturn(mockReport());

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
        assertNotNull(finalState.reportResult(), "reportResult should not be null");
        assertEquals(8, finalState.qaHistory().size(), "should have 8 QaPairs");
    }

    @Test
    @DisplayName("完整流程：PlanNode 抛异常 → LAST_ERROR 非空，流程不卡死")
    void fullFlow_errorInPlan_routesToReport() throws Exception {
        // Plan 抛异常（FaultTolerantNode 会重试 2 次后写入 LAST_ERROR）
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        // Report 仍需 mock（错误路径会路由到 report）
        when(reportAgent.generate(any(), any(DimensionAggregate.class), anyDouble()))
                .thenReturn(mockReport());

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

        // 验证：流程不卡死，LAST_ERROR 非空
        assertTrue(result.isPresent());
        InterviewState finalState = result.get();
        assertNotNull(finalState.lastError(), "lastError should not be null");
    }

    @Test
    @DisplayName("完整流程：第 1 轮触发追问 → 追问后回到主流程，最终生成报告")
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

        // Evaluate: 每轮返回 1 个评估
        when(evaluatorAgent.evaluate(any())).thenReturn(List.of(mockEvaluation()));

        // Summary
        when(summaryAgent.summarize(any())).thenReturn("Summary text");

        // Report
        when(reportAgent.generate(any(), any(DimensionAggregate.class), anyDouble()))
                .thenReturn(mockReport());

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
        assertNotNull(finalState.reportResult(), "reportResult should not be null");
        assertEquals(8, finalState.qaHistory().size(), "should have 8 QaPairs");
        assertTrue(finalState.followUpCount() >= 1, "followUpCount should be >= 1");
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

        // Evaluate
        when(evaluatorAgent.evaluate(any())).thenReturn(List.of(mockEvaluation()));

        // Summary
        when(summaryAgent.summarize(any())).thenReturn("Summary text");

        // Report
        when(reportAgent.generate(any(), any(DimensionAggregate.class), anyDouble()))
                .thenReturn(mockReport());

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
