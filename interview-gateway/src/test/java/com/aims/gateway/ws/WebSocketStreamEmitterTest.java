package com.aims.gateway.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewerPersona;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.service.TtsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * {@link WebSocketStreamEmitter} 测试：验证 emitEnd/emitFollowUpEnd 预落库并随 QUESTION_END 推送真实 roundId。
 *
 * <p>覆盖：主问题/追问创建轮次、参数传递、创建失败降级 roundId=null。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketStreamEmitterTest {

    @Mock private WebSocketSessionManager sessionManager;
    @Mock private WebSocketSession session;
    @Mock private InterviewRoundService roundService;
    @Mock private ObjectProvider<TtsService> ttsServiceProvider;
    @Mock private InterviewSessionService sessionService;
    @Mock private TtsService ttsService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketStreamEmitter emitter;

    @BeforeEach
    void setUp() {
        when(sessionManager.getSession(1L)).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        emitter =
                new WebSocketStreamEmitter(
                        sessionManager,
                        objectMapper,
                        roundService,
                        ttsServiceProvider,
                        sessionService);
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

    @Test
    @DisplayName("TTS 启用：emitEnd 异步合成、落库并推送 AUDIO_READY")
    void emitEnd_ttsEnabled_synthesizeAndPushAudio() throws Exception {
        InterviewRoundEntity created = new InterviewRoundEntity();
        created.setId(101L);
        when(roundService.createRound(1L, 3, "问题")).thenReturn(created);

        when(ttsServiceProvider.getIfAvailable()).thenReturn(ttsService);
        when(ttsService.synthesize(eq("问题"), any(InterviewerPersona.class)))
                .thenReturn(new TtsService.TtsResult("/aims-audio/tts/1.mp3", 1000));
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setPersona("FRIENDLY");
        entity.setTtsEnabled(true);
        when(sessionService.getById(1L)).thenReturn(entity);

        emitter.emitStart(1L, 3);
        emitter.emitEnd(1L, "问题");

        // 异步：等待合成与落库完成
        verify(ttsService, timeout(2000)).synthesize(eq("问题"), any(InterviewerPersona.class));
        verify(roundService, timeout(2000)).updateAudio(101L, "/aims-audio/tts/1.mp3", 1000);

        // 异步：等待 AUDIO_READY 推送（emitStart + QUESTION_END + AUDIO_READY 共 3 次）
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, timeout(2000).times(3)).sendMessage(captor.capture());
        JsonNode audio = objectMapper.readTree(captor.getAllValues().get(2).getPayload());
        assertEquals("AUDIO_READY", audio.get("type").asText());
        assertEquals(101L, audio.get("roundId").asLong());
    }

    @Test
    @DisplayName("TTS 未启用：getIfAvailable 返回 null，跳过合成与落库")
    void emitEnd_ttsDisabled_skipSynthesis() throws Exception {
        InterviewRoundEntity created = new InterviewRoundEntity();
        created.setId(101L);
        when(roundService.createRound(1L, 3, "问题")).thenReturn(created);
        when(ttsServiceProvider.getIfAvailable()).thenReturn(null);

        emitter.emitStart(1L, 3);
        emitter.emitEnd(1L, "问题");

        verify(ttsService, never()).synthesize(anyString(), any(InterviewerPersona.class));
        verify(roundService, never()).updateAudio(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("TTS 合成返回 null：不落库、不抛异常")
    void emitEnd_synthesizeReturnsNull_noUpdateAudio() throws Exception {
        InterviewRoundEntity created = new InterviewRoundEntity();
        created.setId(101L);
        when(roundService.createRound(1L, 3, "问题")).thenReturn(created);
        when(ttsServiceProvider.getIfAvailable()).thenReturn(ttsService);

        CountDownLatch latch = new CountDownLatch(1);
        when(ttsService.synthesize(anyString(), any(InterviewerPersona.class)))
                .thenAnswer(
                        inv -> {
                            latch.countDown();
                            return null;
                        });
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setPersona("FRIENDLY");
        entity.setTtsEnabled(true);
        when(sessionService.getById(1L)).thenReturn(entity);

        emitter.emitStart(1L, 3);
        emitter.emitEnd(1L, "问题");

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(roundService, never()).updateAudio(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("createRound 失败 roundId=null：不触发 TTS")
    void emitEnd_createRoundFails_noTts() throws Exception {
        when(roundService.createRound(1L, 3, "问题")).thenThrow(new RuntimeException("db down"));

        emitter.emitStart(1L, 3);
        emitter.emitEnd(1L, "问题");

        // roundId=null -> triggerTts 不被调用，getIfAvailable 与 synthesize 均不触发
        verify(ttsServiceProvider, never()).getIfAvailable();
        verify(ttsService, never()).synthesize(anyString(), any(InterviewerPersona.class));
    }
}
