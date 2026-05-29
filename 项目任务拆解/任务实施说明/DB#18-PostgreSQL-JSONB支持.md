# DB#18-PostgreSQL JSONB 支持

## 任务目标

为项目中所有 JSONB 半结构化字段建立 GIN 索引，支持高效的 JSONB 查询和过滤。

## 对应总览编号

```text
DB#18
```

## 涉及模块

```text
database: 全部含 JSONB 列的表
backend: contract, review, agent, llm, tool, rag
```

## 本次实现

`V8__enhance_knowledge_and_jsonb.sql` 为以下 JSONB 字段添加 GIN 索引：

| 表 | JSONB 字段 | 查询场景 |
| --- | --- | --- |
| contract | metadata | 按自定义属性、标签、部门筛选 |
| contract_review | result_summary | 按风险分布、条款数量统计 |
| agent_step | input_summary, output_summary | 按步骤输入输出内容追溯 |
| llm_call_log | request_body, response_body | 按模型请求响应内容排查 |
| knowledge_document | metadata | 按文档自定义字段过滤 |
| document_chunk | metadata | 按 chunk 自定义属性过滤 |
| knowledge_base | allowed_roles | 按角色权限包含查询 |

## 验收标准

- 支持 `metadata @> '{"key":"value"}'::jsonb` 高效查询
- 支持 `allowed_roles ?| array['ADMIN','LEGAL_MANAGER']` 角色包含查询
- GIN 索引不阻塞 INSERT/UPDATE 性能

## 关键文件记录

```text
backend/src/main/resources/db/migration/V8__enhance_knowledge_and_jsonb.sql
```

## 验证记录

```text
mvn compile 通过
```

## 遗留问题

```text
后续可引入 JSON Path 查询（PostgreSQL 12+ 原生支持）
```
