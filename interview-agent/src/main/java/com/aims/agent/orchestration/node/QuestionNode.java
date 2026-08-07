package com.aims.agent.orchestration.node;

import com.aims.agent.InterviewContext;
import com.aims.agent.InterviewerAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.QaPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 问题生成节点：调用 {@link InterviewerAgent#streamQuestion} 流式生成面试问题。
 *
 * <p>数据流：读 State 元数据 → 构建 InterviewContext → 流式调用 → emit chunks + 累积完整文本 → 写 CURRENT_QUESTION +
 * CURRENT_SEQ + QUESTIONS_ASKED + CURRENT_ANSWER
 *
 * <p>流式适配：通过 {@link StreamEmitter} 推送 chunk 到 WebSocket，同时用 StringBuilder 累积完整文本。
 *
 * @since 1.1.0
 */
@Component
public class QuestionNode extends AbstractNode<InterviewState> {

    private final InterviewerAgent interviewerAgent;
    private final StreamEmitter streamEmitter;

    public QuestionNode(InterviewerAgent interviewerAgent, StreamEmitter streamEmitter) {
        this.interviewerAgent = interviewerAgent;
        this.streamEmitter = streamEmitter;
    }

    @Override
    public String nodeName() {
        return "ask";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        int nextSeq = state.currentSeq() + 1;

        List<String> recentQuestions = state.qaHistory().stream().map(QaPair::question).toList();
        List<String> recentAnswers = state.qaHistory().stream().map(QaPair::answer).toList();

        InterviewContext ctx =
                new InterviewContext(
                        state.sessionId(),
                        state.candidateName(),
                        state.positionTitle(),
                        state.interviewPlan(),
                        nextSeq,
                        recentQuestions,
                        recentAnswers,
                        state.resumeSummary(),
                        state.ragQuestions(),
                        state.persona(),
                        state.runningSummary());

        log.debug("流式生成问题 sessionId={} seq={}", state.sessionId(), nextSeq);

        // 流式：emit chunks + 累积完整文本
        StringBuilder full = new StringBuilder();
        interviewerAgent
                .streamQuestion(ctx)
                .doOnNext(
                        chunk -> {
                            streamEmitter.emit(chunk);
                            full.append(chunk);
                        })
                .blockLast();

        String question = full.toString().trim();
        log.info(
                "问题生成完成 sessionId={} seq={} len={}", state.sessionId(), nextSeq, question.length());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.CURRENT_QUESTION, question);
        updates.put(InterviewState.CURRENT_SEQ, nextSeq);
        updates.put(InterviewState.QUESTIONS_ASKED, question);
        updates.put(InterviewState.CURRENT_ANSWER, "");
        return updates;
    }
}
