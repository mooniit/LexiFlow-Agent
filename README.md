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

## 启动中间件

```bash
docker compose up -d postgres redis rabbitmq
```

## 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

健康检查：

```text
GET http://localhost:8080/api/actuator/health
```

## 启动前端

```bash
cd frontend
npm install
npm run dev
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
