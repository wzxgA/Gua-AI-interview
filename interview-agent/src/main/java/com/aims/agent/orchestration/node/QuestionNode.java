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
 * <p>数据流：读 State 元数据 -> 构建 InterviewContext -> 流式调用 -> emit chunks + 累积完整文本 -> 写 CURRENT_QUESTION +
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

    /** F1 总指挥：上一轮判定 TIGHTEN 时，本轮提问要求收敛话题。 */
    private boolean isTighten(InterviewState state) {
        return state.supervisorDecision() != null
                && state.supervisorDecision().action()
                        == com.aims.core.interview.SupervisorAction.TIGHTEN;
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
                        state.runningSummary(),
                        isTighten(state));

        Long sessionId = state.sessionId();

        log.debug("流式生成问题 sessionId={} seq={}", sessionId, nextSeq);

        // 通知前端问题开始
        streamEmitter.emitStart(sessionId, nextSeq);

        // 流式：emit chunks + 累积完整文本
        // sessionId 经 Reactor Context 传播：chunk 在 reactor-netty 线程发出，ThreadLocal 不可用，
        // contextWrite 写入的 sessionId 由 transformDeferredContextual 在订阅时读取（跨线程安全）。
        StringBuilder full = new StringBuilder();
        interviewerAgent
                .streamQuestion(ctx)
                .transformDeferredContextual(
                        (flux, ctxView) -> {
                            Long sid =
                                    ctxView.getOrDefault(StreamEmitter.SESSION_CONTEXT_KEY, null);
                            return flux.doOnNext(
                                    chunk -> {
                                        streamEmitter.emit(sid, chunk);
                                        full.append(chunk);
                                    });
                        })
                .contextWrite(
                        context ->
                                sessionId != null
                                        ? context.put(StreamEmitter.SESSION_CONTEXT_KEY, sessionId)
                                        : context)
                .blockLast();

        String question = full.toString().trim();

        // 通知前端问题结束
        streamEmitter.emitEnd(sessionId, question);

        log.info("问题生成完成 sessionId={} seq={} len={}", sessionId, nextSeq, question.length());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.CURRENT_QUESTION, question);
        updates.put(InterviewState.CURRENT_SEQ, nextSeq);
        updates.put(InterviewState.QUESTIONS_ASKED, question);
        updates.put(InterviewState.CURRENT_ANSWER, "");
        // 换题时重置追问上下文：计数、暂停标记、序号、类型、父序号全部清零（否则跨题残留导致追问序号跳号）
        updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
        updates.put(InterviewState.PENDING_FOLLOW_UP, false);
        updates.put(InterviewState.FOLLOW_UP_INDEX, null);
        updates.put(InterviewState.FOLLOW_UP_TYPE, com.aims.core.interview.FollowUpType.NONE);
        updates.put(InterviewState.PARENT_SEQ, null);
        return updates;
    }
}
