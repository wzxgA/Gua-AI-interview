package com.aims.agent.orchestration.node;

import com.aims.agent.EvaluatorAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.EvaluationContext;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.QaPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 评估节点：调用 {@link EvaluatorAgent#evaluate} 批量评估未评估的 QaPair。
 *
 * <p>数据流：读 QA_HISTORY + EVALUATED_ROUND_IDS → 筛选未评估 → 调用 EvaluatorAgent → 写 ROUND_EVALUATIONS +
 * EVALUATED_ROUND_IDS
 *
 * @since 1.1.0
 */
@Component
public class EvaluateNode extends AbstractNode<InterviewState> {

    private final EvaluatorAgent evaluatorAgent;

    public EvaluateNode(EvaluatorAgent evaluatorAgent) {
        this.evaluatorAgent = evaluatorAgent;
    }

    @Override
    public String nodeName() {
        return "evaluate";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        List<Long> evaluated = state.evaluatedRoundIds();
        List<RoundEvaluation> newEvals = new ArrayList<>();
        List<Long> newIds = new ArrayList<>();

        for (QaPair qa : state.qaHistory()) {
            if (evaluated.contains((long) qa.seq())) continue;

            EvaluationContext ctx =
                    new EvaluationContext(
                            state.sessionId(),
                            null,
                            qa.seq(),
                            null,
                            null,
                            qa.question(),
                            qa.answer(),
                            state.positionTitle(),
                            state.jdText(),
                            state.resumeSummary());

            log.debug("评估轮次 sessionId={} seq={}", state.sessionId(), qa.seq());

            List<RoundEvaluation> evals = evaluatorAgent.evaluate(ctx);
            newEvals.addAll(evals);
            newIds.add((long) qa.seq());
        }

        log.info("批量评估完成 sessionId={} 新评估轮次数={}", state.sessionId(), newIds.size());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.ROUND_EVALUATIONS, newEvals);
        updates.put(InterviewState.EVALUATED_ROUND_IDS, newIds);
        return updates;
    }
}
