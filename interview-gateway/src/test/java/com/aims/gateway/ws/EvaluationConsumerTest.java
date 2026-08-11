package com.aims.gateway.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link EvaluationConsumer} 测试：验证 FE.04 新增的有限重试（成功路径 / 失败重试成功 / 3 次失败置 FAILED）。 */
@ExtendWith(MockitoExtension.class)
class EvaluationConsumerTest {

    @Mock private EvaluationService evaluationService;
    @Mock private InterviewSessionService sessionService;
    @Mock private EvaluationMessageProducer messageProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EvaluationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer =
                new EvaluationConsumer(
                        evaluationService, sessionService, messageProducer, objectMapper);
    }

    @Test
    @DisplayName("评估成功：evaluateSession + 发送报告请求")
    void handle_success() {
        consumer.handleEvaluationRequest("{\"sessionId\":1}");

        verify(evaluationService).evaluateSession(1L);
        verify(messageProducer).sendReportRequest(1L);
    }

    @Test
    @DisplayName("首次失败重试后成功：evaluateSession 调用 2 次")
    void handle_retrySucceeds() {
        doThrow(new RuntimeException("ai down"))
                .doNothing()
                .when(evaluationService)
                .evaluateSession(anyLong());

        consumer.handleEvaluationRequest("{\"sessionId\":2}");

        verify(evaluationService, times(2)).evaluateSession(2L);
        verify(messageProducer).sendReportRequest(2L);
        verify(sessionService, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("3 次失败：置 FAILED，方法不抛异常（offset 提交防无限重投）")
    void handle_allFailures_markFailed() {
        doThrow(new RuntimeException("ai down")).when(evaluationService).evaluateSession(anyLong());

        consumer.handleEvaluationRequest("{\"sessionId\":3}");

        verify(evaluationService, times(3)).evaluateSession(3L);
        verify(sessionService).updateStatus(3L, SessionStatus.FAILED);
        verify(sessionService).updateEvaluationStatus(3L, "FAILED");
    }

    @Test
    @DisplayName("消息解析失败：不触发评估（格式错误不可重试）")
    void handle_parseFailure_skip() {
        consumer.handleEvaluationRequest("not-json");

        verify(evaluationService, never()).evaluateSession(anyLong());
    }
}
