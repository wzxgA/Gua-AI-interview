/**
 * Redis Checkpointer：基于 {@code AbstractCheckpointSaver} 实现断点续面。
 *
 * <p>Phase 4 在此包中实现：
 *
 * <ul>
 *   <li>{@link com.aims.agent.orchestration.checkpoint.RedisCheckpointSaver} — 继承 {@code
 *       AbstractCheckpointSaver}，使用 {@code StringRedisTemplate} 持久化
 *   <li>{@link com.aims.agent.orchestration.checkpoint.CheckpointSerializer} — JSON 序列化 + Schema
 *       感知类型归一化（还原 Long/Integer/enum/record）
 *   <li>Key 设计：{@code interview:checkpoint:{threadId}}，{@code threadId} 即面试 sessionId
 *   <li>TTL 策略：默认 24h，由 {@code interview.checkpoint.ttl-hours} 配置
 * </ul>
 *
 * <p>用途：面试中断后恢复、调试追踪。
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration.checkpoint;
