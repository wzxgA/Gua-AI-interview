package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.InterviewPlanGenerator;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link PlanNode} 测试。 */
@ExtendWith(MockitoExtension.class)
class PlanNodeTest {

    @Mock private InterviewPlanGenerator planGenerator;

    private PlanNode node;

    @BeforeEach
    void setUp() {
        node = new PlanNode(planGenerator);
    }

    private static InterviewPlan mockPlan(int questionCount) {
        List<PlannedQuestion> questions =
                IntStream.range(0, questionCount)
                        .mapToObj(
                                i ->
                                        new PlannedQuestion(
                                                "q" + i, "topic", "EASY", List.of("hint"), "focus"))
                        .toList();
        return new InterviewPlan(
                "张三",
                "Java",
                List.of(new PlanSection("基础", questionCount, "考察")),
                questions,
                60,
                "1.0");
    }

    @Test
    @DisplayName("正常生成面试计划：返回 INTERVIEW_PLAN 和 TOTAL_ROUNDS")
    void generate_plan_success() throws Exception {
        var state = new InterviewState(Map.of());
        var plan = mockPlan(8);
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(plan);

        Map<String, Object> result = node.apply(state);

        assertNotNull(result.get(InterviewState.INTERVIEW_PLAN));
        assertEquals(8, result.get(InterviewState.TOTAL_ROUNDS));
    }

    @Test
    @DisplayName("Agent 异常时异常传播")
    void generate_agentThrows() {
        var state = new InterviewState(Map.of());
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenThrow(new RuntimeException("AI error"));

        assertThrows(RuntimeException.class, () -> node.apply(state));
    }

    @Test
    @DisplayName("State 更新 Key 正确：只有 INTERVIEW_PLAN 和 TOTAL_ROUNDS")
    void state_update_keys_correct() throws Exception {
        var state = new InterviewState(Map.of());
        var plan = mockPlan(10);
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(plan);

        Map<String, Object> result = node.apply(state);

        assertEquals(2, result.size());
        assertTrue(result.containsKey(InterviewState.INTERVIEW_PLAN));
        assertTrue(result.containsKey(InterviewState.TOTAL_ROUNDS));
    }
}
