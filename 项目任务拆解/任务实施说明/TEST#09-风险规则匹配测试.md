# TEST#09-风险规则匹配测试

## 覆盖范围

- 无限责任条款识别为 HIGH 风险并要求审批。
- 超长付款周期识别为 MEDIUM 风险。

## 验证记录

```text
mvn test 通过，22 tests, 0 failures
```

## 后续补充

当前为 DOING。后续在金额阈值、违约责任、保密期限、管辖条款等规则库扩展后，继续补齐多风险类型和 requires_approval 判断矩阵。
