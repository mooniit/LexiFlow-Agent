# G#11-DeepSeek 与 DashScope 真实网关

## 实施内容

- 新增 `DeepSeekLlmGateway`，在 `LEXIFLOW_LLM_PROVIDER=deepseek` 时启用。
- 文本模型能力 `chat`、`streamChat`、`structuredOutput`、`toolCalling` 统一调用 DeepSeek Chat Completions。
- 默认文本模型为 `deepseek-v4-flash`。
- Embedding 调用 DashScope OpenAI 兼容 Embeddings API。
- 默认 Embedding 模型为 `text-embedding-v4`。
- API Key 仅从环境变量读取：`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`。
- `.env` 已由 `.gitignore` 忽略，仓库只保留无密钥的 `.env.example`。

## 验证记录

```text
mvn test 通过，27 tests, 0 failures
```

## 后续补充

后续可继续补充真实 HTTP stub / WireMock 测试，覆盖超时、限流、非 2xx、SSE 中断、结构化 JSON 不合法和工具参数异常等场景。
