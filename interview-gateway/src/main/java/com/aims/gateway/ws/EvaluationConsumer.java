package com.aims.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aims.core.session.SessionStatus;
import com.aims.infra.config.InfraConfig;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.messaging.EvaluationRequestMessage;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 评估请求 Kafka 消费者。 */
@Component
public class EvaluationConsumer {

    private static final Logger log = LoggerFactory.getLogger(EvaluationConsumer.class);

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

    @KafkaListener(
            topics = InfraConfig.TOPIC_EVALUATION_REQUESTED,
            groupId = "aims-evaluation")
    public void handleEvaluationRequest(String message) {
        try {
            EvaluationRequestMessage req =
                    objectMapper.readValue(message, EvaluationRequestMessage.class);
            Long sessionId = req.sessionId();
            log.info("收到评估请求消息 sessionId={}", sessionId);

            evaluationService.evaluateSession(sessionId);

            // 评估完成，发送报告请求
            messageProducer.sendReportRequest(sessionId);
        } catch (Exception e) {
            log.error("评估消费失败 message={}", message, e);
            try {
                EvaluationRequestMessage req =
                        objectMapper.readValue(message, EvaluationRequestMessage.class);
                sessionService.updateStatus(req.sessionId(), SessionStatus.FAILED);
                sessionService.updateEvaluationStatus(req.sessionId(), "FAILED");
            } catch (Exception ex) {
                log.error("更新会话状态为 FAILED 失败", ex);
            }
        }
    }
}
