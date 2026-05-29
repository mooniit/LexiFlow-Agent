# LexiFlow Agent

企业合同与合规审查 Agent 项目。

## 当前启动基础

已完成最小启动顺序的工程基础文件：

```text
DEP#01 后端工程脚手架
DEP#03 Docker Compose 本地环境
DEP#04 PostgreSQL + pgvector 服务
DEP#05 Redis 服务
DEP#06 RabbitMQ 服务
DEP#08 应用配置分层
DEP#09 文件存储目录
DB#01 数据库初始化与 Flyway
DB#20 审计字段规范
TEST#01 单元测试基础配置
```

## 本地前置条件

```text
Java 21
Maven 3.9+
Docker Desktop / Docker Compose
```

当前仓库按 Java 21 配置。若本机仍是 Java 17，需要先安装或切换到 Java 21。

## 一键启动顺序

### 1. 启动中间件

```bash
docker compose up -d postgres redis rabbitmq
```

### 2. 配置 LLM Provider

默认使用 `mock`，不需要外部密钥。若要启用真实模型，在当前终端设置环境变量：

```powershell
$env:LEXIFLOW_LLM_PROVIDER="deepseek"
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
```

文本模型能力 `chat`、`streamChat`、`structuredOutput`、`toolCalling` 默认使用 `deepseek-v4-flash`；Embedding 默认使用 `text-embedding-v4`。

可选覆盖项：

```powershell
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_CHAT_MODEL="deepseek-v4-flash"
$env:DASHSCOPE_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:DASHSCOPE_EMBEDDING_MODEL="text-embedding-v4"
```

不要把真实密钥写入仓库。`.env` 已在 `.gitignore` 中忽略。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. 检查健康状态

```text
GET http://localhost:8080/api/actuator/health
GET http://localhost:8080/api/actuator/health/readiness
GET http://localhost:8080/api/actuator/metrics
```

健康检查覆盖：

```text
application
PostgreSQL
Redis
RabbitMQ
diskSpace
readiness/liveness probes
```

### 5. 启动前端

```bash
cd frontend
npm.cmd install
npm.cmd run dev
```

前端地址：

```text
http://localhost:5173
```

RabbitMQ 管理台：

```text
http://localhost:15672
user: lexiflow
password: lexiflow
```

## 运行测试

```bash
cd backend
mvn test
```

前端构建：

```bash
cd frontend
npm.cmd run build
```

## 常用本地地址

```text
后端 API: http://localhost:8080/api
Actuator: http://localhost:8080/api/actuator
前端: http://localhost:5173
RabbitMQ 管理台: http://localhost:15672
```

## 默认开发账号

```text
username: admin
password: admin123
```
