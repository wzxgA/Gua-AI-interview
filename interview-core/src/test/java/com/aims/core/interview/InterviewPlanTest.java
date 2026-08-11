package com.aims.core.interview;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link InterviewPlan} 结构化校验测试。 */
class InterviewPlanTest {

    private static PlanSection section(String name, int count) {
        return new PlanSection(name, count, "考察目标");
    }

    private static PlannedQuestion question(String id) {
        return new PlannedQuestion(id, "topic", "EASY", List.of("hint"), "evaluationFocus");
    }

    private static List<PlannedQuestion> questions(int n) {
        return IntStream.range(0, n).mapToObj(i -> question("q" + i)).toList();
    }

    @Test
    void validPlanWith8Questions() {
        var plan =
                new InterviewPlan(
                        "张三", "Java 后端", List.of(section("基础", 8)), questions(8), 60, "1.0");
        org.junit.jupiter.api.Assertions.assertEquals(8, plan.questions().size());
    }

    @Test
    void validPlanWith10Questions() {
        var plan =
                new InterviewPlan(
                        "张三",
                        "Java 后端",
                        List.of(section("基础", 5), section("项目", 5)),
                        questions(10),
                        90,
                        "1.0");
        org.junit.jupiter.api.Assertions.assertEquals(10, plan.questions().size());
    }

    @Test
    void rejectLessThanMinQuestions() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new InterviewPlan(
                                "张三",
                                "Java 后端",
                                List.of(section("基础", 0)),
                                questions(0),
                                60,
                                "1.0"),
                "应拒绝少于 1 题");
    }

    @Test
    void rejectMoreThan10Questions() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new InterviewPlan(
                                "张三",
                                "Java 后端",
                                List.of(section("基础", 11)),
                                questions(11),
                                60,
                                "1.0"),
                "应拒绝超过 10 题");
    }

    @Test
    void rejectEmptySections() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewPlan("张三", "Java 后端", List.of(), questions(8), 60, "1.0"),
                "应拒绝空模块");
    }

    @Test
    void rejectMismatchedSectionQuestionCount() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new InterviewPlan(
                                "张三",
                                "Java 后端",
                                List.of(section("基础", 5)),
                                questions(8),
                                60,
                                "1.0"),
                "应拒绝模块题目数不匹配");
    }

    @Test
    void rejectBlankCandidateName() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new InterviewPlan(
                                "", "Java 后端", List.of(section("基础", 8)), questions(8), 60, "1.0"));
    }

    @Test
    void rejectZeroEstimatedMinutes() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new InterviewPlan(
                                "张三",
                                "Java 后端",
                                List.of(section("基础", 8)),
                                questions(8),
                                0,
                                "1.0"));
    }
}
