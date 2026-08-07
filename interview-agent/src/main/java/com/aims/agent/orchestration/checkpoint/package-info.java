/**
 * Redis Checkpointer：基于 {@code BaseCheckpointSaver} 实现断点续面。
 *
 * <p>Phase 4 将在此包中实现：
 *
 * <ul>
 *   <li>{@code RedisCheckpointSaver} — 实现 {@code BaseCheckpointSaver} 接口
 *   <li>序列化策略：Jackson JSON + 状态快照压缩
 *   <li>Key 设计：{@code interview:checkpoint:{sessionId}:{threadId}}
 *   <li>TTL 策略：7 天自动过期，与 Session 实体生命周期对齐
 * </ul>
 *
 * <p>用途：面试中断后恢复、A/B 测试回放、调试追踪。
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration.checkpoint;
