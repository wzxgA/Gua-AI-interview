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
        try {
            ReportRequestMessage req = objectMapper.readValue(message, ReportRequestMessage.class);
            Long sessionId = req.sessionId();
            log.info("收到报告请求消息 sessionId={}", sessionId);

            reportService.generateReport(sessionId);
        } catch (Exception e) {
            log.error("报告消费失败 message={}", message, e);
            try {
                ReportRequestMessage req =
                        objectMapper.readValue(message, ReportRequestMessage.class);
                sessionService.updateStatus(req.sessionId(), SessionStatus.FAILED);
                sessionService.updateEvaluationStatus(req.sessionId(), "FAILED");
            } catch (Exception ex) {
                log.error("更新会话状态为 FAILED 失败", ex);
            }
        }
    }
}
