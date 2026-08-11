package com.aims.gateway.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.core.interview.FollowUpType;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * {@link WebSocketStreamEmitter} 测试：验证 emitEnd/emitFollowUpEnd 预落库并随 QUESTION_END 推送真实 roundId。
 *
 * <p>覆盖：主问题/追问创建轮次、参数传递、创建失败降级 roundId=null。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketStreamEmitterTest {

    @Mock private WebSocketSessionManager sessionManager;
    @Mock private WebSocketSession session;
    @Mock private InterviewRoundService roundService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketStreamEmitter emitter;

    @BeforeEach
    void setUp() {
        when(sessionManager.getSession(1L)).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        emitter = new WebSocketStreamEmitter(sessionManager, objectMapper, roundService);
    }

    /** 取最后一次发送的 WS 消息并解析为 JSON。 */
    private JsonNode lastSent() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        var all = captor.getAllValues();
        return objectMapper.readTree(all.get(all.size() - 1).getPayload());
    }

    @Test
    @DisplayName("主问题 emitEnd：预创建轮次并随 QUESTION_END 推送真实 roundId")
    void emitEnd_createsRound_andCarriesRoundId() throws Exception {
        InterviewRoundEntity created = new InterviewRoundEntity();
        created.setId(101L);
        when(roundService.createRound(1L, 3, "问题")).thenReturn(created);

        emitter.emitStart(1L, 3);
        emitter.emit(1L, "chunk");
        emitter.emitEnd(1L, "问题");

        JsonNode end = lastSent();
        assertEquals("QUESTION_END", end.get("type").asText());
        assertEquals(101L, end.get("roundId").asLong());
        assertEquals(3, end.get("seq").asInt());
        verify(roundService).createRound(1L, 3, "问题");
    }

    @Test
    @DisplayName("追问 emitFollowUpEnd：携带 parentSeq/followUpIndex/followUpType 创建轮次并推送 roundId")
    void emitFollowUpEnd_createsRound_withFollowUpMeta() throws Exception {
        InterviewRoundEntity created = new InterviewRoundEntity();
        created.setId(102L);
        when(roundService.createRound(1L, null, "追问", "DEEPEN", 1, 2)).thenReturn(created);

        emitter.emitFollowUpStart(1L, FollowUpType.DEEPEN, 1, 2);
        emitter.emitFollowUpEnd(1L, "追问");

        JsonNode end = lastSent();
        assertEquals("QUESTION_END", end.get("type").asText());
        assertEquals(102L, end.get("roundId").asLong());
        verify(roundService).createRound(1L, null, "追问", "DEEPEN", 1, 2);
    }

    @Test
    @DisplayName("createRound 失败：降级 roundId=null，不中断推送")
    void emitEnd_createRoundFails_degradesToNull() throws Exception {
        when(roundService.createRound(1L, 3, "问题")).thenThrow(new RuntimeException("db down"));

        emitter.emitStart(1L, 3);
        emitter.emitEnd(1L, "问题");

        JsonNode end = lastSent();
        assertEquals("QUESTION_END", end.get("type").asText());
        // roundId=null 时 NON_NULL 序列化省略字段
        assertFalse(end.has("roundId"));
    }

    @Test
    @DisplayName("无 start 上下文（emitEnd 前未 emitStart）：不创建轮次，roundId=null")
    void emitEnd_withoutStartContext_noCreateRound() throws Exception {
        emitter.emitEnd(1L, "孤儿问题");

        JsonNode end = lastSent();
        assertEquals("QUESTION_END", end.get("type").asText());
        assertFalse(end.has("roundId"));
    }
}
