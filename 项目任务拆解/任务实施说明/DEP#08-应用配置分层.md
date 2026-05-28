# DEP#08-应用配置分层

## 任务目标

建立可维护的配置分层，区分默认配置、开发环境和测试环境。

## 本次实现

- 新建 `application.yml`：应用名、虚拟线程、端口、Actuator、MyBatis Plus、LexiFlow 自定义配置。
- 新建 `application-dev.yml`：PostgreSQL、Flyway、Redis、RabbitMQ 开发环境连接。
- 新建 `application-test.yml`：测试环境连接占位。

## 后续补充

- 增加模型供应商配置。
- 增加 JWT secret 和过期时间配置。
- 增加文件大小限制、SSE 超时、任务重试参数。

