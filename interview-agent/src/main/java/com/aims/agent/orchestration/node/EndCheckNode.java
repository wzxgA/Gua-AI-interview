package com.aims.agent.orchestration.node;

import com.aims.agent.orchestration.graph.NodeNames;
import com.aims.agent.orchestration.state.InterviewState;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结束判断透传节点。
 *
 * <p>纯逻辑节点，不调用任何 Agent，不修改状态。仅作为条件边的显式判断点，使 {@code endCheck → {report | ask}} 路由逻辑清晰可测。
 *
 * @since 1.1.0
 */
@Component
public class EndCheckNode extends AbstractNode<InterviewState> {

    @Override
    public String nodeName() {
        return NodeNames.END_CHECK;
    }

    @Override
    public Map<String, Object> apply(InterviewState state) {
        log.debug(
                "[{}] session={} seq={}/{}",
                nodeName(),
                state.sessionId(),
                state.currentSeq(),
                state.totalRounds());
        return Map.of();
    }
}
