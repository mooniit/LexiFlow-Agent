# G#15 RAG 检索模块

## 任务目标

基于规则库文档 chunk 检索相关规则/模板/审查规范，并向风险识别提供引用来源。

## 实现路径

- 新增 `RagRetrievalService`。
- 读取 `document_chunk` 内容，通过关键词 token 相似度计算 TopK。
- 每次检索写入 `retrieval_log`，记录 query、过滤条件、命中 chunk 和耗时。
- 审查任务进入 `RETRIEVING_RULES` 阶段时自动检索相关规则。

## 关键接口

- `POST /api/knowledge-bases/search`

## 验证方式

- `mvn "-Dmaven.test.skip=true" package` 通过。

## 遗留问题

- 当前未使用 pgvector SQL 排序，因为 mock embedding 是 16 维，旧表字段是 1536 维。后续接真实 embedding 后可切换到 `<=>` 向量距离检索。
