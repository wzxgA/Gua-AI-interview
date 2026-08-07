package com.aims.agent.orchestration.node;

import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.QaPair;
import com.aims.core.interview.SummaryContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 摘要节点：调用 {@link SummaryAgent#summarize} 对未摘要的 QaPair 生成滚动摘要。
 *
 * <p>数据流：读 QA_HISTORY + LAST_SUMMARIZED_SEQ → 筛选 seq > lastSummarizedSeq → 不足 5 条跳过 → 调用
 * SummaryAgent → 写 RUNNING_SUMMARY + LAST_SUMMARIZED_SEQ
 *
 * @since 1.1.0
 */
@Component
public class SummaryNode extends AbstractNode<InterviewState> {

    private static final int SUMMARY_THRESHOLD = 5;

    private final SummaryAgent summaryAgent;

    public SummaryNode(SummaryAgent summaryAgent) {
        this.summaryAgent = summaryAgent;
    }

    @Override
    public String nodeName() {
        return "summary";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        List<QaPair> toSummarize =
                state.qaHistory().stream()
                        .filter(qa -> qa.seq() > state.lastSummarizedSeq())
                        .toList();

        if (toSummarize.size() < SUMMARY_THRESHOLD) {
            log.debug("摘要未达阈值 sessionId={} 待摘要={}", state.sessionId(), toSummarize.size());
            return Map.of();
        }

        SummaryContext ctx =
                new SummaryContext(
                        state.sessionId(),
                        state.positionTitle(),
                        state.runningSummary(),
                        toSummarize,
                        state.lastSummarizedSeq());

        log.debug("生成摘要 sessionId={} 待摘要={}", state.sessionId(), toSummarize.size());

        String newSummary = summaryAgent.summarize(ctx);
        int newLastSeq = toSummarize.get(toSummarize.size() - 1).seq();

        log.info("摘要完成 sessionId={} lastSummarizedSeq={}", state.sessionId(), newLastSeq);

        return Map.of(
                InterviewState.RUNNING_SUMMARY, newSummary,
                InterviewState.LAST_SUMMARIZED_SEQ, newLastSeq);
    }
}
