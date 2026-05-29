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

