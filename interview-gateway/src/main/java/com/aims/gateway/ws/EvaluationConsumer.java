package com.aims.gateway.ws;

import com.aims.core.session.SessionStatus;
import com.aims.infra.config.InfraConfig;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.messaging.EvaluationRequestMessage;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 评估请求 Kafka 消费者。 */
@Component
public class EvaluationConsumer {

    private static final Logger log = LoggerFactory.getLogger(EvaluationConsumer.class);

    /** FE.04：评估失败重试次数与基础退避间隔（ms），重试耗尽才置 FAILED。 */
    private static final int MAX_ATTEMPTS = 3;

    private static final long BASE_DELAY_MS = 500;

    private final EvaluationService evaluationService;
    private final InterviewSessionService sessionService;
    private final EvaluationMessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    public EvaluationConsumer(
            EvaluationService evaluationService,
            InterviewSessionService sessionService,
            EvaluationMessageProducer messageProducer,
            ObjectMapper objectMapper) {
        this.evaluationService = evaluationService;
        this.sessionService = sessionService;
        this.messageProducer = messageProducer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = InfraConfig.TOPIC_EVALUATION_REQUESTED, groupId = "aims-evaluation")
    public void handleEvaluationRequest(String message) {
        EvaluationRequestMessage req;
        try {
            req = objectMapper.readValue(message, EvaluationRequestMessage.class);
        } catch (Exception e) {
            log.error("评估请求消息解析失败 message={}", message, e);
            return; // 消息格式错误不可重试
        }
        Long sessionId = req.sessionId();

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("收到评估请求消息 sessionId={} attempt={}/{}", sessionId, attempt, MAX_ATTEMPTS);
                evaluationService.evaluateSession(sessionId);
                // 评估完成，发送报告请求
                messageProducer.sendReportRequest(sessionId);
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
        log.error("评估消费失败，重试耗尽置 FAILED sessionId={}", sessionId, last);
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
