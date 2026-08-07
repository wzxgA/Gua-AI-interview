package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.QaPair;
import com.aims.core.interview.SummaryContext;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link SummaryNode} 测试。 */
@ExtendWith(MockitoExtension.class)
class SummaryNodeTest {

    @Mock private SummaryAgent summaryAgent;

    private SummaryNode node;

    @BeforeEach
    void setUp() {
        node = new SummaryNode(summaryAgent);
    }

    private static List<QaPair> qaPairs(int count, int startSeq) {
        return IntStream.range(0, count)
                .mapToObj(i -> new QaPair(startSeq + i, "Q" + (startSeq + i), "A" + (startSeq + i)))
                .toList();
    }

    @Test
    @DisplayName("不足阈值（5 条）跳过摘要")
    void below_threshold_skipped() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_SEQ,
                                0,
                                InterviewState.QA_HISTORY,
                                qaPairs(4, 1)));

        Map<String, Object> result = node.apply(state);

        verify(summaryAgent, never()).summarize(any());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("达到阈值生成摘要：返回 RUNNING_SUMMARY 和 LAST_SUMMARIZED_SEQ")
    void above_threshold_summarize() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_SEQ,
                                0,
                                InterviewState.QA_HISTORY,
                                qaPairs(5, 1)));
        when(summaryAgent.summarize(any())).thenReturn("摘要文本");

        Map<String, Object> result = node.apply(state);

        assertEquals("摘要文本", result.get(InterviewState.RUNNING_SUMMARY));
        assertEquals(5, result.get(InterviewState.LAST_SUMMARIZED_SEQ));
    }

    @Test
    @DisplayName("LAST_SUMMARIZED_SEQ 更新为最后一条 QaPair 的 seq")
    void lastSummarizedSeq_updated() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_SEQ,
                                3,
                                InterviewState.QA_HISTORY,
                                qaPairs(5, 4)));
        when(summaryAgent.summarize(any())).thenReturn("摘要");

        Map<String, Object> result = node.apply(state);

        assertEquals(8, result.get(InterviewState.LAST_SUMMARIZED_SEQ));

        ArgumentCaptor<SummaryContext> captor = ArgumentCaptor.forClass(SummaryContext.class);
        verify(summaryAgent).summarize(captor.capture());
        assertEquals(3, captor.getValue().lastSummarizedSeq());
    }
}
