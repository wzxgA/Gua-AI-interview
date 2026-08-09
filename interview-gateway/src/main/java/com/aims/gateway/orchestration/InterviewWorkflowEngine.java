package com.aims.gateway.orchestration;

import com.aims.agent.orchestration.checkpoint.RedisCheckpointSaver;
import com.aims.agent.orchestration.graph.InterviewGraphFactory;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.session.SessionStatus;
import com.aims.gateway.ws.WebSocketStreamEmitter;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 面试编排引擎：封装 {@link CompiledGraph} 的执行、暂停、恢复。
 *
 * <p>Phase 5 核心：替代 {@code InterviewWebSocketHandler} 中命令式主循环，用 Graph 声明式编排。
 *
 * <p>使用 {@code interruptBefore(ANSWER)} 实现提问后暂停——Graph 执行到 ASK 节点后， 在进入 ANSWER 之前自动暂停， 等待外部 {@link
 * #submitAnswer} 注入回答后 resume 继续。
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

    /** Phase 5 灰度开关：true 时 Handler 委托 Engine，false 时走旧命令式路径。 */
    @Value("${interview.engine.enabled:false}")
    private boolean engineEnabled;

    private CompiledGraph<InterviewState> compiledGraph;

    public InterviewWorkflowEngine(
            InterviewGraphFactory graphFactory,
            RedisCheckpointSaver checkpointSaver,
            StatePersistenceService statePersistenceService,
            com.aims.infra.persistence.service.InterviewSessionService sessionService,
            WebSocketStreamEmitter streamEmitter) {
        this.graphFactory = graphFactory;
        this.checkpointSaver = checkpointSaver;
        this.statePersistenceService = statePersistenceService;
        this.sessionService = sessionService;
        this.streamEmitter = streamEmitter;
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

        InterviewState initial = statePersistenceService.buildInitialState(sessionId);
        RunnableConfig config = newConfig(sessionId);

        // 绑定 sessionId 到 StreamEmitter，让 Node 的 emit chunk 能推到 WS
        streamEmitter.bindSession(sessionId);
        try {
            compiledGraph.invoke(initial.data(), config);
        } finally {
            streamEmitter.unbindSession();
        }

        // Graph 暂停后，从 checkpoint 加载最新 state，同步到 DB
        InterviewState pausedState = loadStateFromCheckpoint(config);
        if (pausedState != null) {
            statePersistenceService.syncFromState(sessionId, pausedState);
            log.info(
                    "Engine 启动面试完成 sessionId={} seq={} questionLen={}",
                    sessionId,
                    pausedState.currentSeq(),
                    pausedState.currentQuestion() != null
                            ? pausedState.currentQuestion().length()
                            : 0);
        }
        sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);
    }

    /**
     * 提交回答：注入 answer → resume → 执行到下一个 ASK 或 END。
     *
     * <p>从 checkpoint 加载暂停时的 state，注入 CURRENT_ANSWER，再次 invoke 让 Graph 从 ANSWER 节点继续。 执行完成后会暂停在下一个
     * ANSWER 前（若未结束）或执行到 END。
     *
     * @param sessionId 面试 sessionId
     * @param answer 候选人回答文本
     * @throws Exception Graph 执行异常
     */
    public void submitAnswer(Long sessionId, String answer) throws Exception {
        log.info("Engine 提交回答 sessionId={} answerLen={}", sessionId, answer.length());

        RunnableConfig config = newConfig(sessionId);
        InterviewState pausedState = loadStateFromCheckpoint(config);
        if (pausedState == null) {
            throw new IllegalStateException("无 checkpoint，无法提交回答: sessionId=" + sessionId);
        }

        // 注入 answer：构造新的 inputs map，含原 state + CURRENT_ANSWER
        Map<String, Object> inputs = new java.util.HashMap<>(pausedState.data());
        inputs.put(InterviewState.CURRENT_ANSWER, answer);

        streamEmitter.bindSession(sessionId);
        try {
            compiledGraph.invoke(inputs, config);
        } finally {
            streamEmitter.unbindSession();
        }

        InterviewState newState = loadStateFromCheckpoint(config);
        if (newState != null) {
            statePersistenceService.syncFromState(sessionId, newState);
            log.info(
                    "Engine 提交回答完成 sessionId={} seq={} qaCount={}",
                    sessionId,
                    newState.currentSeq(),
                    newState.qaHistory().size());
            if (newState.sessionStatus() == SessionStatus.COMPLETED) {
                sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);
                sessionService.markEnded(sessionId);
            }
        }
    }

    /**
     * 结束面试：设置 FORCE_END → 执行到 END。
     *
     * <p>从 checkpoint 加载 state，注入 FORCE_END=true，用无 interrupt 的 graph 执行到 END（直接生成报告）。
     *
     * @param sessionId 面试 sessionId
     * @throws Exception Graph 执行异常
     */
    public void finishInterview(Long sessionId) throws Exception {
        log.info("Engine 结束面试 sessionId={}", sessionId);

        RunnableConfig config = newConfig(sessionId);
        InterviewState pausedState = loadStateFromCheckpoint(config);
        if (pausedState == null) {
            throw new IllegalStateException("无 checkpoint，无法结束: sessionId=" + sessionId);
        }

        Map<String, Object> inputs = new java.util.HashMap<>(pausedState.data());
        inputs.put(InterviewState.FORCE_END, true);

        // 用无 interrupt 的 graph 执行到 END
        CompiledGraph<InterviewState> noInterrupt = graphFactory.compile(checkpointSaver);
        streamEmitter.bindSession(sessionId);
        try {
            noInterrupt.invoke(inputs, config);
        } finally {
            streamEmitter.unbindSession();
        }

        InterviewState finalState = loadStateFromCheckpoint(config);
        if (finalState != null) {
            statePersistenceService.syncFromState(sessionId, finalState);
        }
        sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);
        sessionService.markEnded(sessionId);
    }

    /**
     * 暂停面试：仅更新 DB 状态，Graph 暂停由 interruptBefore 自动完成。
     *
     * @param sessionId 面试 sessionId
     */
    public void pauseInterview(Long sessionId) {
        log.info("Engine 暂停面试 sessionId={}", sessionId);
        sessionService.updateStatus(sessionId, SessionStatus.PAUSED);
    }

    /**
     * 取消面试：释放 Redis checkpoint + 更新 DB 状态。
     *
     * @param sessionId 面试 sessionId
     * @throws Exception checkpoint 释放异常
     */
    public void cancelInterview(Long sessionId) throws Exception {
        log.info("Engine 取消面试 sessionId={}", sessionId);
        RunnableConfig config = newConfig(sessionId);
        try {
            checkpointSaver.release(config);
        } catch (Exception e) {
            log.warn("释放 checkpoint 失败 sessionId={}", sessionId, e);
        }
        sessionService.updateStatus(sessionId, SessionStatus.CANCELLED);
        sessionService.markEnded(sessionId);
    }

    /** Engine 是否启用（Phase 5 灰度开关）。 */
    public boolean isEnabled() {
        return engineEnabled;
    }

    /** 初始化：编译带 interruptBefore(ANSWER) + Redis Checkpointer 的 Graph。 */
    @org.springframework.beans.factory.annotation.Autowired
    public void setCompiledGraph() throws Exception {
        this.compiledGraph = graphFactory.compileWithInterruptBeforeAnswer(checkpointSaver);
    }

    // ─── 内部方法 ───

    private RunnableConfig newConfig(Long sessionId) {
        return RunnableConfig.builder().threadId(sessionId.toString()).build();
    }

    /**
     * 从 checkpoint 加载最新 InterviewState。
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
        return new InterviewState(checkpoint.get().getState());
    }
}
