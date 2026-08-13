/**
 * Workflow Engine：对外暴露图执行的统一入口。
 *
 * <p>Phase 5 将在此包中实现：
 *
 * <ul>
 *   <li>{@code InterviewWorkflowEngine} — 封装 {@code CompiledGraph.invoke()} / {@code stream()}
 *   <li>方法：{@code start(sessionId)} / {@code resume(sessionId)} / {@code cancel(sessionId)}
 *   <li>与 {@code InterviewWebSocketHandler} 集成，替换命令式主循环
 *   <li>流式输出通过 {@code Flux<GraphEvent>} 推送到 WebSocket
 * </ul>
 *
 * <p>设计目标：Handler 只负责 WebSocket I/O，流程编排完全委托给 Engine。
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration.engine;
