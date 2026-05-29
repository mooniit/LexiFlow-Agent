# TEST#03-ToolPermissionGuard 测试

## 覆盖范围

- 工具启用且权限满足时允许调用。
- 用户缺少 required_permission 时拒绝。
- 工具禁用时拒绝。
- approval_required 工具缺少审批权限时拒绝。
- 未注册工具拒绝。

## 验证记录

```text
mvn test 通过，22 tests, 0 failures
```

