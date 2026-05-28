# TEST#01-单元测试基础配置

## 任务目标

建立后端测试基础，支持后续单元测试和集成测试。

## 本次实现

- 引入 `spring-boot-starter-test`。
- 引入 `spring-security-test`。
- 引入 Testcontainers JUnit、PostgreSQL、RabbitMQ 依赖。
- 新建基础测试 `LexiFlowApplicationTests`。

## 验证方式

```bash
cd backend
mvn test
```

当前本机未检测到 Maven，且 Java 版本为 17；运行前需要安装 Maven 并切换 Java 21。

