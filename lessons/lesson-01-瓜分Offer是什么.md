<img src="assets/proj-logo.svg" alt="瓜分 Offer" width="300" height="200">

# 第1期 · 瓜分 Offer 是什么 
```mermaid
mindmap
  root((瓜分 Offer AI 面试 Agent 平台))

    用户端
      管理端
      候选人端
      REST / WebSocket
      面试实时交互

    网关层
      WebSocket Handler
      JWT 与权限控制

    AI Agent 层
      LangGraph4j
      面试计划生成
      智能提问与追问
      面试评估与报告
      多模型路由

    AI 能力层
      Advisor 链
      对话记忆
      模型降级与重试

    基础设施层
      PostgreSQL + pgvector
      Redis
      Kafka
      MinIO
      TTS 服务

    核心业务
      岗位管理
      题库管理
      简历解析
      RAG 检索
      实时面试
      五维评估
      综合报告

    工程部署
      Maven 多模块
      Docker Compose
      Flyway 数据库迁移
      Actuator 监控
      Prometheus 指标
```
## 一、瓜分 Offer 是什么

### 1. 一句话定位

**瓜分 Offer 是一个基于 Spring Boot 3.5 + Spring AI 1.1 + LangGraph4j 的 AI 智能面试 Agent 平台**——它可以模拟一位资深面试官，把读简历 → 出题 → 追问 → 打分 → 出报告这一整套面试动作，从人肉流程变成了一条可配置、可回放、可度量的自动化流水线。

瓜分offer不只是一个题库 + 聊天框的堆砌，而是一套完整的产品：面试前有 `岗位 / 简历 / 题库 / 计划`，面试中有 `WebSocket 实时问答 / 动态追问 / TTS 语音 / 防作弊`，面试后有 `五维评估 / 综合报告 / 简历真实性校验`。你看到的每一个环节，背后都是一个 Agent 或一套异步链路在干活。

### 2. 瓜分offer的使用流程

**面试前**

瓜瓜要面一个后端岗，他先在后台上传了岗位 JD，又上传了几份候选人的简历（PDF/TXT）。系统自动开始处理：

- 简历被 AI 结构化解析，自动拆出**工作经历、项目经历、项目亮点、竞赛证书**，还能人工修正后自动重向量化；
- 瓜瓜把网上找到的面经传上去，系统自动提取出规范化的题目进入题库；
- 题库、岗位 JD、简历通通变成 **2048 维向量**，随时能被语义检索命中。



瓜瓜在`创建面试`里选好岗位 + 简历，一键**生成面试计划**——题数（1-30）、难度、面试官人设（温和 / 压力面 / 深度技术型）都可以配。计划不是简单的题目列表，而是按`自我介绍 / 技术考察 / 项目深挖 / 行为面试 / 反问 `切分的结构化方案。

**面试中**

系统生成一条**候选人专属链接**，候选人无需登录，输个访问密码就能进面试间。链接上还挂着两个防作弊开关：切屏检测 + 眼神检测。

面试在这里真正展示Agent成色：

- 前端建立 WebSocket 长连接，面试官的问题**逐字流式**吐出，实时渲染；
- 瓜瓜的每个回答都会被审一遍：**答得空泛就深挖（DEEPEN）、提到技术没展开就追问原理、答偏了就拉回正轨（REDIRECT）、和简历对不上就要求澄清（CLARIFY）**——每个主问题最多追问 3 次；
- 可选开启 **TTS 语音播报**，问题用温柔或犀利的音色念出来；
- 整场面试瓜瓜在一个浏览器里完成，**切屏、眼神偏离的每一帧数据都只在本地 WASM 跑，不出浏览器**，异常事件才批量上报给**管理端面板**。

**面试后**


瓜瓜点**结束面试**，系统进入异步收尾：

- 按**五维**（专业能力 40% / 逻辑思维 20% / 沟通表达 15% / 岗位匹配 15% / 学习与潜力 10%）逐题打分，每分都附**证据引用**（原话片段），不凭空给分；
- 汇总成**综合报告 + 录用建议 + 总分**；
- 还有一手暗棋——**简历真实性校验**：系统把瓜瓜的回答里提到的公司和经历，与简历里的工作经历做交叉比对，标出**疑似未提及 / 存在冲突的地方**，甄别简历注水。

面试结束后不用守着屏幕，报告生成走 Kafka 异步，系统会调研LLM把报告生成出来。

### 3. 功能流程图

按面试生命周期 × 功能模块来读，比背清单直观得多：

```mermaid
    flowchart TB
        subgraph 面试前["面试前"]
            subgraph 资料
                A1[上传岗位 JD<br/>JD 向量化]
                A2[上传简历 PDF/TXT<br/>AI 结构化拆经历]
                A3[面经上传<br/>自动提题入题库]
            end
            subgraph 计划
                A4[生成面试计划<br/>题数 1-30 · 难度 · 人设]
                A5[候选人生成链接<br/>访问密码 · 防作弊开关]
            end
            A1 --- A2 --- A3
            subgraph 底座
                RAG[(pgvector 语义检索<br/>题库 / 简历 / JD)]
            end
            A1 & A2 & A3 --> RAG
        end

        subgraph 面试中["面试中"]
            B1[WebSocket 实时问答<br/>问题流式输出]
            B2[面试官动态追问<br/>澄清 / 深挖 / 引导 最多追问3 次]
            B3[TTS 语音播报<br/>可开关]
            B4[防作弊<br/>切屏 + 眼神 · 帧不出浏览器]
            B1 --> B2 --> B1
        end

        subgraph 面试后["面试后"]
            C1[五维评估<br/>逐题打分 · 带证据引用]
            C2[综合报告 + 录用建议<br/>总分]
            C3[简历真实性校验<br/>交叉比对 · 标出冲突]
            C1 --> C2
        end

        RAG -.-> B1
        面试前 --> 面试中 --> 面试后
```

### 4. 为什么要做这个项目

两个理由。

**场景真实**：招聘是刚需，AI 面试已被商业产品验证过付费价值，做出来不会为了用技术而用技术。

**技术全面**：一条面试业务线，从上到下串起了 **Agent 编排、RAG 语义检索、WebSocket 流式通信、Kafka 异步削峰、Redis 有状态恢复、多模型路由降级、并发与幂等防御**

---

## 二、技术栈


### 1. 语言与框架

- **Java 21 虚拟线程**：面试是典型的 IO 密集场景——一场面试同时挂着 WebSocket 长连接、调 LLM、等 Kafka、写 Redis。虚拟线程让**一个请求一条物理线程**的旧观念过时，IO 等待不再空耗线程，天然适配这类长连接 + 高并发的会话。
- **Spring Boot 3.5 + Spring AI 1.1**：Spring AI 用 **OpenAI 兼容协议**对接LLM，`base-url + api-key` 改个配置就能换模型。模型是可替换的，业务代码不用动。

### 2. AI 与编排

- **LangGraph4j**：这是最核心的选型。面试流程**不是一段线性脚本**（不是 `for question in questions: ask`），而是一张**状态图**——有**问完该不该追问？追问回答完回到哪？题数够没够要不要结束？**这类会**回头、会中断、会分支**的有状态逻辑。LangGraph4j 用 `StateGraph` 把 `plan → ask → answer → followUpDecision → (followUp|summary) → endCheck → (ask|END)` 画成图，配合 Redis Checkpointer 做**图级中断与断点恢复**。


### 3. 数据与中间件

- **PostgreSQL + pgvector**：`halfvec` 2048 维向量 + **HNSW 索引** + **pg_trgm 关键词模糊匹配**，做**向量 + 关键词 混合检索**——向量管语义相近，关键词管精确术语命中（比如框架名、技术栈）。
- **Redis**：承担四个角色——**会话记忆**（对话上下文）、**checkpoint**（图断点）、**会话锁**（同一会话只允许一个连接）、**缓存**（RAG 结果、RefreshToken、滚动摘要）。
- **Kafka （KRaft 单节点）**：评估、报告这类**耗时写操作**不进实时对话链路，投递到 `interview.evaluation.requested` / `interview.report.requested` 两个 topic 异步消化，削峰解耦。
- **MinIO**：存**简历原件、TTS 音频、报告**，前端不能直连对象存储，由后端 `/audio` 代理流出。

### 4. 前端与工程化

- **React 19 + TypeScript + Vite 6 + Tailwind CSS**，状态用 **Zustand**（面试间实时状态）、服务端数据用 **TanStack Query**（带轮询）。
- **可观测性**：Actuator + Micrometer，把**节点耗时、模型延迟、Token 成本**全部量化为指标。
- **纪律性工具**：**Flyway** 给 DDL 做版本化管理，**Spotless + Google Java Format** 强制统一代码格式。


---

## 三、架构

### 1. 总览图

后端按 Maven 多模块分层。**自上而下，就是一次请求从上到下的完整旅程。**
```mermaid
flowchart TB
    FE["前端应用<br/>React + TypeScript"]

    subgraph G["网关服务（Java Spring Boot）"]
        G1["REST API"]
        G2["WebSocket 流式面试"]
        G3["JWT 鉴权 / 模型配置"]
    end

    subgraph A["智能面试 Agent 服务"]
        A1["面试流程编排"]
        A2["面试计划 / 提问 / 追问"]
        A3["评估 / 摘要 / 报告"]
        A4["工具调用：简历核验 / 代码评测"]
    end

    subgraph AI["AI 能力服务"]
        AI1["AiChatFacade<br/>统一模型调用"]
        AI2["ModelRouter<br/>多模型路由与降级"]
        AI3["Advisor 链<br/>日志 / Token / 重试"]
        AI4["Embedding / 会话记忆"]
    end

    subgraph INFRA["业务基础设施服务"]
        I1["简历 / 岗位 / 题库 / 面试会话"]
        I2["RAG 混合检索<br/>向量 + 关键词"]
        I3["数据持久化与流程状态"]
    end

    subgraph DATA["数据存储与外部服务"]
        D1["PostgreSQL<br/>业务数据 + pgvector"]
        D2["Redis<br/>缓存 / 会话记忆 / Checkpoint"]
        D3["Kafka<br/>异步消息"]
        D4["Qwen / DeepSeek<br/>Chat 与 Embedding 模型"]
    end

    FE --> G
    G --> A
    A --> AI
    A --> INFRA
    AI --> INFRA
    AI --> D4
    INFRA --> DATA
    G --> DATA

    classDef fe fill:#edf4ff,stroke:#527fc5,stroke-width:3px,color:#222;
    classDef gateway fill:#fff4d6,stroke:#d5af48,stroke-width:3px,color:#222;
    classDef agent fill:#eee6ff,stroke:#8668c7,stroke-width:3px,color:#222;
    classDef ai fill:#e8f7ed,stroke:#4c9b6a,stroke-width:3px,color:#222;
    classDef infra fill:#f4eaff,stroke:#a46bc4,stroke-width:3px,color:#222;
    classDef data fill:#e5eafb,stroke:#6375ad,stroke-width:3px,color:#222;

    class FE fe;
    class G1,G2,G3 gateway;
    class A1,A2,A3,A4 agent;
    class AI1,AI2,AI3,AI4 ai;
    class I1,I2,I3 infra;
    class D1,D2,D3,D4 data;

    style G fill:#fff9e5,stroke:#d5af48,stroke-width:3px
    style A fill:#f1ebff,stroke:#8668c7,stroke-width:3px
    style AI fill:#edf9f1,stroke:#4c9b6a,stroke-width:3px
    style INFRA fill:#f8efff,stroke:#a46bc4,stroke-width:3px
    style DATA fill:#ebeffb,stroke:#6375ad,stroke-width:3px
```

### 2. 六模块职责

| 模块 | 职责 | 关键词 |
| --- | --- | --- |
| `interview-core` | 领域模型、错误码、统一响应 | **零框架依赖** |
| `interview-ai` | 模型路由 / Advisor / 会话记忆 / Facade | **LLM 调用的统一出口**，ModelRouter 四档位守门，Advisor 链每次调用前置 |
| `interview-agent` | LangGraph4j 状态图 + 5 个 业务Agent + 一个总指挥 Agent | **面试流程的大脑** |
| `interview-infra` | pgvector RAG / 持久层 / Kafka / MinIO / TTS | **落地能力** |
| `interview-gateway` | REST / WS / JWT / Engine 驱动 | **唯一入口** |



### 3. 架构设计决策



**为什么 core 零依赖？**
因为**领域模型最稳定，框架会换、领域不会**。`SessionStatus` 状态机、`InterviewPlan`、五维评估模型这些是在描述业务本质，它们不该认识 Spring、MyBatis、Redis 任何一家。把它们关在框架之外，是给整个系统上了一份业务不会因为技术换代而推翻的保险。

**为什么单向依赖？**
`gateway → agent → ai → core` 一路向下，**编译期**就强制隔离了方向。好处是：**AI 层可以整体替换而不动业务编排**——今天用 Spring AI，明天想换 LangChain4j，只需要在 `interview-ai` 内部换实现，`interview-agent` 的流程图、`interview-gateway` 的 Handler 一行都不用改。依赖向下的每一层，都给了上层替换的底气。

### 4. 一次面试请求的完整旅程

把上面的模块图串成一条时间线，你会看到数据如何在六层之间流动：

```mermaid
sequenceDiagram
    participant F as 前端
    participant G as Gateway(REST/WS)
    participant E as WorkflowEngine(StateGraph)
    participant A as Agent/AiFacade
    participant K as Kafka(异步)

    F->>G: POST /interviews/{id}/plan
    G->>A: 查岗位+简历 → RAG 检索题库
    A->>G: 生成结构化 InterviewPlan(STANDARD)
    G->>G: 存 plan_json，状态 PLANNING
    F->>G: POST /interviews/{id}/start → IN_PROGRESS
    F->>G: WS 建连(JWT 握手, 取连接锁)
    G->>E: Engine 驱动 StateGraph 从 START 跑
    E->>A: ASK → InterviewerAgent 流式出题(FLAGSHIP)
    A-->>F: QUESTION_START → CHUNK… → END(流式推送)
    E->>E: interruptBefore(ANSWER) 暂停，落 Redis Checkpoint
    F->>G: ANSWER 提交候选回答
    G->>E: 注入回答，Graph resume 从 ANSWER 续跑
    E->>A: followUpDecision(STANDARD) 决定是否追问
    E->>E: followUp 回环 / summary → endCheck
    alt 追问
        E->>A: FLAGSHIP 流式追问 → 回到 ANSWER 等答
    else 达到题数
        E->>K: FINISH → Kafka 触发异步评估
        K->>K: 逐题五维评分(STANDARD) → 生成报告(STANDARD)
        K->>G: 置 COMPLETED，ReportConsumer 收尾
    end
```

**一次请求的完整旅程**：REST 生成计划 → WebSocket 建连（JWT / GuestToken 握手）→ Engine 驱动 StateGraph → 问题流式推送 → 候选人答题 → 图 resume → 追问 / 下一题 → 结束 → Kafka 异步评估 / 报告。**实时性交给 WebSocket 和流式输出，耗时计算甩给 Kafka 异步**，分工明确。


