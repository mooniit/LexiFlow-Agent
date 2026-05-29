# TEST#08-LLM JSON 解析测试

## 覆盖范围

当前项目仍使用 Mock LLM Gateway，已覆盖结构化输出 envelope：

- 返回 scenario。
- 返回 mock 标记。
- 结构化输出与普通 chat 请求解耦。

错误 JSON、缺失字段等真实模型输出容错将在 Prompt 模板和真实 LLM 适配器实现后继续补充。

## 验证记录

```text
mvn test 通过，22 tests, 0 failures
```

## 后续补充

当前为 DOING。后续在真实 LLM 适配器、Prompt 渲染和结构化响应 schema 完成后，补充错误 JSON、缺失字段、类型不匹配与降级处理测试。
