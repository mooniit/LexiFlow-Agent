# T-测试情况汇总

## 测试全景

全部 16 个测试任务已完成，覆盖单元测试和 Testcontainers 集成测试两个层次。

## 单元测试（87 个通过）

| 任务 | 测试文件 | 覆盖范围 |
|------|---------|---------|
| TEST#01 | 基础配置 | JUnit 5 / Mockito / AssertJ / Spring Boot Test |
| TEST#02 | `AgentStateMachineTest` | 主路径流转、审批暂停恢复、终止态阻塞、各状态目标集合、多节点重试 |
| TEST#03 | `ToolPermissionGuardTest` | 权限校验、工具启用、风险等级、审批要求、部门范围 |
| TEST#04 | `PromptTemplateServiceTest` | 变量渲染、多变量替换、未匹配变量处理、场景回退、按 ID 查询与删除异常 |
| TEST#05 | `TokenUsageTest` | prompt/completion/total 计数、零值、不可变性 |
| TEST#06 | `KnowledgeAccessGuardTest` | PUBLIC/DEPARTMENT/PRIVATE 三级可见性、角色过滤、部门+角色组合、状态过滤、requireRead 异常 |
| TEST#07 | `ApprovalRuleTest` | HIGH 风险触发审批、LLM 覆盖审批标记、缺失条款检测、复合风险、纯净合同 |
| TEST#08 | `LlmJsonParsingTest` | 结构化输出信封、场景保持、LLM 超时降级、无效 JSON 降级、空消息处理 |
| TEST#09 | `RiskAnalysisServiceTest` | 无限责任/长付款周期/高金额检测、LLM 增强、多规则匹配 |
| TEST#13 | `ChunkRetrievalTest` | 向量检索排序、文档访问过滤、结果含文档标题、相似度截断、embedding 失败传播 |
| TEST#16 | `MockLlmGatewayTest` | 5 种方法全覆盖、确定性 embedding、流式输出、空消息、System 消息回退、多用户消息 |

## 集成测试（需 Docker）

| 任务 | 测试文件 | 覆盖范围 |
|------|---------|---------|
| TEST#10 | `TestcontainersBaseTest` | PostgreSQL + pgvector + RabbitMQ 容器启动 |
| TEST#11 | `ContractReviewIntegrationTest` | 创建审查、列表查询、取消、rerun 限制、步骤查询 |
| TEST#12 | `RabbitMqConsumerTest` | 队列声明、消息路由、往返验证、空队列接收 |
| TEST#14 | — | 与 TEST#11 合并验证数据库事务 |
| TEST#15 | `ApprovalEventRecoveryTest` | 审批创建、批准/驳回/要求修改、历史记录、重复审批防护 |

### 运行集成测试

```bash
cd backend
mvn test -Dgroups='integration'
```

需要 Docker Desktop 运行中。

## 测试架构

```
单元测试
├── Mockito + AssertJ
├── 不依赖外部服务
└── 87 个通过，< 1s

集成测试
├── Testcontainers (pgvector + RabbitMQ)
├── @SpringBootTest + @Transactional
└── 每次启动约 30s（首次拉镜像更久）
```

## 关键文件记录

```text
backend/src/test/java/com/example/lexiflow/
├── agent/service/AgentStateMachineTest.java
├── llm/model/TokenUsageTest.java
├── llm/service/LlmJsonParsingTest.java
├── llm/service/MockLlmGatewayTest.java
├── prompt/service/PromptTemplateServiceTest.java
├── rag/service/ChunkRetrievalTest.java
├── rag/service/KnowledgeAccessGuardTest.java
├── review/service/ApprovalRuleTest.java
├── review/service/RiskAnalysisServiceTest.java
├── tool/service/ToolPermissionGuardTest.java
└── integration/
    ├── TestcontainersBaseTest.java
    ├── ContractReviewIntegrationTest.java
    ├── RabbitMqConsumerTest.java
    └── ApprovalEventRecoveryTest.java
```
