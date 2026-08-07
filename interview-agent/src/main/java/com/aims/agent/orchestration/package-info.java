/**
 * LangGraph4j 编排层：将面试流程从命令式调用重构为声明式状态图。
 *
 * <h2>子包职责</h2>
 *
 * <ul>
 *   <li>{@code state} — InterviewState 状态容器，定义 Channel 与 Reducer
 *   <li>{@code node} — NodeAction 实现，每个 Node 封装一个 Agent 调用
 *   <li>{@code graph} — StateGraph 工厂，定义节点拓扑与条件边
 *   <li>{@code checkpoint} — Redis Checkpointer，支持断点续面
 *   <li>{@code engine} — WorkflowEngine，对外暴露 start/resume/cancel API
 * </ul>
 *
 * <h2>集成阶段</h2>
 *
 * <ol>
 *   <li>Phase 0 — 依赖引入与脚手架（本阶段）
 *   <li>Phase 1 — InterviewState 状态容器
 *   <li>Phase 2 — Graph Node 实现
 *   <li>Phase 3 — StateGraph 定义与编译
 *   <li>Phase 4 — Redis Checkpointer
 *   <li>Phase 5 — Engine + Handler 改造
 * </ol>
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration;
