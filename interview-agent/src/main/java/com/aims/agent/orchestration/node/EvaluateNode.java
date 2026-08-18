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
            long key = evalKey(qa);
            if (evaluated.contains(key)) continue;

            // 追问的 QaPair.seq 存的是主问题 seq（AnswerNode 用 currentSeq 构造）：
            // parentSeq 补传 qa.seq()、followUpIndex 补传 qa.followUpIndex()，评估 prompt 才能显示
            // Q{parentSeq}.{followUpIndex}
            Integer followUpIndex = qa.followUpIndex();
            // v1.1-F4：按轮取矛盾点（key=主问题 seq 或 "seq:followUpIndex"）
            List<com.aims.core.interview.ConflictDetail> roundConflicts =
                    state.conflictDetailsByRound()
                            .getOrDefault(
                                    followUpIndex != null
                                            ? qa.seq() + ":" + followUpIndex
                                            : String.valueOf(qa.seq()),
                                    List.of());
            EvaluationContext ctx =
                    new EvaluationContext(
                            state.sessionId(),
                            null,
                            qa.seq(),
                            followUpIndex != null ? qa.seq() : null,
                            followUpIndex,
                            qa.question(),
                            qa.answer(),
                            state.positionTitle(),
                            state.jdText(),
                            state.resumeSummary(),
                            roundConflicts);

            log.debug("评估轮次 sessionId={} seq={} key={}", state.sessionId(), qa.seq(), key);

            List<RoundEvaluation> evals = evaluatorAgent.evaluate(ctx);
            newEvals.addAll(evals);
            newIds.add(key);
        }

        log.info("批量评估完成 sessionId={} 新评估轮次数={}", state.sessionId(), newIds.size());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.ROUND_EVALUATIONS, newEvals);
        updates.put(InterviewState.EVALUATED_ROUND_IDS, newIds);
        return updates;
    }

    /** 评估去重键：主问题用 seq；追问编码为 seq*100+followUpIndex（追问 ≤3 次，与主问题 seq 无碰撞）。 */
    private long evalKey(QaPair qa) {
        return qa.followUpIndex() == null ? qa.seq() : qa.seq() * 100L + qa.followUpIndex();
    }
}
