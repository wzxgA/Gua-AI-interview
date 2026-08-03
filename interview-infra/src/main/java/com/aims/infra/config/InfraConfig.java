package com.aims.infra.config;

import io.minio.MinioClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 基础设施装配：MinIO Client + Kafka 预留 topic。
 *
 * <p>PG 数据源、Redis 连接工厂、KafkaAdmin 由 Spring Boot 自动装配； topic 与 docker/kafka-init 容器创建保持同名（幂等，双保险），P4
 * 开始真正收发消息。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MinioProperties.class, TtsProperties.class})
public class InfraConfig {

    /** P4 评估请求 topic（P1 仅创建）。 */
    public static final String TOPIC_EVALUATION_REQUESTED = "interview.evaluation.requested";

    /** P4 报告请求 topic（P1 仅创建）。 */
    public static final String TOPIC_REPORT_REQUESTED = "interview.report.requested";

    @Bean
    MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    NewTopic evaluationRequestedTopic() {
        return TopicBuilder.name(TOPIC_EVALUATION_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic reportRequestedTopic() {
        return TopicBuilder.name(TOPIC_REPORT_REQUESTED).partitions(3).replicas(1).build();
    }
}
