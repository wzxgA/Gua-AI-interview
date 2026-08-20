<p align="center">
  <img src="frontend/public/logo.svg" alt="瓜分Offer Logo" width="120" height="120" style="border-radius:16px;" />
</p>

<p align="center">
  <b style="font-size:32px">瓜分Offer · AI 智能面试 Agent 平台</b>
</p>

<p align="center">
  <i>Spring Boot + Spring AI + LangGraph4j · AI Interview Agent Platform</i>
</p>

<p align="center">
  <a href="#功能特性">功能特性</a> ·
  <a href="#技术栈">技术栈</a> ·
  <a href="#快速开始">快速开始</a> ·
  <a href="#核心流程与架构">架构</a> ·
  <a href="#API-接口">API</a>
</p>

---

基于 Spring Boot 3.5 + Spring AI 1.1 + Java 21 虚拟线程 + LangGraph4j 构建的 AI 面试 Agent 服务平台。覆盖岗位/题库/简历管理、RAG 语义检索、简历 AI 解析、面试实时问答（动态追问、面试官人设、TTS 语音播报、断线恢复）以及五维评估与报告生成。

## 功能特性

| 模块         | 说明                                                                                                    |
| ---------- | ----------------------------------------------------------------------------------------------------- |
| 岗位/题库/简历管理 | CRUD + 向量化（pgvector 语义索引）；题库批量导入与面试笔记导入（AI 结构化提取题目）；简历上传（PDF/TXT）与 AI 结构化解析（工作/项目经历拆分），支持人工修正后自动失效旧向量并重新向量化        |
| RAG 语义检索   | 基于 pgvector（halfvec + HNSW 索引）的题库/简历相似度检索，支持分类/难度过滤、最低相似度阈值、指定候选人匹配；面试计划生成时自动以岗位 JD 检索参考题库；检索结果与查询向量双层 Redis 缓存（异常自动降级直查） |
| 面试全流程      | 面试计划生成（题数/难度可配置）→ WebSocket 实时问答（问题流式输出）→ 动态追问（每主问题最多 3 次，类型含澄清/深挖/引导）→ 断线恢复（Redis Checkpoint + 会话快照） |
| 面试官人设      | 温和型 / 压力面型 / 深度技术型，并可联动 TTS 音色                                                                        |
| TTS 语音播报   | 火山引擎豆包 TTS 2.0，问题生成后异步合成，前端音频播放（可选开关）                                                                 |
| 五维评估与报告    | 专业能力 / 逻辑思维 / 沟通表达 / 岗位匹配 / 学习与潜力，逐轮 AI 评分 + 综合报告 + 录用建议，Kafka 异步处理                                   |
| 简历真实性校验    | 面试中与结束后交叉校验简历工作经历与候选人回答，突出"疑似未提及/存在冲突"的经历，辅助甄别简历造假（冲突详情写入报告）                    |
| 面试防作弊      | 生成候选人链接时可选开启：切屏检测（标签页/窗口失焦）+ 眼神检测（MediaPipe 本地 WASM 人脸/视线偏离，帧不出浏览器）；控制台实时面板 + 报告页专注度参考 |
| 多模型路由      | FLAGSHIP / STANDARD / ECONOMY / EMBEDDING 四档位，失败自动降级换模型，带指数退避重试、token/延迟/成本指标统计                       |
| 配置中心化      | AI 模型路由与 TTS 均支持 DB 覆盖 yml、运行期改配置即时生效（无需重启）；TTS API Key 入库加密、界面掩码展示（DB 增量覆盖 + yml 兜底模式）  |
| 鉴权与安全      | JWT + RefreshToken 轮换，基于角色的接口权限（仅 ADMIN 可执行全量重向量化等操作）                                                 |

## 技术栈

| 层级       | 技术选型                                                                              |
| -------- | --------------------------------------------------------------------------------- |
| 语言       | Java 21（虚拟线程）                                                                     |
| 框架       | Spring Boot 3.5.3 / Spring AI 1.1.2 / LangGraph4j 1.8.22                          |
| Agent 编排 | StateGraph（7 节点 2 条件边）+ Redis Checkpointer（interruptBefore(ANSWER)），灰度开关可回退旧命令式链路 |
| AI 模型    | 通义千问（DashScope）/ DeepSeek，OpenAI 兼容模式接入；四档位路由 + fallback 降级                       |
| 数据库      | PostgreSQL 16 + pgvector（halfvec 2048 维，HNSW + pg\_trgm 混合检索索引）                   |
| 持久层      | MyBatis-Plus 3.5.12 + Flyway 11.7（DDL 版本化管理）                                      |
| 缓存       | Redis 7（会话记忆、Checkpoint、RefreshToken、滚动摘要、会话锁）                                    |
| 消息队列     | Kafka 3.8（KRaft 模式，无 ZooKeeper，评估/报告异步链路）                                         |
| 对象存储     | MinIO（简历原件、TTS 音频、报告）                                                             |
| 语音       | 火山引擎豆包 TTS 2.0（可选）                                                                |
| 文档抽取     | Apache PDFBox 3.0（简历 PDF 文本抽取）                                                    |
| API 文档   | springdoc-openapi（Swagger UI）                                                     |
| 代码格式化    | Spotless + Google Java Format                                                     |
| 安全       | Spring Security 6 + JWT（jjwt 0.12.6）+ BCrypt                                      |
| 前端       | React 19 + TypeScript + Vite 6 + Tailwind CSS 3.4 + TanStack Query + zustand      |
| 可观测性     | Actuator / Micrometer（节点耗时、模型延迟、成本）/ Prometheus 端点                                |

## 模块结构

```
ai-ms/
├── interview-core        # 核心层：领域模型、统一响应体、错误码、异常体系、分页参数（无框架依赖）
├── interview-ai          # AI 层：多模型路由(ModelRouter/ModelTier)、ChatClient 封装(AiChatFacade)、Advisor、Redis 对话记忆
├── interview-agent       # Agent 编排层：Interviewer/FollowUp/Evaluator/Report/Summary Agent + LangGraph4j 编排图(节点/状态/checkpoint/可观测性)
├── interview-infra       # 基础设施层：持久层(Entity/Mapper/Service)、pgvector RAG、Kafka 消息、MinIO、TTS、Flyway、健康检查
├── interview-gateway     # 网关层：Spring Boot 启动入口、REST Controller、面试 WebSocket Handler、InterviewWorkflowEngine、JWT 安全、Kafka 消费端
├── frontend/             # 前端工程（React 19 + TypeScript + Vite 6 + Tailwind CSS）
├── docker/               # Docker Compose 基础设施 + 初始化脚本
├── scripts/              # 各类数据清理脚本（按业务域）
├── dev.ps1               # 一键管理本地基础设施
├── pom.xml               # 父 POM（版本锁定 + 依赖管理）
└── .env.example          # 应用环境变量模板
```

依赖方向：`gateway -> agent -> ai -> core`，`gateway -> infra -> ai -> core`

## 前置条件

- **JDK 21+**（推荐 Eclipse Temurin 21）
- **Maven 3.9+**
- **Docker** + Docker Compose（用于启动本地基础设施）
- 至少一个 AI 模型 API Key（DashScope 或 DeepSeek）
- **Node.js + pnpm**（前端开发）

## 快速开始

### 1. 启动本地基础设施

```powershell
# Windows PowerShell
./dev.ps1 up
```

该命令会自动从 `.env.example` 生成 `docker/.env`，并启动以下服务：

| 服务                       | 容器端口        | 宿主机端口       | 说明                   |
| ------------------------ | ----------- | ----------- | -------------------- |
| PostgreSQL 16 (pgvector) | 5432        | 15432       | 数据库 + 向量扩展           |
| Redis 7                  | 6379        | 16379       | 缓存                   |
| Kafka 3.8 (KRaft)        | 9092        | 9092        | 消息队列（已预建 2 个 topic）  |
| MinIO                    | 9000 / 9001 | 9000 / 9001 | 对象存储（已预建 3 个 bucket） |

> 宿主机端口与 `application-local.yml` 默认值对齐，避免与本机已安装的服务冲突。

```powershell
./dev.ps1 ps      # 查看容器状态
./dev.ps1 logs    # 查看日志
./dev.ps1 down    # 停止容器
./dev.ps1 reset   # 停止并删除所有数据卷（慎用）
```

### 2. 配置 API Key

```powershell
cp .env.example .env
```

编辑 `.env`，填入真实的 API Key：

```properties
DASHSCOPE_API_KEY=sk-your-dashscope-key
DEEPSEEK_API_KEY=sk-your-deepseek-key
```

> 应用启动时会检查 API Key 是否已配置，缺失则直接报错。

### 3. 编译与运行

```powershell
# 编译全部模块
mvn clean install -DskipTests

# 启动后端应用
mvn -pl interview-gateway spring-boot:run

# 启动前端开发服务器（另一个终端）
cd frontend
pnpm install
pnpm dev
```

应用启动后访问：

- 后端 API：<http://localhost:8080>
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>
- 前端页面：<http://localhost:5173>
- 默认账号：admin / admin123（首次启动由 AdminUserInitializer 自动创建）

## 核心流程与架构

### 面试状态机

```
CREATED → PLANNING → IN_PROGRESS ⇄ PAUSED → EVALUATING → REPORTING → COMPLETED
                        └──────────────→ CANCELLED / FAILED
```

- 面试结束（REST `/finish` 或 WebSocket `FINISH`）后进入 `EVALUATING`：Kafka 异步逐轮五维评分 → `REPORTING` 生成综合报告 → `COMPLETED`（前端 2s 轮询感知）
- 断线时 `IN_PROGRESS` 原子转 `PAUSED`，重连后按 checkpoint / 会话快照恢复

### WebSocket 实时问答

- 连接端点：`/ws/interview/{sessionId}?token={accessToken}`（握手时校验 JWT，同一会话仅允许一个活跃连接）
- **客户端消息**：`ANSWER`（提交回答，幂等防连点重入）、`HEARTBEAT`（30s 心跳）、`PAUSE`、`FINISH`、`CANCEL`
- **服务端消息**：`SESSION_READY`、`QUESTION_START`、`QUESTION_CHUNK`（流式分片）、`QUESTION_END`、`ANSWER_ACK`、`AUDIO_READY`（TTS 音频就绪）、`STATUS`、`HEARTBEAT_ACK`、`SESSION_COMPLETED`、`ERROR`

### 模型档位

| 档位          | 默认模型              | 用途              | 降级策略                      |
| ----------- | ----------------- | --------------- | ------------------------- |
| `FLAGSHIP`  | qwen-max          | 高质量面试对话         | fallback -> deepseek-chat |
| `STANDARD`  | deepseek-chat     | 追问决策/评估打分/计划/报告 | 无 fallback                |
| `ECONOMY`   | qwen-turbo        | 摘要/低成本任务        | 无 fallback                |
| `EMBEDDING` | text-embedding-v4 | 2048 维向量嵌入      | 无 fallback                |

### 评估维度（五维加权）

专业能力 40% · 逻辑思维 20% · 沟通表达 15% · 岗位匹配 15% · 学习与潜力 10%（评分域 0-5，逐轮 AI 评分后聚合）

## API 接口

所有 REST 接口统一返回 `Result<T>` 包装，全局异常统一处理（400/401/403/404/502/500）。

### 认证（`/api/v1/auth`）

| 方法   | 路径              | 说明                                      |
| ---- | --------------- | --------------------------------------- |
| POST | `/auth/login`   | 登录，签发 accessToken + refreshToken，返回用户信息 |
| POST | `/auth/refresh` | 用 refreshToken 换取新令牌（旧 token 轮换作废）      |
| POST | `/auth/logout`  | 登出，吊销 refreshToken                      |
| GET  | `/auth/me`      | 当前登录用户信息                                |

### 岗位（`/api/v1/positions`）

| 方法     | 路径                      | 说明                             |
| ------ | ----------------------- | ------------------------------ |
| POST   | `/positions`            | 创建岗位                           |
| PUT    | `/positions/{id}`       | 更新岗位（仅更新非 null 字段）             |
| GET    | `/positions/{id}`       | 岗位详情                           |
| GET    | `/positions`            | 分页查询（title 模糊 / department 过滤） |
| DELETE | `/positions/{id}`       | 删除（软删除置 INACTIVE）              |
| POST   | `/positions/{id}/embed` | 触发 JD 向量化                      |

### 题库（`/api/v1/questions`）

| 方法     | 路径                   | 说明                                     |
| ------ | -------------------- | -------------------------------------- |
| POST   | `/questions`         | 创建题目，保存后同步向量化                          |
| PUT    | `/questions/{id}`    | 更新题目，content 变化时自动重新向量化                |
| GET    | `/questions/{id}`    | 题目详情                                   |
| GET    | `/questions`         | 分页查询（category / difficulty / topic 过滤） |
| DELETE | `/questions/{id}`    | 删除题目                                   |
| POST   | `/questions/import`  | 批量导入题目（异步批量向量化）                        |
| POST   | `/questions/reembed` | 存量补齐 embedding（需 ADMIN）                |

### 简历（`/api/v1/resumes`）

| 方法     | 路径                                | 说明                            |
| ------ | --------------------------------- | ----------------------------- |
| POST   | `/resumes/upload`                 | 上传简历（PDF/TXT），自动抽文本并异步触发结构化解析 |
| POST   | `/resumes/{id}/parse`             | 手动触发 AI 结构化解析                 |
| PATCH  | `/resumes/{id}/parsed-resume`     | 人工修改解析结果，自动失效旧向量并异步重向量化       |
| GET    | `/resumes/{id}`                   | 简历详情                          |
| GET    | `/resumes`                        | 分页列表（candidateName 模糊）        |
| DELETE | `/resumes/{id}`                   | 删除（同步删 MinIO 对象 + DB 记录）      |
| POST   | `/resumes/{id}/embed`             | 触发向量化                         |
| POST   | `/resumes/{id}/reembed`           | 重新向量化（失效旧向量）                  |
| POST   | `/resumes/reembed-batch`          | 批量重新向量化（需 ADMIN，返回 taskId）    |
| GET    | `/resumes/reembed-batch/{taskId}` | 轮询批量向量化任务进度                   |

### RAG 检索（`/api/v1/rag`，仅 local/dev）

| 方法  | 路径                                              | 说明                   |
| --- | ----------------------------------------------- | -------------------- |
| GET | `/rag/questions?query&topK&category&difficulty` | 题库语义检索 Top-K         |
| GET | `/rag/resumes?query&topK&minScore&resumeId`     | 简历语义检索，支持最低相似度与指定候选人 |

### 面试会话（`/api/v1/interviews`）

| 方法     | 路径                        | 说明                            |
| ------ | ------------------------- | ----------------------------- |
| GET    | `/interviews`             | 分页查询（status 过滤）               |
| POST   | `/interviews`             | 创建会话                          |
| GET    | `/interviews/{id}`        | 会话详情                          |
| POST   | `/interviews/{id}/plan`   | 生成面试计划（基于岗位 JD + 简历 + RAG 检索） |
| POST   | `/interviews/{id}/start`  | 开始面试                          |
| POST   | `/interviews/{id}/finish` | 结束面试（触发 Kafka 异步评估）           |
| POST   | `/interviews/{id}/pause`  | 暂停                            |
| POST   | `/interviews/{id}/cancel` | 取消                            |
| POST   | `/interviews/{id}/resume` | 恢复                            |
| GET    | `/interviews/{id}/rounds` | 轮次列表（用于重连恢复历史消息）              |
| DELETE | `/interviews/{id}`        | 删除会话（级联删除轮次）                  |

### 候选人访问与防作弊（`/api/v1/interviews` + `/api/v1/access`）

| 方法     | 路径                                                    | 说明                                       |
| ------ | ----------------------------------------------------- | ---------------------------------------- |
| POST   | `/interviews/{id}/access/generate`                    | 生成候选人面试链接（可选密码 + 防作弊配置 `{tabSwitch, gaze}`） |
| GET    | `/interviews/{id}/access`                             | 查询候选人访问配置（含防作弊开关）                      |
| POST   | `/interviews/{id}/access/password`                    | 重置访问密码（明文仅返回一次）                        |
| POST   | `/interviews/{id}/access/disable`                     | 作废候选人入口（管理端恢复可进面试间）                    |
| GET    | `/interviews/{id}/proctor/events?after=&limit=`       | 防作弊事件增量查询（控制台 5s 轮询用）                  |
| GET    | `/interviews/{id}/proctor/summary`                    | 防作弊聚合摘要（各类型次数/总偏离时长）                   |
| POST   | `/api/v1/access/interviews/{sessionId}/proctor/events` | 候选端批量上报防作弊事件（GUEST 权限 + 同会话校验）         |

### 评估报告（`/api/v1/interviews`）

| 方法  | 路径                                       | 说明                          |
| --- | ---------------------------------------- | --------------------------- |
| GET | `/interviews/{id}/report`                | 综合报告（评述 + 维度聚合 + 录用建议 + 总分） |
| GET | `/interviews/{id}/evaluations`           | 全部轮次五维度评分明细                 |
| GET | `/interviews/{id}/evaluations/{roundId}` | 指定轮次五维度评分明细                 |

### 其他

| 方法  | 路径                                          | 说明                                 |
| --- | ------------------------------------------- | ---------------------------------- |
| GET | `/api/v1/audio/{bucket}/{*objectPath}`      | 从 MinIO 代理读取音频流（前端无法直连 MinIO）      |
| GET | `/api/smoke/chat?prompt=你好&tier=STANDARD`   | 冒烟：阻塞式文本调用                         |
| GET | `/api/smoke/stream?prompt=你好&tier=FLAGSHIP` | 冒烟：SSE 流式输出                        |
| GET | `/api/smoke/entity`                         | 冒烟：结构化输出链路验证                       |
| GET | `/api/smoke/infra`                          | 冒烟：基础设施连通性检查（PG/Redis/Kafka/MinIO） |

> 冒烟与 RAG 接口仅 local/dev profile 加载。

## 数据库

由 Flyway 版本化管理（`interview-infra/src/main/resources/db/migration/`，当前 V2\_1\_7），核心表：

| 表                      | 说明                                       |
| ---------------------- | ---------------------------------------- |
| `position`             | 岗位（含 JD、halfvec 向量、HNSW 索引）              |
| `question_bank`        | 题库（分类/难度/主题/标签、halfvec 向量、搜索索引）           |
| `resume`               | 简历（原始文本、解析结果、解析/向量状态与重试、向量模型版本）          |
| `work_experience` / `project_experience` / `project_highlight` | 简历工作/项目经历拆分后的明细（v1.3 起从 resume 拆出） |
| `candidate`            | 候选人（简历解析出，关联经历与面试访问）                     |
| `interview_session`    | 面试会话（状态机、计划、人设、评估进度、防作弊配置 proctor\_json、tts\_enabled、finished\_by/finish\_reason） |
| `interview_round`      | 面试轮次（追问通过 parentSeq/followUpIndex 关联主问题，含冲突校验详情 conflict\_details） |
| `interview_evaluation` | 逐轮五维评估结果                                 |
| `interview_report`     | 综合报告（session 唯一约束）                       |
| `interview_proctor_event` | 面试防作弊事件（切屏/失焦/眼神偏离/摄像头状态，按会话聚合展示）       |
| `ai_provider_config` / `ai_tier_config` | AI 模型提供商/档位配置（DB 覆盖 yml，运行期生效）          |
| `tts_config`           | TTS 配置（单行 id=1，DB 增量覆盖 yml，API Key 加密）      |
| `sys_user`             | 用户表（用户名/密码/角色/启用状态）                      |

## 测试

### 单元测试

```powershell
# 运行所有单元测试
mvn test

# 仅运行指定模块测试
mvn -pl interview-ai test
```

### 集成测试（Live IT）

Live 集成测试需要真实 API Key 和运行中的基础设施，默认跳过：

```powershell
# 启动基础设施
./dev.ps1 up

# 开启 Live 测试开关
$env:AIMS_LIVE_TEST = "true"

# 运行集成测试
mvn verify -Dit.test=SmokeApiLiveIT
```

> 测试分层：surefire 跑单元测试（排除 integration/live/performance），failsafe 跑集成测试；JaCoCo 对 orchestration 包有行覆盖率门槛，Spotless 在 verify 阶段检查格式。

## 开发指南

### 代码格式化

项目使用 Spotless + Google Java Format（AOSP 风格），在 `verify` 阶段自动检查：

```powershell
mvn spotless:check   # 检查格式
mvn spotless:apply   # 自动格式化
```

### 数据库迁移

- 脚本命名：`V{版本号}__{描述}.sql`（如 `V2_0_0__create_core_tables.sql`）
- 应用启动时自动执行，已执行的脚本不会重复执行
- 表结构变更一律走增量迁移脚本，禁止手改数据库

### 环境变量一览

| 变量名                         | 默认值                                                               | 说明                                           |
| --------------------------- | ----------------------------------------------------------------- | -------------------------------------------- |
| `SPRING_PROFILES_ACTIVE`    | local                                                             | Spring Profile                               |
| `AIMS_DEFAULT_TIER`         | STANDARD                                                          | 默认模型档位                                       |
| `AIMS_FLAGSHIP_PROVIDER`    | dashscope                                                         | 旗舰档提供商                                       |
| `AIMS_FLAGSHIP_MODEL`       | qwen-max                                                          | 旗舰档模型                                        |
| `AIMS_FLAGSHIP_TEMPERATURE` | 0.7                                                               | 旗舰档温度                                        |
| `AIMS_FLAGSHIP_MAX_TOKENS`  | 2048                                                              | 旗舰档最大 tokens                                 |
| `AIMS_STANDARD_PROVIDER`    | deepseek                                                          | 标准档提供商                                       |
| `AIMS_STANDARD_MODEL`       | deepseek-chat                                                     | 标准档模型                                        |
| `AIMS_STANDARD_TEMPERATURE` | 0.2                                                               | 标准档温度                                        |
| `AIMS_STANDARD_MAX_TOKENS`  | 2048                                                              | 标准档最大 tokens                                 |
| `AIMS_ECONOMY_PROVIDER`     | dashscope                                                         | 经济档提供商                                       |
| `AIMS_ECONOMY_MODEL`        | qwen-turbo                                                        | 经济档模型                                        |
| `AIMS_ECONOMY_TEMPERATURE`  | 0.3                                                               | 经济档温度                                        |
| `AIMS_ECONOMY_MAX_TOKENS`   | 1024                                                              | 经济档最大 tokens                                 |
| `AIMS_EMBEDDING_PROVIDER`   | dashscope                                                         | 向量化提供商                                       |
| `AIMS_EMBEDDING_MODEL`      | text-embedding-v4                                                 | 向量化模型                                        |
| `AIMS_EMBEDDING_DIMENSIONS` | 2048                                                              | 向量维度                                         |
| `AIMS_FLAGSHIP_THINKING`    | （空）                                                            | 旗舰档 DeepSeek 推理思考模式（enabled/disabled，留空走模型默认） |
| `AIMS_FLAGSHIP_REASONING_EFFORT` | （空）                                                      | 旗舰档思考强度（low/high/max，仅思考开启时生效）              |
| `AIMS_CONFIG_ENCRYPT_KEY`   | （空）                                                            | 配置中心化 DB 覆盖层的 API Key 加密密钥（base64 32 字节，生产必须配置，未配置退化为由 JWT 密钥派生） |
| `AIMS_RAG_RESULT_CACHE_ENABLED` | true                                                         | 题库 RAG 检索结果缓存（TTL 60s；Redis 异常自动降级直查）       |
| `AIMS_RAG_EMBEDDING_CACHE_ENABLED` | true                                                    | 题库 RAG 查询向量缓存（TTL 30min）                   |
| `AIMS_LOG_PROMPT_MAX_CHARS` | 200                                                               | LLM 日志 prompt 摘要截断长度（字符）                    |
| `DASHSCOPE_API_KEY`         | -                                                                 | 通义千问 API Key（必填）                             |
| `DASHSCOPE_BASE_URL`        | <https://dashscope.aliyuncs.com/compatible-mode>                  | 通义千问 base URL                                |
| `DEEPSEEK_API_KEY`          | -                                                                 | DeepSeek API Key（必填）                         |
| `DEEPSEEK_BASE_URL`         | <https://api.deepseek.com/v1>                                     | DeepSeek base URL                            |
| `POSTGRES_USER`             | aims                                                              | PostgreSQL 用户名                               |
| `POSTGRES_PASSWORD`         | aims123                                                           | PostgreSQL 密码                                |
| `POSTGRES_DB`               | aims                                                              | PostgreSQL 库名                                |
| `POSTGRES_PORT`             | 15432                                                             | PostgreSQL 端口                                |
| `REDIS_PORT`                | 16379                                                             | Redis 端口                                     |
| `REDIS_PASSWORD`            | aims123                                                           | Redis 密码                                     |
| `KAFKA_PORT`                | 9092                                                              | Kafka 端口                                     |
| `KAFKA_BOOTSTRAP_SERVERS`   | localhost:9092                                                    | Kafka 引导服务器（local profile 下由 KAFKA\_PORT 覆盖） |
| `MINIO_ROOT_USER`           | aims                                                              | MinIO 根用户                                    |
| `MINIO_ROOT_PASSWORD`       | aims12345                                                         | MinIO 根密码                                    |
| `MINIO_API_PORT`            | 9000                                                              | MinIO API 端口                                 |
| `MINIO_CONSOLE_PORT`        | 9001                                                              | MinIO 控制台端口                                  |
| `AIMS_TTS_ENABLED`          | false                                                             | 是否启用 TTS 语音播报                                |
| `AIMS_TTS_PROVIDER`         | volc                                                              | TTS 提供商                                      |
| `VOLC_TTS_API_KEY`          | -                                                                 | 火山引擎 API Key（TTS 开启时必填）                      |
| `VOLC_TTS_RESOURCE_ID`      | seed-tts-2.0                                                      | 火山 TTS 资源 ID                                 |
| `VOLC_TTS_BASE_URL`         | <https://openspeech.bytedance.com/api/v3/plan/tts/unidirectional> | 火山 TTS 接口地址                                  |
| `VOLC_TTS_SPEAKER`          | zh\_male\_m191\_uranus\_bigtts                                    | 默认音色（云舟 2.0，人设联动音色）                          |
| `AIMS_JWT_SECRET`           | -                                                                 | JWT 签名密钥（至少 32 字节，生产必须修改）                    |
| `AIMS_JWT_ACCESS_TTL`       | 7200                                                              | AccessToken 有效期（秒）                           |
| `AIMS_JWT_REFRESH_TTL`      | 604800                                                            | RefreshToken 有效期（秒）                          |
| `AIMS_LIVE_TEST`            | false                                                             | Live 集成测试开关                                  |

