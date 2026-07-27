# interview-agent

Agent 编排层（P1 空壳占位）。

| 期数 | 填充内容 |
|---|---|
| P3 | 会话状态机、InterviewerAgent、面试计划生成 |
| P4 | EvaluatorAgent、ReportAgent |
| P5 | LangChain4j 引入 + Spring AI 桥接适配器、SupervisorAgent、FollowUpAgent |

约束：
- P5 前**不引入** langchain4j 依赖
- 编排层只调用 `interview-ai` 的 `AiChatFacade`，不直接使用 Spring AI API
