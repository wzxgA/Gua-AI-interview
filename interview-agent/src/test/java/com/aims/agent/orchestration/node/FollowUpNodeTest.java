package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

/** {@link FollowUpNode} 测试：验证流式适配。 */
@ExtendWith(MockitoExtension.class)
class FollowUpNodeTest {

    @Mock private FollowUpAgent followUpAgent;
    @Mock private StreamEmitter streamEmitter;

    private FollowUpNode node;

    @BeforeEach
    void setUp() {
        node = new FollowUpNode(followUpAgent, streamEmitter);
    }

    private InterviewState stateWithDecision(FollowUpDecision decision, Integer followUpIndex) {
        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, 1L);
        data.put(InterviewState.CURRENT_SEQ, 3);
        data.put(InterviewState.FOLLOW_UP_DECISION, decision);
        if (followUpIndex != null) {
            data.put(InterviewState.FOLLOW_UP_INDEX, followUpIndex);
        }
        return new InterviewState(data);
    }

    @Test
    @DisplayName("流式生成追问：chunk 推送 + 完整文本写入 State")
    void streamFollowUp_emitsChunks_andAccumulatesFullText() throws Exception {
        var state = stateWithDecision(FollowUpDecision.of(FollowUpType.DEEPEN, "追问", "需要深挖"), null);
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("能否", "详细", "说明"));

        Map<String, Object> result = node.apply(state);

        verify(streamEmitter, times(3)).emit(eq(1L), anyString());
        assertEquals("能否详细说明", result.get(InterviewState.CURRENT_QUESTION));
    }

    @Test
    @DisplayName("追问协议帧：emitFollowUpStart 携带 type/parentSeq/index，emitFollowUpEnd 携带完整文本")
    void followUp_protocolFrames() throws Exception {
        var state =
                stateWithDecision(FollowUpDecision.of(FollowUpType.CLARIFY, "追问", "需要澄清"), null);
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("能否", "具体"));

        node.apply(state);

        verify(streamEmitter).emitFollowUpStart(1L, FollowUpType.CLARIFY, 3, 1);
        verify(streamEmitter).emitFollowUpEnd(1L, "能否具体");
    }

    @Test
    @DisplayName("生成后计数：FOLLOW_UP_COUNT+1、PENDING_FOLLOW_UP=true")
    void followUp_incrementsCount_andSetsPendingFlag() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, 1L);
        data.put(InterviewState.CURRENT_SEQ, 3);
        data.put(InterviewState.FOLLOW_UP_COUNT, 1);
        data.put(
                InterviewState.FOLLOW_UP_DECISION,
                FollowUpDecision.of(FollowUpType.DEEPEN, "追问", "需要深挖"));
        var state = new InterviewState(data);
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("问题"));

        Map<String, Object> result = node.apply(state);

        assertEquals(2, result.get(InterviewState.FOLLOW_UP_COUNT));
        assertEquals(true, result.get(InterviewState.PENDING_FOLLOW_UP));
    }

    @Test
    @DisplayName("FOLLOW_UP_INDEX 递增：null → 1，已有值 +1")
    void followUpIndex_incremented() throws Exception {
        var decision = FollowUpDecision.of(FollowUpType.DEEPEN, "追问", "需要深挖");

        // null → 1
        var state1 = stateWithDecision(decision, null);
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("问题"));
        Map<String, Object> result1 = node.apply(state1);
        assertEquals(1, result1.get(InterviewState.FOLLOW_UP_INDEX));

        // 2 → 3
        var state2 = stateWithDecision(decision, 2);
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("问题"));
        Map<String, Object> result2 = node.apply(state2);
        assertEquals(3, result2.get(InterviewState.FOLLOW_UP_INDEX));
    }

    @Test
    @DisplayName("PARENT_SEQ 设置为 CURRENT_SEQ")
    void parentSeq_set_to_currentSeq() throws Exception {
        var state = stateWithDecision(FollowUpDecision.of(FollowUpType.DEEPEN, "追问", "需要深挖"), null);
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("问题"));

        Map<String, Object> result = node.apply(state);

        assertEquals(3, result.get(InterviewState.PARENT_SEQ));
    }

    @Test
    @DisplayName("FOLLOW_UP_DECISION 为 null 时抛出异常")
    void noDecision_throws() {
        var state = new InterviewState(Map.of(InterviewState.SESSION_ID, 1L));

        assertThrows(IllegalStateException.class, () -> node.apply(state));
    }
}
