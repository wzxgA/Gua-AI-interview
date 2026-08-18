package com.aims.agent.orchestration.node;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.ConflictDetail;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.InterviewPlan;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 追问决策节点：调用 {@link FollowUpAgent#evaluate} 判断是否需要追问。
 *
 * <p>数据流：读当前 Q&A → 构建 FollowUpContext → 调用 FollowUpAgent → 写 FOLLOW_UP_DECISION + FOLLOW_UP_COUNT
 *
 * @since 1.1.0
 */
@Component
public class FollowUpDecisionNode extends AbstractNode<InterviewState> {

    private final FollowUpAgent followUpAgent;

    public FollowUpDecisionNode(FollowUpAgent followUpAgent) {
        this.followUpAgent = followUpAgent;
    }

    @Override
    public String nodeName() {
        return "followUpDecision";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        List<String> hints = extractFollowUpHints(state);

        FollowUpContext ctx =
                new FollowUpContext(
                        state.sessionId(),
                        state.resumeId(),
                        state.currentRoundId(),
                        state.currentQuestion(),
                        state.currentAnswer(),
                        state.candidateName(),
                        state.positionTitle(),
                        state.jdText(),
                        state.resumeSummary(),
                        hints,
                        state.questionsAsked(),
                        state.persona());

        log.debug("追问决策 sessionId={} seq={}", state.sessionId(), state.currentSeq());

        FollowUpDecision decision = followUpAgent.evaluate(ctx);

        log.info(
                "追问决策完成 sessionId={} shouldFollowUp={} type={} conflicts={}",
                state.sessionId(),
                decision.shouldFollowUp(),
                decision.followUpType(),
                decision.conflictDetails().size());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.FOLLOW_UP_DECISION, decision);
        // F4：本回答轮矛盾点写入状态（key=主问题 seq 或 "seq:followUpIndex"），供 syncFromState 落库与评估/报告引用
        if (!decision.conflictDetails().isEmpty()) {
            String key =
                    state.followUpIndex() != null
                            ? state.currentSeq() + ":" + state.followUpIndex()
                            : String.valueOf(state.currentSeq());
            Map<String, List<ConflictDetail>> merged =
                    new LinkedHashMap<>(state.conflictDetailsByRound());
            merged.put(key, decision.conflictDetails());
            updates.put(InterviewState.CONFLICT_DETAILS_BY_ROUND, merged);
        }
        return updates;
    }

    /** 从 plan 按 seq 提取 followUpHints。 */
    private List<String> extractFollowUpHints(InterviewState state) {
        InterviewPlan plan = state.interviewPlan();
        if (plan == null) return List.of();
        int idx = state.currentSeq() - 1;
        if (idx < 0 || idx >= plan.questions().size()) return List.of();
        return plan.questions().get(idx).followUpHints();
    }
}
