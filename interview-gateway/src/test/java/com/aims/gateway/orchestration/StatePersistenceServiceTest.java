package com.aims.gateway.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.interview.QaPair;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link StatePersistenceService} 单元测试。
 *
 * <p>验证 DB ↔ State 双向同步：buildInitialState 字段填充、syncFromState 幂等同步、rebuildFromDb 重建。
 *
 * @since 1.1.0 Phase 5
 */
@ExtendWith(MockitoExtension.class)
class StatePersistenceServiceTest {

    @Mock private InterviewSessionService sessionService;
    @Mock private InterviewRoundService roundService;
    @Mock private ResumeService resumeService;
    @Mock private PositionService positionService;

    private StatePersistenceService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service =
                new StatePersistenceService(
                        sessionService, roundService, resumeService, positionService, objectMapper);
    }

    private InterviewSessionEntity mockSessionEntity(Long id) {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setId(id);
        entity.setCandidateId(10L);
        entity.setPositionId(20L);
        entity.setPersona("FRIENDLY");
        return entity;
    }

    private ResumeEntity mockResume() {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(10L);
        resume.setCandidateName("张三");
        resume.setRawText("Java 工程师，5 年经验");
        return resume;
    }

    private PositionEntity mockPosition() {
        PositionEntity position = new PositionEntity();
        position.setId(20L);
        position.setTitle("Java 后端");
        position.setJdText("JD 内容");
        return position;
    }

    private String mockPlanJson() {
        List<PlannedQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            questions.add(
                    new PlannedQuestion(
                            "q" + i, "topic" + i, "BALANCED", List.of("hint" + i), "focus" + i));
        }
        InterviewPlan plan =
                new InterviewPlan(
                        "张三",
                        "Java 后端",
                        List.of(new PlanSection("技术", 8, "技术评估")),
                        questions,
                        45,
                        "1.0");
        try {
            return new ObjectMapper().writeValueAsString(plan);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("buildInitialState 所有字段填充正确")
    void buildInitialState_allFieldsPopulated() {
        InterviewSessionEntity entity = mockSessionEntity(1L);
        entity.setPlanJson(mockPlanJson());
        when(sessionService.getById(1L)).thenReturn(entity);
        when(resumeService.getById(10L)).thenReturn(mockResume());
        when(positionService.getById(20L)).thenReturn(mockPosition());

        InterviewState state = service.buildInitialState(1L);

        assertEquals(1L, state.sessionId());
        assertEquals("张三", state.candidateName());
        assertEquals("Java 后端", state.positionTitle());
        assertEquals("JD 内容", state.jdText());
        assertEquals(InterviewerPersona.FRIENDLY, state.persona());
        assertEquals(8, state.totalRounds());
        assertEquals(0, state.currentSeq());
        assertTrue(state.qaHistory().isEmpty());
        assertEquals(SessionStatus.IN_PROGRESS, state.sessionStatus());
        assertNotNull(state.interviewPlan());
    }

    @Test
    @DisplayName("buildInitialState session 不存在抛 IllegalArgumentException")
    void buildInitialState_sessionNotFound_throws() {
        when(sessionService.getById(999L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.buildInitialState(999L));
    }

    @Test
    @DisplayName("syncFromState 新问题创建轮次")
    void syncFromState_newQuestion_createsRound() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(List.of(new QaPair(1, "问题1", ""))),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        when(roundService.listBySession(100L)).thenReturn(List.of());

        service.syncFromState(100L, state);

        verify(roundService).createRound(100L, 1, "问题1");
    }

    @Test
    @DisplayName("syncFromState 已存在的轮次不重复创建")
    void syncFromState_existingRound_skipsCreate() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(List.of(new QaPair(1, "问题1", "答案1"))),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        InterviewRoundEntity existing = new InterviewRoundEntity();
        existing.setId(50L);
        existing.setSeq(1);
        existing.setAnswer("答案1");
        when(roundService.listBySession(100L)).thenReturn(List.of(existing));

        service.syncFromState(100L, state);

        verify(roundService, never()).createRound(anyLong(), anyInt(), anyString());
        verify(roundService, never()).updateAnswer(anyLong(), anyString());
    }

    @Test
    @DisplayName("syncFromState 新回答更新已存在的轮次")
    void syncFromState_newAnswer_updatesRound() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(List.of(new QaPair(1, "问题1", "新答案"))),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        InterviewRoundEntity existing = new InterviewRoundEntity();
        existing.setId(50L);
        existing.setSeq(1);
        existing.setAnswer(null);
        when(roundService.listBySession(100L)).thenReturn(List.of(existing));

        service.syncFromState(100L, state);

        verify(roundService).updateAnswer(50L, "新答案");
    }

    @Test
    @DisplayName("syncFromState 空 qaHistory 不操作")
    void syncFromState_emptyQaHistory_noOp() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        when(roundService.listBySession(100L)).thenReturn(List.of());

        service.syncFromState(100L, state);

        verify(roundService, never()).createRound(anyLong(), anyInt(), anyString());
        verify(sessionService, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("syncFromState COMPLETED 状态更新 DB")
    void syncFromState_completedStatus_updatesDb() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.COMPLETED));
        when(roundService.listBySession(100L)).thenReturn(List.of());

        service.syncFromState(100L, state);

        verify(sessionService).updateStatus(100L, SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("rebuildFromDb 从 DB 重建完整 State")
    void rebuildFromDb_restoresAllFields() {
        InterviewSessionEntity entity = mockSessionEntity(1L);
        entity.setPlanJson(mockPlanJson());
        when(sessionService.getById(1L)).thenReturn(entity);
        when(resumeService.getById(10L)).thenReturn(mockResume());
        when(positionService.getById(20L)).thenReturn(mockPosition());

        InterviewRoundEntity r1 = new InterviewRoundEntity();
        r1.setId(100L);
        r1.setSeq(1);
        r1.setQuestion("问题1");
        r1.setAnswer("答案1");
        InterviewRoundEntity r2 = new InterviewRoundEntity();
        r2.setId(101L);
        r2.setSeq(2);
        r2.setQuestion("问题2");
        r2.setAnswer("答案2");
        when(roundService.listBySession(1L)).thenReturn(List.of(r1, r2));

        InterviewState state = service.rebuildFromDb(1L);

        assertEquals(1L, state.sessionId());
        assertEquals("张三", state.candidateName());
        assertEquals(8, state.totalRounds());
        assertEquals(2, state.qaHistory().size());
        assertEquals(2, state.currentSeq());
        assertEquals("问题1", state.qaHistory().get(0).question());
        assertEquals("答案1", state.qaHistory().get(0).answer());
    }
}
