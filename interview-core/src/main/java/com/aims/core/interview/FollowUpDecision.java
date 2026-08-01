package com.aims.core.interview;

/** 追问决策结果。 */
public record FollowUpDecision(
        boolean shouldFollowUp, FollowUpType followUpType, String followUpQuestion, String reason) {

    public static FollowUpDecision noFollowUp(String reason) {
        return new FollowUpDecision(false, FollowUpType.NONE, null, reason);
    }

    public static FollowUpDecision of(FollowUpType type, String question, String reason) {
        return new FollowUpDecision(true, type, question, reason);
    }
}
