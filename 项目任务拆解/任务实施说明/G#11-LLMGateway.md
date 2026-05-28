# G#11-LLM Gateway

## 任务目标

统一大模型访问入口，避免业务代码直接依赖某个厂商 SDK 或 Spring AI 具体实现。

## 本次实现

- 新增 `LlmGateway` 接口。
- 支持 `chat`、`streamChat`、`embed`、`structuredOutput`、`toolCalling`。
- 新增 Chat、Embedding、Structured Output、TokenUsage 等请求响应对象。
- 新增 Tool Calling 请求响应对象。
- 新增 `MockLlmGateway`，默认通过 `lexiflow.llm.provider=mock` 启用。
- 新增 Mock Gateway 单元测试。

## 关键文件

```text
backend/src/main/java/com/example/lexiflow/llm/service/LlmGateway.java
backend/src/main/java/com/example/lexiflow/llm/service/MockLlmGateway.java
backend/src/main/java/com/example/lexiflow/llm/model
backend/src/test/java/com/example/lexiflow/llm/service/MockLlmGatewayTest.java
```

## 后续补充

- 接入 Spring AI ChatClient / EmbeddingModel。
- 记录 LLM 调用日志。
- Prompt 版本管理。
- 超时、重试、降级和 token 预算。
