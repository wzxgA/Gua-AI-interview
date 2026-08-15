package com.aims.gateway.ws;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
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

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

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
     * <p>带连接身份校验：仅当 {@code sessionId} 当前注册的正是 {@code session} 时才移除。 防止旧连接（如 StrictMode
     * 双连接、断线重连时的前一连接）的关闭回调把新注册的活跃连接误删—— 一旦误删，Engine 推送 {@link #getSession} 返回 null 会静默丢弃，前端收不到任何消息。
     *
     * @param sessionId 面试 sessionId
     * @param session 正在关闭的 WebSocket 会话
     */
    public void unregister(Long sessionId, WebSocketSession session) {
        // ConcurrentHashMap.remove(key, value)：仅当 key 映射到该 value 时才删除，原子且安全
        sessions.remove(sessionId, session);
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

    /**
     * FE.16 A2：关闭本实例所有活跃 WS 连接（实例优雅停机时调用）。
     *
     * <p>主动断开让客户端立即重连（走 P2 断线恢复），而非等待 TCP 超时（数秒~数十秒）。 连接关闭后由 {@code afterConnectionClosed}
     * 回调释放连接锁并转 PAUSED，无需在此额外处理。
     *
     * @param status 关闭状态（如 SERVICE_RESTARTED）
     */
    public void closeAll(CloseStatus status) {
        log.info("关闭本实例全部 WebSocket 连接 count={}", sessions.size());
        sessions.forEach(
                (sessionId, session) -> {
                    if (session.isOpen()) {
                        try {
                            session.close(status);
                        } catch (IOException e) {
                            log.warn("优雅停机关闭会话失败 sessionId={}", sessionId, e);
                        }
                    }
                });
        sessions.clear();
    }
}
