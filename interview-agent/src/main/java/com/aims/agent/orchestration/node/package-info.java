/**
 * Graph Node 实现：每个 NodeAction 封装一个 Agent 调用并更新 State。
 *
 * <p>Phase 2 将在此包中实现 8 个 Node：
 *
 * <ol>
 *   <li>{@code PlanNode} — 调用 {@link com.aims.agent.InterviewPlanGenerator}
 *   <li>{@code QuestionNode} — 调用 {@link com.aims.agent.InterviewerAgent}
 *   <li>{@code AnswerNode} — 接收候选人回答，写入 State
 *   <li>{@code FollowUpDecisionNode} — 调用 {@link com.aims.agent.FollowUpAgent}
 *   <li>{@code FollowUpNode} — 生成追问问题
 *   <li>{@code EvaluateNode} — 调用 {@link com.aims.agent.EvaluatorAgent}
 *   <li>{@code SummaryNode} — 调用 {@link com.aims.agent.SummaryAgent}
 *   <li>{@code ReportNode} — 调用 {@link com.aims.agent.ReportAgent}
 * </ol>
 *
 * <p>每个 Node 遵循 {@code NodeAction<AgentState>} 接口，返回 {@code Map<String, Object>} 增量更新。
 *
 * @since 1.1.0
 */
package com.aims.agent.orchestration.node;
