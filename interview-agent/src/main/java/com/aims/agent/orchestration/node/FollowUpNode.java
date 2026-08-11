package com.aims.agent.orchestration.node;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.InterviewPlan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 追问问题生成节点：调用 {@link FollowUpAgent#streamFollowUp} 流式生成追问问题。
 *
 * <p>数据流：读 FOLLOW_UP_DECISION → 构建 FollowUpContext → 流式调用 → 写 CURRENT_QUESTION + QUESTIONS_ASKED +
 * FOLLOW_UP_INDEX + FOLLOW_UP_TYPE + PARENT_SEQ + CURRENT_ANSWER
 *
 * @since 1.1.0
 */
@Component
public class FollowUpNode extends AbstractNode<InterviewState> {

    private final FollowUpAgent followUpAgent;
    private final StreamEmitter streamEmitter;

    public FollowUpNode(FollowUpAgent followUpAgent, StreamEmitter streamEmitter) {
        this.followUpAgent = followUpAgent;
        this.streamEmitter = streamEmitter;
    }

    @Override
    public String nodeName() {
        return "followUp";
    }

    @Override
    public Map<String, Object> apply(InterviewState state) throws Exception {
        FollowUpDecision decision = state.followUpDecision();
        if (decision == null || !decision.shouldFollowUp()) {
            throw new IllegalStateException("FollowUpNode 在 shouldFollowUp=false 时被调用");
        }

        FollowUpContext ctx = buildContext(state);
        Long sessionId = state.sessionId();

        Integer currentIndex = state.followUpIndex();
        int newIndex = currentIndex == null ? 1 : currentIndex + 1;

        log.debug(
                "流式生成追问 sessionId={} seq={} followUpIndex={}",
                sessionId,
                state.currentSeq(),
                newIndex);

        // 通知前端追问开始（携带 followUpType/parentSeq/followUpIndex）
        streamEmitter.emitFollowUpStart(
                sessionId, decision.followUpType(), state.currentSeq(), newIndex);

        // 流式：emit chunks + 累积完整文本
        // sessionId 经 Reactor Context 传播：chunk 在 reactor-netty 线程发出，ThreadLocal 不可用。
        StringBuilder full = new StringBuilder();
        followUpAgent
                .streamFollowUp(ctx, decision)
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

        // 通知前端追问结束
        streamEmitter.emitFollowUpEnd(sessionId, question);

        log.info(
                "追问生成完成 sessionId={} seq={} followUpIndex={}",
                state.sessionId(),
                state.currentSeq(),
                newIndex);

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.CURRENT_QUESTION, question);
        updates.put(InterviewState.QUESTIONS_ASKED, question);
        updates.put(InterviewState.FOLLOW_UP_INDEX, newIndex);
        updates.put(InterviewState.FOLLOW_UP_TYPE, decision.followUpType());
        updates.put(InterviewState.PARENT_SEQ, state.currentSeq());
        updates.put(InterviewState.CURRENT_ANSWER, "");
        // 生成后才计数（路由条件 followUpCount < 3 语义=本题已生成追问数）；标记等待追问回答
        updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
        updates.put(InterviewState.PENDING_FOLLOW_UP, true);
        return updates;
    }

    private FollowUpContext buildContext(InterviewState state) {
        List<String> hints = extractFollowUpHints(state);
        return new FollowUpContext(
                state.sessionId(),
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
    }

    private List<String> extractFollowUpHints(InterviewState state) {
        InterviewPlan plan = state.interviewPlan();
        if (plan == null) return List.of();
        int idx = state.currentSeq() - 1;
        if (idx < 0 || idx >= plan.questions().size()) return List.of();
        return plan.questions().get(idx).followUpHints();
    }
}
