package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.orchestration.state.InterviewState;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link FaultTolerantNode} 测试。 */
@ExtendWith(MockitoExtension.class)
class FaultTolerantNodeTest {

    @Mock private NodeAction<InterviewState> delegate;

    @BeforeEach
    void setUp() throws Exception {
        // lenient: only some tests use this
        lenient().when(delegate.apply(any())).thenReturn(Map.of("key", "value"));
    }

    @Test
    @DisplayName("首次成功：直接返回结果，不重试")
    void first_attempt_success() throws Exception {
        var node = new FaultTolerantNode<>(delegate);

        Map<String, Object> result = node.apply(new InterviewState(Map.of()));

        assertEquals("value", result.get("key"));
        verify(delegate, times(1)).apply(any());
    }

    @Test
    @DisplayName("重试后成功：第一次失败，第二次成功")
    void retry_then_success() throws Exception {
        when(delegate.apply(any()))
                .thenThrow(new RuntimeException("first fail"))
                .thenReturn(Map.of("key", "value"));

        var node = new FaultTolerantNode<>(delegate, 3, 1);

        Map<String, Object> result = node.apply(new InterviewState(Map.of()));

        assertEquals("value", result.get("key"));
        verify(delegate, times(2)).apply(any());
    }

    @Test
    @DisplayName("重试耗尽：写入 LAST_ERROR 和 RETRY_COUNT，不抛异常")
    void retry_exhausted_writesLastError() throws Exception {
        when(delegate.apply(any())).thenThrow(new RuntimeException("AI error"));

        var node = new FaultTolerantNode<>(delegate, 2, 1);

        Map<String, Object> result = node.apply(new InterviewState(Map.of()));

        assertEquals("AI error", result.get(InterviewState.LAST_ERROR));
        assertEquals(2, result.get(InterviewState.RETRY_COUNT));
    }

    @Test
    @DisplayName("指数退避：delegate 被调用 maxRetries 次")
    void exponential_backoff() throws Exception {
        when(delegate.apply(any())).thenThrow(new RuntimeException("fail"));

        var node = new FaultTolerantNode<>(delegate, 3, 1);

        node.apply(new InterviewState(Map.of()));

        verify(delegate, times(3)).apply(any());
    }
}
