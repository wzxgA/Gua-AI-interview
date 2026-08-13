package com.aims.gateway.ws;

import com.aims.core.session.SessionStatus;
import com.aims.infra.config.InfraConfig;
import com.aims.infra.persistence.messaging.ReportRequestMessage;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 报告请求 Kafka 消费者。 */
@Component
public class ReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportConsumer.class);

    /** FE.04：报告生成失败重试次数与基础退避间隔（ms），重试耗尽才置 FAILED。 */
    private static final int MAX_ATTEMPTS = 3;

    private static final long BASE_DELAY_MS = 500;

    private final ReportService reportService;
    private final InterviewSessionService sessionService;
    private final ObjectMapper objectMapper;

    public ReportConsumer(
            ReportService reportService,
            InterviewSessionService sessionService,
            ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = InfraConfig.TOPIC_REPORT_REQUESTED, groupId = "aims-report")
    public void handleReportRequest(String message) {
        ReportRequestMessage req;
        try {
            req = objectMapper.readValue(message, ReportRequestMessage.class);
        } catch (Exception e) {
            log.error("报告请求消息解析失败 message={}", message, e);
            return; // 消息格式错误不可重试
        }
        Long sessionId = req.sessionId();

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("收到报告请求消息 sessionId={} attempt={}/{}", sessionId, attempt, MAX_ATTEMPTS);
                reportService.generateReport(sessionId);
                return;
            } catch (Exception e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(BASE_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        // 重试耗尽：置 FAILED 后正常返回（不抛），保证 offset 提交、避免 Kafka 无限重投
        log.error("报告消费失败，重试耗尽置 FAILED sessionId={}", sessionId, last);
        markFailed(sessionId);
    }

    private void markFailed(Long sessionId) {
        try {
            sessionService.updateStatus(sessionId, SessionStatus.FAILED);
            sessionService.updateEvaluationStatus(sessionId, "FAILED");
        } catch (Exception ex) {
            log.error("更新会话状态为 FAILED 失败 sessionId={}", sessionId, ex);
        }
    }
}
