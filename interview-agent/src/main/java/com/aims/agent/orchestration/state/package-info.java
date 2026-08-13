/**
 * 面试状态容器：基于 LangGraph4j Channel + Reducer 模式。
 *
 * <p>Phase 1 将在此包中定义：
 *
 * <ul>
 *   <li>{@code InterviewState} — 继承 {@code AgentState}，声明所有面试相关字段
 *   <li>{@code MessagesChannel} — AppenderChannel，累积对话历史
 *   <li>{@code RoundChannel} — ReplaceChannel，覆盖当前轮次数据
 *   <li>{@code EvaluationChannel} — AppenderChannel，累积评分结果
 * </ul>
 *
 * <p>设计原则：状态只追加/覆盖，不可变更新，保证 Checkpoint 可序列化。
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration.state;
