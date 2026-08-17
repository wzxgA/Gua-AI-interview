package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.DefaultReportAgent;
import com.aims.core.report.Recommendation;
import com.aims.core.report.ReportResult;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.mapper.ReportMapper;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.persistence.service.ResumeSummaryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ReportServiceImpl} 测试：FE.15 P11 幂等守卫。 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportMapper reportMapper;
    @Mock private EvaluationService evaluationService;
    @Mock private InterviewSessionService sessionService;
    @Mock private InterviewRoundService roundService;
    @Mock private PositionService positionService;
    @Mock private ResumeService resumeService;
    @Mock private DefaultReportAgent reportAgent;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new ReportServiceImpl(
                        reportMapper,
                        evaluationService,
                        sessionService,
                        roundService,
                        positionService,
                        resumeService,
                        new ResumeSummaryBuilder(new ObjectMapper()),
                        reportAgent,
                        new ObjectMapper());
    }

    private InterviewSessionEntity session(String evaluationStatus, String status) {
        InterviewSessionEntity e = new InterviewSessionEntity();
        e.setId(1L);
        e.setPositionId(10L);
        e.setCandidateId(20L);
        e.setEvaluationStatus(evaluationStatus);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("重复投递：evaluationStatus=DONE -> 跳过报告生成")
    void generateReport_done_skipsAi() {
        when(sessionService.getById(1L)).thenReturn(session("DONE", "REPORTING"));

        assertDoesNotThrow(() -> service.generateReport(1L));

        verify(reportAgent, never()).generate(any(), any(), anyDouble());
        verify(reportMapper, never()).upsert(any());
        verify(sessionService, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("重复投递：status=COMPLETED -> 跳过报告生成")
    void generateReport_completed_skipsAi() {
        when(sessionService.getById(1L)).thenReturn(session("REPORTING", "COMPLETED"));

        assertDoesNotThrow(() -> service.generateReport(1L));

        verify(reportAgent, never()).generate(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("首次消费：EVALUATING -> 正常生成报告（空评分走通到 COMPLETED/DONE）")
    void generateReport_firstTime_runs() {
        when(sessionService.getById(1L)).thenReturn(session("EVALUATING", "REPORTING"));
        when(evaluationService.listBySession(1L)).thenReturn(List.of());
        when(evaluationService.aggregateDimensions(1L))
                .thenReturn(new com.aims.core.evaluation.DimensionAggregate());
        PositionEntity position = new PositionEntity();
        position.setTitle("Java 开发");
        position.setJdText("JD");
        when(positionService.getById(10L)).thenReturn(position);
        ResumeEntity resume = new ResumeEntity();
        resume.setCandidateName("候选人");
        resume.setParsedJson("简历摘要");
        when(resumeService.getById(20L)).thenReturn(resume);
        when(roundService.listBySession(1L)).thenReturn(List.of());
        when(reportAgent.generate(any(), any(), anyDouble()))
                .thenReturn(
                        new ReportResult("summary", Map.of(), Recommendation.STRONGLY_RECOMMEND));

        assertDoesNotThrow(() -> service.generateReport(1L));

        verify(reportAgent).generate(any(), any(), anyDouble());
        verify(sessionService).updateStatus(1L, com.aims.core.session.SessionStatus.COMPLETED);
        verify(sessionService).updateEvaluationStatus(1L, "DONE");
    }
}
