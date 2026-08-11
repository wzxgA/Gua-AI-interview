package com.aims.gateway.ws;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ReportConsumer} 测试：验证 FE.04 新增的有限重试（成功路径 / 失败重试成功 / 3 次失败置 FAILED）。 */
@ExtendWith(MockitoExtension.class)
class ReportConsumerTest {

    @Mock private ReportService reportService;
    @Mock private InterviewSessionService sessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReportConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReportConsumer(reportService, sessionService, objectMapper);
    }

    @Test
    @DisplayName("报告生成成功")
    void handle_success() {
        consumer.handleReportRequest("{\"sessionId\":1}");

        verify(reportService).generateReport(1L);
    }

    @Test
    @DisplayName("首次失败重试后成功：generateReport 调用 2 次")
    void handle_retrySucceeds() {
        doThrow(new RuntimeException("ai down"))
                .doNothing()
                .when(reportService)
                .generateReport(anyLong());

        consumer.handleReportRequest("{\"sessionId\":2}");

        verify(reportService, times(2)).generateReport(2L);
    }

    @Test
    @DisplayName("3 次失败：置 FAILED，方法不抛异常（offset 提交防无限重投）")
    void handle_allFailures_markFailed() {
        doThrow(new RuntimeException("ai down")).when(reportService).generateReport(anyLong());

        consumer.handleReportRequest("{\"sessionId\":3}");

        verify(reportService, times(3)).generateReport(3L);
        verify(sessionService).updateStatus(3L, SessionStatus.FAILED);
        verify(sessionService).updateEvaluationStatus(3L, "FAILED");
    }

    @Test
    @DisplayName("消息解析失败：不触发报告生成")
    void handle_parseFailure_skip() {
        consumer.handleReportRequest("not-json");

        verify(reportService, never()).generateReport(anyLong());
    }
}
