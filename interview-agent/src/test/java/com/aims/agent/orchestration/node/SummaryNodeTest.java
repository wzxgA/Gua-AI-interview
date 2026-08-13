package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpType;
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
    @DisplayName("达到阈值生成摘要：返回 RUNNING_SUMMARY 和 LAST_SUMMARIZED_INDEX/SEQ")
    void above_threshold_summarize() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_INDEX,
                                -1,
                                InterviewState.QA_HISTORY,
                                qaPairs(5, 1)));
        when(summaryAgent.summarize(any())).thenReturn("摘要文本");

        Map<String, Object> result = node.apply(state);

        assertEquals("摘要文本", result.get(InterviewState.RUNNING_SUMMARY));
        assertEquals(4, result.get(InterviewState.LAST_SUMMARIZED_INDEX));
        assertEquals(5, result.get(InterviewState.LAST_SUMMARIZED_SEQ));
    }

    @Test
    @DisplayName("LAST_SUMMARIZED_INDEX 更新为最后一条 QaPair 的下标（兼容写 SEQ）")
    void lastSummarizedIndex_advances() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_INDEX,
                                4,
                                InterviewState.QA_HISTORY,
                                qaPairs(10, 1)));
        when(summaryAgent.summarize(any())).thenReturn("摘要");

        Map<String, Object> result = node.apply(state);

        // index>4 的 5 条（下标 5..9）触发摘要
        assertEquals(9, result.get(InterviewState.LAST_SUMMARIZED_INDEX));
        assertEquals(10, result.get(InterviewState.LAST_SUMMARIZED_SEQ));

        ArgumentCaptor<SummaryContext> captor = ArgumentCaptor.forClass(SummaryContext.class);
        verify(summaryAgent).summarize(captor.capture());
        // 提示性字段取 state 旧字段（未设置=0），不影响摘要正确性
        assertEquals(0, captor.getValue().lastSummarizedSeq());
    }

    @Test
    @DisplayName("追问混排：q1,q1.1,q2,q2.1,q3 凑满 5 条触发，下标推进到 4")
    void followUpMixed_triggerAndAdvance() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_INDEX,
                                -1,
                                InterviewState.QA_HISTORY,
                                List.of(
                                        new QaPair(1, "q1", "a1"),
                                        new QaPair(1, "q1.1", "a1.1", 1, FollowUpType.DEEPEN),
                                        new QaPair(2, "q2", "a2"),
                                        new QaPair(2, "q2.1", "a2.1", 1, FollowUpType.DEEPEN),
                                        new QaPair(3, "q3", "a3"))));
        when(summaryAgent.summarize(any())).thenReturn("摘要");

        Map<String, Object> result = node.apply(state);

        assertEquals(4, result.get(InterviewState.LAST_SUMMARIZED_INDEX));
        assertEquals(3, result.get(InterviewState.LAST_SUMMARIZED_SEQ));
    }

    @Test
    @DisplayName("边界后追问不遗漏：下标推进后同 seq 追问仍被纳入")
    void followUpAfterBoundary_notDropped() throws Exception {
        // 已摘要前 5 条（index 0..4 = q1,q1.1,q2,q2.1,q3）；后续 q3.1(seq=3) 追问不应被 seq 过滤漏掉
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID,
                                1L,
                                InterviewState.LAST_SUMMARIZED_INDEX,
                                4,
                                InterviewState.QA_HISTORY,
                                List.of(
                                        new QaPair(1, "q1", "a1"),
                                        new QaPair(1, "q1.1", "a1.1", 1, FollowUpType.DEEPEN),
                                        new QaPair(2, "q2", "a2"),
                                        new QaPair(2, "q2.1", "a2.1", 1, FollowUpType.DEEPEN),
                                        new QaPair(3, "q3", "a3"),
                                        new QaPair(3, "q3.1", "a3.1", 1, FollowUpType.DEEPEN),
                                        new QaPair(4, "q4", "a4"),
                                        new QaPair(4, "q4.1", "a4.1", 1, FollowUpType.DEEPEN),
                                        new QaPair(5, "q5", "a5"),
                                        new QaPair(5, "q5.1", "a5.1", 1, FollowUpType.DEEPEN))));
        when(summaryAgent.summarize(any())).thenReturn("摘要");

        Map<String, Object> result = node.apply(state);

        // index>4 的 5 条（q3.1, q4, q4.1, q5, q5.1）触发，newIndex=9
        assertEquals(9, result.get(InterviewState.LAST_SUMMARIZED_INDEX));

        ArgumentCaptor<SummaryContext> captor = ArgumentCaptor.forClass(SummaryContext.class);
        verify(summaryAgent).summarize(captor.capture());
        List<QaPair> toSummarize = captor.getValue().roundsToSummarize();
        // 边界后同 seq(3) 的追问 q3.1 必须被纳入，不再被漏掉
        assertTrue(
                toSummarize.stream()
                        .anyMatch(
                                qa ->
                                        qa.seq() == 3
                                                && qa.followUpIndex() != null
                                                && qa.followUpIndex() == 1),
                "边界后同 seq 追问 q3.1 应被纳入待摘要");
    }
}
