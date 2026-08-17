package com.aims.agent.orchestration.graph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.FaultTolerantNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.SummaryNode;
import com.aims.agent.orchestration.node.SuperviseNode;
import com.aims.agent.orchestration.observability.GraphMetricsRegistry;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.SupervisorAction;
import java.util.Map;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 面试流程 StateGraph 工厂。
 *
 * <p>构建完整面试流程的有向图，包含 7 个节点（plan→ask→answer→followUpDecision→summary→endCheck），2
 * 条条件边（追问决策路由、结束判断循环路由），所有业务节点通过 {@link FaultTolerantNode} 包装。
 *
 * <p>评估（evaluate）与报告（report）已移至 Kafka 链路（面试结束后由 {@code EvaluationConsumer}/{@code ReportConsumer} 从
 * DB 统一评估并落库），故不注册在图内。
 *
 * <h2>图拓扑</h2>
 *
 * <pre>
 *   START → plan → ask → answer → followUpDecision
 *                      ↑      ├─ followUp → answer（追问回环，interruptBefore(ANSWER) 暂停等待追问回答）
 *                      │      └─ summary → supervise → endCheck
 *                      │                        ├─ ask (循环回提问)
 *                      └────────────────────────┘
 *                      endCheck ──（达上限/FINISH/supervisor=END/错误）──> END
 * </pre>
 *
 * <h2>条件边路由</h2>
 *
 * <ul>
 *   <li>followUpDecision → followUp（shouldFollowUp 且未达上限）| summary（否则）
 *   <li>endCheck → END（达上限、forceEnd、supervisor=END 或 lastError）| ask（循环）
 * </ul>
 *
 * @since 1.1.0
 */
@Component
public class InterviewGraphFactory {

    private static final Logger log = LoggerFactory.getLogger(InterviewGraphFactory.class);

    /** 每个问题最多追问次数，防止 FollowUpAgent 持续返回 shouldFollowUp 导致死循环。 */
    private static final int MAX_FOLLOW_UPS_PER_QUESTION = 3;

    private final PlanNode planNode;
    private final QuestionNode questionNode;
    private final AnswerNode answerNode;
    private final FollowUpDecisionNode followUpDecisionNode;
    private final FollowUpNode followUpNode;
    private final SummaryNode summaryNode;
    private final SuperviseNode superviseNode;
    private final EndCheckNode endCheckNode;

    /** Phase 6：注入用于 FaultTolerantNode 重试埋点；可为 null（测试场景）。 */
    private final GraphMetricsRegistry metricsRegistry;

    public InterviewGraphFactory(
            PlanNode planNode,
            QuestionNode questionNode,
            AnswerNode answerNode,
            FollowUpDecisionNode followUpDecisionNode,
            FollowUpNode followUpNode,
            SummaryNode summaryNode,
            EndCheckNode endCheckNode) {
        this(
                planNode,
                questionNode,
                answerNode,
                followUpDecisionNode,
                followUpNode,
                summaryNode,
                null,
                endCheckNode,
                null);
    }

    /** Phase 6 兼容构造：未注入 SuperviseNode（测试场景或历史调用）。 */
    public InterviewGraphFactory(
            PlanNode planNode,
            QuestionNode questionNode,
            AnswerNode answerNode,
            FollowUpDecisionNode followUpDecisionNode,
            FollowUpNode followUpNode,
            SummaryNode summaryNode,
            EndCheckNode endCheckNode,
            GraphMetricsRegistry metricsRegistry) {
        this(
                planNode,
                questionNode,
                answerNode,
                followUpDecisionNode,
                followUpNode,
                summaryNode,
                null,
                endCheckNode,
                metricsRegistry);
    }

    /** 完整构造（含 SuperviseNode）：F1 总指挥节点。 */
    @Autowired
    public InterviewGraphFactory(
            PlanNode planNode,
            QuestionNode questionNode,
            AnswerNode answerNode,
            FollowUpDecisionNode followUpDecisionNode,
            FollowUpNode followUpNode,
            SummaryNode summaryNode,
            SuperviseNode superviseNode,
            EndCheckNode endCheckNode,
            GraphMetricsRegistry metricsRegistry) {
        this.planNode = planNode;
        this.questionNode = questionNode;
        this.answerNode = answerNode;
        this.followUpDecisionNode = followUpDecisionNode;
        this.followUpNode = followUpNode;
        this.summaryNode = summaryNode;
        this.superviseNode = superviseNode;
        this.endCheckNode = endCheckNode;
        this.metricsRegistry = metricsRegistry;
    }

    // ─── Graph 构建 ───

    /**
     * 构建未编译的 StateGraph（测试可单独使用）。
     *
     * @return 配置好节点和边的 StateGraph
     * @throws Exception 如果节点或边配置有误
     */
    public StateGraph<InterviewState> buildGraph() throws Exception {
        StateGraph<InterviewState> graph =
                new StateGraph<>(InterviewState.SCHEMA, InterviewState::new);

        // 1. 注册节点（全部用 FaultTolerantNode 包装）
        graph.addNode(NodeNames.PLAN, async(wrap(planNode, 2, 1000)));
        graph.addNode(NodeNames.ASK, async(wrap(questionNode, 2, 2000)));
        graph.addNode(NodeNames.ANSWER, async(wrap(answerNode, 1, 0)));
        graph.addNode(NodeNames.FOLLOW_UP_DECISION, async(wrap(followUpDecisionNode, 3, 500)));
        graph.addNode(NodeNames.FOLLOW_UP, async(wrap(followUpNode, 2, 2000)));
        graph.addNode(NodeNames.SUMMARY, async(wrap(summaryNode, 2, 1000)));
        graph.addNode(NodeNames.END_CHECK, async(wrap(endCheckNode, 1, 0)));
        // F1 总指挥：注入 SuperviseNode 时插入 SUMMARY→SUPERVISE→END_CHECK；否则直连（兼容测试构造器）
        if (superviseNode != null) {
            graph.addNode(NodeNames.SUPERVISE, async(wrap(superviseNode, 2, 1000)));
            graph.addEdge(NodeNames.SUMMARY, NodeNames.SUPERVISE);
            graph.addEdge(NodeNames.SUPERVISE, NodeNames.END_CHECK);
        } else {
            graph.addEdge(NodeNames.SUMMARY, NodeNames.END_CHECK);
        }
        // 评估（evaluate）与报告（report）已移至 Kafka 链路（FE.04），不再注册图内

        // 2. 固定边
        graph.addEdge(START, NodeNames.PLAN);
        graph.addEdge(NodeNames.PLAN, NodeNames.ASK);
        graph.addEdge(NodeNames.ASK, NodeNames.ANSWER);
        graph.addEdge(NodeNames.ANSWER, NodeNames.FOLLOW_UP_DECISION);
        // 追问回环：followUp 生成追问问题后回到 ANSWER 等待候选人回答
        // （interruptBefore(ANSWER) 为节点级中断，ask→answer 与 followUp→answer 两条入边均会暂停）
        graph.addEdge(NodeNames.FOLLOW_UP, NodeNames.ANSWER);
        // 结束路径：endCheck 条件路由到 END（评估/报告由 Kafka 异步完成）

        // 3. 条件边: 追问决策
        EdgeAction<InterviewState> followUpRouter = this::routeAfterFollowUpDecision;
        graph.addConditionalEdges(
                NodeNames.FOLLOW_UP_DECISION,
                AsyncEdgeAction.edge_async(followUpRouter),
                Map.of(
                        NodeNames.FOLLOW_UP, NodeNames.FOLLOW_UP,
                        NodeNames.SUMMARY, NodeNames.SUMMARY));

        // 4. 条件边: 结束判断
        EdgeAction<InterviewState> endCheckRouter = this::routeAfterEndCheck;
        graph.addConditionalEdges(
                NodeNames.END_CHECK,
                AsyncEdgeAction.edge_async(endCheckRouter),
                Map.of(END, END, NodeNames.ASK, NodeNames.ASK));

        return graph;
    }

    /**
     * 无 Checkpointer 编译（Phase 3 测试用）。
     *
     * <p>设置 recursionLimit=100，因为 8 轮面试 × ~6 节点/轮 ≈ 48 次迭代，超过默认 25。
     *
     * @return 编译后的 CompiledGraph
     * @throws Exception 如果图配置有误
     */
    public CompiledGraph<InterviewState> compileWithoutCheckpoint() throws Exception {
        CompileConfig config = CompileConfig.builder().recursionLimit(100).build();
        return buildGraph().compile(config);
    }

    /**
     * 带 Checkpointer 编译（Phase 4+ 使用）。
     *
     * @param checkpointer Redis Checkpointer
     * @return 编译后的 CompiledGraph
     * @throws Exception 如果图配置有误
     */
    public CompiledGraph<InterviewState> compile(BaseCheckpointSaver checkpointer)
            throws Exception {
        CompileConfig config =
                CompileConfig.builder().checkpointSaver(checkpointer).recursionLimit(100).build();
        return buildGraph().compile(config);
    }

    /**
     * 带 Checkpointer + interruptBefore(ANSWER) 编译（Phase 5 Engine 使用）。
     *
     * <p>Graph 执行到 ASK 节点后，会在进入 ANSWER 之前暂停，{@code invoke} 返回空 Optional。 调用方需通过 {@link
     * CompiledGraph#stateOf(RunnableConfig)} 读取 next 节点，确认暂停位置。 提交回答时把 CURRENT_ANSWER 注入 state，再次
     * invoke 即从 ANSWER 继续。
     *
     * @param checkpointer Redis Checkpointer（可为 null，用于纯验证测试）
     * @return 编译后的 CompiledGraph
     * @throws Exception 如果图配置有误
     */
    public CompiledGraph<InterviewState> compileWithInterruptBeforeAnswer(
            BaseCheckpointSaver checkpointer) throws Exception {
        CompileConfig.Builder builder =
                CompileConfig.builder().recursionLimit(100).interruptBefore(NodeNames.ANSWER);
        if (checkpointer != null) {
            builder.checkpointSaver(checkpointer);
        }
        return buildGraph().compile(builder.build());
    }

    // ─── 条件边路由方法（package-private，可独立单元测试）───

    /**
     * 追问决策后的条件边路由。
     *
     * <p>路由规则：
     *
     * <ol>
     *   <li>lastError 非空 → SUMMARY（错误时跳过追问，走摘要）
     *   <li>decision 非空且 shouldFollowUp 且 followUpCount &lt; 3 → FOLLOW_UP
     *   <li>否则 → SUMMARY
     * </ol>
     *
     * @param state 当前状态
     * @return 目标节点名称
     */
    String routeAfterFollowUpDecision(InterviewState state) throws Exception {
        if (state.lastError() != null) {
            return NodeNames.SUMMARY;
        }
        FollowUpDecision decision = state.followUpDecision();
        if (decision != null
                && decision.shouldFollowUp()
                && state.followUpCount() < MAX_FOLLOW_UPS_PER_QUESTION) {
            return NodeNames.FOLLOW_UP;
        }
        return NodeNames.SUMMARY;
    }

    /**
     * 结束判断后的条件边路由。
     *
     * <p>路由规则：
     *
     * <ol>
     *   <li>lastError 非空 → END（错误终止，评估/报告由 Kafka 链路完成）
     *   <li>forceEnd=true → END（外部 FINISH 强制结束）
     *   <li>currentSeq &gt;= totalRounds → END（面试结束）
     *   <li>否则 → ASK（循环回提问）
     * </ol>
     *
     * @param state 当前状态
     * @return 目标节点名称
     */
    String routeAfterEndCheck(InterviewState state) throws Exception {
        if (state.lastError() != null) {
            return END;
        }
        if (state.forceEnd()) {
            return END;
        }
        // P5 防御：totalRounds<=0 视为配置错误（正常面试至少 1 题），打日志后走 END，
        // 避免 checkpoint 恢复/残留 state 下 currentSeq>=totalRounds 恒真导致 0 题评估
        if (state.totalRounds() <= 0) {
            log.error(
                    "totalRounds<=0 异常，按配置错误终止 sessionId={} seq={}",
                    state.sessionId(),
                    state.currentSeq());
            return END;
        }
        if (state.currentSeq() >= state.totalRounds()) {
            return END;
        }
        // F1 总指挥：未达上限但总指挥判定超时严重 → 提前结束
        if (state.supervisorDecision() != null
                && state.supervisorDecision().action() == SupervisorAction.END) {
            log.info(
                    "总指挥判定提前结束 sessionId={} seq={}/{} reason={}",
                    state.sessionId(),
                    state.currentSeq(),
                    state.totalRounds(),
                    state.supervisorDecision().reason());
            return END;
        }
        return NodeNames.ASK;
    }

    // ─── FaultTolerantNode 包装 ───

    private NodeAction<InterviewState> wrap(
            NodeAction<InterviewState> node, int retries, long delayMs) {
        return new FaultTolerantNode<>(node, retries, delayMs, metricsRegistry);
    }

    /** 将同步 NodeAction 适配为 LangGraph4j 要求的 AsyncNodeAction。 */
    private AsyncNodeAction<InterviewState> async(NodeAction<InterviewState> node) {
        return AsyncNodeAction.node_async(node);
    }
}
