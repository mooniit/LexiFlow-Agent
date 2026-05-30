# I1-知识库文档导入与Agent审查管线就绪

## 本阶段目标

将 11 份真实中国法律文本接入 RAG 知识库，打通"文档导入→文本解析→法条切片→Embedding 生成→pgvector 入库→相似度检索"的完整 Agent 审查管线。

## 已完成工作

### 1. 法律文本获取

从 GitHub `risshun/Chinese_Laws` 拉取 + 用户补充，`storage/knowledge/` 下现有：

| 文件 | 格式 | 大小 |
|------|------|------|
| 中华人民共和国民法典 | .md | 335KB |
| 企业破产法 | .md | 46KB |
| 劳动合同法 | .md | 36KB |
| 劳动法 | .md | 27KB |
| 反垄断法 | .md | 26KB |
| 公司法 | .docx | 67KB |
| 网络安全法 | .docx | 33KB |
| 个人信息保护法 | .docx | 31KB |
| 招标投标法 | .docx | 24KB |
| 数据安全法 | .docx | 21KB |
| 电子签名法 | .docx | 20KB |

### 2. 知识库摄入管线修复

**问题**：`KnowledgeBaseService` 有三处断点——docx 内容读取始终为空、不支持 md 格式、ingestChunks 不生成 embedding。

**修复**：

| 断点 | 修复 |
|------|------|
| docx 解析未接入 | `ContractTextParser` 新增 `parsePath()` 公开方法，`KnowledgeBaseService` 调用它解析 docx |
| md 文件不支持 | `uploadDocument` 文件类型检测扩展为 txt/md/docx |
| embedding 未生成 | `ingestChunks` 接入 `LlmGateway.embed()`，切片后批量调 DashScope |

### 3. pgvector 类型映射

- 新增 `com.pgvector:pgvector:0.1.6` 依赖
- 新增 `VectorTypeHandler`，实现 Java String ↔ pgvector VECTOR(1536) 双向转换
- `DocumentChunk.embedding` 从 `@TableField(exist = false)` 改为 VectorTypeHandler 持久化

### 4. 法条感知切片策略

`splitByArticlesWithMeta()`:

```
第463条 → 第464条 → 第465条  → chunk_0 {"articles":["第463条","第464条","第465条"]}
第466条 → 第467条 (超长)       → chunk_1 {"articles":["第466条","第467条"]}
第467条 (后半段)                → chunk_2 {"articles":["第467条"]}
```

- 以 `第X条` 为原子边界，不跨法条合并
- 目标 800 字/chunk，超长法条优先在换行/句号处安全切分
- 每 chunk 的 metadata 记录所属法条，检索时可追溯到具体条文

### 5. 批量导入端点

- `KnowledgeBaseService.batchImportFromStorage()` — 扫描 `storage/knowledge/` 下所有 md/docx/txt，自动导入为 knowledge_document + document_chunk
- `POST /knowledge-bases/{id}/batch-import` — REST 端点触发

### 6. LLM Gateway 双模型架构

`DeepSeekLlmGateway`（`lexiflow.llm.provider=deepseek` 激活）：

```
chat / streamChat / structuredOutput / toolCalling  → DeepSeek API (deepseek-v4-flash)
embed                                               → DashScope API (text-embedding-v4, 1536维)
```

| 环境变量 | 说明 |
|------|------|
| `LEXIFLOW_LLM_PROVIDER` | 设为 `deepseek` 启用真实模型 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key |

## 数据流全景

```
storage/knowledge/*.md|docx
        │
        ▼
ContractTextParser.parsePath()         ← Apache POI + 纯文本
        │
        ▼
splitByArticlesWithMeta()              ← 法条边界 + article metadata
        │
        ▼
DashScope text-embedding-v4            ← 1536 维向量
        │
        ▼
document_chunk (pgvector + GIN)        ← VectorTypeHandler
        │
        ▼
search_chunks_by_similarity()          ← cosine 距离 + 权限过滤
        │
        ▼
RAG 检索结果（带法条引用）           ← chunk.metadata.articles
```

## 关键文件记录

```text
backend/src/main/java/com/example/lexiflow/common/persistence/VectorTypeHandler.java
backend/src/main/java/com/example/lexiflow/contract/service/ContractTextParser.java
backend/src/main/java/com/example/lexiflow/rag/service/KnowledgeBaseService.java
backend/src/main/java/com/example/lexiflow/rag/api/KnowledgeBaseController.java
backend/src/main/java/com/example/lexiflow/rag/model/DocumentChunk.java
backend/src/main/java/com/example/lexiflow/llm/service/DeepSeekLlmGateway.java
backend/pom.xml
storage/knowledge/ (11 份法律文档)
```

## 验证情况

```text
backend: mvn compile 通过
```

未完成验证：

```text
后端启动 + batch-import + RAG 检索（需先设 LEXIFLOW_LLM_PROVIDER=deepseek 并启动后端）
```

## 已提交记录

```text
acd7243 fix: wire docx/md parsing, embedding, and pgvector type handler for knowledge ingestion
bcf3253 fix: track article references in chunk metadata for traceable retrieval
ba6d1cc feat: add batch knowledge import from storage directory
```

## 下一步建议

```text
1. 设 LEXIFLOW_LLM_PROVIDER=deepseek，启动后端
2. 调 POST /knowledge-bases/1/batch-import 批量导入
3. 调 POST /knowledge-bases/search 验证 RAG 检索
4. 推进 G#12 Prompt 模板管理、G#22 合规知识库问答
```
