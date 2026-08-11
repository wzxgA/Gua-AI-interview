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
    @DisplayName("syncFromState 追问 Q&A 创建追问轮次（seq=null + parentSeq + followUpIndex + type）")
    void syncFromState_followUpQa_createsFollowUpRound() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(
                                        List.of(
                                                new QaPair(1, "问题1", "答案1"),
                                                new QaPair(
                                                        1,
                                                        "追问1",
                                                        "追问答案1",
                                                        1,
                                                        com.aims.core.interview.FollowUpType
                                                                .DEEPEN))),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        when(roundService.listBySession(100L)).thenReturn(List.of());

        service.syncFromState(100L, state);

        verify(roundService).createRound(100L, 1, "问题1");
        verify(roundService).createRound(100L, null, "追问1", "DEEPEN", 1, 1);
    }

    @Test
    @DisplayName("syncFromState 已存在的追问轮次只回填 answer，不重复创建")
    void syncFromState_existingFollowUpRound_updatesAnswerOnly() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(
                                        List.of(
                                                new QaPair(
                                                        1,
                                                        "追问1",
                                                        "追问答案1",
                                                        1,
                                                        com.aims.core.interview.FollowUpType
                                                                .DEEPEN))),
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        InterviewRoundEntity existing = new InterviewRoundEntity();
        existing.setId(60L);
        existing.setSeq(null);
        existing.setParentSeq(1);
        existing.setFollowUpIndex(1);
        existing.setAnswer(null);
        when(roundService.listBySession(100L)).thenReturn(List.of(existing));

        service.syncFromState(100L, state);

        verify(roundService).updateAnswer(60L, "追问答案1");
        verify(roundService, never())
                .createRound(anyLong(), any(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("syncFromState 追问暂停窗口：预创建待答追问轮次")
    void syncFromState_pendingFollowUp_preCreatesRound() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(),
                                InterviewState.CURRENT_QUESTION,
                                "追问问题",
                                InterviewState.CURRENT_ANSWER,
                                "",
                                InterviewState.PENDING_FOLLOW_UP,
                                true,
                                InterviewState.PARENT_SEQ,
                                1,
                                InterviewState.FOLLOW_UP_INDEX,
                                1,
                                InterviewState.FOLLOW_UP_TYPE,
                                com.aims.core.interview.FollowUpType.CLARIFY,
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        when(roundService.listBySession(100L)).thenReturn(List.of());

        service.syncFromState(100L, state);

        verify(roundService).createRound(100L, null, "追问问题", "CLARIFY", 1, 1);
    }

    @Test
    @DisplayName("syncFromState 主问题暂停窗口：预创建待答主问题轮次")
    void syncFromState_pendingMainQuestion_preCreatesRound() {
        InterviewState state =
                new InterviewState(
                        Map.of(
                                InterviewState.QA_HISTORY,
                                new ArrayList<>(),
                                InterviewState.CURRENT_SEQ,
                                3,
                                InterviewState.CURRENT_QUESTION,
                                "主问题3",
                                InterviewState.CURRENT_ANSWER,
                                "",
                                InterviewState.SESSION_STATUS,
                                SessionStatus.IN_PROGRESS));
        when(roundService.listBySession(100L)).thenReturn(List.of());

        service.syncFromState(100L, state);

        verify(roundService).createRound(100L, 3, "主问题3");
    }

    @Test
    @DisplayName("rebuildFromDb 追问轮次重建为带标记的 QaPair")
    void rebuildFromDb_restoresFollowUpQaPairs() {
        InterviewSessionEntity entity = mockSessionEntity(1L);
        entity.setPlanJson(mockPlanJson());
        when(sessionService.getById(1L)).thenReturn(entity);
        when(resumeService.getById(10L)).thenReturn(mockResume());
        when(positionService.getById(20L)).thenReturn(mockPosition());

        InterviewRoundEntity main = new InterviewRoundEntity();
        main.setId(100L);
        main.setSeq(1);
        main.setQuestion("问题1");
        main.setAnswer("答案1");
        InterviewRoundEntity followUp = new InterviewRoundEntity();
        followUp.setId(101L);
        followUp.setSeq(null);
        followUp.setParentSeq(1);
        followUp.setFollowUpIndex(1);
        followUp.setFollowUpType("DEEPEN");
        followUp.setQuestion("追问1");
        followUp.setAnswer("追问答案1");
        when(roundService.listBySession(1L)).thenReturn(List.of(main, followUp));

        InterviewState state = service.rebuildFromDb(1L);

        assertEquals(2, state.qaHistory().size());
        assertEquals(1, state.currentSeq(), "currentSeq 只统计主问题");
        QaPair fu = state.qaHistory().get(1);
        assertTrue(fu.isFollowUp());
        assertEquals(1, fu.seq());
        assertEquals(1, fu.followUpIndex());
        assertEquals(com.aims.core.interview.FollowUpType.DEEPEN, fu.followUpType());
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
