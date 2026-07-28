package com.aims.core.interview;

import java.util.List;

/** 面试计划中的一道题。 */
public record PlannedQuestion(
        String questionId,
        String topic,
        String difficulty,
        List<String> followUpHints,
        String evaluationFocus) {

    public PlannedQuestion {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("计划题目 ID 不能为空");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("计划题目主题不能为空");
        }
        if (difficulty == null || difficulty.isBlank()) {
            throw new IllegalArgumentException("计划题目难度不能为空");
        }
        if (evaluationFocus == null || evaluationFocus.isBlank()) {
            throw new IllegalArgumentException("计划题目评价重点不能为空");
        }
        followUpHints = followUpHints == null ? List.of() : List.copyOf(followUpHints);
    }
}
