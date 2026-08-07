package com.aims.agent.orchestration.node;

import com.aims.agent.DefaultReportAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.DimensionAggregate;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.report.ReportContext;
import com.aims.core.report.ReportResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 报告生成节点：调用 {@link DefaultReportAgent#generate} 生成综合面试报告。
 *
 * <p>数据流：读 ROUND_EVALUATIONS + RUNNING_SUMMARY + QA_HISTORY → 聚合维度分数 → 构建 ReportContext → 调用
 * ReportAgent → 写 REPORT_RESULT
 *
 * <p>注意：{@link DefaultReportAgent#generate(ReportContext)} 接口方法会抛
 * UnsupportedOperationException，需调用重载方法 {@code generate(ReportContext, DimensionAggregate,
 * double)}。
 *
 * @since 1.1.0
 */
@Component
public class ReportNode extends AbstractNode<InterviewState> {

    private final DefaultReportAgent reportAgent;

    public ReportNode(DefaultReportAgent reportAgent) {
        this.reportAgent = reportAgent;
    }

    @Override
    public String nodeName() {
        return "report";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        // 聚合维度分数
        DimensionAggregate aggregate = aggregateEvaluations(state.roundEvaluations());
        double weightedScore = calculateWeightedScore(aggregate);

        // 构建 EvaluationSummary 列表
        List<ReportContext.EvaluationSummary> summaries =
                state.roundEvaluations().stream()
                        .map(
                                e ->
                                        new ReportContext.EvaluationSummary(
                                                0,
                                                e.dimension().name(),
                                                e.score(),
                                                e.comment(),
                                                e.evidenceQuote()))
                        .toList();

        // 对话摘要 fallback：无 runningSummary 时从 QA_HISTORY 构建
        String convSummary =
                state.runningSummary() != null
                        ? state.runningSummary()
                        : state.qaHistory().stream()
                                .map(qa -> "Q: " + qa.question() + "\nA: " + qa.answer())
                                .collect(Collectors.joining("\n\n"));

        ReportContext ctx =
                new ReportContext(
                        state.sessionId(),
                        state.candidateName(),
                        state.positionTitle(),
                        state.jdText(),
                        state.resumeSummary(),
                        summaries,
                        convSummary);

        log.debug("生成报告 sessionId={} weightedScore={}", state.sessionId(), weightedScore);

        ReportResult result = reportAgent.generate(ctx, aggregate, weightedScore);

        log.info("报告生成完成 sessionId={}", state.sessionId());

        return Map.of(InterviewState.REPORT_RESULT, result);
    }

    /** 聚合 RoundEvaluation 列表为 DimensionAggregate。 */
    private DimensionAggregate aggregateEvaluations(List<RoundEvaluation> evaluations) {
        DimensionAggregate aggregate = new DimensionAggregate();
        for (RoundEvaluation eval : evaluations) {
            aggregate.add(eval.dimension(), eval.score());
        }
        return aggregate;
    }

    /** 计算加权总分。 */
    private double calculateWeightedScore(DimensionAggregate aggregate) {
        double score = 0.0;
        for (EvaluationDimension dim : EvaluationDimension.values()) {
            DimensionAggregate.DimensionScore ds = aggregate.get(dim);
            if (ds != null) {
                score += ds.avgScore() * dim.getWeight();
            }
        }
        return score;
    }
}
