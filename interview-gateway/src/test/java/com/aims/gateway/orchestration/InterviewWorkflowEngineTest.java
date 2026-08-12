package com.aims.gateway.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
}
