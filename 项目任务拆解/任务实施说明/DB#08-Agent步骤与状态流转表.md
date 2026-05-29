# DB#08-Agent 步骤与状态流转表

## 任务目标

完善 agent_step 与 agent_state_transition_log 表，记录审查步骤执行详情与状态变更历史。

## 对应总览编号

```text
DB#08
```

## 涉及模块

```text
database: agent_step, agent_state_transition_log
backend: agent, trace
```

## 本次实现

- `V1__init_schema.sql` 已创建两张基础表。
- `V7__enhance_agent_and_log_tables.sql` 补充索引：

**agent_step**：记录审查任务中每一步（合同解析/条款抽取/规则检索/风险分析/审批请求/报告生成/LLM调用/工具调用）的执行情况，包括输入输出摘要、开始结束时间和错误信息。

| 新增索引 | 用途 |
| --- | --- |
| idx_agent_step_step_type | 按步骤类型筛选 |
| idx_agent_step_status | 按执行状态筛选 |
| idx_agent_step_started_at | 按开始时间排序 |

**agent_state_transition_log**：记录审查任务每次状态变更（from_status → to_status），用于审计追溯。

| 新增索引 | 用途 |
| --- | --- |
| idx_agent_state_transition_log_review_id | 按审查任务查询流转历史 |
| idx_agent_state_transition_log_created_at | 按变更时间排序 |

## 验收标准

- agent_step 与 AgentStepType 枚举（CONTRACT_PARSE / CLAUSE_EXTRACTION / RULE_RETRIEVAL / RISK_ANALYSIS / APPROVAL_REQUEST / REPORT_GENERATION / LLM_CALL / TOOL_CALL）对齐
- agent_state_transition_log 与 AgentTaskStatus 枚举对齐
- started_at/finished_at 支持计算步骤耗时
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
无
```
