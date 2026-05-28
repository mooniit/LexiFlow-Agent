# DB#01-数据库初始化与Flyway

## 任务目标

使用 Flyway 管理数据库 schema 版本，保证本地环境可复现。

## 本次实现

- 引入 `flyway-core` 和 `flyway-database-postgresql`。
- 新建 `backend/src/main/resources/db/migration/V1__init_schema.sql`。
- 迁移脚本包含 pgvector 扩展、RBAC 基础表、合同/审查/Trace/知识库/日志等基础表。

## 验证方式

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

应用连接 PostgreSQL 后会自动执行迁移。

