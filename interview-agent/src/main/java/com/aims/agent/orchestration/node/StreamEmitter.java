package com.aims.agent.orchestration.node;

/** 流式 chunk 推送接口：将流式 Agent 输出的文本 chunk 推送到 WebSocket。 */
public interface StreamEmitter {

    /** 推送一个文本 chunk 到 WebSocket。 */
    void emit(String chunk);

    /** 流式开始前推送 QUESTION_START，seq 为当前问题序号。默认 NOOP。 */
    default void emitStart(int seq) {}

    /** 流式结束后推送 QUESTION_END，fullQuestion 为完整问题文本。默认 NOOP。 */
    default void emitEnd(String fullQuestion) {}

    /** 空实现：丢弃所有 chunk（测试用）。 */
    StreamEmitter NOOP = chunk -> {};
}
