# G#07-自研 Agent 状态机

## 任务目标

提供合同审查任务状态流转规则，避免业务流程散落在不可维护的条件判断中。

## 本次实现

- 新增 `AgentTaskStatus`。
- 新增 `AgentStepType`。
- 新增 `AgentStateMachine`，支持合法流转校验。
- 新增 `InvalidAgentTransitionException`。
- 新增单元测试覆盖主链路、审批暂停恢复和终态保护。

## 关键文件

```text
backend/src/main/java/com/example/lexiflow/agent/model/AgentTaskStatus.java
backend/src/main/java/com/example/lexiflow/agent/model/AgentStepType.java
backend/src/main/java/com/example/lexiflow/agent/service/AgentStateMachine.java
backend/src/test/java/com/example/lexiflow/agent/service/AgentStateMachineTest.java
```

## 后续补充

- 与 `contract_review` 表状态更新结合。
- 写入 `agent_state_transition_log`。
- 审批事件恢复执行。

