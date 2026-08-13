package com.aims.agent.orchestration.checkpoint;

import java.util.Map;

/**
 * 可序列化的 Checkpoint 快照，用于 Redis 持久化。
 *
 * <p>对应 langgraph4j {@code Checkpoint} 的可存储形式：
 *
 * <ul>
 *   <li>{@code checkpointId} — {@code String}，与 {@code Checkpoint.getId()} 类型一致
 *   <li>{@code nodeId} / {@code nextNodeId} — 图节点标识
 *   <li>{@code stateData} — {@code InterviewState} 的 {@code Map<String, Object>} 形式
 *   <li>{@code timestampEpochMillis} — 写入时间戳，用于历史排序与调试
 * </ul>
 *
 * @since 1.1.0
 */
public record CheckpointRecord(
        String checkpointId,
        String nodeId,
        String nextNodeId,
        Map<String, Object> stateData,
        long timestampEpochMillis) {}
