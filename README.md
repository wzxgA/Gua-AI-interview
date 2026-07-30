# 瓜分Offer AI 智能面试 Agent 平台（AIMS）

基于 Spring Boot 3.5 + Spring AI 1.1 + Java 21 虚拟线程构建的 AI 面试 Agent 服务平台。

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 语言 | Java 21（虚拟线程） |
| 框架 | Spring Boot 3.5.3 / Spring AI 1.1.2 |
| AI 模型 | 通义千问（DashScope）/ DeepSeek，OpenAI 兼容模式接入 |
| 数据库 | PostgreSQL 16 + pgvector 向量扩展 |
| 持久层 | MyBatis-Plus 3.5.12 + Flyway 11.7（DDL 版本化管理） |
| 缓存 | Redis 7 |
| 消息队列 | Kafka 3.8（KRaft 模式，无 ZooKeeper） |
| 对象存储 | MinIO |
| 文档抽取 | Apache PDFBox 3.0（简历 PDF 文本抽取） |
| API 文档 | springdoc-openapi（Swagger UI） |
| 代码格式化 | Spotless + Google Java Format |
| 前端 | React 19 + TypeScript + Vite 6 + Tailwind CSS 3.4 + TanStack Query |

## 模块结构

```
ai-ms/
├── interview-core        # 核心层：领域模型(7模块)、统一响应体、错误码、异常体系、分页参数
├── interview-ai          # AI 层：多模型路由(ModelRouter)、ChatClient 封装(AiChatFacade)、三个基础 Advisor、embed/embedBatch
├── interview-agent       # Agent 编排层：InterviewerAgent（问题生成）、InterviewPlanGenerator（计划生成）、EvaluatorAgent（评估）、ReportAgent（报告）
├── interview-infra       # 基础设施层：持久层(Entity/Mapper/Service)、RAG 检索、Redis 会话快照、MinIO、Flyway、健康检查
├── interview-gateway     # 网关层：Spring Boot 启动入口、业务 Controller、面试 WebSocket Handler、全局异常处理、OpenAPI
├── docker/               # Docker Compose 基础设施 + 初始化脚本
├── plans/                # 技术方案、分期状态文档、前端实现计划书、RAG 优化计划
├── frontend/             # 前端工程（React 19 + TypeScript + Vite 6 + Tailwind CSS）
├── dev.ps1               # 一键管理本地基础设施
├── pom.xml               # 父 POM（版本锁定 + 依赖管理）
└── .env.example          # 应用环境变量模板
```

依赖方向：`gateway -> agent -> ai -> core`，`gateway -> infra -> ai -> core`


## 前置条件

- **JDK 21+**（推荐 Eclipse Temurin 21）
- **Maven 3.9+**（或使用项目自带的 `.mvn/wrapper`）
- **Docker** + Docker Compose（用于启动本地基础设施）
- 至少一个 AI 模型 API Key（DashScope 或 DeepSeek）

## 快速开始

### 1. 启动本地基础设施

```powershell
# Windows PowerShell
./dev.ps1 up
```

该命令会自动从 `.env.example` 生成 `docker/.env`，并启动以下服务：

| 服务 | 容器端口 | 宿主机端口 | 说明 |
|------|----------|------------|------|
| PostgreSQL 16 (pgvector) | 5432 | 15432 | 数据库 + 向量扩展 |
| Redis 7 | 6379 | 16379 | 缓存 |
| Kafka 3.8 (KRaft) | 9092 | 9092 | 消息队列（已预建 2 个 topic） |
| MinIO | 9000 / 9001 | 9000 / 9001 | 对象存储（已预建 3 个 bucket） |

> 宿主机端口与 `application-local.yml` 默认值对齐，避免与本机已安装的服务冲突。

```powershell
# 其他管理命令
./dev.ps1 ps      # 查看容器状态
./dev.ps1 logs    # 查看日志
./dev.ps1 down    # 停止容器
./dev.ps1 reset   # 停止并删除所有数据卷（慎用）
```

### 2. 配置 API Key

```powershell
# 从模板创建环境变量文件
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
- 后端 API：http://localhost:8080
- Swagger UI：http://localhost:8080/swagger-ui.html
- 健康检查：http://localhost:8080/actuator/health
- 前端页面：http://localhost:5173

## API 接口

### 冒烟测试接口（仅 local/dev）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/smoke/chat?prompt=你好&tier=STANDARD` | GET | 阻塞式文本调用 |
| `/api/smoke/stream?prompt=你好&tier=FLAGSHIP` | GET (SSE) | 流式文本调用 |
| `/api/smoke/entity` | GET | 结构化输出（返回 JSON 对象） |
| `/api/smoke/infra` | GET | 基础设施连通性检查 |

### 业务接口

| 模块 | 路径前缀 | 端点 | 说明 |
|------|----------|------|------|
| 岗位管理 | `/api/v1/positions` | CRUD + `/{id}/embed` | 岗位增删改查 + JD 向量化 |
| 题库管理 | `/api/v1/questions` | CRUD + `/import` + `/reembed` | 题库增删改查 + 批量导入 + 向量化 ETL |
| 简历管理 | `/api/v1/resumes` | `/upload` + `/{id}/parse` + `/{id}/embed` + `/{id}/reembed` + `/reembed-batch` | 简历上传 + 结构化解析 + 向量化 + 重新向量化 + 批量重建 |
| RAG 检索 | `/api/v1/rag` | `/questions` + `/resumes` | 题库/简历相似度检索，支持 `topK`、`minScore`、`resumeId` 参数 |
| 面试会话 | `/api/v1/interviews` | CRUD + `/{id}/start` + `/{id}/pause` + `/{id}/finish` + `/{id}/cancel` + `/{id}/resume` + `/{id}/rounds` | 面试创建、计划生成、暂停/结束/取消/恢复、轮次查询 |
| 评估报告 | `/api/v1/interviews` | `/{id}/report` + `/{id}/evaluations` + `/{id}/evaluations/{roundId}` | 获取面试报告、轮次评分明细（P4 已实现） |
| 面试 WebSocket | `/ws/interview/{sessionId}` | `ANSWER` / `HEARTBEAT` / `PAUSE` / `FINISH` / `CANCEL` | 实时面试问答，连接后自动触发首题，支持断线重连 |

> **finish 端点说明**：调用 `POST /api/v1/interviews/{id}/finish`（或 WebSocket 发送 `FINISH`）后，面试不再直接进入终态，而是触发评估流程：`IN_PROGRESS` -> `EVALUATING`（Kafka 异步逐题评分）-> `REPORTING`（生成综合报告）-> `COMPLETED`。

**模型档位说明**：

| 档位 | 默认模型 | 用途 | 降温策略 |
|------|----------|------|----------|
| `FLAGSHIP` | qwen-max | 高质量对话 | fallback -> deepseek-chat |
| `STANDARD` | deepseek-chat | 日常推理 | 无 fallback |
| `ECONOMY` | qwen-turbo | 轻量任务 | 无 fallback |
| `EMBEDDING` | text-embedding-v4 | 2048 维向量嵌入 | 无 fallback |

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

## 开发指南

### 代码格式化

项目使用 Spotless + Google Java Format（AOSP 风格），在 `verify` 阶段自动检查：

```powershell
# 检查格式
mvn spotless:check

# 自动格式化
mvn spotless:apply
```

### 数据库迁移

项目使用 Flyway 管理 DDL，迁移脚本位于 `interview-infra/src/main/resources/db/migration/`：

- 脚本命名：`V{版本号}__{描述}.sql`（如 `V2_0_0__create_core_tables.sql`）
- 应用启动时自动执行，已执行的脚本不会重复执行
- 表结构变更一律走增量迁移脚本，禁止手改数据库

### 环境变量一览

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SPRING_PROFILES_ACTIVE` | local | Spring Profile |
| `AIMS_DEFAULT_TIER` | STANDARD | 默认模型档位 |
| `AIMS_FLAGSHIP_PROVIDER` | dashscope | 旗舰档提供商 |
| `AIMS_FLAGSHIP_MODEL` | qwen-max | 旗舰档模型 |
| `AIMS_FLAGSHIP_TEMPERATURE` | 0.7 | 旗舰档温度 |
| `AIMS_FLAGSHIP_MAX_TOKENS` | 2048 | 旗舰档最大 tokens |
| `AIMS_STANDARD_PROVIDER` | deepseek | 标准档提供商 |
| `AIMS_STANDARD_MODEL` | deepseek-chat | 标准档模型 |
| `AIMS_STANDARD_TEMPERATURE` | 0.2 | 标准档温度 |
| `AIMS_STANDARD_MAX_TOKENS` | 2048 | 标准档最大 tokens |
| `AIMS_ECONOMY_PROVIDER` | dashscope | 经济档提供商 |
| `AIMS_ECONOMY_MODEL` | qwen-turbo | 经济档模型 |
| `AIMS_ECONOMY_TEMPERATURE` | 0.3 | 经济档温度 |
| `AIMS_ECONOMY_MAX_TOKENS` | 1024 | 经济档最大 tokens |
| `AIMS_EMBEDDING_PROVIDER` | dashscope | 向量化提供商 |
| `AIMS_EMBEDDING_MODEL` | text-embedding-v4 | 向量化模型 |
| `AIMS_EMBEDDING_DIMENSIONS` | 2048 | 向量维度 |
| `DASHSCOPE_API_KEY` | - | 通义千问 API Key（必填） |
| `DASHSCOPE_BASE_URL` | https://dashscope.aliyuncs.com/compatible-mode | 通义千问 base URL |
| `DEEPSEEK_API_KEY` | - | DeepSeek API Key（必填） |
| `DEEPSEEK_BASE_URL` | https://api.deepseek.com/v1 | DeepSeek base URL |
| `POSTGRES_PORT` | 15432 | PostgreSQL 端口 |
| `POSTGRES_USER` | aims | PostgreSQL 用户名 |
| `POSTGRES_PASSWORD` | aims123 | PostgreSQL 密码 |
| `REDIS_PORT` | 16379 | Redis 端口 |
| `REDIS_PASSWORD` | aims123 | Redis 密码 |
| `KAFKA_PORT` | 9092 | Kafka 端口 |
| `KAFKA_BOOTSTRAP_SERVERS` | localhost:9092 | Kafka 引导服务器（local profile 下由 KAFKA_PORT 覆盖） |
| `MINIO_API_PORT` | 9000 | MinIO API 端口 |
| `MINIO_CONSOLE_PORT` | 9001 | MinIO 控制台端口 |
| `AIMS_LIVE_TEST` | false | Live 集成测试开关 |

### 版本锁定说明

技术方案假设的 Spring Boot 4.1.0 / Spring AI 2.0.0 在 Maven Central 尚无 GA 发布，按预案降级锁定为最近的 GA 版本：

- Spring Boot：`3.5.3`
- Spring AI：`1.1.2`

待官方 GA 发布后统一升级。
