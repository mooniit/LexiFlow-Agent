# DEP#09-文件存储目录

## 任务目标

为合同文件和知识库文档提供本地存储目录。

## 本次实现

- 新建 `storage/contracts/.gitkeep`。
- 新建 `storage/knowledge/.gitkeep`。
- `.gitignore` 忽略上传后的真实文件，仅保留目录。
- `application.yml` 增加：

```yaml
lexiflow:
  storage:
    contracts-dir: ../storage/contracts
    knowledge-dir: ../storage/knowledge
```

## 后续补充

- 实现文件上传服务。
- 增加文件类型、大小和安全校验。
- 后续可迁移到对象存储。

