package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.DefaultEvaluatorAgent;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.mapper.EvaluationMapper;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ResumeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link EvaluationServiceImpl} 测试：FE.15 P11 幂等守卫。 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock private EvaluationMapper evaluationMapper;
    @Mock private InterviewSessionService sessionService;
    @Mock private InterviewRoundService roundService;
    @Mock private PositionService positionService;
    @Mock private ResumeService resumeService;
    @Mock private DefaultEvaluatorAgent evaluatorAgent;

    private EvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new EvaluationServiceImpl(
                        evaluationMapper,
                        sessionService,
                        roundService,
                        positionService,
                        resumeService,
                        evaluatorAgent);
    }

    private InterviewSessionEntity session(String evaluationStatus) {
        InterviewSessionEntity e = new InterviewSessionEntity();
        e.setId(1L);
        e.setPositionId(10L);
        e.setCandidateId(20L);
        e.setEvaluationStatus(evaluationStatus);
        return e;
    }

    @Test
    @DisplayName("重复投递：evaluationStatus=REPORTING -> 跳过 AI 评估")
    void evaluateSession_alreadyDone_skipsAi() {
        when(sessionService.getById(1L)).thenReturn(session("REPORTING"));

        assertDoesNotThrow(() -> service.evaluateSession(1L));

        verify(evaluationMapper, never()).deleteBySession(anyLong());
        verify(evaluatorAgent, never()).evaluate(any());
        verify(sessionService, never()).updateEvaluationStatus(anyLong(), anyString());
        verify(sessionService, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("重复投递：evaluationStatus=DONE -> 跳过 AI 评估")
    void evaluateSession_done_skipsAi() {
        when(sessionService.getById(1L)).thenReturn(session("DONE"));

        assertDoesNotThrow(() -> service.evaluateSession(1L));

        verify(evaluatorAgent, never()).evaluate(any());
    }

    @Test
    @DisplayName("首次消费：EVALUATING -> 正常执行评估（空轮次走通到 REPORTING）")
    void evaluateSession_firstTime_runs() {
        when(sessionService.getById(1L)).thenReturn(session("EVALUATING"));
        when(roundService.listBySession(1L)).thenReturn(List.of());
        when(positionService.getById(10L)).thenReturn(new PositionEntity());
        when(resumeService.getById(20L)).thenReturn(new ResumeEntity());

        assertDoesNotThrow(() -> service.evaluateSession(1L));

        verify(sessionService).updateEvaluationStatus(1L, "EVALUATING");
        verify(sessionService).updateStatus(1L, com.aims.core.session.SessionStatus.REPORTING);
        verify(sessionService).updateEvaluationStatus(1L, "REPORTING");
        verify(evaluatorAgent, never()).evaluate(any());
    }
}
