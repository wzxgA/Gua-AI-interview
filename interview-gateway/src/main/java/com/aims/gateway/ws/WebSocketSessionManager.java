package com.aims.gateway.ws;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 会话管理器：维护 sessionId → {@link WebSocketSession} 的映射。
 *
 * <p>Phase 5 引入：让 {@link com.aims.gateway.ws.InterviewWebSocketHandler} 与 Engine 解耦—— Engine 通过
 * sessionId 查找活跃会话，推送流式输出，无需直接依赖 Handler 实例。
 *
 * <p>线程安全：基于 {@link ConcurrentHashMap}，{@code register/unregister/get} 均无锁。
 *
 * <p>生命周期：
 *
 * <ul>
 *   <li>WS 连接建立时 {@link InterviewWebSocketHandler#afterConnectionEstablished} 调 {@link #register}
 *   <li>WS 连接关闭时 {@link InterviewWebSocketHandler#afterConnectionClosed} 调 {@link #unregister}
 *   <li>Engine 推送时调 {@link #getSession(Long)} 拿到 session
 * </ul>
 *
 * @since 1.1.0 Phase 5
 */
@Component
public class WebSocketSessionManager {

    private final ConcurrentMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话。若同一 sessionId 已有旧会话，会被覆盖（旧会话由调用方负责关闭）。
     *
     * @param sessionId 面试 sessionId
     * @param session WebSocket 会话
     */
    public void register(Long sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    /**
     * 注销会话。
     *
     * @param sessionId 面试 sessionId
     */
    public void unregister(Long sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 查找活跃会话。
     *
     * @param sessionId 面试 sessionId
     * @return WebSocket 会话；若不存在或已关闭返回 null
     */
    public WebSocketSession getSession(Long sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (!session.isOpen()) {
            // 优雅清理：发现已关闭的 session 顺手清掉，避免内存泄漏
            sessions.remove(sessionId, session);
            return null;
        }
        return session;
    }

    /**
     * 判断 sessionId 是否有活跃会话。
     *
     * @param sessionId 面试 sessionId
     * @return true 表示有活跃会话
     */
    public boolean hasActiveSession(Long sessionId) {
        return getSession(sessionId) != null;
    }
}
