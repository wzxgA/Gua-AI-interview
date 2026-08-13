/**
 * Graph 可观测性：AOP 切面、指标、事件、健康检查。
 *
 * <p>本包为 Phase 6（LG.7）新增，补齐现有"仅模型级计量"的流程级可观测性缺口。
 *
 * <h2>指标命名规范</h2>
 *
 * <ul>
 *   <li>{@code aims.graph.node.duration}（Timer，tag=node）— 节点耗时
 *   <li>{@code aims.graph.node.error}（Counter，tag=node,error_type）— 节点错误聚合
 *   <li>{@code aims.graph.node.retry}（Counter，tag=node）— 节点重试频次
 *   <li>{@code aims.graph.node.tokens}（Counter，tag=node,tier,type）— per-node Token 归因
 *   <li>{@code aims.graph.round.current} / {@code aims.graph.round.total}（Gauge）— 当前进度
 *   <li>{@code aims.graph.checkpoint.restore}（Counter，tag=source）— 断点恢复频次
 *   <li>{@code aims.graph.execution}（Counter，tag=entrypoint,outcome）— 入口调用统计
 * </ul>
 *
 * <h2>标签基数控制铁律</h2>
 *
 * <p>sessionId 永不作为 metric tag（基数爆炸），仅进入 MDC / 日志。允许的 tag 闭集：
 *
 * <ul>
 *   <li>{@code node}：9 个（{@link com.aims.agent.orchestration.graph.NodeNames} 闭集）
 *   <li>{@code tier}：4 个（{@link com.aims.ai.router.ModelTier} 枚举）
 *   <li>{@code model}：~5 个（配置档位数）
 *   <li>{@code entrypoint}：5 个（start/submitAnswer/finish/pause/cancel）
 *   <li>{@code error_type}：~10 个（异常类简单名）
 *   <li>{@code outcome}：3 个（success / retry_exhausted / error）
 *   <li>{@code source}：2 个（checkpoint / db_rebuild）
 * </ul>
 *
 * @since 1.2.0 Phase 6
 */
package com.aims.agent.orchestration.observability;
