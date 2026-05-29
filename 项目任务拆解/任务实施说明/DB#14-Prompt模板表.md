# DB#14-Prompt 模板表

## 任务目标

创建 prompt_template 表，支持 Prompt 模板的版本管理和场景化复用。

## 对应总览编号

```text
DB#14
```

## 涉及模块

```text
database: prompt_template
backend: llm
```

## 本次实现

`V9__create_prompt_template.sql` 新建表：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGSERIAL PK | 主键 |
| name | VARCHAR(128) | 模板名称 |
| version | VARCHAR(32) | 版本号 |
| scene | VARCHAR(64) | 适用场景枚举 |
| description | TEXT | 模板描述 |
| template_content | TEXT | 模板正文，使用 {{variable}} 占位符 |
| variables | JSONB | 变量定义与是否必填 |
| output_constraints | JSONB | 输出约束（格式、长度等） |
| enabled | BOOLEAN | 是否启用 |

场景枚举：CLAUSE_EXTRACTION / RISK_ANALYSIS / RULE_EXPLANATION / SUGGESTION_GENERATION / REPORT_GENERATION / KNOWLEDGE_QA

唯一约束：(name, version) 在未删除记录中保证唯一。

## 验收标准

- 支持多场景独立模板管理
- 变量定义 JSONB 支持 LLM Gateway 渲染时校验
- 版本号支持同模板多版本共存
- 启用/禁用支持灰度切换

## 关键文件记录

```text
backend/src/main/resources/db/migration/V9__create_prompt_template.sql
```

## 验证记录

```text
mvn compile 通过
```

## 遗留问题

```text
后续可增强：模板 A/B 测试、调用频次统计、自动版本递增
```
