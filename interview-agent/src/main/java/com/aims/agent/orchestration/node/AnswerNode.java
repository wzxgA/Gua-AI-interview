package com.aims.agent.orchestration.node;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.QaPair;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 回答接收节点：无 Agent 调用，将候选人回答追加到 QA_HISTORY。
 *
 * <p>数据流：读 CURRENT_ANSWER + CURRENT_SEQ + CURRENT_QUESTION → 构造 QaPair → 写 QA_HISTORY
 *
 * <p>Engine 层在调用 Graph 前将候选人回答注入 CURRENT_ANSWER。
 *
 * @since 1.1.0
 */
@Component
public class AnswerNode extends AbstractNode<InterviewState> {

    @Override
    public String nodeName() {
        return "answer";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        String answer = state.currentAnswer();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("AnswerNode 收到空回答");
        }

        QaPair qaPair = new QaPair(state.currentSeq(), state.currentQuestion(), answer);
        log.debug("接收回答 sessionId={} seq={}", state.sessionId(), state.currentSeq());

        return Map.of(InterviewState.QA_HISTORY, qaPair);
    }
}
