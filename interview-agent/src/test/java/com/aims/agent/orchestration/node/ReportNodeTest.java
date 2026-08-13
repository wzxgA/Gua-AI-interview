package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aims.agent.DefaultReportAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.QaPair;
import com.aims.core.report.ReportContext;
import com.aims.core.report.ReportResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ReportNode} 测试。 */
@ExtendWith(MockitoExtension.class)
class ReportNodeTest {

    @Mock private DefaultReportAgent reportAgent;

    private ReportNode node;

    @BeforeEach
    void setUp() {
        node = new ReportNode(reportAgent);
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
    @DisplayName("正常生成报告：返回 REPORT_RESULT")
    void generate_report_success() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID, 1L,
                                InterviewState.CANDIDATE_NAME, "张三",
                                InterviewState.POSITION_TITLE, "Java",
                                InterviewState.RUNNING_SUMMARY, "运行中摘要",
                                InterviewState.ROUND_EVALUATIONS, fiveEvals()));
        var mockResult = mock(ReportResult.class);
        when(reportAgent.generate(any(), any(), anyDouble())).thenReturn(mockResult);

        Map<String, Object> result = node.apply(state);

        assertEquals(mockResult, result.get(InterviewState.REPORT_RESULT));

        ArgumentCaptor<ReportContext> captor = ArgumentCaptor.forClass(ReportContext.class);
        verify(reportAgent).generate(captor.capture(), any(), anyDouble());
        assertEquals("张三", captor.getValue().candidateName());
        assertEquals("Java", captor.getValue().positionTitle());
    }

    @Test
    @DisplayName("无 runningSummary 时从 QA_HISTORY 构建 fallback summary")
    void fallback_summary_when_no_runningSummary() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, 1L);
        data.put(InterviewState.CANDIDATE_NAME, "张三");
        data.put(InterviewState.POSITION_TITLE, "Java");
        data.put(InterviewState.RUNNING_SUMMARY, null);
        data.put(InterviewState.ROUND_EVALUATIONS, fiveEvals());
        data.put(InterviewState.QA_HISTORY, List.of(new QaPair(1, "Q1", "A1")));
        var state = new InterviewState(data);
        when(reportAgent.generate(any(), any(), anyDouble())).thenReturn(mock(ReportResult.class));

        node.apply(state);

        ArgumentCaptor<ReportContext> captor = ArgumentCaptor.forClass(ReportContext.class);
        verify(reportAgent).generate(captor.capture(), any(), anyDouble());
        String convSummary = captor.getValue().conversationSummary();
        assertTrue(convSummary.contains("Q: Q1"));
        assertTrue(convSummary.contains("A: A1"));
    }

    @Test
    @DisplayName("EvaluationSummary 转换：dimension 名和 score 正确")
    void evaluationSummary_conversion() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.SESSION_ID, 1L,
                                InterviewState.CANDIDATE_NAME, "张三",
                                InterviewState.POSITION_TITLE, "Java",
                                InterviewState.RUNNING_SUMMARY, "摘要",
                                InterviewState.ROUND_EVALUATIONS, fiveEvals()));
        when(reportAgent.generate(any(), any(), anyDouble())).thenReturn(mock(ReportResult.class));

        node.apply(state);

        ArgumentCaptor<ReportContext> captor = ArgumentCaptor.forClass(ReportContext.class);
        verify(reportAgent).generate(captor.capture(), any(), anyDouble());
        List<ReportContext.EvaluationSummary> summaries = captor.getValue().evaluationSummaries();
        assertEquals(5, summaries.size());
        assertEquals("PROFESSIONAL", summaries.get(0).dimension());
        assertEquals(4, summaries.get(0).score());
    }
}
