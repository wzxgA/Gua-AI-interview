/**
 * StateGraph 定义与编译：声明节点拓扑、边和条件路由。
 *
 * <p>Phase 3 将在此包中实现：
 *
 * <ul>
 *   <li>{@code InterviewGraphFactory} — Spring {@code @Configuration} Bean 工厂
 *   <li>使用 {@code StateGraph.Builder} 构建 DAG
 *   <li>条件边：{@code addConditionalEdges} 实现 followUp→evaluate 路由
 *   <li>编译为 {@code CompiledGraph}，注册为 Spring Bean
 * </ul>
 *
 * <p>图拓扑概览：
 *
 * <pre>
 *   START → plan → question → answer → followUpDecision
 *                                     ├─ followUp → question (循环)
 *                                     └─ evaluate → summary → nextRound?
 *                                          ├─ question (循环)
 *                                          └─ report → END
 * </pre>
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration.graph;
