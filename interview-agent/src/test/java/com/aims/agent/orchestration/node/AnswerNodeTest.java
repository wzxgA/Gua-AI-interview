package com.aims.agent.orchestration.node;

import static org.junit.jupiter.api.Assertions.*;

import com.aims.agent.orchestration.state.InterviewState;
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
