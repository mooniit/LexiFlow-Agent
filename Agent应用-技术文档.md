# Agent 应用技术文档

## 1. 项目定位

本项目定位为一个可写入简历的 Java Agent 应用开发项目，业务方向确定为：

```text
LexiFlow Agent - 企业合同与合规审查 Agent
```

项目目标不是简单调用大模型 API 做问答，而是构建一个面向企业销售、采购、法务团队的合同审查 Agent 平台，支持合同上传、条款抽取、合规规则检索、风险识别、修改建议、人工审批和审查链路追踪。

项目应体现以下能力：

- 大模型接入与统一治理
- 合同文档解析与结构化条款抽取
- RAG 合规规则检索
- Agent 审查任务状态机
- 审查工具调用与权限控制
- 高风险条款人工审批 Human-in-the-loop
- 异步任务处理
- Agent 审查链路追踪
- 前后端可视化演示
- 本地可复现部署

项目整体建议采用：

```text
模块化单体架构
```

也就是部署上保持一个 Spring Boot 后端应用，代码上按照业务边界拆分模块，为后续微服务拆分预留边界。

---

## 2. 总体技术栈

### 后端

```text
Java 21
Spring Boot 3.x
Spring MVC
Spring Security + JWT
Spring AI
自定义 LLM Gateway
PostgreSQL + pgvector
MyBatis Plus
Flyway
Redis
RabbitMQ
自研轻量 Agent 状态机
REST + SSE
Spring Boot Actuator
Micrometer
JUnit 5 + Mockito + Testcontainers
Docker Compose
```

### 前端

```text
React
TypeScript
Vite
Ant Design
SSE Client
```

前端主要用于直观展示合同审查 Agent 的后端能力，不作为项目主战场。目标是让面试官可以看到合同上传、审查任务创建、实时执行过程、条款风险识别、RAG 引用、人工审批和 Agent Trace。

---

## 3. Java 21

### 在项目中的作用

Java 21 是后端运行时基础，负责承载 Spring Boot 应用、合同审查 Agent 执行逻辑、异步任务、数据库访问和大模型调用。

合同审查 Agent 中会出现大量 I/O 密集型场景：

- 调用 LLM API 做条款抽取、风险分析、报告生成
- 调用 Embedding API
- 查询 pgvector 合规规则库
- 调用审查工具，例如合同解析、风险规则匹配、审批创建
- 访问 Redis / RabbitMQ / PostgreSQL
- SSE 流式推送审查进度

Java 21 的虚拟线程适合这类阻塞式 I/O 场景，可以在保持同步编程模型的同时提升并发承载能力。

### 选择优势

相比 Java 17，Java 21 的优势是：

- Java 21 是 LTS 版本，适合生产与简历项目。
- Virtual Threads 已正式可用。
- Spring Boot 3.2+ 对虚拟线程支持更完善。
- 适合高延迟外部 API 调用较多的 Agent 后端。

---

## 4. Spring Boot

### 在项目中的作用

Spring Boot 是后端应用底座，负责：

- REST API
- SSE 流式接口
- 依赖注入
- 事务管理
- 配置管理
- 安全认证
- 数据库集成
- Redis / RabbitMQ 集成
- Actuator 监控端点
- 后端模块组织

Spring Boot 承载完整企业后端系统，Spring AI 只作为其中的 AI 能力接入层。

### 选择优势

相比 Quarkus、Micronaut 等框架，Spring Boot 的优势是：

- Java 企业生态成熟。
- 招聘市场认可度高。
- 和 Spring Security、Spring AI、Spring Data Redis、Spring AMQP、Actuator 等组件集成自然。
- 更适合展示企业级 Agent 应用开发能力。

---

## 5. Spring AI

### 在项目中的作用

Spring AI 是 Spring 生态中的 AI 应用开发框架，用来统一接入：

- Chat Model
- Embedding Model
- Vector Store
- Tool Calling
- RAG 相关能力

它不是训练模型的框架，而是将大模型能力接入 Java / Spring Boot 业务系统的工程框架。

### 项目中的定位

推荐架构：

```text
合同审查服务 / Agent 服务
        |
        v
自定义 LLM Gateway
        |
        v
Spring AI ChatClient / EmbeddingModel / VectorStore
        |
        v
OpenAI / DashScope / Ollama / Azure / 其他模型
```

Spring AI 负责模型与向量库适配，自定义 LLM Gateway 负责项目内部的模型治理。

### 选择优势

- 与 Spring Boot 融合自然。
- 适合 Java Agent 应用开发。
- 支持模型、Embedding、向量库、工具调用等统一抽象。
- 相比直接调用厂商 SDK，更利于后续模型切换。

---

## 6. LLM Gateway

### 在项目中的作用

LLM Gateway 是项目自定义的大模型接入层，负责将所有模型调用统一收口。

它应提供统一接口：

```text
chat
streamChat
embed
structuredOutput
toolCalling
```

### 主要职责

- 统一模型调用接口
- 支持多模型供应商切换
- 管理 Prompt 模板和版本
- 记录模型调用日志
- 统计 token 消耗
- 处理超时、重试、降级
- 支持流式输出
- 支持结构化 JSON 输出

### 在合同审查中的典型调用

- 合同条款抽取
- 条款风险分析
- 合规规则解释
- 修改建议生成
- 审查报告生成
- 合规知识库问答

### 选择优势

不建议业务代码直接依赖具体模型 SDK 或到处使用 Spring AI ChatClient。统一 LLM Gateway 后，可以把模型治理能力集中到一层，便于排查、测试和扩展。

简历表达：

```text
设计统一 LLM Gateway，支持多模型供应商接入、模型路由、流式响应、结构化输出、调用日志记录和失败降级。
```

---

## 7. PostgreSQL

### 在项目中的作用

PostgreSQL 是主数据库，用于保存必须可靠、可审计、可恢复的业务事实数据。

包括：

- 用户、角色、权限
- 合同文件元数据
- 合同审查任务
- 合同条款抽取结果
- 条款风险识别结果
- 审批记录
- Agent 步骤
- LLM 调用日志
- 工具调用日志
- RAG 检索日志
- 合规知识库文档元数据
- 文档 chunk 元数据

### 选择优势

相比 MySQL，PostgreSQL 在本项目中的优势：

- JSONB 适合保存模型请求、模型响应、工具参数、合同条款抽取结果、审查 trace 等半结构化数据。
- 可通过 pgvector 同时支持向量检索。
- 事务能力强，适合审批、工具调用、状态流转等一致性要求。
- 复杂查询能力强，适合 Agent Trace 和审查统计分析。

---

## 8. pgvector

### 在项目中的作用

pgvector 是 PostgreSQL 的向量扩展，用于实现合规规则库 RAG 检索。

典型流程：

```text
规则文档上传
 -> 文档解析
 -> 文本切分
 -> 生成 embedding
 -> 写入 pgvector
 -> 审查合同时生成 query embedding
 -> 检索相关规则 / 模板 / 审查规范
 -> 将检索结果提供给 LLM 分析条款风险
```

### 选择优势

第一版推荐 PostgreSQL + pgvector，而不是直接使用独立向量数据库。

原因：

- 部署简单。
- 主数据和向量数据在同一个数据库中，便于关联。
- 权限过滤、部门过滤、知识库过滤可以直接通过 SQL 实现。
- 足够支撑简历项目规模。
- 后续可以通过 VectorStore 抽象迁移到 Qdrant / Milvus。

### 演进方向

```text
第一阶段：PostgreSQL + pgvector
第二阶段：增加 hybrid search、metadata filter、rerank
第三阶段：抽象 VectorStore，支持 Qdrant / Milvus
```

---

## 9. MyBatis Plus

### 在项目中的作用

MyBatis Plus 是数据访问层框架，负责 Java 对象与 PostgreSQL 表之间的数据读写。

用于管理：

- 标准 CRUD
- 分页查询
- 条件查询
- 复杂 SQL
- JSONB 查询
- pgvector 相似度查询

### 选择优势

相比 Spring Data JPA，MyBatis Plus 在本项目中更适合：

- SQL 可控。
- 更自然支持 PostgreSQL JSONB。
- 更方便编写 pgvector 相似度检索 SQL。
- 适合 Agent Trace、LLM 日志、工具调用日志这类复杂查询。
- 国内 Java 岗位认可度高。

### 配套选择

```text
MyBatis Plus
XML Mapper
Spring @Transactional
Flyway
```

---

## 10. Flyway

### 在项目中的作用

Flyway 用于数据库 schema 版本管理。

例如：

```text
V1__init_schema.sql
V2__add_contract_review_tables.sql
V3__add_agent_trace.sql
V4__add_approval.sql
V5__add_pgvector_chunks.sql
```

### 选择优势

- 项目启动时自动迁移数据库。
- 避免手动建表。
- 方便他人本地复现。
- 保证数据库结构随代码版本演进。

---

## 11. Redis

### 在项目中的作用

Redis 是内存型高速数据存储，适合保存高频、短生命周期、快速变化的数据。

在本项目中，Redis 不替代 PostgreSQL。

职责划分：

```text
PostgreSQL：长期业务事实、审计记录、历史数据
Redis：短期状态、缓存、锁、限流、任务进度
```

### 具体用途

#### 1. 缓存热点数据

例如：

- 模型配置
- 审查工具定义
- 用户权限
- 合规知识库配置
- Prompt 模板
- 常用风险规则

#### 2. 短期任务状态

例如：

- 当前合同审查任务执行状态
- SSE 连接状态
- 文档解析进度
- 用户当前查看的审查上下文

长期审查记录仍然写入 PostgreSQL。

#### 3. 限流

用于控制：

- 用户每分钟请求次数
- IP 请求次数
- 每日 token 消耗
- 合同审查任务提交频率
- 高风险审批操作频率

#### 4. 分布式锁

用于避免：

- 同一个审查任务重复执行
- 同一个合同文件重复解析
- 同一份规则文档重复向量化
- 同一个审批回调重复处理

#### 5. 异步任务进度

例如合同审查进度：

```text
上传完成
解析合同中
抽取条款中
检索规则中
分析风险中
等待审批
生成报告中
完成
```

### 选择优势

- 快速读写。
- 支持过期时间。
- 支持原子自增。
- 适合限流、锁、缓存、临时状态。

---

## 12. RabbitMQ

### 在项目中的作用

RabbitMQ 是消息队列，用于可靠异步任务处理。

适合承载：

- 合同解析任务
- 规则文档向量化任务
- 合同审查后台长任务
- 审批通过后继续执行
- 审查工具调用失败重试
- 通知事件

### 推荐队列

```text
contract.review.queue
document.ingest.queue
tool.retry.queue
approval.event.queue
notification.queue
```

### 选择优势

相比 Spring Async：

- 任务不依赖单个 JVM 内线程。
- 支持消费确认。
- 支持失败重试。
- 支持死信队列。
- 更适合可靠异步任务。

相比 Redis Stream：

- 业务 MQ 边界更清晰。
- 路由模型更成熟。
- Java / Spring AMQP 集成成熟。
- 企业项目接受度更高。

相比 Kafka：

- 更适合任务队列和业务事件。
- 部署和理解成本更低。
- 不会为第一版项目引入过重的日志流平台。

---

## 13. 自研轻量 Agent 状态机

### 在项目中的作用

Agent 状态机用于管理一次合同审查任务从创建到完成的状态流转。

合同审查任务可能经历：

- 合同解析
- 条款抽取
- 合规规则检索
- 风险分析
- 人工审批
- 失败重试
- 审批后恢复执行
- 审查报告生成

如果没有状态机，业务逻辑容易变成大量不可维护的 if else。

### 推荐数据模型

```text
AgentTask：一次完整合同审查任务
AgentStep：任务中的每一步执行记录
```

### AgentTask 状态

```text
CREATED
PARSING
EXTRACTING
RETRIEVING_RULES
ANALYZING
WAITING_APPROVAL
GENERATING_REPORT
COMPLETED
FAILED
CANCELLED
```

### AgentStep 类型

```text
CONTRACT_PARSE
CLAUSE_EXTRACTION
RULE_RETRIEVAL
RISK_ANALYSIS
APPROVAL_REQUEST
REPORT_GENERATION
LLM_CALL
TOOL_CALL
```

### 状态机职责

- 校验状态流转是否合法
- 记录状态变更日志
- 驱动下一步执行
- 支持失败重试
- 支持审批后恢复执行
- 支持任务取消

### 选择优势

相比 Spring StateMachine、Flowable、Temporal，自研轻量状态机更适合第一版：

- 简单可控。
- 更容易和合同审查 Agent 逻辑结合。
- 不引入重框架。
- 便于面试讲清楚。
- 后续可演进到 Temporal 或 Flowable。

---

## 14. REST + SSE

### 在项目中的作用

后端接口采用：

```text
REST API + SSE 实时事件流
```

### REST 负责

- 登录
- 上传合同
- 创建合同审查任务
- 查询审查任务详情
- 查询审查步骤和 Agent Trace
- 查询条款风险结果
- 上传合规规则文档
- 管理知识库
- 审批通过 / 驳回 / 要求修改
- 管理审查工具配置

### SSE 负责

- 合同解析进度
- 条款抽取进度
- RAG 规则检索进度
- 风险分析进度
- LLM 流式输出
- 审批等待提示
- 报告生成进度
- 任务完成 / 失败事件

### 推荐接口形式

```text
POST /api/contracts
POST /api/contracts/{contractId}/reviews
GET  /api/reviews/{reviewId}/events
GET  /api/reviews/{reviewId}
GET  /api/reviews/{reviewId}/steps
GET  /api/reviews/{reviewId}/risks
```

推荐采用任务式设计：

```text
先上传合同并创建审查任务，返回 reviewId
再订阅 reviewId 对应的 SSE 事件流
```

### 选择优势

相比 WebSocket：

- SSE 更简单。
- 合同审查场景主要是服务端向客户端单向推送进度。
- 更适合 LLM 流式文本输出。
- 和 HTTP / Spring MVC 集成更自然。

相比单一 POST 流式接口：

- reviewId 可以持久化。
- 前端刷新后仍可查询任务历史。
- 支持审批等待和恢复执行。
- 更符合企业级长任务设计。

---

## 15. Spring Security + JWT + RBAC

### 在项目中的作用

安全模块负责：

- 用户认证
- JWT 校验
- 接口授权
- 角色权限控制
- 审查工具调用权限控制
- 合规知识库访问控制
- 高风险条款审批控制

### 推荐角色

```text
ADMIN
LEGAL_MANAGER
LEGAL_REVIEWER
BUSINESS_USER
VIEWER
```

### 角色说明

```text
ADMIN：管理用户、角色、规则库、系统配置
LEGAL_MANAGER：审批高风险合同或高风险条款
LEGAL_REVIEWER：查看审查结果、补充法务意见、处理一般风险
BUSINESS_USER：上传合同、发起审查、查看自己的审查结果
VIEWER：只读查看授权范围内的审查记录
```

### Agent 项目中的特殊权限

普通系统只控制接口权限，合同审查 Agent 还必须控制：

```text
Agent 是否有权替当前用户调用某个审查工具
Agent 是否可以访问某个合规知识库内容
高风险结论是否必须进入人工审批
```

### 工具权限控制

工具表可设计字段：

```text
tool_name
required_permission
risk_level
approval_required
enabled
```

调用工具前必须经过：

```text
ToolPermissionGuard
```

校验内容：

- 用户是否有权限
- 工具是否启用
- 是否满足部门或数据范围
- 是否需要审批
- 是否命中风控规则

### 知识库权限控制

RAG 检索时必须带权限过滤：

```text
knowledge_base_id
visibility
department_id
allowed_roles
document_status
```

避免模型回答用户无权访问的合规文档内容。

### 选择优势

Spring Security 是 Spring 生态标准安全框架，和 JWT、方法级权限、接口过滤、角色权限集成自然，适合企业级 Java 项目。

---

## 16. Agent Trace 与可观测性

### 在项目中的作用

Agent Trace 用于回答：

```text
这次合同审查 Agent 到底做了什么，为什么这样判断？
```

普通后端日志不足以解释 Agent 行为，因此需要自定义 Agent Trace。

### 系统可观测性

推荐：

```text
Spring Boot Actuator
Micrometer
结构化日志
Prometheus + Grafana 可选
OpenTelemetry 可选
```

### Agent 可观测性

建议记录：

- 审查任务
- 审查步骤
- 合同解析结果
- 条款抽取结果
- LLM 调用日志
- Tool 调用日志
- RAG 检索日志
- Prompt 版本
- Token 消耗
- 审批记录
- 状态流转历史

### 推荐表

```text
contract
contract_review
contract_clause
clause_risk
agent_step
llm_call_log
tool_call_log
retrieval_log
agent_state_transition_log
```

### 选择优势

- 支持审查链路回放。
- 支持失败排查。
- 支持 token 成本统计。
- 支持审查工具调用审计。
- 支持 RAG 引用溯源。
- 支持高风险条款审批追责。

---

## 17. 测试策略

### 技术栈

```text
JUnit 5
Mockito
AssertJ
Spring Boot Test
Testcontainers
WireMock 可选
```

### 单元测试重点

- Agent 状态机
- ToolPermissionGuard
- Prompt 渲染
- Token 预算计算
- RAG chunk 权限过滤
- 审批规则
- LLM 响应 JSON 解析
- 条款风险规则匹配

### 集成测试重点

使用 Testcontainers 启动：

```text
PostgreSQL + pgvector
Redis
RabbitMQ
```

覆盖：

- 合同审查任务入库
- RabbitMQ 消息消费
- 规则文档 chunk 写入与检索
- Redis 限流
- 审批事件驱动任务恢复

### LLM 测试策略

不在测试中真实调用大模型 API。

推荐：

```text
Mock 自定义 LLM Gateway
WireMock 模拟模型供应商 API
```

---

## 18. Docker Compose 部署

### 在项目中的作用

Docker Compose 用于本地一键启动完整开发环境。

推荐组件：

```text
backend
frontend
postgres-pgvector
redis
rabbitmq
```

可选组件：

```text
prometheus
grafana
```

### 选择优势

- 本地可复现。
- 方便面试演示。
- 降低环境搭建成本。
- 不必第一版引入 Kubernetes。

Kubernetes 可作为后续扩展方向，不建议第一版实现。

---

## 19. 前端技术选型

### 推荐技术栈

```text
React
TypeScript
Vite
Ant Design
```

### 在项目中的作用

前端不是项目核心，但需要能够直观展示所有后端功能设计。

建议前端定位为：

```text
企业合同审查 Agent 工作台
```

而不是营销型官网或传统桌面 GUI。

### 推荐展示能力

- 登录页
- 工作台 Dashboard
- 合同列表页
- 合同上传页
- 合同审查详情页
- SSE 实时审查过程
- 风险点列表
- RAG 检索引用
- 审批中心
- 合规知识库问答页
- 规则库管理页
- Agent Trace 详情页

### 选择优势

React + TypeScript + Vite：

- 开发体验好。
- 适合构建交互式 Agent 工作台。
- SSE 流式展示和复杂状态管理更方便。
- 生态组件丰富。

Ant Design：

- 适合后台管理系统。
- 表格、表单、弹窗、时间线、Tabs、通知组件成熟。
- 能快速构建专业的企业应用界面。

---

## 20. 项目模块结构

### 推荐架构

```text
模块化单体
```

第一版建议采用单 Spring Boot 工程，内部按 package 拆分模块。

### 推荐后端 package

```text
com.example.lexiflow
├── common
├── config
├── security
├── user
├── contract
├── review
├── agent
├── llm
├── rag
├── tool
├── approval
├── trace
└── infrastructure
```

### 模块职责

```text
common：通用响应、异常、工具类
config：Spring 配置
security：认证、JWT、权限
user：用户、角色、权限
contract：合同文件、合同元数据、合同文本
review：审查任务、条款风险、审查报告
agent：AgentTask、AgentStep、状态机、执行器
llm：LLM Gateway、Prompt、模型调用日志
rag：合规知识库、文档 chunk、向量检索
tool：审查工具注册、工具执行、工具权限
approval：高风险合同或条款人工审批
trace：Agent 审查链路记录与查询
infrastructure：Redis、RabbitMQ、外部系统适配
```

### 为什么不第一版上微服务

微服务会引入：

- 服务注册
- 网关
- 服务间调用
- 分布式事务
- 链路追踪
- 多服务部署
- 接口版本管理

这些复杂度会分散 Agent 核心能力建设。

模块化单体的优势：

- 部署简单。
- 调试简单。
- 本地容易启动。
- 事务处理直接。
- 代码边界仍然清晰。
- 后续可拆分微服务。

---

## 21. 当前最终选型结论

### 后端

```text
Java 21
Spring Boot 3.x
Spring MVC
Spring Security + JWT + RBAC
Spring AI
自定义 LLM Gateway
PostgreSQL + pgvector
MyBatis Plus
Flyway
Redis
RabbitMQ
自研轻量 Agent 状态机
REST + SSE
Actuator + Micrometer + Agent Trace
JUnit 5 + Mockito + Testcontainers
Docker Compose
```

### 前端

```text
React
TypeScript
Vite
Ant Design
SSE
```

### 架构

```text
模块化单体
```

### 项目简历表达方向

```text
基于 Java 21、Spring Boot 与 Spring AI 构建企业合同与合规审查 Agent 平台，采用模块化单体架构，集成 PostgreSQL + pgvector、Redis、RabbitMQ、Spring Security 和自研 Agent 状态机，实现合同条款抽取、RAG 合规规则检索、风险识别、人工审批、异步审查任务、流式响应和 Agent Trace 可观测能力。
```
