package com.aims.agent.orchestration.node;

import com.aims.agent.SupervisorAgent;
import com.aims.agent.orchestration.graph.NodeNames;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.SupervisorContext;
import com.aims.core.interview.SupervisorDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 总指挥节点：每轮结束后（SUMMARY 之后）评估节奏，产出 {@link SupervisorDecision}。
 *
 * <p>灰度开关关闭时纯透传（返回空 Map，行为等同现状）；调用失败经 {@link DefaultSupervisorAgent} 兜底为 CONTINUE，不阻断面试。
 *
 * @since 1.1.0
 */
@Component
public class SuperviseNode extends AbstractNode<InterviewState> {

    private final SupervisorAgent supervisorAgent;

    @Value("${interview.supervisor.enabled:false}")
    private boolean supervisorEnabled;

    public SuperviseNode(SupervisorAgent supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    @Override
    public String nodeName() {
        return NodeNames.SUPERVISE;
    }

    @Override
    public Map<String, Object> apply(InterviewState state) {
        // 灰度关闭：纯透传，行为等同现状
        if (!supervisorEnabled) {
            return Map.of();
        }

        long elapsedMs = Duration.between(state.sessionStartedAt(), Instant.now()).toMillis();
        SupervisorContext ctx =
                new SupervisorContext(
                        state.sessionId(),
                        state.currentSeq(),
                        state.totalRounds(),
                        answeredMainCount(state),
                        state.answeredCount(),
                        state.followUpCount(),
                        elapsedMs,
                        avgScore(state));

        // 总指挥决策失败不阻断面试：降级为 CONTINUE 正常继续
        SupervisorDecision decision;
        try {
            decision = supervisorAgent.supervise(ctx);
        } catch (Exception e) {
            log.warn(
                    "总指挥决策调用失败，按正常继续 sessionId={} seq={}/{} err={}",
                    state.sessionId(),
                    state.currentSeq(),
                    state.totalRounds(),
                    e.getMessage());
            decision = SupervisorDecision.fallback();
        }
        return Map.of(
                InterviewState.ELAPSED_MS, elapsedMs, InterviewState.SUPERVISOR_DECISION, decision);
    }

    /** 已完成主问题数：过滤追问（followUpIndex 非空）后的 QaPair 数量。 */
    private int answeredMainCount(InterviewState state) {
        return (int) state.qaHistory().stream().filter(q -> !q.isFollowUp()).count();
    }

    /** 聚合已评估轮次的平均分（评估为 Kafka 异步时可能为空）。 */
    private Double avgScore(InterviewState state) {
        List<RoundEvaluation> evals = state.roundEvaluations();
        if (evals == null || evals.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (RoundEvaluation e : evals) {
            sum += e.score();
        }
        return sum / evals.size();
    }
}
