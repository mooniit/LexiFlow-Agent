# TEST#06-RAG 权限过滤测试

## 覆盖范围

- PUBLIC 知识库可读。
- DEPARTMENT 知识库仅同部门可读。
- PRIVATE 知识库支持 allowed_roles 或 owner 访问。

## 验证记录

```text
mvn test 通过，22 tests, 0 failures
```

## 后续补充

当前为 DOING。后续在规则文档入库、向量检索和引用结果返回链路完成后，继续补充真实 chunk 检索与文档状态过滤测试。
