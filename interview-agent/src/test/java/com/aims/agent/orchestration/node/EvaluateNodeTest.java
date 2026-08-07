package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.EvaluatorAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.QaPair;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link EvaluateNode} 测试。 */
@ExtendWith(MockitoExtension.class)
class EvaluateNodeTest {

    @Mock private EvaluatorAgent evaluatorAgent;

    private EvaluateNode node;

    @BeforeEach
    void setUp() {
        node = new EvaluateNode(evaluatorAgent);
    }

    private static List<RoundEvaluation> fiveEvals() {
        return List.of(
                new RoundEvaluation(EvaluationDimension.PROFESSIONAL, 4, "good", "e1"),
                new RoundEvaluation(EvaluationDimension.LOGIC, 3, "ok", "e2"),
                new RoundEvaluation(EvaluationDimension.COMMUNICATION, 5, "great", "e3"),
                new RoundEvaluation(EvaluationDimension.JOB_MATCH, 4, "good", "e4"),
                new RoundEvaluation(EvaluationDimension.POTENTIAL, 3, "ok", "e5"));
    }

    @Test
    @DisplayName("批量评估：全部未评估时评估所有 QaPair")
    void batch_evaluate_allUnevaluated() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.QA_HISTORY,
                                List.of(new QaPair(1, "Q1", "A1"), new QaPair(2, "Q2", "A2"))));
        when(evaluatorAgent.evaluate(any())).thenReturn(fiveEvals());

        Map<String, Object> result = node.apply(state);

        List<RoundEvaluation> evals =
                (List<RoundEvaluation>) result.get(InterviewState.ROUND_EVALUATIONS);
        List<Long> ids = (List<Long>) result.get(InterviewState.EVALUATED_ROUND_IDS);

        assertEquals(10, evals.size());
        assertEquals(2, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }

    @Test
    @DisplayName("跳过已评估的轮次")
    void skip_already_evaluated() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID, 1L,
                                InterviewState.QA_HISTORY,
                                        List.of(
                                                new QaPair(1, "Q1", "A1"),
                                                new QaPair(2, "Q2", "A2")),
                                InterviewState.EVALUATED_ROUND_IDS, List.of(1L)));
        when(evaluatorAgent.evaluate(any())).thenReturn(fiveEvals());

        Map<String, Object> result = node.apply(state);

        verify(evaluatorAgent, times(1)).evaluate(any());
        List<Long> ids = (List<Long>) result.get(InterviewState.EVALUATED_ROUND_IDS);
        assertEquals(1, ids.size());
        assertEquals(2L, ids.get(0));
    }

    @Test
    @DisplayName("空 QA_HISTORY 返回空列表")
    void empty_qaHistory_returnsEmpty() throws Exception {
        var state = new InterviewState(Map.of(InterviewState.SESSION_ID, 1L));

        Map<String, Object> result = node.apply(state);

        List<RoundEvaluation> evals =
                (List<RoundEvaluation>) result.get(InterviewState.ROUND_EVALUATIONS);
        List<Long> ids = (List<Long>) result.get(InterviewState.EVALUATED_ROUND_IDS);
        assertTrue(evals.isEmpty());
        assertTrue(ids.isEmpty());
    }
}
