package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.PlannedQuestion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link FollowUpDecisionNode} 测试。 */
@ExtendWith(MockitoExtension.class)
class FollowUpDecisionNodeTest {

    @Mock private FollowUpAgent followUpAgent;

    private FollowUpDecisionNode node;

    @BeforeEach
    void setUp() {
        node = new FollowUpDecisionNode(followUpAgent);
    }

    private static InterviewPlan planWithHints(int seq, List<String> hints) {
        // Use mock to bypass 8-10 question validation
        var plan = mock(InterviewPlan.class);
        var question = new PlannedQuestion("q" + seq, "topic", "EASY", hints, "focus");
        when(plan.questions()).thenReturn(List.of(question));
        return plan;
    }

    @Test
    @DisplayName("正常决策：返回 FOLLOW_UP_DECISION 和递增的 FOLLOW_UP_COUNT")
    void evaluate_returnsDecision() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID, 1L,
                                InterviewState.CURRENT_SEQ, 1,
                                InterviewState.FOLLOW_UP_COUNT, 0));
        var decision = FollowUpDecision.of(FollowUpType.DEEPEN, "追问", "需要深挖");
        when(followUpAgent.evaluate(any())).thenReturn(decision);

        Map<String, Object> result = node.apply(state);

        assertEquals(decision, result.get(InterviewState.FOLLOW_UP_DECISION));
        assertEquals(1, result.get(InterviewState.FOLLOW_UP_COUNT));
    }

    @Test
    @DisplayName("noFollowUp 时 FOLLOW_UP_COUNT 不递增")
    void evaluate_noFollowUp_countNotIncremented() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID, 1L,
                                InterviewState.CURRENT_SEQ, 1,
                                InterviewState.FOLLOW_UP_COUNT, 2));
        var decision = FollowUpDecision.noFollowUp("回答充分");
        when(followUpAgent.evaluate(any())).thenReturn(decision);

        Map<String, Object> result = node.apply(state);

        assertEquals(2, result.get(InterviewState.FOLLOW_UP_COUNT));
    }

    @Test
    @DisplayName("从 Plan 按 seq 提取 followUpHints 传入 FollowUpContext")
    void hints_extracted_from_plan() throws Exception {
        var plan = planWithHints(1, List.of("hint1", "hint2"));
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.CURRENT_SEQ,
                                1,
                                InterviewState.INTERVIEW_PLAN,
                                plan));
        when(followUpAgent.evaluate(any())).thenReturn(FollowUpDecision.noFollowUp("ok"));

        node.apply(state);

        ArgumentCaptor<FollowUpContext> captor = ArgumentCaptor.forClass(FollowUpContext.class);
        verify(followUpAgent).evaluate(captor.capture());
        assertEquals(List.of("hint1", "hint2"), captor.getValue().followUpHints());
    }
}
