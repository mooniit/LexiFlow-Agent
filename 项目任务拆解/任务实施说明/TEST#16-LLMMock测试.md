# TEST#16-LLM Mock 测试

## 覆盖范围

- Mock chat 返回确定性内容。
- Mock embedding 对每段文本返回向量。
- Mock toolCalling 选择工具。
- Mock structuredOutput 返回结构化 envelope。
- Mock streamChat 按 token 回调输出。

## 验证记录

```text
mvn test 通过，22 tests, 0 failures
```

## 后续补充

当前为 DOING。后续在真实 LLM 客户端接入后，补充 WireMock 或本地 HTTP stub，覆盖超时、限流、错误响应和重试场景。
