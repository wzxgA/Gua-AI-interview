package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.SupervisorAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.agent.orchestration.state.TestStateBuilder;
import com.aims.core.interview.SupervisorAction;
import com.aims.core.interview.SupervisorContext;
import com.aims.core.interview.SupervisorDecision;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link SuperviseNode} 单元测试：灰度开关透传 / 决策写入 / 失败兜底。 */
class SuperviseNodeTest {

    private SupervisorAgent supervisorAgent;
    private SuperviseNode node;

    @BeforeEach
    void setUp() {
        supervisorAgent = mock(SupervisorAgent.class);
        node = new SuperviseNode(supervisorAgent);
    }

    private void enable(boolean enabled) {
        ReflectionTestUtils.setField(node, "supervisorEnabled", enabled);
    }

    private InterviewState state(int seq, int total) {
        return TestStateBuilder.forTesting()
                .withSessionId(1L)
                .withCurrentSeq(seq)
                .withTotalRounds(total)
                .build();
    }

    @Test
    void disabled_passthrough_noDecision() {
        enable(false);
        Map<String, Object> updates = node.apply(state(1, 3));
        assertEquals(Map.of(), updates);
        verify(supervisorAgent, never()).supervise(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabled_writesDecision() {
        enable(true);
        SupervisorDecision d =
                new SupervisorDecision(SupervisorAction.TIGHTEN, "进度偏慢", null, false);
        when(supervisorAgent.supervise(org.mockito.ArgumentMatchers.any())).thenReturn(d);

        Map<String, Object> updates = node.apply(state(1, 3));

        assertEquals(d, updates.get(InterviewState.SUPERVISOR_DECISION));
        assertEquals(true, updates.containsKey(InterviewState.ELAPSED_MS));
    }

    @Test
    void enabled_agentFails_fallbackContinue_notBlock() {
        enable(true);
        when(supervisorAgent.supervise(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("ai down"));

        Map<String, Object> updates = node.apply(state(1, 3));

        SupervisorDecision d = (SupervisorDecision) updates.get(InterviewState.SUPERVISOR_DECISION);
        assertEquals(SupervisorAction.CONTINUE, d.action());
    }

    @Test
    void contextCarriesElapsedAndQuality() {
        enable(true);
        SupervisorDecision d = new SupervisorDecision(SupervisorAction.CONTINUE, "ok", null, false);
        when(supervisorAgent.supervise(org.mockito.ArgumentMatchers.any())).thenReturn(d);

        node.apply(state(1, 3));

        // 校验传递给总指挥的上下文包含关键字段（elapsedMs >= 0，answeredCount=0）
        var captor = org.mockito.ArgumentCaptor.forClass(SupervisorContext.class);
        verify(supervisorAgent).supervise(captor.capture());
        SupervisorContext ctx = captor.getValue();
        assertEquals(1L, ctx.sessionId());
        assertEquals(1, ctx.currentSeq());
        assertEquals(3, ctx.totalRounds());
        // 空 QA_HISTORY → 已完成主问题数为 0
        assertEquals(0, ctx.answeredMainCount());
        assertEquals(0, ctx.answeredCount());
        assertNull(ctx.avgScore());
    }
}
