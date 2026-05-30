# R2 RAG文档规范化与知识库重切

## 背景

本次任务围绕知识库问答与规则检索的 RAG 入库质量展开。此前 `.md`、`.docx` 文档虽然可以解析和展示，但不同格式进入切片前缺少统一清洗；Markdown 中多余空格、换行和标记会进入切片，法案说明、目录等非正文内容也可能参与匹配。同时原有 800 字符切片会把多条法案合并进同一个 chunk，导致问答命中范围过宽、精度偏低。

## 处理内容

- 新增 `KnowledgeDocumentNormalizer`，在知识库文档入库前统一清洗 `.txt`、`.md`、`.docx` 文本。
- Markdown 清洗覆盖 front matter、标题符号、加粗/代码标记、链接、表格分隔线、多余空格和连续空行。
- DOCX 与 Markdown 最终统一为同一种 canonical plain text，用同一份清洗后内容参与切片、embedding 和前端展示。
- 入库前删除法案说明和目录：优先从第一个正文法条开始保留，并额外清理目录行，例如 `第一条 适用范围 ........ 1` 和 `第一条 适用范围 1`。
- 将知识库切片策略从固定 800 字符合并改为法条优先：
  - 默认最大切片长度调整为 450 字符。
  - 优先保持一条法案一个 chunk。
  - 单条法案过长时再按换行或句号安全拆分，并保留 60 字符 overlap。
- 修复 mock embedding 与数据库向量维度不一致问题：mock embedding 从 16 维调整为 1536 维，匹配 `document_chunk.embedding VECTOR(1536)`。
- DashScope embedding 配置补充 `embedding-dimensions`，默认 1536。
- 改进批量导入错误信息，包含底层 cause，便于定位 embedding 或数据库异常。

## 数据处理

- 清空 `公司合规规则库` 下旧知识库数据：
  - 删除旧 `retrieval_log` 6 条。
  - 删除旧 `document_chunk` 24 条。
  - 删除旧 `knowledge_document` 8 条。
- 使用新清洗与切片逻辑重新批量导入 `storage/knowledge` 中的两个文件：
  - `中华人民共和国劳动合同法.md`
  - `中华人民共和国电子签名法_20190423.docx`

## 重切结果

- `中华人民共和国劳动合同法.md`
  - 生成 98 个切片。
  - 切片长度范围：22 到 393 字符。
- `中华人民共和国电子签名法_20190423.docx`
  - 生成 36 个切片。
  - 切片长度范围：30 到 275 字符。

抽查结果显示切片已经按法条拆分，并在 `metadata.articles` 中记录对应条号，例如：

```json
{"articles": ["第一条"]}
```

## 验证情况

- 后端测试通过：

```text
mvn test
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 注意事项

- 当前未新增重切接口。后续如果需要重建已有文档切片，仍需通过清空旧文档/切片后重新上传或批量导入完成。
- `storage/knowledge-temp/` 当前作为临时材料目录保留，未纳入本次提交。
