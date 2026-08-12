package com.aims.gateway.orchestration;

import com.aims.agent.orchestration.checkpoint.RedisCheckpointSaver;
import com.aims.agent.orchestration.graph.InterviewGraphFactory;
import com.aims.agent.orchestration.observability.GraphExecutionEvent;
import com.aims.agent.orchestration.observability.GraphMetricsRegistry;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.session.SessionStatus;
import com.aims.gateway.ws.WebSocketStreamEmitter;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.InterviewSessionStore;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 面试编排引擎：封装 {@link CompiledGraph} 的执行、暂停、恢复。
 *
 * <p>Phase 5 核心：替代 {@code InterviewWebSocketHandler} 中命令式主循环，用 Graph 声明式编排。
 *
 * <p>使用 {@code interruptBefore(ANSWER)} 实现提问后暂停——Graph 执行到 ASK（或追问回环的 FOLLOW_UP）节点后， 在进入 ANSWER
 * 之前自动暂停，等待外部 {@link #submitAnswer} 注入回答后 resume 继续。
 *
 * <p><b>恢复机制（重要）</b>：resume 必须使用 {@link GraphInput#resume(java.util.Map)}——它会从 checkpoint 重建执行上下文
 * （nextNodeId=中断点后继节点），把更新数据合并进 checkpoint state 后从中断节点继续。 切勿使用 {@code invoke(Map, config)}：非空 Map
 * 会被包装为 {@code GraphInput.args}， 语义是<b>从 START 重新执行</b>（仅合并 checkpoint state），会导致 plan/ask
 * 重跑、注入的回答被 QuestionNode 清空。
 *
 * <p>5 个公开方法：
 *
 * <ul>
 *   <li>{@link #startInterview} — 构建 initial state → 执行到 ASK → 暂停在 ANSWER 前
 *   <li>{@link #submitAnswer} — 注入 answer → resume → 执行到下一个 ASK 或 END
 *   <li>{@link #finishInterview} — 注入 FORCE_END → 执行到 END
 *   <li>{@link #pauseInterview} — 更新 DB 状态为 PAUSED
 *   <li>{@link #cancelInterview} — 释放 checkpoint + 更新 DB 状态为 CANCELLED
 * </ul>
 *
 * @since 1.1.0 Phase 5
 */
@Component
public class InterviewWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(InterviewWorkflowEngine.class);

    private final InterviewGraphFactory graphFactory;
    private final RedisCheckpointSaver checkpointSaver;
    private final StatePersistenceService statePersistenceService;
    private final com.aims.infra.persistence.service.InterviewSessionService sessionService;
    private final WebSocketStreamEmitter streamEmitter;

    /** Phase 6：指标埋点（可为 null，兼容无 metrics 上下文）。 */
    private final GraphMetricsRegistry metrics;

    /** Phase 6：事件发布器，用于发布 CheckpointRestored 等事件。 */
    private final ApplicationEventPublisher eventPublisher;

    /** FE.04：面试结束触发 Kafka 异步评估（评估/报告由 EvaluationConsumer/ReportConsumer 完成）。 */
    private final EvaluationMessageProducer evaluationMessageProducer;

    /** FE.04：连接锁管理，评估开始时释放（与旧链路 triggerEvaluation 语义一致）。 */
    private final InterviewSessionStore sessionStore;

    /** Phase 5 灰度开关：true 时 Handler 委托 Engine，false 时走旧命令式路径。 */
    @Value("${interview.engine.enabled:false}")
    private boolean engineEnabled;

    private CompiledGraph<InterviewState> compiledGraph;

    @Autowired
    public InterviewWorkflowEngine(
            InterviewGraphFactory graphFactory,
            RedisCheckpointSaver checkpointSaver,
            StatePersistenceService statePersistenceService,
            com.aims.infra.persistence.service.InterviewSessionService sessionService,
            WebSocketStreamEmitter streamEmitter,
            GraphMetricsRegistry metrics,
            ApplicationEventPublisher eventPublisher,
            EvaluationMessageProducer evaluationMessageProducer,
            InterviewSessionStore sessionStore) {
        this.graphFactory = graphFactory;
        this.checkpointSaver = checkpointSaver;
        this.statePersistenceService = statePersistenceService;
        this.sessionService = sessionService;
        this.streamEmitter = streamEmitter;
        this.metrics = metrics;
        this.eventPublisher = eventPublisher;
        this.evaluationMessageProducer = evaluationMessageProducer;
        this.sessionStore = sessionStore;
    }

    /** 测试用：无 metrics/事件发布器/Producer/SessionStore（保持与已有测试兼容）。 */
    InterviewWorkflowEngine(
            InterviewGraphFactory graphFactory,
            RedisCheckpointSaver checkpointSaver,
            StatePersistenceService statePersistenceService,
            com.aims.infra.persistence.service.InterviewSessionService sessionService,
            WebSocketStreamEmitter streamEmitter) {
        this(
                graphFactory,
                checkpointSaver,
                statePersistenceService,
                sessionService,
                streamEmitter,
                null,
                null,
                null,
                null);
    }

    /**
     * 启动面试：构建初始 State → 执行 Graph 到 ASK → 暂停在 ANSWER 前。
     *
     * <p>Graph 执行 plan → ask 节点后，因 interruptBefore(ANSWER) 暂停。 State 通过 checkpoint 持久化到
     * Redis，syncFromState 把 question 同步到 DB。
     *
     * @param sessionId 面试 sessionId
     * @throws Exception Graph 执行异常
     */
    public void startInterview(Long sessionId) throws Exception {
        log.info("Engine 启动面试 sessionId={}", sessionId);
        incrementExecution("startInterview", "invoked");

        InterviewState initial = statePersistenceService.buildInitialState(sessionId);
        RunnableConfig config = newConfig(sessionId);

        // sessionId 由各 Node 从 State 读取并经 Reactor Context 传播到流式 chunk，无需线程绑定
        compiledGraph.invoke(initial.data(), config);

        // Graph 暂停后，从 checkpoint 加载最新 state，同步到 DB
        InterviewState pausedState = loadStateFromCheckpoint(config);
        if (pausedState != null) {
            statePersistenceService.syncFromState(sessionId, pausedState);
            updateRoundGauge(pausedState);
            log.info(
                    "Engine 启动面试完成 sessionId={} seq={} questionLen={}",
                    sessionId,
                    pausedState.currentSeq(),
                    pausedState.currentQuestion() != null
                            ? pausedState.currentQuestion().length()
                            : 0);
        }
        // 幂等转移：REST /start 可能已将状态改为 IN_PROGRESS，tryTransitionTo 不会重复转移
        sessionService.tryTransitionTo(
                sessionId,
                SessionStatus.IN_PROGRESS,
                SessionStatus.PLANNING,
                SessionStatus.PLANNING);
        incrementExecution("startInterview", "success");
    }

    /**
     * 提交回答：注入 answer → resume → 执行到下一个 ASK 或 END。
     *
     * <p>用 {@link GraphInput#resume(Map)} 把 CURRENT_ANSWER 合并进 checkpoint state，Graph 从中断点（ANSWER
     * 节点）继续。 执行完成后会暂停在下一个 ANSWER 前（主问题或追问）或执行到 END。
     *
     * @param sessionId 面试 sessionId
     * @param answer 候选人回答文本
     * @throws Exception Graph 执行异常
     */
    public void submitAnswer(Long sessionId, String answer) throws Exception {
        log.info("Engine 提交回答 sessionId={} answerLen={}", sessionId, answer.length());
        incrementExecution("submitAnswer", "invoked");

        RunnableConfig config = newConfig(sessionId);
        InterviewState pausedState = loadStateFromCheckpoint(config);
        if (pausedState == null) {
            incrementExecution("submitAnswer", "error");
            throw new IllegalStateException("无 checkpoint，无法提交回答: sessionId=" + sessionId);
        }

        // 从 checkpoint 断点恢复：answer 经 resume 更新数据合并进 checkpoint state，由 ANSWER 节点消费。
        // 不可用 invoke(Map)——那是 GraphArgs 语义，会从 START 重跑并丢失回答。
        compiledGraph.invoke(
                GraphInput.resume(Map.of(InterviewState.CURRENT_ANSWER, answer)), config);

        InterviewState newState = loadStateFromCheckpoint(config);
        if (newState != null) {
            statePersistenceService.syncFromState(sessionId, newState);
            updateRoundGauge(newState);
            log.info(
                    "Engine 提交回答完成 sessionId={} seq={} qaCount={}",
                    sessionId,
                    newState.currentSeq(),
                    newState.qaHistory().size());
            // 面试结束（图走到 END，而非"下一题已生成"）→ 触发 Kafka 异步评估（FE.04）
            if (isInterviewFinished(sessionId)) {
                triggerEvaluationViaEngine(sessionId);
            }
        }
        incrementExecution("submitAnswer", "success");
    }

    /**
     * 结束面试：设置 FORCE_END → 执行到 END。
     *
     * <p>用 {@link GraphInput#resume(Map)} 把 FORCE_END=true 合并进 checkpoint state，用无 interrupt 的
     * graph 从中断点继续执行到 END（生成报告）。暂停点无回答时 AnswerNode 检测到 forceEnd 会跳过 QA 收集。
     *
     * @param sessionId 面试 sessionId
     * @throws Exception Graph 执行异常
     */
    public void finishInterview(Long sessionId) throws Exception {
        log.info("Engine 结束面试 sessionId={}", sessionId);
        incrementExecution("finishInterview", "invoked");

        RunnableConfig config = newConfig(sessionId);
        InterviewState pausedState = loadStateFromCheckpoint(config);
        if (pausedState == null) {
            incrementExecution("finishInterview", "error");
            throw new IllegalStateException("无 checkpoint，无法结束: sessionId=" + sessionId);
        }

        // 用无 interrupt 的 graph 从 checkpoint 断点恢复执行到 END
        CompiledGraph<InterviewState> noInterrupt = graphFactory.compile(checkpointSaver);
        noInterrupt.invoke(GraphInput.resume(Map.of(InterviewState.FORCE_END, true)), config);

        InterviewState finalState = loadStateFromCheckpoint(config);
        if (finalState != null) {
            statePersistenceService.syncFromState(sessionId, finalState);
            updateRoundGauge(finalState);
        }
        // 转 EVALUATING + 发 Kafka，异步评估/报告（COMPLETED 由 ReportConsumer 设置，FE.04）
        triggerEvaluationViaEngine(sessionId);
        incrementExecution("finishInterview", "success");
    }

    /**
     * 暂停面试：仅更新 DB 状态，Graph 暂停由 interruptBefore 自动完成。
     *
     * @param sessionId 面试 sessionId
     */
    public void pauseInterview(Long sessionId) {
        log.info("Engine 暂停面试 sessionId={}", sessionId);
        incrementExecution("pauseInterview", "invoked");
        sessionService.updateStatus(sessionId, SessionStatus.PAUSED);
        incrementExecution("pauseInterview", "success");
    }

    /**
     * 取消面试：释放 Redis checkpoint + 更新 DB 状态。
     *
     * @param sessionId 面试 sessionId
     * @throws Exception checkpoint 释放异常
     */
    public void cancelInterview(Long sessionId) throws Exception {
        log.info("Engine 取消面试 sessionId={}", sessionId);
        incrementExecution("cancelInterview", "invoked");
        releaseCheckpoint(sessionId);
        sessionService.updateStatus(sessionId, SessionStatus.CANCELLED);
        sessionService.markEnded(sessionId);
        incrementExecution("cancelInterview", "success");
    }

    /** Engine 是否启用（Phase 5 灰度开关）。 */
    public boolean isEnabled() {
        return engineEnabled;
    }

    /** 重连恢复结果，供 Handler 决定后续动作（FE.06 P2）。 */
    public enum ResumeResult {
        /** 从 DB 重建了 state（checkpoint 不存在时的容错）。 */
        REBUILT_FROM_DB,
        /** 面试已结束，已触发评估。 */
        FINISHED,
        /** 正常恢复，暂停在 ANSWER 前，需补发当前问题。 */
        RESUMED
    }

    /**
     * 重连恢复（FE.06 P2）：从 checkpoint 加载状态，决定补发当前题、触发评估或从 DB 重建。
     *
     * <p>Engine 启用时，Handler 断线重连（DB rounds 非空）统一委托本方法，以 checkpoint 为唯一进度来源， 避免旧链路 命令式生成导致 DB 与
     * checkpoint 脱节。三种情况：
     *
     * <ul>
     *   <li>checkpoint 不存在（Redis 过期/清理/start 执行一半失败）-&gt; 降级 {@link
     *       StatePersistenceService#rebuildFromDb} 重建 state，invoke 重跑写入新 checkpoint
     *   <li>checkpoint 存在且 nextNodeId == END -&gt; 面试已结束，触发评估（幂等）
     *   <li>checkpoint 存在且暂停在 ANSWER 前 -&gt; 正常恢复，由 Handler 从 DB 补发当前待答题
     * </ul>
     *
     * @param sessionId 面试 sessionId
     * @return 恢复结果（Handler 据此决定后续动作）
     * @throws Exception Graph 执行异常
     */
    public ResumeResult resumeInterview(Long sessionId) throws Exception {
        log.info("Engine 重连恢复 sessionId={}", sessionId);
        incrementExecution("resumeInterview", "invoked");

        RunnableConfig config = newConfig(sessionId);
        InterviewState state = loadStateFromCheckpoint(config);

        // 1. checkpoint 不存在 -> 从 DB 重建并重跑 Graph 写入新 checkpoint（容错）
        if (state == null) {
            log.warn("重连时 checkpoint 不存在，从 DB 重建 sessionId={}", sessionId);
            state = statePersistenceService.rebuildFromDb(sessionId);
            // 用重建 state 作为 GraphArgs 从 START 执行：PLAN 幂等跳过（plan 已存在）、ASK 生成下一题，
            // interruptBefore(ANSWER) 暂停后写入新 checkpoint（syncFromState 按 seq/followUpIndex 幂等落库）
            compiledGraph.invoke(state.data(), config);
            InterviewState resumedState = loadStateFromCheckpoint(config);
            if (resumedState != null) {
                statePersistenceService.syncFromState(sessionId, resumedState);
                updateRoundGauge(resumedState);
            }
            incrementExecution("resumeInterview", "success");
            return ResumeResult.REBUILT_FROM_DB;
        }

        // 2. 面试已结束（checkpoint nextNodeId == END）-> 触发评估（tryTransitionTo 保证幂等）
        if (isInterviewFinished(sessionId)) {
            log.info("重连时发现面试已结束，触发评估 sessionId={}", sessionId);
            triggerEvaluationViaEngine(sessionId);
            incrementExecution("resumeInterview", "success");
            return ResumeResult.FINISHED;
        }

        // 3. 正常暂停态 -> 由 Handler 从 DB 补发当前待答题
        log.info("重连恢复，暂停在 ANSWER 前 sessionId={} seq={}", sessionId, state.currentSeq());
        incrementExecution("resumeInterview", "success");
        return ResumeResult.RESUMED;
    }

    /**
     * 面试是否已结束：checkpoint 的 nextNodeId 为 END（图真正走到 END）。
     *
     * <p>不能用 {@code currentSeq >= totalRounds} 判定——QuestionNode 生成下一题时会把 CURRENT_SEQ 提前递增 （endCheck
     * 路由用旧值判定 ASK 正确，但 resume 返回后 Engine 读到的 currentSeq 已是新题序号）， 导致"最后一题已生成、尚未回答"时误判结束并提前触发评估。
     */
    private boolean isInterviewFinished(Long sessionId) {
        RunnableConfig config = newConfig(sessionId);
        try {
            Optional<Checkpoint> cp = checkpointSaver.get(config);
            return cp.map(c -> GraphDefinition.END.equals(c.getNextNodeId())).orElse(false);
        } catch (Exception e) {
            log.warn("检查 checkpoint 结束状态失败 sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 面试结束 → 转 EVALUATING + 释放连接锁 + 发 Kafka 评估请求（FE.04）。
     *
     * <p>评估/报告由 {@code EvaluationConsumer}/{@code ReportConsumer} 异步完成并落库，本方法同步返回。
     * 状态原子转移保证只触发一次；测试构造下 producer 为 null 时仅转状态不发送。
     */
    private void triggerEvaluationViaEngine(Long sessionId) {
        boolean transitioned =
                sessionService.tryTransitionTo(
                        sessionId,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED);
        if (!transitioned) {
            log.info("评估已触发，跳过重复请求 sessionId={}", sessionId);
            return;
        }
        if (sessionStore != null) {
            sessionStore.forceUnlock(sessionId);
        }
        sessionService.updateEvaluationStatus(sessionId, "PENDING");
        if (evaluationMessageProducer != null) {
            evaluationMessageProducer.sendEvaluationRequest(sessionId);
        } else {
            log.warn("evaluationMessageProducer 未注入，跳过 Kafka 发送 sessionId={}", sessionId);
        }
        // P3：首次触发评估即会话结束，checkpoint 不再需要，释放避免 Redis 残留
        releaseCheckpoint(sessionId);
        incrementExecution("evaluation", "triggered");
    }

    /** Phase 6：CompiledGraph 是否已就绪（供健康检查）。 */
    public boolean isGraphReady() {
        return compiledGraph != null;
    }

    /** Phase 6：暴露 checkpointSaver 是否可用（供健康检查）。 */
    public boolean isCheckpointBackendAvailable() {
        return checkpointSaver != null;
    }

    /** 初始化：编译带 interruptBefore(ANSWER) + Redis Checkpointer 的 Graph。 */
    @org.springframework.beans.factory.annotation.Autowired
    public void setCompiledGraph() throws Exception {
        this.compiledGraph = graphFactory.compileWithInterruptBeforeAnswer(checkpointSaver);
    }

    // ─── 内部方法 ───

    /**
     * 释放 Redis checkpoint（P3，容错：释放失败仅告警，不影响业务状态转移）。
     *
     * <p>面试结束（图走到 END 并触发评估）与取消面试后调用，避免 Redis 残留。
     */
    private void releaseCheckpoint(Long sessionId) {
        RunnableConfig config = newConfig(sessionId);
        try {
            checkpointSaver.release(config);
            log.info("已释放 checkpoint sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("释放 checkpoint 失败 sessionId={}", sessionId, e);
        }
    }

    private RunnableConfig newConfig(Long sessionId) {
        return RunnableConfig.builder().threadId(sessionId.toString()).build();
    }

    /**
     * 从 checkpoint 加载最新 InterviewState。
     *
     * <p>Phase 6：加载到非空 state 时发布 {@link GraphExecutionEvent.CheckpointRestored} 事件，由 {@code
     * CheckpointRestoreListener} 触发 {@code aims.graph.checkpoint.restore} 计数。
     *
     * @param config RunnableConfig（含 threadId）
     * @return 最新 state；无 checkpoint 时返回 null
     * @throws Exception checkpoint 读取异常
     */
    private InterviewState loadStateFromCheckpoint(RunnableConfig config) throws Exception {
        Optional<Checkpoint> checkpoint = checkpointSaver.get(config);
        if (checkpoint.isEmpty()) {
            return null;
        }
        InterviewState state = new InterviewState(checkpoint.get().getState());
        publishCheckpointRestored(config);
        return state;
    }

    /** Phase 6：发布 checkpoint 恢复事件（null-guarded）。 */
    private void publishCheckpointRestored(RunnableConfig config) {
        if (eventPublisher == null) {
            return;
        }
        String sessionId = config.threadId().orElse("-");
        eventPublisher.publishEvent(
                new GraphExecutionEvent.CheckpointRestored(sessionId, "checkpoint", Instant.now()));
    }

    /** Phase 6：累加 Engine 入口指标（null-guarded）。 */
    private void incrementExecution(String entrypoint, String outcome) {
        if (metrics != null) {
            metrics.incrementExecution(entrypoint, outcome);
        }
    }

    /** Phase 6：更新当前轮次 Gauge（null-guarded）。 */
    private void updateRoundGauge(InterviewState state) {
        if (metrics != null && state != null) {
            metrics.updateCurrentRound(state.currentSeq(), state.totalRounds());
        }
    }
}
