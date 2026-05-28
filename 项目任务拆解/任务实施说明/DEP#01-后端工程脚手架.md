# DEP#01-后端工程脚手架

## 任务目标

建立 Java 21 + Spring Boot 3.x 后端工程，为后续功能模块开发提供统一工程结构。

## 本次实现

- 新建 `backend/pom.xml`。
- 新建 Spring Boot 启动类 `LexiFlowApplication`。
- 按模块化单体预创建 package：`common`、`config`、`security`、`user`、`contract`、`review`、`agent`、`llm`、`rag`、`tool`、`approval`、`trace`、`infrastructure`。
- 引入 Web、Security、Redis、AMQP、Actuator、Flyway、PostgreSQL、MyBatis Plus、JWT、Testcontainers 等基础依赖。

## 验证方式

```bash
cd backend
mvn test
```

当前本机未检测到 Maven，且 Java 版本为 17；运行前需要安装 Maven 并切换 Java 21。

