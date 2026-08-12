package com.aims.gateway.ws;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewContext;
import com.aims.agent.InterviewerAgent;
import com.aims.ai.memory.ConversationMemory;
import com.aims.core.common.ErrorCode;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.session.SessionStatus;
import com.aims.gateway.orchestration.InterviewWorkflowEngine;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.InterviewSessionStore;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.QuestionRagService;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.service.TtsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 面试 WebSocket 核心处理器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>连接建立：校验会话、获取连接锁、发送 SESSION_READY；IN_PROGRESS 时自动触发首题或补发下一题
 *   <li>消息分发：ANSWER / HEARTBEAT / PAUSE / FINISH / CANCEL
 *   <li>连接关闭：释放连接锁、IN_PROGRESS 自动转 PAUSED
 * </ul>
 *
 * <p>面试计划生成由 REST {@code POST /api/v1/interviews/{id}/start} 唯一负责，WebSocket 只读取已保存的 plan_json。
 *
 * <p>线程安全：每个 WebSocket 会话独立，发送消息时使用 synchronized 防止并发写入。
 */
@Component
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InterviewWebSocketHandler.class);

    // ---- session attributes keys ----
    private static final String ATTR_SESSION_ID = "sessionId";
    private static final String ATTR_CONNECTION_ID = "connectionId";

    // ---- 业务常量 ----
    private static final int RESUME_SUMMARY_MAX = 2000;
    private static final int RAG_TOP_K = 10;
    private static final int MAX_ANSWER_LENGTH = 10000;
    private static final int RECENT_HISTORY_LIMIT = 5;
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_FOLLOW_UPS = 3;

    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final InterviewSessionStore sessionStore;
    private final InterviewerAgent interviewerAgent;
    private final FollowUpAgent followUpAgent;
    private final PositionService positionService;
    private final ResumeService resumeService;
    private final QuestionRagService questionRagService;
    private final ConversationMemory conversationMemory;
    private final EvaluationMessageProducer evaluationMessageProducer;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TtsService> ttsServiceProvider;
    private final RollingSummaryService rollingSummaryService;
    // Phase 5 新增：Engine 委托路径 + 会话管理
    private final InterviewWorkflowEngine engine;
    private final WebSocketSessionManager sessionManager;

    public InterviewWebSocketHandler(
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            InterviewSessionStore sessionStore,
            InterviewerAgent interviewerAgent,
            FollowUpAgent followUpAgent,
            PositionService positionService,
            ResumeService resumeService,
            QuestionRagService questionRagService,
            ConversationMemory conversationMemory,
            EvaluationMessageProducer evaluationMessageProducer,
            ObjectMapper objectMapper,
            ObjectProvider<TtsService> ttsServiceProvider,
            RollingSummaryService rollingSummaryService,
            InterviewWorkflowEngine engine,
            WebSocketSessionManager sessionManager) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.sessionStore = sessionStore;
        this.interviewerAgent = interviewerAgent;
        this.followUpAgent = followUpAgent;
        this.positionService = positionService;
        this.resumeService = resumeService;
        this.questionRagService = questionRagService;
        this.conversationMemory = conversationMemory;
        this.evaluationMessageProducer = evaluationMessageProducer;
        this.objectMapper = objectMapper;
        this.ttsServiceProvider = ttsServiceProvider;
        this.rollingSummaryService = rollingSummaryService;
        this.engine = engine;
        this.sessionManager = sessionManager;
    }

    // ==================== 连接生命周期 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long sessionId = extractSessionId(session);
        if (sessionId == null) {
            send(session, WsOutbound.error(ErrorCode.PARAM_INVALID.getCode(), "URL 中缺少 sessionId"));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 校验会话存在
        InterviewSessionEntity entity;
        try {
            entity = sessionService.getById(sessionId);
        } catch (Exception e) {
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.SESSION_NOT_FOUND.getCode(), "面试会话不存在: " + sessionId));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        // 生成连接 ID 并尝试获取连接锁
        String connectionId = UUID.randomUUID().toString();
        boolean locked = sessionStore.tryLock(sessionId, connectionId);
        if (!locked) {
            send(session, WsOutbound.error(ErrorCode.SESSION_LOCKED.getCode(), "会话已被其他连接占用"));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("session locked"));
            return;
        }

        // 存储上下文
        session.getAttributes().put(ATTR_SESSION_ID, sessionId);
        session.getAttributes().put(ATTR_CONNECTION_ID, connectionId);

        // Phase 5：注册到 SessionManager，供 Engine 推送流式输出
        sessionManager.register(sessionId, session);

        log.info("WebSocket 连接建立 sessionId={} connectionId={}", sessionId, connectionId);
        send(session, WsOutbound.sessionReady(sessionId, entity.getStatus()));

        // 若状态为 IN_PROGRESS 或 PAUSED，根据轮次记录决定是否自动生成首题或补发当前问题
        SessionStatus current = SessionStatus.valueOf(entity.getStatus());
        if (current == SessionStatus.IN_PROGRESS || current == SessionStatus.PAUSED) {
            handleReconnectOrStart(session, sessionId, entity);
        }
    }

    /**
     * 连接建立后，根据轮次记录决定后续动作：
     *
     * <ul>
     *   <li>无轮次记录：首次进入面试间，自动生成首题
     *   <li>最后一条轮次已回答且未达上限：补发下一题
     *   <li>最后一条轮次已回答且已达上限：直接完成
     *   <li>最后一条轮次未回答：补发该问题（支持断线重连恢复当前轮次）
     * </ul>
     */
    private void handleReconnectOrStart(
            WebSocketSession session, Long sessionId, InterviewSessionEntity entity) {
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);

        if (rounds.isEmpty()) {
            // 首次进入面试间，生成首题
            log.info("首次进入面试间，自动生成首题 sessionId={}", sessionId);
            if (engine.isEnabled()) {
                // Engine 路径：Graph 执行 plan → ask → interruptBefore(ANSWER) 暂停 → 创建 checkpoint
                try {
                    engine.startInterview(sessionId);
                } catch (Exception e) {
                    log.error("Engine 启动面试失败 sessionId={}", sessionId, e);
                    send(session, WsOutbound.error(ErrorCode.INTERNAL_ERROR.getCode(), "面试启动失败"));
                }
            } else {
                generateAndSendQuestion(session, sessionId);
            }
            return;
        }

        // 检查最后一条轮次是否已回答
        InterviewRoundEntity lastRound = rounds.get(rounds.size() - 1);
        boolean hasUnansweredQuestion =
                lastRound.getAnswer() == null || lastRound.getAnswer().isBlank();

        if (!hasUnansweredQuestion) {
            // 最后一条轮次已回答，检查是否达到题数上限
            int answeredCount = roundService.countAnswered(sessionId);
            int totalRounds = getTotalRounds(entity);
            if (totalRounds > 0 && answeredCount >= totalRounds) {
                // 已达上限，进入评估流程
                log.info(
                        "重连时发现已达题数上限，进入评估流程 sessionId={} answeredCount={}",
                        sessionId,
                        answeredCount);
                if (triggerEvaluation(sessionId)) {
                    send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
                }
            } else {
                // 补发下一题
                log.info("重连后补发下一题 sessionId={} answeredCount={}", sessionId, answeredCount);
                generateAndSendQuestion(session, sessionId);
            }
        } else {
            // 补发未回答的当前问题
            log.info("重连后补发当前未回答问题 sessionId={} roundId={}", sessionId, lastRound.getId());
            // 追问补发：携带 parentSeq + followUpIndex
            if (lastRound.getParentSeq() != null) {
                send(
                        session,
                        WsOutbound.questionStart(
                                sessionId,
                                lastRound.getId(),
                                null,
                                lastRound.getFollowUpType(),
                                lastRound.getParentSeq(),
                                lastRound.getFollowUpIndex()));
            } else {
                send(
                        session,
                        WsOutbound.questionStart(sessionId, lastRound.getId(), lastRound.getSeq()));
            }
            send(
                    session,
                    WsOutbound.questionChunk(
                            sessionId, lastRound.getId(), lastRound.getQuestion()));
            send(
                    session,
                    WsOutbound.questionEnd(sessionId, lastRound.getId(), lastRound.getSeq(), null));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long sessionId = (Long) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) {
            send(session, WsOutbound.error(ErrorCode.SESSION_MESSAGE_INVALID.getCode(), "连接未初始化"));
            return;
        }

        WsInbound inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), WsInbound.class);
        } catch (JsonProcessingException e) {
            send(session, WsOutbound.error(ErrorCode.SESSION_MESSAGE_INVALID.getCode(), "消息格式非法"));
            return;
        }

        if (inbound.type() == null) {
            send(
                    session,
                    WsOutbound.error(ErrorCode.SESSION_MESSAGE_INVALID.getCode(), "消息类型不能为空"));
            return;
        }

        log.debug("收到 WebSocket 消息 sessionId={} type={}", sessionId, inbound.type());
        try {
            if (inbound.isType("START")) {
                send(
                        session,
                        WsOutbound.error(
                                ErrorCode.SESSION_MESSAGE_INVALID.getCode(),
                                "面试计划请通过 REST /start 接口生成，WebSocket 不再支持 START 消息"));
            } else if (inbound.isType("ANSWER")) {
                handleAnswer(session, sessionId, inbound.text());
            } else if (inbound.isType("HEARTBEAT")) {
                handleHeartbeat(session, sessionId);
            } else if (inbound.isType("PAUSE")) {
                handlePause(session, sessionId);
            } else if (inbound.isType("FINISH")) {
                handleFinish(session, sessionId);
            } else if (inbound.isType("CANCEL")) {
                handleCancel(session, sessionId);
            } else {
                send(
                        session,
                        WsOutbound.error(
                                ErrorCode.SESSION_MESSAGE_INVALID.getCode(),
                                "不支持的消息类型: " + inbound.type()));
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息异常 sessionId={} type={}", sessionId, inbound.type(), e);
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.INTERNAL_ERROR.getCode(), "处理消息异常: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long sessionId = (Long) session.getAttributes().get(ATTR_SESSION_ID);
        String connectionId = (String) session.getAttributes().get(ATTR_CONNECTION_ID);
        if (sessionId == null || connectionId == null) {
            return;
        }

        // 释放连接锁
        sessionStore.unlock(sessionId, connectionId);
        // Phase 5：从 SessionManager 注销
        sessionManager.unregister(sessionId);
        log.info(
                "WebSocket 连接关闭 sessionId={} connectionId={} status={}",
                sessionId,
                connectionId,
                status);

        // 如果状态为 IN_PROGRESS 则转为 PAUSED
        try {
            InterviewSessionEntity entity = sessionService.getById(sessionId);
            SessionStatus current = SessionStatus.valueOf(entity.getStatus());
            if (current == SessionStatus.IN_PROGRESS) {
                sessionService.updateStatus(sessionId, SessionStatus.PAUSED);
                log.info("连接断开，会话自动暂停 sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.warn("连接关闭后状态处理失败 sessionId={}", sessionId, e);
        }
    }

    // ==================== 消息处理 ====================

    /**
     * 触发评估流程（原子保证同一会话只触发一次）。
     *
     * <p>先通过原子条件更新将状态从 IN_PROGRESS/PAUSED 转为 EVALUATING，再释放连接锁、发送 Kafka 评估请求。 若状态已被其他线程转移，返回 false
     * 跳过重复触发。
     *
     * @return true 表示成功触发评估，false 表示评估已被其他线程触发
     */
    private boolean triggerEvaluation(Long sessionId) {
        boolean transitioned =
                sessionService.tryTransitionTo(
                        sessionId,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED);
        if (!transitioned) {
            log.info("评估已触发，跳过重复请求 sessionId={}", sessionId);
            return false;
        }
        sessionStore.forceUnlock(sessionId);
        sessionService.updateEvaluationStatus(sessionId, "PENDING");
        evaluationMessageProducer.sendEvaluationRequest(sessionId);
        return true;
    }

    /** ANSWER：接收回答，更新轮次，决定是否生成下一题或结束。 */
    private void handleAnswer(WebSocketSession session, Long sessionId, String text) {
        // 状态校验：两链路统一要求 IN_PROGRESS 才允许提交回答（Engine 路径此前缺该校验，P1）
        InterviewSessionEntity entity = sessionService.getById(sessionId);
        SessionStatus current = SessionStatus.valueOf(entity.getStatus());
        if (current != SessionStatus.IN_PROGRESS) {
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.SESSION_STATUS_CONFLICT.getCode(),
                            "会话状态不允许 ANSWER，当前: " + current));
            return;
        }

        // Phase 5：Engine 启用时委托 Engine，否则走旧命令式路径
        if (engine.isEnabled()) {
            handleAnswerViaEngine(session, sessionId, text);
            return;
        }

        // 校验回答内容
        if (text == null || text.isBlank()) {
            send(
                    session,
                    WsOutbound.error(ErrorCode.SESSION_MESSAGE_INVALID.getCode(), "回答内容不能为空"));
            return;
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.SESSION_MESSAGE_INVALID.getCode(),
                            "回答内容超过 " + MAX_ANSWER_LENGTH + " 字符"));
            return;
        }

        // 查找当前未回答的轮次（最后一个 answer 为空的轮次）
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);
        InterviewRoundEntity currentRound = null;
        for (int i = rounds.size() - 1; i >= 0; i--) {
            InterviewRoundEntity r = rounds.get(i);
            if (r.getAnswer() == null || r.getAnswer().isBlank()) {
                currentRound = r;
                break;
            }
        }
        if (currentRound == null) {
            send(session, WsOutbound.error(ErrorCode.SESSION_ROUND_CONFLICT.getCode(), "没有待回答的问题"));
            return;
        }

        // 更新回答
        roundService.updateAnswer(currentRound.getId(), text);
        currentRound.setAnswer(text);
        // 写入会话记忆
        conversationMemory.addUser(sessionId.toString(), text, currentRound.getId().toString());
        // 发送确认
        send(session, WsOutbound.answerAck(sessionId, currentRound.getId()));
        log.info("收到回答 sessionId={} roundId={}", sessionId, currentRound.getId());

        // 异步触发滚动摘要生成（每 5 轮回答后触发，含追问轮次）
        rollingSummaryService.triggerSummaryIfNeeded(sessionId);

        // 确定主问题 seq（追问取 parentSeq，主问题取 seq）
        int parentSeq =
                currentRound.getParentSeq() != null
                        ? currentRound.getParentSeq()
                        : currentRound.getSeq();

        // 1. 先检查是否需要追问（追问未达上限）
        int followUpCount = roundService.countFollowUps(sessionId, parentSeq);
        if (followUpCount < MAX_FOLLOW_UPS) {
            FollowUpContext followUpContext = buildFollowUpContext(sessionId, entity, currentRound);
            FollowUpDecision decision = followUpAgent.evaluate(followUpContext);
            log.info(
                    "追问决策 sessionId={} roundId={} shouldFollowUp={} type={} reason={}",
                    sessionId,
                    currentRound.getId(),
                    decision.shouldFollowUp(),
                    decision.followUpType(),
                    decision.reason());
            if (decision.shouldFollowUp()) {
                generateAndSendFollowUp(
                        session, sessionId, parentSeq, followUpCount, followUpContext, decision);
                return;
            }
        }

        // 2. 不追问或追问已达上限：检查是否达到题目上限
        int answeredCount = roundService.countAnswered(sessionId);
        int totalRounds = getTotalRounds(entity);
        if (totalRounds > 0 && answeredCount >= totalRounds) {
            if (triggerEvaluation(sessionId)) {
                send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
                log.info("达到题数上限，进入评估流程 sessionId={} answeredCount={}", sessionId, answeredCount);
            }
        } else {
            // 3. 未达上限，生成下一题
            generateAndSendQuestion(session, sessionId);
        }
    }

    /** HEARTBEAT：续租连接锁。 */
    private void handleHeartbeat(WebSocketSession session, Long sessionId) {
        String connectionId = (String) session.getAttributes().get(ATTR_CONNECTION_ID);
        boolean renewed = sessionStore.renewLock(sessionId, connectionId);
        if (!renewed) {
            send(session, WsOutbound.error(ErrorCode.SESSION_LOCKED.getCode(), "连接锁已失效，请重新连接"));
        } else {
            send(session, WsOutbound.heartbeatAck(sessionId));
        }
    }

    /** PAUSE：暂停会话。 */
    private void handlePause(WebSocketSession session, Long sessionId) {
        // Phase 5：Engine 启用时委托
        if (engine.isEnabled()) {
            engine.pauseInterview(sessionId);
            send(session, WsOutbound.status(sessionId, SessionStatus.PAUSED.name()));
            log.info("会话暂停（Engine）sessionId={}", sessionId);
            return;
        }
        sessionService.updateStatus(sessionId, SessionStatus.PAUSED);
        send(session, WsOutbound.status(sessionId, SessionStatus.PAUSED.name()));
        log.info("会话暂停 sessionId={}", sessionId);
    }

    /** FINISH：结束会话，触发评估流程。 */
    private void handleFinish(WebSocketSession session, Long sessionId) {
        // Phase 5：Engine 启用时委托
        if (engine.isEnabled()) {
            handleFinishViaEngine(session, sessionId);
            return;
        }
        InterviewSessionEntity entity = sessionService.getById(sessionId);
        SessionStatus current = SessionStatus.valueOf(entity.getStatus());
        if (current != SessionStatus.IN_PROGRESS) {
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.SESSION_STATUS_CONFLICT.getCode(),
                            "会话状态不允许 FINISH，当前: " + current));
            return;
        }
        // 状态转为 EVALUATING，触发评估
        if (triggerEvaluation(sessionId)) {
            // 通知前端进入评估状态
            send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
            log.info("面试结束，进入评估流程 sessionId={}", sessionId);
        }
    }

    /** CANCEL：取消会话。 */
    private void handleCancel(WebSocketSession session, Long sessionId) {
        // Phase 5：Engine 启用时委托
        if (engine.isEnabled()) {
            try {
                engine.cancelInterview(sessionId);
            } catch (Exception e) {
                log.warn("Engine 取消面试失败 sessionId={}", sessionId, e);
            }
            sessionStore.forceUnlock(sessionId);
            send(session, WsOutbound.status(sessionId, SessionStatus.CANCELLED.name()));
            log.info("会话取消（Engine）sessionId={}", sessionId);
            return;
        }
        sessionService.updateStatus(sessionId, SessionStatus.CANCELLED);
        sessionService.markEnded(sessionId);
        sessionStore.forceUnlock(sessionId);
        send(session, WsOutbound.status(sessionId, SessionStatus.CANCELLED.name()));
        log.info("会话取消 sessionId={}", sessionId);
    }

    // ==================== Phase 5 Engine 委托方法 ====================

    /**
     * Phase 5：通过 Engine 提交回答。
     *
     * <p>Graph 在 interruptBefore(ANSWER) 暂停时，Engine.submitAnswer 注入 answer → resume → 执行到下一个 ASK 或
     * END。流式 chunk 由 WebSocketStreamEmitter 自动推送。
     */
    private void handleAnswerViaEngine(WebSocketSession session, Long sessionId, String text) {
        if (text == null || text.isBlank()) {
            send(
                    session,
                    WsOutbound.error(ErrorCode.SESSION_MESSAGE_INVALID.getCode(), "回答内容不能为空"));
            return;
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.SESSION_MESSAGE_INVALID.getCode(),
                            "回答内容超过 " + MAX_ANSWER_LENGTH + " 字符"));
            return;
        }
        try {
            engine.submitAnswer(sessionId, text);
            log.info("回答已提交（Engine）sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Engine 提交回答失败 sessionId={}", sessionId, e);
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.INTERNAL_ERROR.getCode(),
                            "Engine 处理回答失败: " + e.getMessage()));
        }
    }

    /**
     * Phase 5：通过 Engine 结束面试。
     *
     * <p>设置 FORCE_END=true → 图执行到 END → Engine 触发 Kafka 异步评估（FE.04），本方法同步返回后推送 EVALUATING 状态，
     * 评估/报告完成后前端经 2s 轮询感知 COMPLETED。
     */
    private void handleFinishViaEngine(WebSocketSession session, Long sessionId) {
        try {
            engine.finishInterview(sessionId);
            send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
            log.info("面试已结束，进入评估（Engine）sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Engine 结束面试失败 sessionId={}", sessionId, e);
            send(
                    session,
                    WsOutbound.error(
                            ErrorCode.INTERNAL_ERROR.getCode(),
                            "Engine 结束面试失败: " + e.getMessage()));
        }
    }

    // ==================== 核心业务方法 ====================

    /** 流式生成问题并发送给客户端。 */
    private void generateAndSendQuestion(WebSocketSession session, Long sessionId) {
        InterviewContext context;
        try {
            context = buildInterviewContext(sessionId);
        } catch (Exception e) {
            log.error("构建面试上下文失败 sessionId={}", sessionId, e);
            send(session, WsOutbound.error(ErrorCode.INTERNAL_ERROR.getCode(), "构建面试上下文失败"));
            return;
        }

        // 流式生成问题，收集所有 chunk
        List<String> chunks = new ArrayList<>();
        try {
            interviewerAgent
                    .streamQuestion(context)
                    .doOnNext(chunks::add)
                    .blockLast(STREAM_TIMEOUT);
        } catch (Exception e) {
            log.error("流式生成问题失败 sessionId={}", sessionId, e);
            send(session, WsOutbound.error(ErrorCode.MODEL_CALL_FAILED.getCode(), "生成问题失败"));
            return;
        }

        if (chunks.isEmpty()) {
            send(session, WsOutbound.error(ErrorCode.MODEL_CALL_FAILED.getCode(), "生成问题为空"));
            return;
        }

        String fullQuestion = String.join("", chunks);

        // 创建轮次记录
        int seq = roundService.maxSeq(sessionId) + 1;
        InterviewRoundEntity round = roundService.createRound(sessionId, seq, fullQuestion);

        // 发送 QUESTION_START
        send(session, WsOutbound.questionStart(sessionId, round.getId(), seq));

        // 逐 chunk 发送 QUESTION_CHUNK
        for (String chunk : chunks) {
            send(session, WsOutbound.questionChunk(sessionId, round.getId(), chunk));
        }

        // 发送 QUESTION_END
        send(session, WsOutbound.questionEnd(sessionId, round.getId(), seq, fullQuestion));

        // 写入会话记忆
        conversationMemory.addAssistant(
                sessionId.toString(), fullQuestion, round.getId().toString());
        log.info("生成问题完成 sessionId={} roundId={} seq={}", sessionId, round.getId(), seq);

        // 异步触发 TTS 语音合成
        triggerTts(session, sessionId, round.getId(), fullQuestion, context.persona());
    }

    /** 流式生成追问问题并发送给客户端。 */
    private void generateAndSendFollowUp(
            WebSocketSession session,
            Long sessionId,
            int parentSeq,
            int followUpCount,
            FollowUpContext context,
            FollowUpDecision decision) {
        // 流式生成追问问题
        List<String> chunks = new ArrayList<>();
        try {
            followUpAgent
                    .streamFollowUp(context, decision)
                    .doOnNext(chunks::add)
                    .blockLast(STREAM_TIMEOUT);
        } catch (Exception e) {
            log.error("流式生成追问问题失败 sessionId={}", sessionId, e);
            send(session, WsOutbound.error(ErrorCode.MODEL_CALL_FAILED.getCode(), "生成追问问题失败"));
            return;
        }

        if (chunks.isEmpty()) {
            log.warn("追问问题为空，改用决策中的追问问题 sessionId={}", sessionId);
            chunks.add(decision.followUpQuestion());
        }

        String fullQuestion = String.join("", chunks);

        // 创建追问轮次记录（seq=null，parentSeq 指向主问题，followUpIndex 递增）
        int followUpIndex = followUpCount + 1;
        InterviewRoundEntity round =
                roundService.createRound(
                        sessionId,
                        null,
                        fullQuestion,
                        decision.followUpType().name(),
                        parentSeq,
                        followUpIndex);

        // 发送 QUESTION_START（携带 followUpType、parentSeq、followUpIndex）
        send(
                session,
                WsOutbound.questionStart(
                        sessionId,
                        round.getId(),
                        null,
                        decision.followUpType().name(),
                        parentSeq,
                        followUpIndex));

        // 逐 chunk 发送
        for (String chunk : chunks) {
            send(session, WsOutbound.questionChunk(sessionId, round.getId(), chunk));
        }

        // 发送 QUESTION_END
        send(session, WsOutbound.questionEnd(sessionId, round.getId(), null, fullQuestion));

        // 写入会话记忆
        conversationMemory.addAssistant(
                sessionId.toString(), fullQuestion, round.getId().toString());
        log.info(
                "生成追问完成 sessionId={} roundId={} parentSeq={} followUpIndex={} followUpType={}",
                sessionId,
                round.getId(),
                parentSeq,
                followUpIndex,
                decision.followUpType());

        // 异步触发 TTS 语音合成
        triggerTts(session, sessionId, round.getId(), fullQuestion, context.persona());
    }

    // ==================== 辅助方法 ====================

    /**
     * 异步触发 TTS 语音合成。
     *
     * <p>使用虚拟线程异步执行，不阻塞面试主流程。TTS 未启用或合成失败时静默降级。
     */
    private void triggerTts(
            WebSocketSession session,
            Long sessionId,
            Long roundId,
            String text,
            InterviewerPersona persona) {
        TtsService ttsService = ttsServiceProvider.getIfAvailable();
        if (ttsService == null) {
            return;
        }
        Thread.startVirtualThread(
                () -> {
                    try {
                        TtsService.TtsResult result = ttsService.synthesize(text, persona);
                        if (result == null) {
                            return;
                        }
                        roundService.updateAudio(roundId, result.audioUrl(), result.durationMs());
                        if (session.isOpen()) {
                            send(
                                    session,
                                    WsOutbound.audioReady(
                                            sessionId,
                                            roundId,
                                            result.audioUrl(),
                                            result.durationMs()));
                        }
                    } catch (Exception e) {
                        log.warn("TTS 异步合成失败 sessionId={} roundId={}", sessionId, roundId, e);
                    }
                });
    }

    /** 构建追问上下文。 */
    private FollowUpContext buildFollowUpContext(
            Long sessionId, InterviewSessionEntity entity, InterviewRoundEntity currentRound) {
        ResumeEntity resume = resumeService.getById(entity.getCandidateId());
        PositionEntity position = positionService.getById(entity.getPositionId());

        // 从计划中获取当前题目的 followUpHints
        List<String> followUpHints = List.of();
        if (entity.getPlanJson() != null && !entity.getPlanJson().isBlank()) {
            try {
                InterviewPlan plan =
                        objectMapper.readValue(entity.getPlanJson(), InterviewPlan.class);
                if (plan != null && plan.questions() != null && !plan.questions().isEmpty()) {
                    // 找到与当前题目对应的计划题目（追问取 parentSeq，主问题取 seq，从 1 开始）
                    int seqForPlan =
                            currentRound.getParentSeq() != null
                                    ? currentRound.getParentSeq()
                                    : currentRound.getSeq();
                    int questionIdx = Math.min(seqForPlan - 1, plan.questions().size() - 1);
                    if (questionIdx >= 0) {
                        PlannedQuestion pq = plan.questions().get(questionIdx);
                        followUpHints = pq.followUpHints();
                    }
                }
            } catch (JsonProcessingException e) {
                log.warn("反序列化面试计划失败 sessionId={}", sessionId, e);
            }
        }

        // 收集最近已问过的问题（用于去重）
        List<InterviewRoundEntity> allRounds = roundService.listBySession(sessionId);
        List<String> recentQuestions =
                allRounds.stream()
                        .map(InterviewRoundEntity::getQuestion)
                        .limit(RECENT_HISTORY_LIMIT)
                        .toList();

        return new FollowUpContext(
                sessionId,
                currentRound.getId(),
                currentRound.getQuestion(),
                currentRound.getAnswer(),
                resume.getCandidateName(),
                position.getTitle(),
                position.getJdText(),
                buildResumeSummary(resume),
                followUpHints,
                recentQuestions,
                InterviewerPersona.fromString(entity.getPersona()));
    }

    /** 构建 InterviewContext。 */
    private InterviewContext buildInterviewContext(Long sessionId) {
        InterviewSessionEntity entity = sessionService.getById(sessionId);

        // 反序列化计划
        InterviewPlan plan = null;
        if (entity.getPlanJson() != null && !entity.getPlanJson().isBlank()) {
            try {
                plan = objectMapper.readValue(entity.getPlanJson(), InterviewPlan.class);
            } catch (JsonProcessingException e) {
                log.warn("反序列化面试计划失败 sessionId={}", sessionId, e);
            }
        }

        // 加载简历和岗位
        ResumeEntity resume = resumeService.getById(entity.getCandidateId());
        PositionEntity position = positionService.getById(entity.getPositionId());

        // 加载轮次历史
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);
        List<InterviewRoundEntity> answered =
                rounds.stream()
                        .filter(r -> r.getAnswer() != null && !r.getAnswer().isBlank())
                        .toList();

        // 读取滚动摘要（异步生成，可能尚未就绪）
        String runningSummary = rollingSummaryService.getRunningSummary(sessionId);
        int lastSummarizedCount = rollingSummaryService.getLastSummarizedCount(sessionId);

        // 提取最近的问答：排除已被摘要覆盖的早期轮次
        List<InterviewRoundEntity> recent;
        if (lastSummarizedCount >= answered.size()) {
            recent = List.of();
        } else {
            recent = answered.subList(lastSummarizedCount, answered.size());
        }
        int recentSize = Math.min(RECENT_HISTORY_LIMIT, recent.size());
        recent =
                recentSize == 0
                        ? List.of()
                        : recent.subList(recent.size() - recentSize, recent.size());
        List<String> recentQuestions =
                recent.stream().map(InterviewRoundEntity::getQuestion).toList();
        List<String> recentAnswers = recent.stream().map(InterviewRoundEntity::getAnswer).toList();

        // RAG 检索参考题目
        List<QuestionSearchResult> ragResults =
                questionRagService.search(position.getJdText(), RAG_TOP_K);
        String ragQuestions = formatRagQuestions(ragResults);

        int currentRound = roundService.countAnswered(sessionId) + 1;

        return new InterviewContext(
                sessionId,
                resume.getCandidateName(),
                position.getTitle(),
                plan,
                currentRound,
                recentQuestions,
                recentAnswers,
                buildResumeSummary(resume),
                ragQuestions,
                InterviewerPersona.fromString(entity.getPersona()),
                runningSummary);
    }

    /** 从 WebSocket URI 路径中提取 sessionId。 */
    private Long extractSessionId(WebSocketSession session) {
        String path = session.getUri().getPath();
        if (path == null) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return null;
        }
        String idStr = path.substring(lastSlash + 1);
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 构建简历摘要：优先使用 parsedJson，否则使用 rawText 截断。 */
    private String buildResumeSummary(ResumeEntity resume) {
        if (resume.getParsedJson() != null && !resume.getParsedJson().isBlank()) {
            return resume.getParsedJson();
        }
        String rawText = resume.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return "未提供";
        }
        if (rawText.length() > RESUME_SUMMARY_MAX) {
            return rawText.substring(0, RESUME_SUMMARY_MAX);
        }
        return rawText;
    }

    /** 格式化 RAG 检索结果为文本。 */
    private String formatRagQuestions(List<QuestionSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "无相关题库参考";
        }
        return results.stream()
                .map(r -> "- [" + r.category() + "/" + r.difficulty() + "] " + r.content())
                .collect(Collectors.joining("\n"));
    }

    /** 获取计划中的总题目数。 */
    private int getTotalRounds(InterviewSessionEntity entity) {
        if (entity.getPlanJson() == null || entity.getPlanJson().isBlank()) {
            return 0;
        }
        try {
            InterviewPlan plan = objectMapper.readValue(entity.getPlanJson(), InterviewPlan.class);
            return plan != null && plan.questions() != null ? plan.questions().size() : 0;
        } catch (JsonProcessingException e) {
            log.warn("反序列化面试计划失败 sessionId={}", entity.getId(), e);
            return 0;
        }
    }

    /** 发送 WebSocket 消息（同步，synchronized 防止并发写入）。 */
    private void send(WebSocketSession session, WsOutbound outbound) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(outbound);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (JsonProcessingException e) {
            log.error("序列化 WebSocket 消息失败", e);
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败", e);
        }
    }
}
