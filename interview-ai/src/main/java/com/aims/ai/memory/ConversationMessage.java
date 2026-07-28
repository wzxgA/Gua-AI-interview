package com.aims.ai.memory;

import java.time.Instant;

/** 项目内部的会话消息模型，隔离 Spring AI ChatMemory API。 */
public record ConversationMessage(
        MessageRole role, String content, String roundId, Instant createdAt) {

    public ConversationMessage {
        if (role == null) {
            throw new IllegalArgumentException("消息角色不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
