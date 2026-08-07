package com.aims.agent.orchestration.node;

/** 流式 chunk 推送接口：将流式 Agent 输出的文本 chunk 推送到 WebSocket。 */
@FunctionalInterface
public interface StreamEmitter {

    /** 推送一个文本 chunk 到 WebSocket。 */
    void emit(String chunk);

    /** 空实现：丢弃所有 chunk（测试用）。 */
    StreamEmitter NOOP = chunk -> {};
}
