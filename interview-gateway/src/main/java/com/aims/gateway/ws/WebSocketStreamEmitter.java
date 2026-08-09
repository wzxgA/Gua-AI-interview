package com.aims.gateway.ws;

import com.aims.agent.orchestration.node.StreamEmitter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 流式推送器：实现 {@link StreamEmitter}，把 Node 流式输出的 chunk 实时推送到 WebSocket。
 *
 * <p>Phase 5 引入，覆盖 {@code NodeBeanConfig} 的 NOOP 默认实现（通过 {@code @ConditionalOnMissingBean} 让位）。
 *
 * <p>设计：
 *
 * <ul>
 *   <li>单例 Bean，注入 {@link WebSocketSessionManager} 和 {@link ObjectMapper}
 *   <li>使用 ThreadLocal 绑定当前 sessionId（由 {@code InterviewWorkflowEngine} 在 graph.invoke 前后
 *       bind/unbind）
 *   <li>{@code emit(chunk)} 从 ThreadLocal 取 sessionId → 从 SessionManager 取 session → 发
 *       QUESTION_CHUNK
 *   <li>未绑定或 session 不存在时静默丢弃（等价 NOOP），保证 Node 执行不因 WS 断开而失败
 * </ul>
 *
 * <p>线程安全：{@code blockLast()} 同步阻塞，emit 在调用线程执行，ThreadLocal 可靠。
 *
 * @since 1.1.0 Phase 5
 */
@Component
public class WebSocketStreamEmitter implements StreamEmitter {

    private static final Logger log = LoggerFactory.getLogger(WebSocketStreamEmitter.class);

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /** 当前线程绑定的 sessionId，由 Engine 在 graph.invoke 前 set，invoke 后 remove。 */
    private final ThreadLocal<Long> currentSessionId = new ThreadLocal<>();

    public WebSocketStreamEmitter(
            WebSocketSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 绑定当前线程的 sessionId。由 {@code InterviewWorkflowEngine} 在 graph.invoke 前调用。
     *
     * @param sessionId 面试 sessionId
     */
    public void bindSession(Long sessionId) {
        currentSessionId.set(sessionId);
    }

    /** 解绑当前线程的 sessionId。由 Engine 在 graph.invoke 后（finally 块）调用。 */
    public void unbindSession() {
        currentSessionId.remove();
    }

    @Override
    public void emit(String chunk) {
        Long sessionId = currentSessionId.get();
        if (sessionId == null) {
            // 未绑定（如单元测试直接调 Node），静默丢弃
            return;
        }
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isOpen()) {
            // 客户端断开，静默丢弃，避免 Node 执行失败
            return;
        }
        // roundId 在 chunk 阶段尚未创建，传 null；前端按 sessionId+seq 累积 chunk
        send(session, WsOutbound.questionChunk(sessionId, null, chunk));
    }

    private void send(WebSocketSession session, WsOutbound outbound) {
        try {
            String json = objectMapper.writeValueAsString(outbound);
            session.sendMessage(new TextMessage(json));
        } catch (JsonProcessingException e) {
            log.warn("WsOutbound 序列化失败 type={}", outbound.type(), e);
        } catch (IOException e) {
            log.warn("WebSocket 发送失败 sessionId={}", currentSessionId.get(), e);
        }
    }
}
