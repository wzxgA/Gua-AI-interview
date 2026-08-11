package com.aims.agent.orchestration.node;

/** 流式 chunk 推送接口：将流式 Agent 输出的文本 chunk 推送到 WebSocket。 */
public interface StreamEmitter {

    /** Reactor Context key：承载当前面试 sessionId，供流式 chunk 跨线程携带会话标识。 */
    String SESSION_CONTEXT_KEY = "aims.interview.sessionId";

    /** 流式开始前推送 QUESTION_START，seq 为当前问题序号。默认 NOOP。 */
    default void emitStart(Long sessionId, int seq) {}

    /** 推送一个文本 chunk 到 WebSocket。默认 NOOP。 */
    default void emit(Long sessionId, String chunk) {}

    /** 流式结束后推送 QUESTION_END，fullQuestion 为完整问题文本。默认 NOOP。 */
    default void emitEnd(Long sessionId, String fullQuestion) {}

    /** 空实现：丢弃所有 chunk（测试用）。 */
    StreamEmitter NOOP = new StreamEmitter() {};
}
