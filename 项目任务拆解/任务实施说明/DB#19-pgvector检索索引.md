# DB#19-pgvector 检索索引

## 任务目标

配置 pgvector 向量检索能力，创建带权限过滤的相似度检索函数。

## 对应总览编号

```text
DB#19
```

## 涉及模块

```text
database: document_chunk, knowledge_base, knowledge_document
backend: rag
```

## 本次实现

`V13__pgvector_search.sql`：

**IVFFlat 调优**：
- 设置 `ivfflat.probes = 10`，平衡检索精度与性能

**相似度检索函数 `search_chunks_by_similarity`**：
- 输入：query_embedding VECTOR(1536)、相似度阈值、返回数量
- 过滤：knowledge_base_id、document_type、allowed_roles（JSONB 包含）
- 仅返回 document_status = 'PUBLISHED' 且 knowledge_base status = 'ACTIVE' 的 chunk
- 按 cosine 距离升序（越相似越靠前）
- 结果包含 chunk_id、文档标题、chunk 内容、相似度分数、metadata

## 调用示例

```sql
SELECT * FROM search_chunks_by_similarity(
    query_embedding := '[0.1, 0.2, ...]'::VECTOR(1536),
    match_threshold := 0.7,
    match_count := 10,
    p_knowledge_base_id := 1,
    p_allowed_roles := '["LEGAL_REVIEWER"]'::jsonb
);
```

## 验收标准

- 函数支持按知识库、文档类型、角色三重过滤下的向量检索
- ivfflat.probes 设为 10
- 仅返回已发布且未删除的 chunk
- cosine 距离作为相似度度量

## 关键文件记录

```text
backend/src/main/resources/db/migration/V1__init_schema.sql (基础表 + ivfflat 索引)
backend/src/main/resources/db/migration/V13__pgvector_search.sql (检索函数 + probes 配置)
```

## 验证记录

```text
mvn compile 通过
```

## 遗留问题

```text
后续可增强：HNSW 索引、语义+关键词混合检索、rerank 服务集成
```
