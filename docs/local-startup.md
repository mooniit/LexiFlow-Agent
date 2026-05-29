# 本地启动说明

## 前置条件

```text
Java 21
Maven 3.9+
Docker Desktop
Node.js 24+
```

## 启动中间件

```powershell
cd D:\AAA-myProject\LexiFlow-Agent
docker compose up -d postgres redis rabbitmq
```

## 启动后端

默认使用 Mock LLM。如果需要启用真实 LLM Gateway，请先在同一个 PowerShell 终端配置：

```powershell
$env:LEXIFLOW_LLM_PROVIDER="deepseek"
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
```

文本模型能力使用 `deepseek-v4-flash`，Embedding 使用 `text-embedding-v4`。真实密钥只放环境变量或本地 `.env`，不要提交到 Git。

```powershell
cd D:\AAA-myProject\LexiFlow-Agent\backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 启动前端

```powershell
cd D:\AAA-myProject\LexiFlow-Agent\frontend
npm.cmd install
npm.cmd run dev
```

## 验证

```powershell
cd D:\AAA-myProject\LexiFlow-Agent\backend
mvn test

cd D:\AAA-myProject\LexiFlow-Agent\frontend
npm.cmd run build
```

## 健康检查

```text
GET http://localhost:8080/api/actuator/health
GET http://localhost:8080/api/actuator/health/readiness
GET http://localhost:8080/api/actuator/health/liveness
GET http://localhost:8080/api/actuator/metrics
```

## 默认账号

```text
admin / admin123
```

## 中间件地址

```text
PostgreSQL: localhost:5432
Redis: localhost:6379
RabbitMQ: localhost:5672
RabbitMQ 管理台: http://localhost:15672
```
