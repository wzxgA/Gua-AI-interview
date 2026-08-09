package com.aims.agent.orchestration.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aims.agent.DefaultReportAgent;
import com.aims.agent.EvaluatorAgent;
import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewPlanGenerator;
import com.aims.agent.InterviewerAgent;
import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.graph.InterviewGraphFactory;
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
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

/**
 * langgraph4j 1.8.22 interruptBefore API 验证测试（T4.5）。
 *
 * <p>目标：确认 {@link CompileConfig.Builder#interruptBefore(String...)} 真的能在指定节点前暂停 Graph 执行， 并能通过
 * {@link CompiledGraph#stateOf(RunnableConfig)} 读到 next 节点为 ANSWER。
 *
 * <p>验证通过 → T5 使用 interruptBefore(ANSWER) 实现提问后暂停。 验证失败 → fallback 到 AnswerNode no-op 方案。
 *
 * @since 1.1.0 Phase 5 前置验证
 */
@ExtendWith(MockitoExtension.class)
class InterruptBeforeApiVerificationTest {

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
        // AnswerNode 用 no-op 实现：interruptBefore 后此节点根本不会被执行
        AnswerNode noOpAnswer =
                new AnswerNode() {
                    @Override
                    public Map<String, Object> apply(InterviewState state) throws Exception {
                        return Map.of();
                    }
                };
        factory =
                new InterviewGraphFactory(
                        new PlanNode(planGenerator),
                        new QuestionNode(interviewerAgent, emitter),
                        noOpAnswer,
                        new FollowUpDecisionNode(followUpAgent),
                        new FollowUpNode(followUpAgent, emitter),
                        new EvaluateNode(evaluatorAgent),
                        new SummaryNode(summaryAgent),
                        new EndCheckNode(),
                        new ReportNode(reportAgent));
    }

    private InterviewPlan mockPlan() {
        List<PlannedQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            questions.add(
                    new PlannedQuestion(
                            "q" + i, "topic" + i, "BALANCED", List.of("hint" + i), "focus" + i));
        }
        return new InterviewPlan(
                "测试候选人",
                "Java 开发",
                List.of(new PlanSection("技术", 8, "技术评估")),
                questions,
                45,
                "1.0");
    }

    @Test
    @DisplayName("interruptBefore(ANSWER) 让 Graph 在 ASK 后暂停，next=ANSWER")
    void interruptBeforeAnswer_pausesGraph() throws Exception {
        when(planGenerator.generate(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        any(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(mockPlan());
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("Mock 问题内容"));

        CompiledGraph<InterviewState> graph = factory.compileWithInterruptBeforeAnswer(null);

        Map<String, Object> inputs =
                Map.of(
                        InterviewState.SESSION_ID,
                        999L,
                        InterviewState.CANDIDATE_NAME,
                        "测试",
                        InterviewState.POSITION_TITLE,
                        "Java",
                        InterviewState.JD_TEXT,
                        "JD",
                        InterviewState.RESUME_SUMMARY,
                        "简历",
                        InterviewState.PERSONA,
                        InterviewerPersona.FRIENDLY,
                        InterviewState.TOTAL_ROUNDS,
                        2);

        RunnableConfig config = RunnableConfig.builder().threadId("verify-interrupt-999").build();

        // 执行：应执行 plan → ask，然后在 answer 前暂停
        Optional<InterviewState> result = graph.invoke(inputs, config);

        // 验证 1：invoke 返回暂停点的 state（非空）
        // langgraph4j 1.8.22 行为：interruptBefore 暂停时 invoke 仍返回 Optional，含暂停前的最新 state
        assertTrue(result.isPresent(), "invoke 应返回暂停时的 state（非空 Optional）");
        InterviewState returnedState = result.get();

        // 验证 2：ASK 已执行（seq=1，currentQuestion 非空），但 ANSWER 未执行（qaHistory 为空）
        assertEquals(1, returnedState.currentSeq(), "ASK 节点应已执行，seq=1");
        assertTrue(returnedState.qaHistory().isEmpty(), "ANSWER 节点应未执行，qaHistory 应为空");
        assertNotNull(returnedState.currentQuestion(), "currentQuestion 应已填充");
        assertTrue(returnedState.currentQuestion().length() > 0, "问题文本不应为空");

        // 验证 3：stateOf 需要 CheckpointSaver，本测试传 null 无法验证
        // 实际 Engine 使用 RedisCheckpointSaver 时，stateOf(config).next() 应返回 NodeNames.ANSWER
        // 该行为由 CheckpointRecoveryIntegrationTest 在 Phase 4 已验证
    }
}
