package com.aims.core.session;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 面试会话状态。
 *
 * <p>状态机合法迁移表：
 *
 * <ul>
 *   <li>CREATED -&gt; PLANNING, CANCELLED, FAILED
 *   <li>PLANNING -&gt; IN_PROGRESS, FAILED, CANCELLED
 *   <li>IN_PROGRESS -&gt; PAUSED, COMPLETED, EVALUATING, CANCELLED, FAILED
 *   <li>PAUSED -&gt; IN_PROGRESS, COMPLETED, CANCELLED
 *   <li>EVALUATING -&gt; REPORTING, IN_PROGRESS
 *   <li>REPORTING -&gt; COMPLETED, FAILED
 *   <li>COMPLETED / CANCELLED / FAILED 为终态，无后续迁移
 * </ul>
 */
public enum SessionStatus {
    CREATED,
    PLANNING,
    IN_PROGRESS,
    EVALUATING,
    REPORTING,
    COMPLETED,
    PAUSED,
    CANCELLED,
    FAILED;

    /** 各状态的合法后续迁移集合；未列入的终态默认无后续迁移。 */
    private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<SessionStatus, Set<SessionStatus>> map = new EnumMap<>(SessionStatus.class);
        map.put(CREATED, EnumSet.of(PLANNING, CANCELLED, FAILED));
        map.put(PLANNING, EnumSet.of(IN_PROGRESS, FAILED, CANCELLED));
        map.put(IN_PROGRESS, EnumSet.of(PAUSED, COMPLETED, EVALUATING, CANCELLED, FAILED));
        map.put(PAUSED, EnumSet.of(IN_PROGRESS, COMPLETED, CANCELLED));
        map.put(EVALUATING, EnumSet.of(REPORTING, IN_PROGRESS));
        map.put(REPORTING, EnumSet.of(COMPLETED, FAILED));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * 判断当前状态是否可以迁移到目标状态。
     *
     * @param target 目标状态
     * @return 合法迁移返回 true，否则 false
     */
    public boolean canTransitionTo(SessionStatus target) {
        Set<SessionStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return !ALLOWED_TRANSITIONS.containsKey(this);
    }

    /**
     * 获取当前状态的合法后续迁移集合。
     *
     * @return 不可变集合，终态返回空集合
     */
    public Set<SessionStatus> allowedTransitions() {
        Set<SessionStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed == null ? Set.of() : allowed;
    }
}
