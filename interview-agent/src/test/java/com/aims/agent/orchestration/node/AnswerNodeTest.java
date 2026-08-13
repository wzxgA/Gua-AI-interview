package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.QaPair;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link AnswerNode} 测试。 */
class AnswerNodeTest {

    private AnswerNode node;

    @BeforeEach
    void setUp() {
        node = new AnswerNode();
    }

    @Test
    @DisplayName("正常追加：QaPair 写入 QA_HISTORY")
    void answer_appended_to_qaHistory() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.CURRENT_SEQ, 1,
                                InterviewState.CURRENT_QUESTION, "Q1",
                                InterviewState.CURRENT_ANSWER, "A1"));

        Map<String, Object> result = node.apply(state);

        Object value = result.get(InterviewState.QA_HISTORY);
        assertInstanceOf(QaPair.class, value);
        QaPair qa = (QaPair) value;
        assertEquals(1, qa.seq());
        assertEquals("Q1", qa.question());
        assertEquals("A1", qa.answer());
    }

    @Test
    @DisplayName("空回答抛出 IllegalStateException")
    void answer_blank_throws() {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.CURRENT_SEQ, 1,
                                InterviewState.CURRENT_QUESTION, "Q1",
                                InterviewState.CURRENT_ANSWER, ""));

        assertThrows(IllegalStateException.class, () -> node.apply(state));
    }

    @Test
    @DisplayName("追问回答：QaPair 携带 followUpIndex/followUpType，seq 沿用主问题 seq")
    void followUpAnswer_carriesFollowUpMarkers() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.CURRENT_SEQ,
                                2,
                                InterviewState.CURRENT_QUESTION,
                                "能详细说明吗？",
                                InterviewState.CURRENT_ANSWER,
                                "具体来说...",
                                InterviewState.PENDING_FOLLOW_UP,
                                true,
                                InterviewState.FOLLOW_UP_INDEX,
                                1,
                                InterviewState.FOLLOW_UP_TYPE,
                                FollowUpType.DEEPEN));

        Map<String, Object> result = node.apply(state);
        QaPair qa = (QaPair) result.get(InterviewState.QA_HISTORY);

        assertEquals(2, qa.seq());
        assertEquals("能详细说明吗？", qa.question());
        assertEquals("具体来说...", qa.answer());
        assertEquals(1, qa.followUpIndex());
        assertEquals(FollowUpType.DEEPEN, qa.followUpType());
        assertTrue(qa.isFollowUp());
    }

    @Test
    @DisplayName("主问题回答：QaPair 不带追问标记")
    void mainAnswer_noFollowUpMarkers() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.CURRENT_SEQ, 1,
                                InterviewState.CURRENT_QUESTION, "Q1",
                                InterviewState.CURRENT_ANSWER, "A1"));

        Map<String, Object> result = node.apply(state);
        QaPair qa = (QaPair) result.get(InterviewState.QA_HISTORY);

        assertNull(qa.followUpIndex());
        assertNull(qa.followUpType());
        assertFalse(qa.isFollowUp());
    }

    @Test
    @DisplayName("QaPair 字段正确：seq/question/answer 与 State 一致")
    void qaPair_fields_correct() throws Exception {
        var state =
                new InterviewState(
                        Map.of(
                                InterviewState.CURRENT_SEQ, 5,
                                InterviewState.CURRENT_QUESTION, "什么是 Spring？",
                                InterviewState.CURRENT_ANSWER, "Spring 是一个框架"));

        Map<String, Object> result = node.apply(state);
        QaPair qa = (QaPair) result.get(InterviewState.QA_HISTORY);

        assertEquals(5, qa.seq());
        assertEquals("什么是 Spring？", qa.question());
        assertEquals("Spring 是一个框架", qa.answer());
    }
}
