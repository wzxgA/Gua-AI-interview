package com.aims.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 基于 Redis List 的会话记忆实现。 */
@Component
public class RedisConversationMemory implements ConversationMemory {

    private static final String KEY_PREFIX = "interview:memory:";
    private static final Duration TTL = Duration.ofHours(24);

    private final ListOperations<String, String> operations;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisConversationMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.operations = redisTemplate.opsForList();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ConversationMessage> messages(String conversationId) {
        List<String> values = operations.range(key(conversationId), 0, -1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(this::deserialize).toList();
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        String key = key(conversationId);
        operations.rightPush(key, serialize(message));
        redisTemplate.expire(key, TTL);
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return KEY_PREFIX + conversationId;
    }

    private String serialize(ConversationMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("会话消息序列化失败", e);
        }
    }

    private ConversationMessage deserialize(String value) {
        try {
            return objectMapper.readValue(value, ConversationMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("会话消息反序列化失败", e);
        }
    }
}
