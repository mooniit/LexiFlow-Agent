# DB#13-文档 chunk 与向量表

## 任务目标

完善 document_chunk 表，支持文档切分后的 chunk 存储、向量检索和 metadata 过滤。

## 对应总览编号

```text
DB#13
```

## 涉及模块

```text
database: document_chunk
backend: rag
```

## 本次实现

- `V1__init_schema.sql` 已创建 `document_chunk` 表，含 VECTOR(1536) 列和 ivfflat 索引。
- `V8__enhance_knowledge_and_jsonb.sql` 新增 `idx_document_chunk_metadata` GIN 索引，支持按 metadata 字段过滤。
- `V13__pgvector_search.sql` 创建 `search_chunks_by_similarity` 函数，支持带权限过滤的向量相似度检索：
  - 按相似度阈值和数量限制
  - 按知识库 ID 过滤
  - 按文档类型过滤
  - 按角色权限过滤（allowed_roles JSONB）

## 验收标准

- VECTOR(1536) 列可存储 OpenAI text-embedding-ada-002 格式向量
- ivfflat 索引基于 cosine 距离
- metadata GIN 索引支持高效过滤
- 相似度检索函数同时应用权限过滤和向量排序
- probes 默认为 10

## 关键文件记录

```text
backend/src/main/resources/db/migration/V1__init_schema.sql (基础表)
backend/src/main/resources/db/migration/V8__enhance_knowledge_and_jsonb.sql (GIN 索引)
backend/src/main/resources/db/migration/V13__pgvector_search.sql (检索函数)
```

## 验证记录

```text
mvn compile 通过
```

## 遗留问题

```text
后续可增强：HNSW 索引、混合检索（向量+关键词）、rerank
```
