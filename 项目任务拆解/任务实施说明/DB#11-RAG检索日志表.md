# DB#11-RAG 检索日志表

## 任务目标

完善 retrieval_log 表，记录 RAG 检索的查询文本、过滤条件、命中结果和耗时，支持检索质量分析。

## 对应总览编号

```text
DB#11
```

## 涉及模块

```text
database: retrieval_log
backend: rag, trace
```

## 本次实现

- `V1__init_schema.sql` 已创建 `retrieval_log` 表，包含查询文本、过滤条件（JSONB）、命中文档 chunk（JSONB）和耗时。
- `V7__enhance_agent_and_log_tables.sql` 补充索引：

| 新增索引 | 用途 |
| --- | --- |
| idx_retrieval_log_review_id | 按审查任务关联查询 |
| idx_retrieval_log_created_at | 按时间排序 |

## 验收标准

- 支持按 review_id 追溯单次审查的 RAG 检索过程
- retrieved_chunks JSONB 存储命中文档、chunk 文本和相似度分数，支持引用溯源
- filter_conditions JSONB 记录权限过滤条件，便于审核过滤是否正确
- 索引覆盖常用查询路径

## 关键文件记录

```text
backend/src/main/resources/db/migration/V1__init_schema.sql (基础表)
backend/src/main/resources/db/migration/V7__enhance_agent_and_log_tables.sql (索引)
```

## 验证记录

```text
mvn compile 通过
```

## 遗留问题

```text
后续可增强：检索质量评分、用户反馈（有用/无用）、rerank 结果记录
```
