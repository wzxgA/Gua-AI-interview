package com.aims.gateway.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewerAgent;
import com.aims.ai.memory.ConversationMemory;
import com.aims.core.common.ErrorCode;
import com.aims.core.session.SessionStatus;
import com.aims.gateway.orchestration.InterviewWorkflowEngine;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.InterviewSessionStore;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.QuestionRagService;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.service.TtsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * {@link InterviewWebSocketHandler} 测试：验证 P1——Engine 路径 ANSWER 状态校验。
 *
 * <p>非 IN_PROGRESS 状态（EVALUATING/PAUSED/COMPLETED）发 ANSWER 应被拒绝（SESSION_STATUS_CONFLICT）， 不委托
 * Engine；IN_PROGRESS 才放行委托。
 */
@ExtendWith(MockitoExtension.class)
class InterviewWebSocketHandlerTest {

    @Mock private InterviewSessionService sessionService;
    @Mock private InterviewRoundService roundService;
    @Mock private InterviewSessionStore sessionStore;
    @Mock private InterviewerAgent interviewerAgent;
    @Mock private FollowUpAgent followUpAgent;
    @Mock private PositionService positionService;
    @Mock private ResumeService resumeService;
    @Mock private QuestionRagService questionRagService;
    @Mock private ConversationMemory conversationMemory;
    @Mock private EvaluationMessageProducer evaluationMessageProducer;
    @Mock private ObjectProvider<TtsService> ttsServiceProvider;
    @Mock private RollingSummaryService rollingSummaryService;
    @Mock private InterviewWorkflowEngine engine;
    @Mock private WebSocketSessionManager sessionManager;
    @Mock private WebSocketSession session;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InterviewWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler =
                new InterviewWebSocketHandler(
                        sessionService,
                        roundService,
                        sessionStore,
                        interviewerAgent,
                        followUpAgent,
                        positionService,
                        resumeService,
                        questionRagService,
                        conversationMemory,
                        evaluationMessageProducer,
                        objectMapper,
                        ttsServiceProvider,
                        rollingSummaryService,
                        engine,
                        sessionManager);
        // handleTextMessage 第一步从 attributes 取 sessionId，所有测试都必经
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("sessionId", 32L);
        when(session.getAttributes()).thenReturn(attrs);
        // send() 会检查 isOpen；状态拒绝/未知类型测试发错误消息，delegatesToEngine 不发，用 lenient
        lenient().when(session.isOpen()).thenReturn(true);
    }

    private void setStatus(SessionStatus status) {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setId(32L);
        entity.setStatus(status.name());
        when(sessionService.getById(32L)).thenReturn(entity);
    }

    /** 取最后发送的 WS 消息并解析为 JSON。 */
    private ObjectNode lastSent() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        var all = captor.getAllValues();
        return (ObjectNode) objectMapper.readTree(all.get(all.size() - 1).getPayload());
    }

    @Test
    @DisplayName("IN_PROGRESS 下 ANSWER 放行委托 Engine")
    void answer_inProgress_delegatesToEngine() throws Exception {
        setStatus(SessionStatus.IN_PROGRESS);
        when(engine.isEnabled()).thenReturn(true);

        handler.handleTextMessage(
                session, new TextMessage("{\"type\":\"ANSWER\",\"text\":\"回答\"}"));

        verify(engine).submitAnswer(32L, "回答");
    }

    @Test
    @DisplayName("EVALUATING 下 ANSWER 被拒（SESSION_STATUS_CONFLICT），不委托 Engine")
    void answer_evaluating_rejected() throws Exception {
        setStatus(SessionStatus.EVALUATING);

        handler.handleTextMessage(
                session, new TextMessage("{\"type\":\"ANSWER\",\"text\":\"回答\"}"));

        verify(engine, never()).submitAnswer(any(), any());
        ObjectNode err = lastSent();
        assertEquals(ErrorCode.SESSION_STATUS_CONFLICT.getCode(), err.get("code").asInt());
    }

    @Test
    @DisplayName("PAUSED 下 ANSWER 被拒（SESSION_STATUS_CONFLICT）")
    void answer_paused_rejected() throws Exception {
        setStatus(SessionStatus.PAUSED);

        handler.handleTextMessage(
                session, new TextMessage("{\"type\":\"ANSWER\",\"text\":\"回答\"}"));

        verify(engine, never()).submitAnswer(any(), any());
        ObjectNode err = lastSent();
        assertEquals(ErrorCode.SESSION_STATUS_CONFLICT.getCode(), err.get("code").asInt());
    }

    @Test
    @DisplayName("COMPLETED 下 ANSWER 被拒（SESSION_STATUS_CONFLICT）")
    void answer_completed_rejected() throws Exception {
        setStatus(SessionStatus.COMPLETED);

        handler.handleTextMessage(
                session, new TextMessage("{\"type\":\"ANSWER\",\"text\":\"回答\"}"));

        verify(engine, never()).submitAnswer(any(), any());
        ObjectNode err = lastSent();
        assertEquals(ErrorCode.SESSION_STATUS_CONFLICT.getCode(), err.get("code").asInt());
    }

    @Test
    @DisplayName("未识别消息类型：不被 Engine 处理")
    void unknownType_notDelegated() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"FOO\"}"));
        verify(engine, never()).submitAnswer(any(), any());
    }

    // ==================== FE.06 P2：断线重连 Engine 路径 ====================

    @Test
    @DisplayName("重连：Engine 启用 + rounds 非空 + RESUMED -> 委托 Engine 并补发当前题")
    void reconnect_engineEnabled_resumed_replaysCurrentQuestion() throws Exception {
        stubConnection();
        when(roundService.listBySession(32L)).thenReturn(List.of(unansweredRound(101L, 1)));
        when(engine.isEnabled()).thenReturn(true);
        when(engine.resumeInterview(32L)).thenReturn(InterviewWorkflowEngine.ResumeResult.RESUMED);

        handler.afterConnectionEstablished(session);

        verify(engine).resumeInterview(32L);
        // 旧链路不推进轮次（countAnswered 不被调用）
        verify(roundService, never()).countAnswered(anyLong());
        assertSentType("QUESTION_START");
    }

    @Test
    @DisplayName("重连：Engine 启用 + REBUILT_FROM_DB -> 补发当前题")
    void reconnect_engineEnabled_rebuiltFromDb_replays() throws Exception {
        stubConnection();
        when(roundService.listBySession(32L)).thenReturn(List.of(unansweredRound(101L, 1)));
        when(engine.isEnabled()).thenReturn(true);
        when(engine.resumeInterview(32L))
                .thenReturn(InterviewWorkflowEngine.ResumeResult.REBUILT_FROM_DB);

        handler.afterConnectionEstablished(session);

        verify(engine).resumeInterview(32L);
        assertSentType("QUESTION_START");
    }

    @Test
    @DisplayName("重连：Engine 启用 + checkpoint 已 END -> 推送 EVALUATING 状态")
    void reconnect_engineEnabled_finished_pushesEvaluating() throws Exception {
        stubConnection();
        when(roundService.listBySession(32L)).thenReturn(List.of(unansweredRound(101L, 1)));
        when(engine.isEnabled()).thenReturn(true);
        when(engine.resumeInterview(32L)).thenReturn(InterviewWorkflowEngine.ResumeResult.FINISHED);

        handler.afterConnectionEstablished(session);

        verify(engine).resumeInterview(32L);
        verify(roundService, never()).countAnswered(anyLong());
        // 最后一条消息为 STATUS(EVALUATING)
        ObjectNode last = lastSent();
        assertEquals("STATUS", last.get("type").asText());
        assertEquals("EVALUATING", last.get("status").asText());
    }

    @Test
    @DisplayName("重连：Engine 禁用 + rounds 非空 -> 走旧链路补发（不委托 Engine）")
    void reconnect_engineDisabled_legacyReplay() throws Exception {
        stubConnection();
        when(roundService.listBySession(32L)).thenReturn(List.of(unansweredRound(101L, 1)));
        // engine.isEnabled() mock 默认 false，走旧链路

        handler.afterConnectionEstablished(session);

        verify(engine, never()).resumeInterview(anyLong());
        assertSentType("QUESTION_START");
    }

    /** 连接建立公共桩：会话 IN_PROGRESS + 连接锁可用 + URI 携带 sessionId=32。 */
    private void stubConnection() {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setId(32L);
        entity.setStatus(SessionStatus.IN_PROGRESS.name());
        when(sessionService.getById(32L)).thenReturn(entity);
        when(sessionStore.tryLock(anyLong(), anyString())).thenReturn(true);
        when(session.getUri()).thenReturn(java.net.URI.create("ws://localhost/ws/interview/32"));
    }

    private InterviewRoundEntity unansweredRound(Long roundId, Integer seq) {
        InterviewRoundEntity r = new InterviewRoundEntity();
        r.setId(roundId);
        r.setSeq(seq);
        r.setQuestion("请介绍下你的项目经历");
        return r;
    }

    /** 断言已发送消息中包含指定 type。 */
    private void assertSentType(String type) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        boolean found =
                captor.getAllValues().stream()
                        .map(
                                t -> {
                                    try {
                                        return (ObjectNode) objectMapper.readTree(t.getPayload());
                                    } catch (Exception e) {
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .anyMatch(n -> type.equals(n.get("type").asText()));
        assertTrue(found, "期望发送 " + type + " 消息");
    }
}
