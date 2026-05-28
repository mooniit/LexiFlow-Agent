# DEP#04-PostgreSQL与pgvector服务

## 任务目标

提供 PostgreSQL 主库和 pgvector 向量检索扩展。

## 本次实现

- 使用 `pgvector/pgvector:pg16` 镜像。
- 默认数据库、用户名、密码均为 `lexiflow`。
- Flyway 首个迁移脚本中执行 `CREATE EXTENSION IF NOT EXISTS vector;`。
- `document_chunk.embedding` 字段使用 `VECTOR(1536)`。

## 验证方式

```bash
docker compose up -d postgres
```

应用启动后 Flyway 应自动创建基础表和 vector 扩展。

