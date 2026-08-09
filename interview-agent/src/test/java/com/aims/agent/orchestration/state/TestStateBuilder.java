package com.aims.agent.orchestration.state;

import com.aims.core.interview.FollowUpDecision;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试用 InterviewState 构建器，提供 fluent API 简化测试状态创建。
 *
 * <p>仅在 {@code src/test/java} 中使用，不属于生产代码。
 *
 * @since 1.1.0
 */
public class TestStateBuilder {

    private final Map<String, Object> data = new HashMap<>();

    public TestStateBuilder withSessionId(Long id) {
        data.put(InterviewState.SESSION_ID, id);
        return this;
    }

    public TestStateBuilder withCandidateName(String name) {
        data.put(InterviewState.CANDIDATE_NAME, name);
        return this;
    }

    public TestStateBuilder withPositionTitle(String title) {
        data.put(InterviewState.POSITION_TITLE, title);
        return this;
    }

    public TestStateBuilder withTotalRounds(int total) {
        data.put(InterviewState.TOTAL_ROUNDS, total);
        return this;
    }

    public TestStateBuilder withCurrentSeq(int seq) {
        data.put(InterviewState.CURRENT_SEQ, seq);
        return this;
    }

    public TestStateBuilder withFollowUpCount(int count) {
        data.put(InterviewState.FOLLOW_UP_COUNT, count);
        return this;
    }

    public TestStateBuilder with(FollowUpDecision decision) {
        data.put(InterviewState.FOLLOW_UP_DECISION, decision);
        return this;
    }

    public TestStateBuilder withLastError(String error) {
        data.put(InterviewState.LAST_ERROR, error);
        return this;
    }

    public TestStateBuilder withForceEnd(boolean forceEnd) {
        data.put(InterviewState.FORCE_END, forceEnd);
        return this;
    }

    public TestStateBuilder with(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public InterviewState build() {
        return new InterviewState(data);
    }

    /** 创建一个空的 TestStateBuilder。 */
    public static TestStateBuilder forTesting() {
        return new TestStateBuilder();
    }
}
