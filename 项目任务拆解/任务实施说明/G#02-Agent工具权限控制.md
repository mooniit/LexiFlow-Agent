# G#02-Agent 工具权限控制

## 任务目标

在 Agent 调用审查工具前统一校验工具是否注册、是否启用、用户是否具备所需权限，以及高风险工具是否需要审批权限。

## 本次实现

- 新增 `review_tool_config` 表与默认工具配置种子。
- 新增 `ReviewToolConfig`、`ReviewToolConfigMapper`。
- 新增 `ToolPermissionGuard`。
- 接入合同解析、条款抽取、规则检索、风险分析链路。
- 新增 `ToolPermissionGuardTest`。

## 验证记录

```text
mvn test 通过，13 tests, 0 failures
```

