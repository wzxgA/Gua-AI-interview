package com.aims.agent.orchestration.node;

import com.aims.agent.InterviewPlanGenerator;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.InterviewPlan;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 面试计划生成节点：调用 {@link InterviewPlanGenerator} 生成结构化面试计划。
 *
 * <p>数据流：读会话元数据 → 调用 PlanGenerator → 写 INTERVIEW_PLAN + TOTAL_ROUNDS
 *
 * @since 1.1.0
 */
@Component
public class PlanNode extends AbstractNode<InterviewState> {

    private final InterviewPlanGenerator planGenerator;

    public PlanNode(InterviewPlanGenerator planGenerator) {
        this.planGenerator = planGenerator;
    }

    @Override
    public String nodeName() {
        return "plan";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        log.debug("生成面试计划 sessionId={}", state.sessionId());

        InterviewPlan plan =
                planGenerator.generate(
                        state.candidateName(),
                        state.positionTitle(),
                        state.jdText(),
                        state.resumeSummary(),
                        state.ragQuestions(),
                        InterviewPlan.DEFAULT_QUESTION_COUNT,
                        "BALANCED",
                        45);

        log.info("面试计划生成完成 sessionId={} questions={}", state.sessionId(), plan.questions().size());

        return Map.of(
                InterviewState.INTERVIEW_PLAN,
                plan,
                InterviewState.TOTAL_ROUNDS,
                plan.questions().size());
    }
}
