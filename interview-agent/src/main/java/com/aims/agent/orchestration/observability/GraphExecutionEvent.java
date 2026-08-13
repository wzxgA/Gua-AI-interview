package com.aims.agent.orchestration.observability;

import java.time.Instant;
import java.util.Set;

/**
 * Graph 执行事件：由 {@link GraphTraceAspect} 在 Node 执行前后发布，经 Spring 事件总线广播。
 *
 * <p>Phase 6 仅落结构化日志（{@link GraphEventLoggingListener}），为 P6 前端流程可视化 （WebSocket 推送 Mermaid 高亮）和 P7
 * OpenTelemetry Span 预留出口。
 *
 * <p>使用 sealed interface + record：所有事件类型编译期闭集，不允许外部扩展。
 *
 * @since 1.2.0 Phase 6
 */
public sealed interface GraphExecutionEvent {

    /** 会话 ID（用于日志，永不作为 metric tag）。 */
    String sessionId();

    /** 节点名（NodeNames 闭集）。CheckpointRestored 事件可为空。 */
    String node();

    /** 当前轮次 seq。CheckpointRestored 事件为 -1。 */
    int round();

    /** 事件时间戳。 */
    Instant timestamp();

    /** Node 开始执行事件。 */
    record NodeStarted(String sessionId, String node, int round, Instant timestamp)
            implements GraphExecutionEvent {}

    /** Node 执行成功事件。 */
    record NodeSucceeded(
            String sessionId,
            String node,
            int round,
            Instant timestamp,
            long durationMs,
            Set<String> outputKeys)
            implements GraphExecutionEvent {}

    /**
     * Node 执行失败事件。
     *
     * <p>注意：{@code FaultTolerantNode} 重试耗尽时<b>不抛异常</b>，而是把 {@code LAST_ERROR} 写入 State 后返回。失败事件由
     * {@link GraphTraceAspect} 检测返回 Map 是否含 {@code LAST_ERROR} key 触发，{@code
     * errorType="retry_exhausted"}。
     */
    record NodeFailed(
            String sessionId,
            String node,
            int round,
            Instant timestamp,
            long durationMs,
            String errorType,
            String errorMessage)
            implements GraphExecutionEvent {}

    /**
     * Checkpoint 恢复事件：{@link com.aims.gateway.orchestration.InterviewWorkflowEngine} 从 Redis
     * checkpoint 加载到非空 state 时发布。
     *
     * @param restoreSource 恢复来源：{@code "checkpoint"}（Redis） 或 {@code "db_rebuild"}（DB 重建）
     */
    record CheckpointRestored(String sessionId, String restoreSource, Instant timestamp)
            implements GraphExecutionEvent {

        @Override
        public String node() {
            return "";
        }

        @Override
        public int round() {
            return -1;
        }
    }
}
