package com.aims.agent.orchestration.state;

import static org.junit.jupiter.api.Assertions.*;

import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.QaPair;
import com.aims.core.session.SessionStatus;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link InterviewState} 状态容器测试：验证 Channel Reducer 行为与 accessor 类型安全。 */
class InterviewStateTest {

    /** 模拟 LangGraph4j CompiledGraph 的状态合并：使用 SCHEMA 中定义的 Channel 策略。 */
    private InterviewState applyUpdate(InterviewState old, Map<String, Object> update) {
        Map<String, Object> merged =
                AgentState.updateState(old.data(), update, InterviewState.SCHEMA);
        return new InterviewState(merged);
    }

    private InterviewState emptyState() {
        return new InterviewState(Map.of());
    }

    // ===== AppenderChannel 测试 =====

    @Nested
    @DisplayName("AppenderChannel：多次更新值被追加到 List")
    class AppenderTests {

        @Test
        @DisplayName("QA_HISTORY 多次追加后累积所有 QaPair")
        void qaHistory_append() {
            var s1 =
                    applyUpdate(
                            emptyState(),
                            Map.of(InterviewState.QA_HISTORY, List.of(new QaPair(1, "Q1", "A1"))));
            var s2 =
                    applyUpdate(
                            s1,
                            Map.of(InterviewState.QA_HISTORY, List.of(new QaPair(2, "Q2", "A2"))));

            assertEquals(2, s2.qaHistory().size());
            assertEquals("Q1", s2.qaHistory().get(0).question());
            assertEquals("Q2", s2.qaHistory().get(1).question());
        }

        @Test
        @DisplayName("QUESTIONS_ASKED 多次追加后含所有问题")
        void questionsAsked_append() {
            var s1 =
                    applyUpdate(
                            emptyState(), Map.of(InterviewState.QUESTIONS_ASKED, List.of("问题1")));
            var s2 = applyUpdate(s1, Map.of(InterviewState.QUESTIONS_ASKED, List.of("问题2")));

            assertEquals(2, s2.questionsAsked().size());
            assertTrue(s2.questionsAsked().contains("问题1"));
            assertTrue(s2.questionsAsked().contains("问题2"));
        }

        @Test
        @DisplayName("ROUND_EVALUATIONS 追加评分结果")
        void roundEvaluations_append() {
            var eval1 = new RoundEvaluation(null, 4, "good", "evidence1");
            var eval2 = new RoundEvaluation(null, 5, "great", "evidence2");
            var s1 =
                    applyUpdate(
                            emptyState(), Map.of(InterviewState.ROUND_EVALUATIONS, List.of(eval1)));
            var s2 = applyUpdate(s1, Map.of(InterviewState.ROUND_EVALUATIONS, List.of(eval2)));

            assertEquals(2, s2.roundEvaluations().size());
        }

        @Test
        @DisplayName("EVALUATED_ROUND_IDS 追加轮次 ID")
        void evaluatedRoundIds_append() {
            var s1 =
                    applyUpdate(
                            emptyState(), Map.of(InterviewState.EVALUATED_ROUND_IDS, List.of(1L)));
            var s2 = applyUpdate(s1, Map.of(InterviewState.EVALUATED_ROUND_IDS, List.of(2L)));

            assertEquals(2, s2.evaluatedRoundIds().size());
            assertTrue(s2.evaluatedRoundIds().contains(1L));
            assertTrue(s2.evaluatedRoundIds().contains(2L));
        }
    }

    // ===== BaseChannel 测试 =====

    @Nested
    @DisplayName("BaseChannel：新值覆盖旧值")
    class BaseChannelTests {

        @Test
        @DisplayName("SESSION_STATUS 新值覆盖旧值")
        void sessionStatus_replace() {
            var s1 =
                    applyUpdate(
                            emptyState(),
                            Map.of(InterviewState.SESSION_STATUS, SessionStatus.IN_PROGRESS));
            var s2 =
                    applyUpdate(
                            s1, Map.of(InterviewState.SESSION_STATUS, SessionStatus.EVALUATING));

            assertEquals(SessionStatus.EVALUATING, s2.sessionStatus());
        }

        @Test
        @DisplayName("CURRENT_QUESTION 新值覆盖旧值")
        void currentQuestion_replace() {
            var s1 = applyUpdate(emptyState(), Map.of(InterviewState.CURRENT_QUESTION, "旧问题"));
            var s2 = applyUpdate(s1, Map.of(InterviewState.CURRENT_QUESTION, "新问题"));

            assertEquals("新问题", s2.currentQuestion());
        }

        @Test
        @DisplayName("FOLLOW_UP_DECISION 新值覆盖旧值")
        void followUpDecision_replace() {
            var d1 = FollowUpDecision.of(FollowUpType.DEEPEN, "追问1", "需要深挖");
            var d2 = FollowUpDecision.noFollowUp("回答充分");
            var s1 = applyUpdate(emptyState(), Map.of(InterviewState.FOLLOW_UP_DECISION, d1));
            var s2 = applyUpdate(s1, Map.of(InterviewState.FOLLOW_UP_DECISION, d2));

            assertSame(d2, s2.followUpDecision());
            assertFalse(s2.followUpDecision().shouldFollowUp());
        }
    }

    // ===== 默认值测试 =====

    @Nested
    @DisplayName("默认值：空 State 的 accessor 返回 SCHEMA 默认值")
    class DefaultValueTests {

        @Test
        @DisplayName("空 State 的 sessionStatus 返回 CREATED")
        void defaultValue_sessionStatus() {
            assertEquals(SessionStatus.CREATED, emptyState().sessionStatus());
        }

        @Test
        @DisplayName("空 State 的 candidateName 返回空字符串")
        void defaultValue_candidateName() {
            assertEquals("", emptyState().candidateName());
        }

        @Test
        @DisplayName("空 State 的 persona 返回 FRIENDLY")
        void defaultValue_persona() {
            assertEquals(InterviewerPersona.FRIENDLY, emptyState().persona());
        }
    }

    // ===== 便捷方法测试 =====

    @Nested
    @DisplayName("便捷方法")
    class ConvenienceMethodTests {

        @Test
        @DisplayName("answeredCount 返回 qaHistory.size()")
        void answeredCount() {
            var state =
                    applyUpdate(
                            emptyState(),
                            Map.of(
                                    InterviewState.QA_HISTORY,
                                    List.of(new QaPair(1, "Q1", "A1"), new QaPair(2, "Q2", "A2"))));
            assertEquals(2, state.answeredCount());
        }

        @Test
        @DisplayName("isMainQuestion：parentSeq==null 时 true，否则 false")
        void isMainQuestion() {
            var mainQData = new java.util.HashMap<String, Object>();
            mainQData.put(InterviewState.PARENT_SEQ, null);
            var mainQ = applyUpdate(emptyState(), mainQData);
            assertTrue(mainQ.isMainQuestion());

            var followUp = applyUpdate(emptyState(), Map.of(InterviewState.PARENT_SEQ, 3));
            assertFalse(followUp.isMainQuestion());
        }

        @Test
        @DisplayName("reachedRoundLimit：answeredCount>=totalRounds 时 true")
        void reachedRoundLimit() {
            var state =
                    applyUpdate(
                            emptyState(),
                            Map.of(
                                    InterviewState.TOTAL_ROUNDS,
                                    2,
                                    InterviewState.QA_HISTORY,
                                    List.of(new QaPair(1, "Q1", "A1"), new QaPair(2, "Q2", "A2"))));
            assertTrue(state.reachedRoundLimit());
        }
    }

    // ===== toString 测试 =====

    @Test
    @DisplayName("toString 包含 sessionId, status, seq 等关键信息")
    void toString_output() {
        var state =
                applyUpdate(
                        emptyState(),
                        Map.of(
                                InterviewState.SESSION_ID,
                                42L,
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS,
                                InterviewState.CURRENT_SEQ,
                                3));
        String str = state.toString();
        assertTrue(str.contains("sessionId=42"));
        assertTrue(str.contains("status=IN_PROGRESS"));
        assertTrue(str.contains("seq=3"));
    }
}
