# DB#09-LLM 调用日志表

## 任务目标

完善 llm_call_log 表，记录每次大模型调用的完整信息，支持成本统计和问题排查。

## 对应总览编号

```text
DB#09
```

## 涉及模块

```text
database: llm_call_log
backend: llm, trace
```

## 本次实现

- `V1__init_schema.sql` 已创建 `llm_call_log` 表，包含模型供应商、模型名称、Prompt 版本、请求/响应体（JSONB）、token 消耗、耗时、成功/失败标记和错误信息。
- `V7__enhance_agent_and_log_tables.sql` 补充索引：

| 新增索引 | 用途 |
| --- | --- |
| idx_llm_call_log_review_id | 按审查任务关联查询 |
| idx_llm_call_log_provider | 按供应商统计 |
| idx_llm_call_log_model_name | 按模型统计 |
| idx_llm_call_log_success | 按成功/失败筛选 |
| idx_llm_call_log_created_at | 按时间排序和统计 |

## 验收标准

- 支持按 review_id 追溯单次审查中的所有 LLM 调用
- JSONB 存储完整的 request_body / response_body，便于结构化查询
- token 字段支持按模型、供应商、时段统计消耗和成本
- 索引覆盖常用查询和统计路径

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
无
```
