package com.aims.agent.orchestration.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.SummaryNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.agent.orchestration.state.TestStateBuilder;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.SupervisorAction;
import com.aims.core.interview.SupervisorDecision;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link InterviewGraphFactory} 的单元测试。
 *
 * <p>覆盖条件边路由逻辑、编译产出、边界情况。
 *
 * @since 1.1.0
 */
class InterviewGraphFactoryTest {

    private InterviewGraphFactory factory;

    @BeforeEach
    void setUp() {
        factory =
                new InterviewGraphFactory(
                        mock(PlanNode.class),
                        mock(QuestionNode.class),
                        mock(AnswerNode.class),
                        mock(FollowUpDecisionNode.class),
                        mock(FollowUpNode.class),
                        mock(SummaryNode.class),
                        mock(EndCheckNode.class));
    }

    // ─── 条件边 1: followUpDecision 路由 ───

    @Nested
    @DisplayName("routeAfterFollowUpDecision")
    class FollowUpDecisionRouter {

        @Test
        @DisplayName("shouldFollowUp=true → FOLLOW_UP")
        void shouldFollowUp() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .with(FollowUpDecision.of(FollowUpType.DEEPEN, "topic", "reason"))
                            .withFollowUpCount(0)
                            .build();
            assertEquals(NodeNames.FOLLOW_UP, factory.routeAfterFollowUpDecision(state));
        }

        @Test
        @DisplayName("shouldFollowUp=false → SUMMARY")
        void noFollowUp() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .with(FollowUpDecision.noFollowUp("sufficient"))
                            .build();
            assertEquals(NodeNames.SUMMARY, factory.routeAfterFollowUpDecision(state));
        }

        @Test
        @DisplayName("decision=null → SUMMARY")
        void nullDecision() throws Exception {
            InterviewState state = TestStateBuilder.forTesting().build();
            assertEquals(NodeNames.SUMMARY, factory.routeAfterFollowUpDecision(state));
        }

        @Test
        @DisplayName("lastError 非空 → SUMMARY（错误优先跳过追问）")
        void hasError() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .with(FollowUpDecision.of(FollowUpType.DEEPEN, "topic", "reason"))
                            .withFollowUpCount(0)
                            .withLastError("LLM timeout")
                            .build();
            assertEquals(NodeNames.SUMMARY, factory.routeAfterFollowUpDecision(state));
        }

        @Test
        @DisplayName("followUpCount >= 3 → SUMMARY（追问上限保护）")
        void followUpCountExceedsLimit() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .with(FollowUpDecision.of(FollowUpType.DEEPEN, "topic", "reason"))
                            .withFollowUpCount(3)
                            .build();
            assertEquals(NodeNames.SUMMARY, factory.routeAfterFollowUpDecision(state));
        }

        @Test
        @DisplayName("错误覆盖决策：lastError + shouldFollowUp=true → SUMMARY")
        void errorOverridesDecision() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .with(FollowUpDecision.of(FollowUpType.DEEPEN, "topic", "reason"))
                            .withFollowUpCount(0)
                            .withLastError("error")
                            .build();
            assertEquals(NodeNames.SUMMARY, factory.routeAfterFollowUpDecision(state));
        }
    }

    // ─── 条件边 2: endCheck 路由 ───

    @Nested
    @DisplayName("routeAfterEndCheck")
    class EndCheckRouter {

        @Test
        @DisplayName("currentSeq >= totalRounds → END")
        void shouldEnd() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting().withCurrentSeq(3).withTotalRounds(3).build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("currentSeq < totalRounds → ASK")
        void shouldContinue() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting().withCurrentSeq(1).withTotalRounds(3).build();
            assertEquals(NodeNames.ASK, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("lastError 非空 → END（错误终止）")
        void hasError() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .withCurrentSeq(1)
                            .withTotalRounds(3)
                            .withLastError("error")
                            .build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("currentSeq == totalRounds 精确匹配 → END")
        void exactMatch() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting().withCurrentSeq(5).withTotalRounds(5).build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("currentSeq > totalRounds 溢出 → END")
        void overflow() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting().withCurrentSeq(6).withTotalRounds(5).build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("totalRounds=0 边界 → END")
        void zeroRounds() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting().withCurrentSeq(0).withTotalRounds(0).build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("forceEnd=true → END（即使 currentSeq < totalRounds）")
        void forceEndOverrides() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .withCurrentSeq(1)
                            .withTotalRounds(3)
                            .withForceEnd(true)
                            .build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("supervisorDecision=END → END（未达上限也提前结束）")
        void supervisorEnd() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .withCurrentSeq(1)
                            .withTotalRounds(3)
                            .with(
                                    InterviewState.SUPERVISOR_DECISION,
                                    new SupervisorDecision(
                                            SupervisorAction.END, "严重超时", null, false))
                            .build();
            assertEquals(StateGraph.END, factory.routeAfterEndCheck(state));
        }

        @Test
        @DisplayName("supervisorDecision=CONTINUE → ASK（未达上限）")
        void supervisorContinue() throws Exception {
            InterviewState state =
                    TestStateBuilder.forTesting()
                            .withCurrentSeq(1)
                            .withTotalRounds(3)
                            .with(
                                    InterviewState.SUPERVISOR_DECISION,
                                    new SupervisorDecision(
                                            SupervisorAction.CONTINUE, "正常", null, false))
                            .build();
            assertEquals(NodeNames.ASK, factory.routeAfterEndCheck(state));
        }
    }

    // ─── Graph 编译验证 ───

    @Nested
    @DisplayName("Graph 编译")
    class CompileTest {

        @Test
        @DisplayName("compileWithoutCheckpoint 返回非 null CompiledGraph")
        void compileWithoutCheckpointReturnsCompiledGraph() throws Exception {
            CompiledGraph<InterviewState> graph = factory.compileWithoutCheckpoint();
            assertNotNull(graph);
        }

        @Test
        @DisplayName("buildGraph 返回非 null StateGraph")
        void buildGraphReturnsStateGraph() throws Exception {
            StateGraph<InterviewState> graph = factory.buildGraph();
            assertNotNull(graph);
        }
    }
}
