# DEP#03-DockerCompose本地环境

## 任务目标

提供本地可复现开发环境，便于启动 PostgreSQL + pgvector、Redis、RabbitMQ。

## 本次实现

- 新建根目录 `docker-compose.yml`。
- 新建 `.env.example`。
- 编排服务：
  - `postgres`
  - `redis`
  - `rabbitmq`
- 为三个中间件配置数据卷、端口和 healthcheck。

## 验证方式

```bash
docker compose up -d postgres redis rabbitmq
docker compose ps
```

当前本机未检测到 Docker 命令，需要安装 Docker Desktop 后验证。

