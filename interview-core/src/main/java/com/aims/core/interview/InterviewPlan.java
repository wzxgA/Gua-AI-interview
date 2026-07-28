package com.aims.core.interview;

import java.util.List;

/** 结构化面试计划。 */
public record InterviewPlan(
        String candidateName,
        String position,
        List<PlanSection> sections,
        List<PlannedQuestion> questions,
        int estimatedMinutes,
        String version) {

    public static final int MIN_QUESTIONS = 8;
    public static final int MAX_QUESTIONS = 10;

    public InterviewPlan {
        if (candidateName == null || candidateName.isBlank()) {
            throw new IllegalArgumentException("候选人名称不能为空");
        }
        if (position == null || position.isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        sections = sections == null ? List.of() : List.copyOf(sections);
        questions = questions == null ? List.of() : List.copyOf(questions);
        if (questions.size() < MIN_QUESTIONS || questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("面试计划题目数必须在 8~10 题之间");
        }
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("面试计划至少需要一个模块");
        }
        int sectionQuestionCount = sections.stream().mapToInt(PlanSection::questionCount).sum();
        if (sectionQuestionCount != questions.size()) {
            throw new IllegalArgumentException("计划模块题目数之和必须等于计划题目数");
        }
        if (estimatedMinutes <= 0) {
            throw new IllegalArgumentException("预计面试时长必须大于 0 分钟");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("计划版本不能为空");
        }
    }

    public void validate() {
        new InterviewPlan(candidateName, position, sections, questions, estimatedMinutes, version);
    }
}
