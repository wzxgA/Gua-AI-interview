package com.aims.ai.memory;

import java.util.List;

/** 会话记忆抽象，业务层不感知具体存储。隔离 Spring AI 版本差异。 */
public interface ConversationMemory {

    /** 读取全部消息。 */
    List<ConversationMessage> messages(String conversationId);

    /** 读取最近 N 条消息。 */
    default List<ConversationMessage> loadRecent(String conversationId, int lastN) {
        List<ConversationMessage> all = messages(conversationId);
        if (all.size() <= lastN) {
            return all;
        }
        return all.subList(all.size() - lastN, all.size());
    }

    /** 追加一条消息。 */
    void append(String conversationId, ConversationMessage message);

    /** 清除会话历史。 */
    default void clear(String conversationId) {
        // 默认空实现，由 Redis 实现覆盖
    }

    /** 便捷方法：追加 USER 消息。 */
    default void addUser(String conversationId, String content, String roundId) {
        append(conversationId, new ConversationMessage(MessageRole.USER, content, roundId, null));
    }

    /** 便捷方法：追加 ASSISTANT 消息。 */
    default void addAssistant(String conversationId, String content, String roundId) {
        append(
                conversationId,
                new ConversationMessage(MessageRole.ASSISTANT, content, roundId, null));
    }

    /** 拼接为 Prompt 文本。 */
    default String asPrompt(String conversationId, int maxMessages) {
        List<ConversationMessage> allMessages = messages(conversationId);
        return allMessages.stream()
                .skip(Math.max(0, allMessages.size() - maxMessages))
                .map(message -> message.role() + ": " + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
