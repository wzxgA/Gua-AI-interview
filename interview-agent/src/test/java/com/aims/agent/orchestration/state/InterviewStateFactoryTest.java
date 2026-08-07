package com.aims.agent.orchestration.state;

import static org.junit.jupiter.api.Assertions.*;

import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.session.SessionStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link InterviewStateFactory} 测试：验证从持久化数据到状态的正确映射。 */
class InterviewStateFactoryTest {

    private final InterviewStateFactory factory = new InterviewStateFactory();

    private static PlannedQuestion question(String id) {
        return new PlannedQuestion(id, "topic", "EASY", List.of("hint"), "evaluationFocus");
    }

    private static InterviewPlan plan(int questionCount) {
        List<PlannedQuestion> questions =
                java.util.stream.IntStream.range(0, questionCount)
                        .mapToObj(i -> question("q" + i))
                        .toList();
        return new InterviewPlan(
                "张三",
                "Java 后端",
                List.of(new PlanSection("基础", questionCount, "考察目标")),
                questions,
                60,
                "1.0");
    }

    @Test
    @DisplayName("新会话（无轮次）：status=CREATED, qaHistory=空")
    void create_newSession() {
        var state =
                factory.create(
                        1L, "FRIENDLY", "CREATED", "张三", "Java 后端", "JD", "简历摘要", null, List.of());

        assertEquals(1L, state.sessionId());
        assertEquals(SessionStatus.CREATED, state.sessionStatus());
        assertEquals("张三", state.candidateName());
        assertTrue(state.qaHistory().isEmpty());
        assertTrue(state.questionsAsked().isEmpty());
        assertNull(state.currentRoundId());
    }

    @Test
    @DisplayName("有计划时 totalRounds = plan.questions().size()")
    void create_withPlan() {
        var plan = plan(8);
        var state =
                factory.create(
                        1L, "FRIENDLY", "PLANNING", "张三", "Java", "JD", "简历", plan, List.of());

        assertEquals(8, state.totalRounds());
        assertSame(plan, state.interviewPlan());
    }

    @Test
    @DisplayName("有轮次时 qaHistory 和 questionsAsked 正确填充")
    void create_withRounds() {
        var rounds =
                List.of(
                        new RoundInitData(10L, 1, "问题1", "回答1", null, null, "NONE"),
                        new RoundInitData(11L, 2, "问题2", "回答2", null, null, "NONE"));
        var state =
                factory.create(
                        1L, "FRIENDLY", "IN_PROGRESS", "张三", "Java", "JD", "简历", null, rounds);

        assertEquals(2, state.qaHistory().size());
        assertEquals("问题1", state.qaHistory().get(0).question());
        assertEquals("回答2", state.qaHistory().get(1).answer());
        assertEquals(2, state.questionsAsked().size());
    }

    @Test
    @DisplayName("最后一个未回答轮次设为 currentRound")
    void create_currentRound() {
        var rounds =
                List.of(
                        new RoundInitData(10L, 1, "问题1", "回答1", null, null, "NONE"),
                        new RoundInitData(11L, 2, "问题2", null, null, null, "NONE"));
        var state =
                factory.create(
                        1L, "FRIENDLY", "IN_PROGRESS", "张三", "Java", "JD", "简历", null, rounds);

        assertEquals(11L, state.currentRoundId());
        assertEquals(2, state.currentSeq());
        assertEquals("问题2", state.currentQuestion());
        assertEquals("", state.currentAnswer());
    }

    @Test
    @DisplayName("所有轮次已回答时 currentRound 为 null")
    void create_allAnswered() {
        var rounds =
                List.of(
                        new RoundInitData(10L, 1, "问题1", "回答1", null, null, "NONE"),
                        new RoundInitData(11L, 2, "问题2", "回答2", null, null, "NONE"));
        var state =
                factory.create(
                        1L, "FRIENDLY", "IN_PROGRESS", "张三", "Java", "JD", "简历", null, rounds);

        assertNull(state.currentRoundId());
        assertEquals(0, state.currentSeq());
    }

    @Test
    @DisplayName("persona 字符串正确解析为枚举")
    void create_personaParsing() {
        var state =
                factory.create(
                        1L, "PRESSURE", "CREATED", "张三", "Java", "JD", "简历", null, List.of());
        assertEquals(InterviewerPersona.PRESSURE, state.persona());

        var state2 = factory.create(1L, null, "CREATED", "张三", "Java", "JD", "简历", null, List.of());
        assertEquals(InterviewerPersona.FRIENDLY, state2.persona());
    }

    @Test
    @DisplayName("followUpType 字符串正确解析为枚举")
    void create_followUpTypeParsing() {
        var rounds = List.of(new RoundInitData(10L, 1, "追问", null, 1, 1, "DEEPEN"));
        var state =
                factory.create(
                        1L, "FRIENDLY", "IN_PROGRESS", "张三", "Java", "JD", "简历", null, rounds);

        assertEquals(com.aims.core.interview.FollowUpType.DEEPEN, state.followUpType());
        assertEquals(1, state.followUpIndex());
        assertEquals(1, state.parentSeq());
    }
}
