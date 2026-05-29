# DEP#10-Actuator 与健康检查

## 任务目标

暴露应用、数据库、Redis、RabbitMQ 健康检查与基础指标，支持本地开发和后续部署探针。

## 本次实现

- 配置 Actuator 暴露 `health`、`info`、`metrics`。
- 启用 readiness/liveness probes。
- 保留 Spring Boot 自动健康检查：数据库、Redis、RabbitMQ、磁盘空间等。
- 配置应用 info 元数据。

## 验证记录

```text
mvn test 通过，22 tests, 0 failures
```
