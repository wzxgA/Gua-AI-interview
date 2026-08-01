package com.aims.gateway.ws;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewContext;
import com.aims.agent.InterviewerAgent;
import com.aims.ai.memory.ConversationMemory;
import com.aims.core.common.ErrorCode;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.session.SessionStatus;
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
            ObjectMapper objectMapper) {
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
            generateAndSendQuestion(session, sessionId);
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
                sessionStore.forceUnlock(sessionId);
                sessionService.updateStatus(sessionId, SessionStatus.EVALUATING);
                sessionService.updateEvaluationStatus(sessionId, "PENDING");
                evaluationMessageProducer.sendEvaluationRequest(sessionId);
                send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
            } else {
                // 补发下一题
                log.info("重连后补发下一题 sessionId={} answeredCount={}", sessionId, answeredCount);
                generateAndSendQuestion(session, sessionId);
            }
        } else {
            // 补发未回答的当前问题
            log.info("重连后补发当前未回答问题 sessionId={} roundId={}", sessionId, lastRound.getId());
            send(
                    session,
                    WsOutbound.questionStart(sessionId, lastRound.getId(), lastRound.getSeq()));
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

    /** ANSWER：接收回答，更新轮次，决定是否生成下一题或结束。 */
    private void handleAnswer(WebSocketSession session, Long sessionId, String text) {
        // 校验状态
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
        // 写入会话记忆
        conversationMemory.addUser(sessionId.toString(), text, currentRound.getId().toString());
        // 发送确认
        send(session, WsOutbound.answerAck(sessionId, currentRound.getId()));
        log.info("收到回答 sessionId={} roundId={}", sessionId, currentRound.getId());

        // 检查是否达到题目上限
        int answeredCount = roundService.countAnswered(sessionId);
        int totalRounds = getTotalRounds(entity);
        if (totalRounds > 0 && answeredCount >= totalRounds) {
            // 释放连接锁，进入评估流程
            sessionStore.forceUnlock(sessionId);
            sessionService.updateStatus(sessionId, SessionStatus.EVALUATING);
            sessionService.updateEvaluationStatus(sessionId, "PENDING");
            evaluationMessageProducer.sendEvaluationRequest(sessionId);
            send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
            log.info("达到题数上限，进入评估流程 sessionId={} answeredCount={}", sessionId, answeredCount);
        } else {
            // 确定主问题 seq（如果当前是追问，取其 parentSeq；否则取当前 seq）
            int parentSeq =
                    currentRound.getParentSeq() != null
                            ? currentRound.getParentSeq()
                            : currentRound.getSeq();
            // 检查是否需要追问
            int followUpCount = roundService.countFollowUps(sessionId, parentSeq);
            if (followUpCount < MAX_FOLLOW_UPS) {
                FollowUpContext followUpContext =
                        buildFollowUpContext(sessionId, entity, currentRound);
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
                            session, sessionId, parentSeq, followUpContext, decision);
                    return;
                }
            }
            // 不追问或追问已达上限，生成下一题
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
        sessionService.updateStatus(sessionId, SessionStatus.PAUSED);
        send(session, WsOutbound.status(sessionId, SessionStatus.PAUSED.name()));
        log.info("会话暂停 sessionId={}", sessionId);
    }

    /** FINISH：结束会话，触发评估流程。 */
    private void handleFinish(WebSocketSession session, Long sessionId) {
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
        // 释放连接锁
        sessionStore.forceUnlock(sessionId);
        // 状态转为 EVALUATING，触发评估
        sessionService.updateStatus(sessionId, SessionStatus.EVALUATING);
        sessionService.updateEvaluationStatus(sessionId, "PENDING");
        evaluationMessageProducer.sendEvaluationRequest(sessionId);
        // 通知前端进入评估状态
        send(session, WsOutbound.status(sessionId, SessionStatus.EVALUATING.name()));
        log.info("面试结束，进入评估流程 sessionId={}", sessionId);
    }

    /** CANCEL：取消会话。 */
    private void handleCancel(WebSocketSession session, Long sessionId) {
        sessionService.updateStatus(sessionId, SessionStatus.CANCELLED);
        sessionService.markEnded(sessionId);
        sessionStore.forceUnlock(sessionId);
        send(session, WsOutbound.status(sessionId, SessionStatus.CANCELLED.name()));
        log.info("会话取消 sessionId={}", sessionId);
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
    }

    /** 流式生成追问问题并发送给客户端。 */
    private void generateAndSendFollowUp(
            WebSocketSession session,
            Long sessionId,
            int parentSeq,
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

        // 创建追问轮次记录（seq 递增，parentSeq 指向主问题）
        int seq = roundService.maxSeq(sessionId) + 1;
        InterviewRoundEntity round =
                roundService.createRound(
                        sessionId, seq, fullQuestion, decision.followUpType().name(), parentSeq);

        // 发送 QUESTION_START（携带 followUpType）
        send(
                session,
                WsOutbound.questionStart(
                        sessionId, round.getId(), seq, decision.followUpType().name()));

        // 逐 chunk 发送
        for (String chunk : chunks) {
            send(session, WsOutbound.questionChunk(sessionId, round.getId(), chunk));
        }

        // 发送 QUESTION_END
        send(session, WsOutbound.questionEnd(sessionId, round.getId(), seq, fullQuestion));

        // 写入会话记忆
        conversationMemory.addAssistant(
                sessionId.toString(), fullQuestion, round.getId().toString());
        log.info(
                "生成追问完成 sessionId={} roundId={} seq={} parentSeq={} followUpType={}",
                sessionId,
                round.getId(),
                seq,
                parentSeq,
                decision.followUpType());
    }

    // ==================== 辅助方法 ====================

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
                    // 找到与当前 seq 对应的题目（seq 从 1 开始）
                    int questionIdx =
                            Math.min(currentRound.getSeq() - 1, plan.questions().size() - 1);
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
                recentQuestions);
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

        // 提取最近的问答
        int recentSize = Math.min(RECENT_HISTORY_LIMIT, answered.size());
        List<InterviewRoundEntity> recent =
                recentSize == 0
                        ? List.of()
                        : answered.subList(answered.size() - recentSize, answered.size());
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
                ragQuestions);
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
