package com.aims.agent.orchestration.checkpoint;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Checkpoint Spring Boot 装配。
 *
 * <p>注册 {@link RedisCheckpointSaver} Bean，供 {@code InterviewGraphFactory.compile(checkpointer)} 在
 * Phase 5 注入。使用项目已有的 {@link StringRedisTemplate}，不引入新 Redis 客户端。
 *
 * @since 1.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CheckpointProperties.class)
public class CheckpointConfig {

    @Bean
    public RedisCheckpointSaver redisCheckpointSaver(
            StringRedisTemplate redisTemplate, CheckpointProperties properties) {
        return new RedisCheckpointSaver(
                redisTemplate,
                new CheckpointSerializer(),
                Duration.ofHours(properties.getTtlHours()),
                properties.isHistoryEnabled());
    }
}
