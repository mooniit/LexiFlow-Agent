# DEP#07-RabbitMQ 队列声明

## 任务目标

声明合同审查 Agent 所需 RabbitMQ 队列、交换机与路由绑定。

## 本次实现

- 新增 `RabbitMqNames` 统一维护交换机、队列名、routing key。
- 新增持久化 DirectExchange：`lexiflow.agent.exchange`。
- 声明并绑定：
  - `contract.review.queue`
  - `document.ingest.queue`
  - `tool.retry.queue`
  - `approval.event.queue`
  - `notification.queue`

## 验证记录

```text
mvn test 通过，13 tests, 0 failures
```

