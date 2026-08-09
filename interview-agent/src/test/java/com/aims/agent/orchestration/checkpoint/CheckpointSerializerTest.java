package com.aims.agent.orchestration.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.interview.QaPair;
import com.aims.core.report.Recommendation;
import com.aims.core.report.ReportResult;
import com.aims.core.session.SessionStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CheckpointSerializer} 序列化往返测试。
 *
 * <p>覆盖 {@link InterviewState} 所有值类型：基本类型、Long/Integer 区分、枚举、record、List。
 *
 * @since 1.1.0
 */
class CheckpointSerializerTest {

    private CheckpointSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new CheckpointSerializer();
    }

    private CheckpointRecord roundTrip(CheckpointRecord record) {
        return serializer.deserialize(serializer.serialize(record));
    }

    @Test
    @DisplayName("基本类型 String/Integer/Long/Boolean 往返完整")
    void serializeDeserialize_primitiveTypes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.CANDIDATE_NAME, "张三");
        data.put(InterviewState.TOTAL_ROUNDS, 8);
        data.put(InterviewState.SESSION_ID, 42L);
        data.put(InterviewState.RETRY_COUNT, 2);

        CheckpointRecord out = roundTrip(record(data));

        assertEquals("张三", out.stateData().get(InterviewState.CANDIDATE_NAME));
        assertEquals(8, out.stateData().get(InterviewState.TOTAL_ROUNDS));
        // 关键：小整数 Long 不应退化为 Integer
        assertEquals(42L, out.stateData().get(InterviewState.SESSION_ID));
        assertSame(Long.class, out.stateData().get(InterviewState.SESSION_ID).getClass());
        assertEquals(2, out.stateData().get(InterviewState.RETRY_COUNT));
        assertSame(Integer.class, out.stateData().get(InterviewState.TOTAL_ROUNDS).getClass());
    }

    @Test
    @DisplayName("InterviewPlan（含 List<PlannedQuestion>）类型完整保留")
    void serializeDeserialize_interviewPlan() {
        InterviewPlan plan = samplePlan();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.INTERVIEW_PLAN, plan);

        CheckpointRecord out = roundTrip(record(data));

        Object value = out.stateData().get(InterviewState.INTERVIEW_PLAN);
        assertSame(InterviewPlan.class, value.getClass());
        InterviewPlan restored = (InterviewPlan) value;
        assertEquals(plan.candidateName(), restored.candidateName());
        assertEquals(plan.position(), restored.position());
        assertEquals(plan.questions().size(), restored.questions().size());
        assertEquals(
                plan.questions().get(0).questionId(), restored.questions().get(0).questionId());
        assertEquals(plan.sections().get(0).name(), restored.sections().get(0).name());
        assertEquals(plan.estimatedMinutes(), restored.estimatedMinutes());
    }

    @Test
    @DisplayName("FollowUpDecision + FollowUpType enum 类型正确")
    void serializeDeserialize_followUpDecision() {
        FollowUpDecision decision =
                FollowUpDecision.of(FollowUpType.DEEPEN, "深挖 Spring 原理", "回答偏浅");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.FOLLOW_UP_DECISION, decision);
        data.put(InterviewState.FOLLOW_UP_TYPE, FollowUpType.DEEPEN);

        CheckpointRecord out = roundTrip(record(data));

        Object decisionValue = out.stateData().get(InterviewState.FOLLOW_UP_DECISION);
        assertSame(FollowUpDecision.class, decisionValue.getClass());
        FollowUpDecision restored = (FollowUpDecision) decisionValue;
        assertTrue(restored.shouldFollowUp());
        assertEquals(FollowUpType.DEEPEN, restored.followUpType());
        assertEquals("深挖 Spring 原理", restored.followUpQuestion());

        assertSame(
                FollowUpType.class, out.stateData().get(InterviewState.FOLLOW_UP_TYPE).getClass());
        assertEquals(FollowUpType.DEEPEN, out.stateData().get(InterviewState.FOLLOW_UP_TYPE));
    }

    @Test
    @DisplayName("List<QaPair> 类型与元素完整保留")
    void serializeDeserialize_qaHistory() {
        List<QaPair> history =
                List.of(
                        new QaPair(1, "什么是 IoC？", "控制反转..."),
                        new QaPair(2, "Spring Bean 作用域？", "singleton/prototype..."));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.QA_HISTORY, history);

        CheckpointRecord out = roundTrip(record(data));

        Object value = out.stateData().get(InterviewState.QA_HISTORY);
        assertTrue(value instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<QaPair> restored = (List<QaPair>) value;
        assertEquals(2, restored.size());
        assertSame(QaPair.class, restored.get(0).getClass());
        assertEquals(1, restored.get(0).seq());
        assertEquals("什么是 IoC？", restored.get(0).question());
    }

    @Test
    @DisplayName("List<RoundEvaluation> 类型与元素完整保留")
    void serializeDeserialize_evaluations() {
        List<RoundEvaluation> evaluations =
                List.of(
                        new RoundEvaluation(
                                EvaluationDimension.PROFESSIONAL, 4, "基础扎实", "IoC 回答完整"),
                        new RoundEvaluation(EvaluationDimension.LOGIC, 3, "逻辑清晰", "Bean 作用域分析到位"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.ROUND_EVALUATIONS, evaluations);

        CheckpointRecord out = roundTrip(record(data));

        @SuppressWarnings("unchecked")
        List<RoundEvaluation> restored =
                (List<RoundEvaluation>) out.stateData().get(InterviewState.ROUND_EVALUATIONS);
        assertEquals(2, restored.size());
        assertSame(RoundEvaluation.class, restored.get(0).getClass());
        assertEquals(EvaluationDimension.PROFESSIONAL, restored.get(0).dimension());
        assertEquals(4, restored.get(0).score());
    }

    @Test
    @DisplayName("ReportResult（含 Map<String, DimensionScore>）类型完整保留")
    void serializeDeserialize_reportResult() {
        ReportResult report =
                new ReportResult(
                        "综合表现优秀",
                        Map.of(
                                "专业能力", new ReportResult.DimensionScore(4.2, 3),
                                "逻辑思维", new ReportResult.DimensionScore(3.8, 2)),
                        Recommendation.RECOMMEND);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.REPORT_RESULT, report);

        CheckpointRecord out = roundTrip(record(data));

        Object value = out.stateData().get(InterviewState.REPORT_RESULT);
        assertSame(ReportResult.class, value.getClass());
        ReportResult restored = (ReportResult) value;
        assertEquals("综合表现优秀", restored.summary());
        assertEquals(2, restored.dimensions().size());
        assertEquals(4.2, restored.dimensions().get("专业能力").avgScore());
        assertEquals(Recommendation.RECOMMEND, restored.recommendation());
    }

    @Test
    @DisplayName("SessionStatus / InterviewerPersona 枚举类型正确")
    void serializeDeserialize_sessionStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.SESSION_STATUS, SessionStatus.EVALUATING);
        data.put(InterviewState.PERSONA, InterviewerPersona.TECHNICAL);

        CheckpointRecord out = roundTrip(record(data));

        assertSame(
                SessionStatus.class, out.stateData().get(InterviewState.SESSION_STATUS).getClass());
        assertEquals(SessionStatus.EVALUATING, out.stateData().get(InterviewState.SESSION_STATUS));
        assertSame(
                InterviewerPersona.class, out.stateData().get(InterviewState.PERSONA).getClass());
        assertEquals(InterviewerPersona.TECHNICAL, out.stateData().get(InterviewState.PERSONA));
    }

    @Test
    @DisplayName("state 含 null 值往返不丢失")
    void serializeDeserialize_nullValues() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.INTERVIEW_PLAN, null);
        data.put(InterviewState.LAST_ERROR, null);
        data.put(InterviewState.PARENT_SEQ, null);

        CheckpointRecord out = roundTrip(record(data));

        assertNull(out.stateData().get(InterviewState.INTERVIEW_PLAN));
        assertNull(out.stateData().get(InterviewState.LAST_ERROR));
        assertNull(out.stateData().get(InterviewState.PARENT_SEQ));
    }

    @Test
    @DisplayName("空 state Map 往返仍为空")
    void serializeDeserialize_emptyMap() {
        CheckpointRecord out = roundTrip(record(new LinkedHashMap<>()));

        assertNotNull(out.stateData());
        assertTrue(out.stateData().isEmpty());
    }

    @Test
    @DisplayName("完整 CheckpointRecord（含所有字段）类型信息完整保留")
    void serialize_fullCheckpointRecord() {
        Map<String, Object> data = fullState();
        CheckpointRecord input =
                new CheckpointRecord("cp-100", "evaluate", "summary", data, 1700000000000L);

        CheckpointRecord out = roundTrip(input);

        assertEquals("cp-100", out.checkpointId());
        assertEquals("evaluate", out.nodeId());
        assertEquals("summary", out.nextNodeId());
        assertEquals(1700000000000L, out.timestampEpochMillis());

        // 抽检关键类型
        assertSame(Long.class, out.stateData().get(InterviewState.SESSION_ID).getClass());
        assertSame(
                InterviewPlan.class, out.stateData().get(InterviewState.INTERVIEW_PLAN).getClass());
        assertSame(
                FollowUpDecision.class,
                out.stateData().get(InterviewState.FOLLOW_UP_DECISION).getClass());
        assertSame(
                SessionStatus.class, out.stateData().get(InterviewState.SESSION_STATUS).getClass());
        assertTrue(out.stateData().get(InterviewState.QA_HISTORY) instanceof List<?>);
        assertTrue(out.stateData().get(InterviewState.EVALUATED_ROUND_IDS) instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<Long> evalIds = (List<Long>) out.stateData().get(InterviewState.EVALUATED_ROUND_IDS);
        assertSame(Long.class, evalIds.get(0).getClass());
    }

    @Test
    @DisplayName("非法 JSON 抛 CheckpointSerializationException")
    void deserialize_invalidJson_throws() {
        assertThrows(
                CheckpointSerializationException.class, () -> serializer.deserialize("{broken"));
    }

    // ─── 测试数据构造 ───

    private CheckpointRecord record(Map<String, Object> data) {
        return new CheckpointRecord("cp-1", "ask", "answer", data, System.currentTimeMillis());
    }

    private InterviewPlan samplePlan() {
        return new InterviewPlan(
                "张三",
                "Java 后端工程师",
                List.of(new PlanSection("基础", 4, "考察基础"), new PlanSection("框架", 4, "考察框架")),
                List.of(
                        new PlannedQuestion("q1", "IoC", "EASY", List.of("循环依赖"), "概念理解"),
                        new PlannedQuestion("q2", "AOP", "EASY", List.of("切面"), "概念理解"),
                        new PlannedQuestion("q3", "Spring Bean", "MEDIUM", List.of("作用域"), "原理"),
                        new PlannedQuestion("q4", "事务", "MEDIUM", List.of("传播"), "原理"),
                        new PlannedQuestion("q5", "MVC", "MEDIUM", List.of("分发"), "原理"),
                        new PlannedQuestion("q6", "Boot", "HARD", List.of("自动装配"), "架构"),
                        new PlannedQuestion("q7", "Cloud", "HARD", List.of("注册"), "架构"),
                        new PlannedQuestion("q8", "JVM", "HARD", List.of("GC"), "底层")),
                60,
                "v1");
    }

    private Map<String, Object> fullState() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(InterviewState.SESSION_ID, 1001L);
        data.put(InterviewState.CANDIDATE_NAME, "张三");
        data.put(InterviewState.POSITION_TITLE, "Java 后端工程师");
        data.put(InterviewState.PERSONA, InterviewerPersona.TECHNICAL);
        data.put(InterviewState.SESSION_STATUS, SessionStatus.EVALUATING);
        data.put(InterviewState.INTERVIEW_PLAN, samplePlan());
        data.put(InterviewState.TOTAL_ROUNDS, 8);
        data.put(InterviewState.QA_HISTORY, List.of(new QaPair(1, "Q1", "A1")));
        data.put(InterviewState.QUESTIONS_ASKED, List.of("Q1"));
        data.put(InterviewState.CURRENT_ROUND_ID, 2001L);
        data.put(InterviewState.CURRENT_SEQ, 3);
        data.put(InterviewState.CURRENT_QUESTION, "当前问题");
        data.put(InterviewState.CURRENT_ANSWER, "当前回答");
        data.put(InterviewState.PARENT_SEQ, null);
        data.put(InterviewState.FOLLOW_UP_INDEX, null);
        data.put(InterviewState.FOLLOW_UP_TYPE, FollowUpType.NONE);
        data.put(InterviewState.FOLLOW_UP_DECISION, FollowUpDecision.noFollowUp("回答充分"));
        data.put(InterviewState.FOLLOW_UP_COUNT, 0);
        data.put(
                InterviewState.ROUND_EVALUATIONS,
                List.of(new RoundEvaluation(EvaluationDimension.LOGIC, 4, "清晰", "引用")));
        data.put(InterviewState.EVALUATED_ROUND_IDS, List.of(2001L, 2002L));
        data.put(InterviewState.RUNNING_SUMMARY, "累计摘要");
        data.put(InterviewState.LAST_SUMMARIZED_SEQ, 2);
        data.put(
                InterviewState.REPORT_RESULT,
                new ReportResult(
                        "summary",
                        Map.of("d", new ReportResult.DimensionScore(4.0, 1)),
                        Recommendation.RECOMMEND));
        data.put(InterviewState.LAST_ERROR, null);
        data.put(InterviewState.RETRY_COUNT, 0);
        data.put(InterviewState.RAG_QUESTIONS, "rag 内容");
        return data;
    }
}
