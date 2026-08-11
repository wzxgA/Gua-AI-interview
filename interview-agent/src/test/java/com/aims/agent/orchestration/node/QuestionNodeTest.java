package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.InterviewerAgent;
import com.aims.agent.orchestration.state.InterviewState;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

/** {@link QuestionNode} 测试：验证流式适配。 */
@ExtendWith(MockitoExtension.class)
class QuestionNodeTest {

    @Mock private InterviewerAgent interviewerAgent;
    @Mock private StreamEmitter streamEmitter;

    private QuestionNode node;

    @BeforeEach
    void setUp() {
        node = new QuestionNode(interviewerAgent, streamEmitter);
    }

    @Test
    @DisplayName("流式生成问题：chunk 通过 emitter 推送，完整文本写入 State")
    void streamQuestion_emitsChunks_andAccumulatesFullText() throws Exception {
        var state = new InterviewState(Map.of(InterviewState.CURRENT_SEQ, 0));
        when(interviewerAgent.streamQuestion(any()))
                .thenReturn(Flux.just("What ", "is ", "Spring ", "Boot?"));

        Map<String, Object> result = node.apply(state);

        verify(streamEmitter).emitStart(1);
        verify(streamEmitter, times(4)).emit(anyString());
        verify(streamEmitter).emitEnd("What is Spring Boot?");
        assertEquals("What is Spring Boot?", result.get(InterviewState.CURRENT_QUESTION));
    }

    @Test
    @DisplayName("CURRENT_SEQ 递增")
    void streamQuestion_incrementsSeq() throws Exception {
        var state = new InterviewState(Map.of(InterviewState.CURRENT_SEQ, 3));
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("问题"));

        Map<String, Object> result = node.apply(state);

        assertEquals(4, result.get(InterviewState.CURRENT_SEQ));
    }

    @Test
    @DisplayName("CURRENT_ANSWER 清空为空字符串")
    void streamQuestion_clearsAnswer() throws Exception {
        var state = new InterviewState(Map.of(InterviewState.CURRENT_SEQ, 0));
        when(interviewerAgent.streamQuestion(any())).thenReturn(Flux.just("问题"));

        Map<String, Object> result = node.apply(state);

        assertEquals("", result.get(InterviewState.CURRENT_ANSWER));
    }

    @Test
    @DisplayName("Agent 异常时异常传播")
    void streamQuestion_agentThrows() {
        var state = new InterviewState(Map.of(InterviewState.CURRENT_SEQ, 0));
        when(interviewerAgent.streamQuestion(any()))
                .thenReturn(Flux.error(new RuntimeException("AI error")));

        assertThrows(RuntimeException.class, () -> node.apply(state));
    }
}
