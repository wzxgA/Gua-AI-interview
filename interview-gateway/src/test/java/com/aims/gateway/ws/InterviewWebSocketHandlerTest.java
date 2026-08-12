package com.aims.gateway.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
}
