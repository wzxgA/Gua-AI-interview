package com.aims.gateway.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.orchestration.checkpoint.RedisCheckpointSaver;
import com.aims.agent.orchestration.graph.InterviewGraphFactory;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.session.SessionStatus;
import com.aims.gateway.ws.WebSocketStreamEmitter;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.InterviewSessionStore;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link InterviewWorkflowEngine} 单元测试。
 *
 * <p>验证非 graph 执行的方法（pause/cancel/isEnabled）和错误路径（无 checkpoint 时抛异常）。 graph 实际执行由 {@code
 * InterruptBeforeApiVerificationTest} 验证 interruptBefore API，完整流程由 E2E 测试覆盖。
 *
 * @since 1.1.0 Phase 5
 */
@ExtendWith(MockitoExtension.class)
class InterviewWorkflowEngineTest {

    @Mock private InterviewGraphFactory graphFactory;
    @Mock private RedisCheckpointSaver checkpointSaver;
    @Mock private StatePersistenceService statePersistenceService;
    @Mock private InterviewSessionService sessionService;
    @Mock private WebSocketStreamEmitter streamEmitter;

    private InterviewWorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine =
                new InterviewWorkflowEngine(
                        graphFactory,
                        checkpointSaver,
                        statePersistenceService,
                        sessionService,
                        streamEmitter);
        // engineEnabled 默认 false（与 application.yml 默认一致）
        ReflectionTestUtils.setField(engine, "engineEnabled", false);
    }

    @Test
    @DisplayName("isEnabled 默认返回 false")
    void isEnabled_returnsConfiguredValue() {
        assertFalse(engine.isEnabled());
    }

    @Test
    @DisplayName("pauseInterview 更新 DB 状态为 PAUSED")
    void pauseInterview_updatesDbStatusPaused() {
        engine.pauseInterview(100L);
        verify(sessionService).updateStatus(100L, SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("cancelInterview 释放 checkpoint + 更新状态为 CANCELLED")
    void cancelInterview_releasesCheckpointAndUpdatesStatus() throws Exception {
        engine.cancelInterview(200L);
        verify(checkpointSaver).release(any());
        verify(sessionService).updateStatus(200L, SessionStatus.CANCELLED);
        verify(sessionService).markEnded(200L);
    }

    @Test
    @DisplayName("cancelInterview 即使 checkpoint 释放失败仍更新 DB 状态")
    void cancelInterview_checkpointReleaseFails_stillUpdatesDb() throws Exception {
        when(checkpointSaver.release(any())).thenThrow(new RuntimeException("redis down"));
        engine.cancelInterview(300L);
        verify(sessionService).updateStatus(300L, SessionStatus.CANCELLED);
        verify(sessionService).markEnded(300L);
    }

    @Test
    @DisplayName("submitAnswer 无 checkpoint 时抛 IllegalStateException")
    void submitAnswer_noCheckpoint_throwsIllegalState() throws Exception {
        when(checkpointSaver.get(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> engine.submitAnswer(400L, "answer text"));
        verify(statePersistenceService, never()).syncFromState(any(), any());
    }

    @Test
    @DisplayName("finishInterview 无 checkpoint 时抛 IllegalStateException")
    void finishInterview_noCheckpoint_throwsIllegalState() throws Exception {
        when(checkpointSaver.get(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> engine.finishInterview(500L));
        verify(sessionService, never()).markEnded(any());
    }

    @Test
    @DisplayName("finishInterview 结束后触发 Kafka 评估（转 EVALUATING + forceUnlock + 发消息，FE.04）")
    void finishInterview_triggersEvaluationViaKafka() throws Exception {
        EvaluationMessageProducer producer = mock(EvaluationMessageProducer.class);
        InterviewSessionStore sessionStore = mock(InterviewSessionStore.class);
        ReflectionTestUtils.setField(engine, "evaluationMessageProducer", producer);
        ReflectionTestUtils.setField(engine, "sessionStore", sessionStore);

        // checkpoint 存在 + noInterrupt graph mock
        Checkpoint cp =
                Checkpoint.builder()
                        .id("c1")
                        .nodeId("ask")
                        .nextNodeId("__END__")
                        .state(Map.of(InterviewState.SESSION_ID, 600L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));
        // tryTransitionTo 返回 true，使 triggerEvaluationViaEngine 走完整触发路径
        when(sessionService.tryTransitionTo(
                        600L,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED))
                .thenReturn(true);
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> noInterrupt = mock(CompiledGraph.class);
        when(graphFactory.compile(any())).thenReturn(noInterrupt);

        engine.finishInterview(600L);

        // 触发评估：状态转移 + 释放连接锁 + 置 PENDING + 发 Kafka；不再直接置 COMPLETED
        verify(sessionService)
                .tryTransitionTo(
                        600L,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED);
        verify(sessionStore).forceUnlock(600L);
        verify(sessionService).updateEvaluationStatus(600L, "PENDING");
        verify(producer).sendEvaluationRequest(600L);
        verify(sessionService, never()).updateStatus(600L, SessionStatus.COMPLETED);
        verify(sessionService, never()).markEnded(600L);
    }

    @Test
    @DisplayName("submitAnswer 后图未到 END（nextNodeId=answer 暂停态）：不触发评估（回归：Q 已生成不算结束）")
    void submitAnswer_notFinished_doesNotTriggerEvaluation() throws Exception {
        EvaluationMessageProducer producer = mock(EvaluationMessageProducer.class);
        ReflectionTestUtils.setField(engine, "evaluationMessageProducer", producer);

        // 暂停态 checkpoint（interruptBefore(ANSWER) 时 nextNodeId=answer）
        Checkpoint paused =
                Checkpoint.builder()
                        .id("c2")
                        .nodeId("ask")
                        .nextNodeId("answer")
                        .state(Map.of(InterviewState.SESSION_ID, 700L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(paused));
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> g = mock(CompiledGraph.class);
        ReflectionTestUtils.setField(engine, "compiledGraph", g);

        engine.submitAnswer(700L, "answer text");

        // 图未结束 → 不触发评估
        verify(producer, never()).sendEvaluationRequest(anyLong());
        verify(sessionService, never()).updateEvaluationStatus(700L, "PENDING");
    }

    @Test
    @DisplayName("resumeInterview checkpoint 暂停态：返回 RESUMED，不触发评估（FE.06 P2）")
    void resumeInterview_paused_returnsResumed() throws Exception {
        Checkpoint paused =
                Checkpoint.builder()
                        .id("c3")
                        .nodeId("ask")
                        .nextNodeId("answer")
                        .state(Map.of(InterviewState.SESSION_ID, 800L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(paused));

        InterviewWorkflowEngine.ResumeResult result = engine.resumeInterview(800L);

        assertEquals(InterviewWorkflowEngine.ResumeResult.RESUMED, result);
        // 暂停态不触发评估、不同步 DB（补发由 Handler 负责）
        verify(sessionService, never()).updateEvaluationStatus(anyLong(), any());
        verify(statePersistenceService, never()).syncFromState(any(), any());
    }

    @Test
    @DisplayName("resumeInterview checkpoint 已 END：触发评估（FE.06 P2）")
    void resumeInterview_finished_triggersEvaluation() throws Exception {
        EvaluationMessageProducer producer = mock(EvaluationMessageProducer.class);
        InterviewSessionStore sessionStore = mock(InterviewSessionStore.class);
        ReflectionTestUtils.setField(engine, "evaluationMessageProducer", producer);
        ReflectionTestUtils.setField(engine, "sessionStore", sessionStore);

        Checkpoint cp =
                Checkpoint.builder()
                        .id("c4")
                        .nodeId("endCheck")
                        .nextNodeId("__END__")
                        .state(Map.of(InterviewState.SESSION_ID, 810L))
                        .build();
        // loadStateFromCheckpoint + isInterviewFinished 各 get 一次（返回同一 cp）
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));
        when(sessionService.tryTransitionTo(
                        810L,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED))
                .thenReturn(true);

        InterviewWorkflowEngine.ResumeResult result = engine.resumeInterview(810L);

        assertEquals(InterviewWorkflowEngine.ResumeResult.FINISHED, result);
        verify(sessionService)
                .tryTransitionTo(
                        810L,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED);
        verify(sessionStore).forceUnlock(810L);
        verify(sessionService).updateEvaluationStatus(810L, "PENDING");
        verify(producer).sendEvaluationRequest(810L);
    }

    @Test
    @DisplayName("resumeInterview checkpoint 不存在：rebuildFromDb 重建 + invoke 重跑（FE.06 P2）")
    void resumeInterview_noCheckpoint_rebuildsFromDb() throws Exception {
        // 第一次 get（loadStateFromCheckpoint）-> empty；invoke 后第二次 get -> 暂停态 cp
        Checkpoint rebuilt =
                Checkpoint.builder()
                        .id("c5")
                        .nodeId("ask")
                        .nextNodeId("answer")
                        .state(Map.of(InterviewState.SESSION_ID, 820L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.empty(), Optional.of(rebuilt));
        InterviewState rebuiltState = new InterviewState(Map.of(InterviewState.SESSION_ID, 820L));
        when(statePersistenceService.rebuildFromDb(820L)).thenReturn(rebuiltState);
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> g = mock(CompiledGraph.class);
        ReflectionTestUtils.setField(engine, "compiledGraph", g);

        InterviewWorkflowEngine.ResumeResult result = engine.resumeInterview(820L);

        assertEquals(InterviewWorkflowEngine.ResumeResult.REBUILT_FROM_DB, result);
        verify(statePersistenceService).rebuildFromDb(820L);
        verify(g).invoke(ArgumentMatchers.<Map<String, Object>>any(), any());
        verify(statePersistenceService).syncFromState(eq(820L), any());
    }

    // ==================== FE.07 P3：面试结束后释放 checkpoint ====================

    @Test
    @DisplayName("finishInterview 触发评估后释放 checkpoint（P3）")
    void triggerEvaluation_releasesCheckpoint() throws Exception {
        EvaluationMessageProducer producer = mock(EvaluationMessageProducer.class);
        ReflectionTestUtils.setField(engine, "evaluationMessageProducer", producer);

        Checkpoint cp =
                Checkpoint.builder()
                        .id("p3-1")
                        .nodeId("endCheck")
                        .nextNodeId("__END__")
                        .state(Map.of(InterviewState.SESSION_ID, 900L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));
        when(sessionService.tryTransitionTo(
                        900L,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED))
                .thenReturn(true);
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> noInterrupt = mock(CompiledGraph.class);
        when(graphFactory.compile(any())).thenReturn(noInterrupt);

        engine.finishInterview(900L);

        // 首次触发评估 -> 释放 checkpoint
        verify(checkpointSaver).release(any());
    }

    @Test
    @DisplayName("评估重复触发（状态已转移）不重复释放 checkpoint（P3 幂等）")
    void triggerEvaluation_duplicate_doesNotReleaseAgain() throws Exception {
        when(sessionService.tryTransitionTo(
                        anyLong(),
                        eq(SessionStatus.EVALUATING),
                        eq(SessionStatus.IN_PROGRESS),
                        eq(SessionStatus.PAUSED)))
                .thenReturn(false);

        // resumeInterview FINISHED 分支：checkpoint END -> isInterviewFinished true ->
        // triggerEvaluationViaEngine
        Checkpoint cp =
                Checkpoint.builder()
                        .id("p3-2")
                        .nodeId("endCheck")
                        .nextNodeId("__END__")
                        .state(Map.of(InterviewState.SESSION_ID, 910L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));

        InterviewWorkflowEngine.ResumeResult result = engine.resumeInterview(910L);

        assertEquals(InterviewWorkflowEngine.ResumeResult.FINISHED, result);
        // 状态未成功转移（已 EVALUATING）-> 不释放
        verify(checkpointSaver, never()).release(any());
    }

    @Test
    @DisplayName("释放 checkpoint 失败时评估流程不受影响（P3 容错）")
    void triggerEvaluation_releaseFails_stillEvaluates() throws Exception {
        EvaluationMessageProducer producer = mock(EvaluationMessageProducer.class);
        ReflectionTestUtils.setField(engine, "evaluationMessageProducer", producer);

        Checkpoint cp =
                Checkpoint.builder()
                        .id("p3-3")
                        .nodeId("endCheck")
                        .nextNodeId("__END__")
                        .state(Map.of(InterviewState.SESSION_ID, 920L))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));
        when(checkpointSaver.release(any())).thenThrow(new RuntimeException("redis down"));
        when(sessionService.tryTransitionTo(
                        920L,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED))
                .thenReturn(true);
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> noInterrupt = mock(CompiledGraph.class);
        when(graphFactory.compile(any())).thenReturn(noInterrupt);

        engine.finishInterview(920L);

        // 释放失败仅告警（releaseCheckpoint try-catch），评估流程不受影响
        verify(sessionService).updateEvaluationStatus(920L, "PENDING");
        verify(producer).sendEvaluationRequest(920L);
    }

    // ==================== FE.08 P4：startInterview 幂等 ====================

    @Test
    @DisplayName("startInterview 无 checkpoint：invoke 启动 + 返回 true")
    void startInterview_noCheckpoint_invokesAndReturnsTrue() throws Exception {
        // 第一次 get（start 开头幂等检查）-> empty；invoke 后第二次 get -> 暂停态 cp
        Checkpoint paused =
                Checkpoint.builder()
                        .id("p4-0")
                        .nodeId("ask")
                        .nextNodeId("answer")
                        .state(
                                Map.of(
                                        InterviewState.SESSION_ID,
                                        1000L,
                                        InterviewState.CURRENT_SEQ,
                                        1))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.empty(), Optional.of(paused));
        InterviewState initial = new InterviewState(Map.of(InterviewState.SESSION_ID, 1000L));
        when(statePersistenceService.buildInitialState(1000L)).thenReturn(initial);
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> g = mock(CompiledGraph.class);
        ReflectionTestUtils.setField(engine, "compiledGraph", g);

        boolean result = engine.startInterview(1000L);

        assertTrue(result);
        verify(g).invoke(ArgumentMatchers.<Map<String, Object>>any(), any());
        verify(statePersistenceService).syncFromState(eq(1000L), any());
    }

    @Test
    @DisplayName("startInterview 已有 checkpoint：不重跑 + 补偿落库 + 返回 false（P4 幂等）")
    void startInterview_existingCheckpoint_skipsInvokeAndReturnsFalse() throws Exception {
        Checkpoint cp =
                Checkpoint.builder()
                        .id("p4-1")
                        .nodeId("ask")
                        .nextNodeId("answer")
                        .state(
                                Map.of(
                                        InterviewState.SESSION_ID,
                                        1010L,
                                        InterviewState.CURRENT_SEQ,
                                        1))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));
        @SuppressWarnings("unchecked")
        CompiledGraph<InterviewState> g = mock(CompiledGraph.class);
        ReflectionTestUtils.setField(engine, "compiledGraph", g);

        boolean result = engine.startInterview(1010L);

        assertFalse(result);
        // 幂等复用：不重跑 invoke，只补偿落库
        verify(g, never()).invoke(ArgumentMatchers.<Map<String, Object>>any(), any());
        verify(statePersistenceService).syncFromState(eq(1010L), any());
    }

    @Test
    @DisplayName("getPendingState 返回 checkpoint 暂停态 state")
    void getPendingState_returnsCheckpointState() throws Exception {
        Checkpoint cp =
                Checkpoint.builder()
                        .id("p4-2")
                        .nodeId("ask")
                        .nextNodeId("answer")
                        .state(
                                Map.of(
                                        InterviewState.SESSION_ID,
                                        1020L,
                                        InterviewState.CURRENT_SEQ,
                                        2))
                        .build();
        when(checkpointSaver.get(any())).thenReturn(Optional.of(cp));

        Optional<InterviewState> state = engine.getPendingState(1020L);

        assertTrue(state.isPresent());
        assertEquals(2, state.get().currentSeq());
    }
}
