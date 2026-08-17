package com.aims.core.interview;

import java.io.Serializable;

/**
 * 面试总指挥决策结果。
 *
 * @param action 动作建议（CONTINUE / TIGHTEN / END）
 * @param reason 决策理由（一句话）
 * @param suggestedRemaining 建议剩余题数（TIGHTEN 时给出，null=不调整）
 * @param hardStop true=立即结束（超时严重时；一期路由仅按 END 处理）
 */
public record SupervisorDecision(
        SupervisorAction action, String reason, Integer suggestedRemaining, boolean hardStop)
        implements Serializable { // 与 interview-core 领域对象序列化惯例保持一致

    /** 决策不可用时兜底：正常继续。 */
    public static SupervisorDecision fallback() {
        return new SupervisorDecision(SupervisorAction.CONTINUE, "决策不可用，按正常继续", null, false);
    }
}
