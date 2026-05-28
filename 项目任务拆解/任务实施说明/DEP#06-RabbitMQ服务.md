# DEP#06-RabbitMQ服务

## 任务目标

提供可靠异步任务处理能力。

## 本次实现

- 在 `docker-compose.yml` 中添加 `rabbitmq:3.13-management`。
- 默认账号密码为 `lexiflow/lexiflow`。
- 在 `RabbitMqConfig` 中声明五个持久化队列：
  - `contract.review.queue`
  - `document.ingest.queue`
  - `tool.retry.queue`
  - `approval.event.queue`
  - `notification.queue`

## 验证方式

```bash
docker compose up -d rabbitmq
```

管理台地址：`http://localhost:15672`。

