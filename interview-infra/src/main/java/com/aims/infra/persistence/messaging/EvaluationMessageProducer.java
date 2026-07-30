package com.aims.infra.persistence.messaging;

import com.aims.infra.config.InfraConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** 评估/报告 Kafka 消息生产者。 */
@Component
public class EvaluationMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(EvaluationMessageProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EvaluationMessageProducer(
            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** 发送评估请求消息。 */
    public void sendEvaluationRequest(Long sessionId) {
        try {
            String payload = objectMapper.writeValueAsString(new EvaluationRequestMessage(sessionId));
            kafkaTemplate.send(
                    InfraConfig.TOPIC_EVALUATION_REQUESTED, sessionId.toString(), payload);
            log.info("发送评估请求消息 sessionId={}", sessionId);
        } catch (JsonProcessingException e) {
            log.error("序列化评估请求消息失败 sessionId={}", sessionId, e);
            throw new RuntimeException("序列化评估请求消息失败", e);
        }
    }

    /** 发送报告请求消息。 */
    public void sendReportRequest(Long sessionId) {
        try {
            String payload = objectMapper.writeValueAsString(new ReportRequestMessage(sessionId));
            kafkaTemplate.send(InfraConfig.TOPIC_REPORT_REQUESTED, sessionId.toString(), payload);
            log.info("发送报告请求消息 sessionId={}", sessionId);
        } catch (JsonProcessingException e) {
            log.error("序列化报告请求消息失败 sessionId={}", sessionId, e);
            throw new RuntimeException("序列化报告请求消息失败", e);
        }
    }
}
