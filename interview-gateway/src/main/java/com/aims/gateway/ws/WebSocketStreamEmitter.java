package com.aims.gateway.ws;

import com.aims.agent.orchestration.node.StreamEmitter;
import com.aims.core.interview.FollowUpType;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>sessionId 由调用方显式传入（Node 从 State 取值并经 Reactor Context 跨线程携带），不依赖 ThreadLocal
 *   <li>{@code emit(sessionId, chunk)} 从 SessionManager 取 session -> 发 QUESTION_CHUNK
 *   <li>{@code emitStart(sessionId, seq)} 发送 QUESTION_START，{@code emitEnd(sessionId,
 *       fullQuestion)} 发送 QUESTION_END
 *   <li>sessionId 为空或 session 不存在/已关闭时静默丢弃（等价 NOOP），保证 Node 执行不因 WS 断开而失败
 * </ul>
 *
 * <p>线程安全：chunk 可能从 reactor-netty 事件循环线程发出，{@code send} 对 session 加锁保证同一会话的消息原子发送。
 *
 * @since 1.1.0 Phase 5
 */
@Component
public class WebSocketStreamEmitter implements StreamEmitter {

    private static final Logger log = LoggerFactory.getLogger(WebSocketStreamEmitter.class);

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /** 预落库服务：emitEnd/emitFollowUpEnd 时创建轮次拿到 DB 主键，随 QUESTION_END 推送真实 roundId。 */
    private final InterviewRoundService roundService;

    /**
     * 按 sessionId 缓存 start 上下文（emitStart/emitFollowUpStart 写入，emitEnd/emitFollowUpEnd 读取并清除）。 同一
     * session 有连接锁保证串行，ConcurrentHashMap 仅为跨线程安全双保险。
     */
    private final Map<Long, PendingRound> pending = new ConcurrentHashMap<>();

    /** start 阶段携带的轮次元数据：主问题用 seq；追问用 parentSeq+followUpIndex+followUpType。 */
    private record PendingRound(
            Integer seq, Integer parentSeq, Integer followUpIndex, String followUpType) {}

    public WebSocketStreamEmitter(
            WebSocketSessionManager sessionManager,
            ObjectMapper objectMapper,
            InterviewRoundService roundService) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.roundService = roundService;
    }

    @Override
    public void emitStart(Long sessionId, int seq) {
        // 缓存 start 上下文，供 emitEnd 创建轮次时携带 seq
        pending.put(sessionId, new PendingRound(seq, null, null, null));
        WebSocketSession session = resolve(sessionId);
        if (session == null) {
            return;
        }
        send(session, WsOutbound.questionStart(sessionId, null, seq));
    }

    @Override
    public void emit(Long sessionId, String chunk) {
        WebSocketSession session = resolve(sessionId);
        if (session == null) {
            // sessionId 为空（如单元测试直接调 Node）或客户端断开，静默丢弃，避免 Node 执行失败
            return;
        }
        // roundId 在 chunk 阶段尚未创建，传 null；前端按 sessionId+seq 累积 chunk
        send(session, WsOutbound.questionChunk(sessionId, null, chunk));
    }

    @Override
    public void emitEnd(Long sessionId, String fullQuestion) {
        WebSocketSession session = resolve(sessionId);
        if (session == null) {
            return;
        }
        // 预落库：创建主问题轮次拿 DB 主键，随 QUESTION_END 推送真实 roundId（失败降级为 null，前端业务键兜底）
        PendingRound ctx = pending.remove(sessionId);
        Long roundId = null;
        if (ctx != null && ctx.seq() != null) {
            try {
                roundId = roundService.createRound(sessionId, ctx.seq(), fullQuestion).getId();
            } catch (Exception e) {
                log.warn(
                        "emitEnd 预创建轮次失败，降级 roundId=null sessionId={} seq={}",
                        sessionId,
                        ctx.seq(),
                        e);
            }
        }
        send(
                session,
                WsOutbound.questionEnd(
                        sessionId, roundId, ctx != null ? ctx.seq() : null, fullQuestion));
    }

    @Override
    public void emitFollowUpStart(
            Long sessionId, FollowUpType type, int parentSeq, int followUpIndex) {
        // 缓存 start 上下文，供 emitFollowUpEnd 创建轮次时携带 parentSeq/followUpIndex/type
        pending.put(
                sessionId,
                new PendingRound(
                        null, parentSeq, followUpIndex, type != null ? type.name() : null));
        WebSocketSession session = resolve(sessionId);
        if (session == null) {
            return;
        }
        // roundId 在 chunk 阶段尚未创建，传 null；前端按 followUpType/parentSeq/followUpIndex 创建追问气泡
        send(
                session,
                WsOutbound.questionStart(
                        sessionId,
                        null,
                        null,
                        type != null ? type.name() : null,
                        parentSeq,
                        followUpIndex));
    }

    @Override
    public void emitFollowUpEnd(Long sessionId, String fullQuestion) {
        WebSocketSession session = resolve(sessionId);
        if (session == null) {
            return;
        }
        // 预落库：创建追问轮次拿 DB 主键，随 QUESTION_END 推送真实 roundId（失败降级为 null）
        PendingRound ctx = pending.remove(sessionId);
        Long roundId = null;
        if (ctx != null && ctx.parentSeq() != null && ctx.followUpIndex() != null) {
            try {
                roundId =
                        roundService
                                .createRound(
                                        sessionId,
                                        null,
                                        fullQuestion,
                                        ctx.followUpType(),
                                        ctx.parentSeq(),
                                        ctx.followUpIndex())
                                .getId();
            } catch (Exception e) {
                log.warn(
                        "emitFollowUpEnd 预创建轮次失败，降级 roundId=null sessionId={} key={}:{}",
                        sessionId,
                        ctx.parentSeq(),
                        ctx.followUpIndex(),
                        e);
            }
        }
        send(session, WsOutbound.questionEnd(sessionId, roundId, null, fullQuestion));
    }

    /** sessionId 为空或会话不存在/已关闭时返回 null（静默丢弃，不中断节点执行）。 */
    private WebSocketSession resolve(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isOpen()) {
            return null;
        }
        return session;
    }

    private void send(WebSocketSession session, WsOutbound outbound) {
        synchronized (session) {
            try {
                String json = objectMapper.writeValueAsString(outbound);
                session.sendMessage(new TextMessage(json));
            } catch (JsonProcessingException e) {
                log.warn("WsOutbound 序列化失败 type={}", outbound.type(), e);
            } catch (IOException e) {
                log.warn("WebSocket 发送失败 sessionId={}", outbound.sessionId(), e);
            }
        }
    }
}
