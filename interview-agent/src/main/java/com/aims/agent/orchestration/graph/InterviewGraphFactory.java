package com.aims.agent.orchestration.graph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.EvaluateNode;
import com.aims.agent.orchestration.node.FaultTolerantNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.ReportNode;
import com.aims.agent.orchestration.node.SummaryNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpDecision;
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
import org.springframework.stereotype.Component;

/**
 * 面试流程 StateGraph 工厂。
 *
 * <p>构建完整面试流程的有向图，包含 9 个节点（plan→ask→answer→followUpDecision→…→report）， 2
 * 条条件边（追问决策路由、结束判断循环路由），所有业务节点通过 {@link FaultTolerantNode} 包装。
 *
 * <h2>图拓扑</h2>
 *
 * <pre>
 *   START → plan → ask → answer → followUpDecision
 *                                     ├─ followUp → evaluate (固定边)
 *                                     └─ evaluate → summary → endCheck
 *                                          ├─ ask (循环回提问)
 *                                          └─ report → END
 * </pre>
 *
 * <h2>条件边路由</h2>
 *
 * <ul>
 *   <li>followUpDecision → followUp（shouldFollowUp 且未达上限）| evaluate（否则）
 *   <li>endCheck → report（达上限或有错误）| ask（循环）
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
    private final EvaluateNode evaluateNode;
    private final SummaryNode summaryNode;
    private final EndCheckNode endCheckNode;
    private final ReportNode reportNode;

    public InterviewGraphFactory(
            PlanNode planNode,
            QuestionNode questionNode,
            AnswerNode answerNode,
            FollowUpDecisionNode followUpDecisionNode,
            FollowUpNode followUpNode,
            EvaluateNode evaluateNode,
            SummaryNode summaryNode,
            EndCheckNode endCheckNode,
            ReportNode reportNode) {
        this.planNode = planNode;
        this.questionNode = questionNode;
        this.answerNode = answerNode;
        this.followUpDecisionNode = followUpDecisionNode;
        this.followUpNode = followUpNode;
        this.evaluateNode = evaluateNode;
        this.summaryNode = summaryNode;
        this.endCheckNode = endCheckNode;
        this.reportNode = reportNode;
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
        graph.addNode(NodeNames.EVALUATE, async(wrap(evaluateNode, 2, 3000)));
        graph.addNode(NodeNames.SUMMARY, async(wrap(summaryNode, 2, 1000)));
        graph.addNode(NodeNames.END_CHECK, async(wrap(endCheckNode, 1, 0)));
        graph.addNode(NodeNames.REPORT, async(wrap(reportNode, 2, 5000)));

        // 2. 固定边
        graph.addEdge(START, NodeNames.PLAN);
        graph.addEdge(NodeNames.PLAN, NodeNames.ASK);
        graph.addEdge(NodeNames.ASK, NodeNames.ANSWER);
        graph.addEdge(NodeNames.ANSWER, NodeNames.FOLLOW_UP_DECISION);
        graph.addEdge(NodeNames.FOLLOW_UP, NodeNames.EVALUATE);
        graph.addEdge(NodeNames.EVALUATE, NodeNames.SUMMARY);
        graph.addEdge(NodeNames.SUMMARY, NodeNames.END_CHECK);
        graph.addEdge(NodeNames.REPORT, END);

        // 3. 条件边: 追问决策
        EdgeAction<InterviewState> followUpRouter = this::routeAfterFollowUpDecision;
        graph.addConditionalEdges(
                NodeNames.FOLLOW_UP_DECISION,
                AsyncEdgeAction.edge_async(followUpRouter),
                Map.of(
                        NodeNames.FOLLOW_UP, NodeNames.FOLLOW_UP,
                        NodeNames.EVALUATE, NodeNames.EVALUATE));

        // 4. 条件边: 结束判断
        EdgeAction<InterviewState> endCheckRouter = this::routeAfterEndCheck;
        graph.addConditionalEdges(
                NodeNames.END_CHECK,
                AsyncEdgeAction.edge_async(endCheckRouter),
                Map.of(
                        NodeNames.REPORT, NodeNames.REPORT,
                        NodeNames.ASK, NodeNames.ASK));

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

    // ─── 条件边路由方法（package-private，可独立单元测试）───

    /**
     * 追问决策后的条件边路由。
     *
     * <p>路由规则：
     *
     * <ol>
     *   <li>lastError 非空 → EVALUATE（错误时跳过追问，直接评估）
     *   <li>decision 非空且 shouldFollowUp 且 followUpCount &lt; 3 → FOLLOW_UP
     *   <li>否则 → EVALUATE
     * </ol>
     *
     * @param state 当前状态
     * @return 目标节点名称
     */
    String routeAfterFollowUpDecision(InterviewState state) throws Exception {
        if (state.lastError() != null) {
            return NodeNames.EVALUATE;
        }
        FollowUpDecision decision = state.followUpDecision();
        if (decision != null
                && decision.shouldFollowUp()
                && state.followUpCount() < MAX_FOLLOW_UPS_PER_QUESTION) {
            return NodeNames.FOLLOW_UP;
        }
        return NodeNames.EVALUATE;
    }

    /**
     * 结束判断后的条件边路由。
     *
     * <p>路由规则：
     *
     * <ol>
     *   <li>lastError 非空 → REPORT（错误时终止，生成报告）
     *   <li>currentSeq &gt;= totalRounds → REPORT（面试结束）
     *   <li>否则 → ASK（循环回提问）
     * </ol>
     *
     * @param state 当前状态
     * @return 目标节点名称
     */
    String routeAfterEndCheck(InterviewState state) throws Exception {
        if (state.lastError() != null) {
            return NodeNames.REPORT;
        }
        if (state.currentSeq() >= state.totalRounds()) {
            return NodeNames.REPORT;
        }
        return NodeNames.ASK;
    }

    // ─── FaultTolerantNode 包装 ───

    private NodeAction<InterviewState> wrap(
            NodeAction<InterviewState> node, int retries, long delayMs) {
        return new FaultTolerantNode<>(node, retries, delayMs);
    }

    /** 将同步 NodeAction 适配为 LangGraph4j 要求的 AsyncNodeAction。 */
    private AsyncNodeAction<InterviewState> async(NodeAction<InterviewState> node) {
        return AsyncNodeAction.node_async(node);
    }
}
