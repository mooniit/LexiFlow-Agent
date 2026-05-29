# G#10 SSE 实时事件流

## 任务目标

为合同审查过程推送解析、条款抽取、规则检索、风险分析、报告生成、完成或失败事件。

## 实现路径

- 新增 `ReviewEventBus` 管理按 reviewId 分组的 `SseEmitter`。
- 审查状态流转和步骤完成时发布事件。
- 新增 `GET /api/reviews/{id}/events`，返回 `text/event-stream`。

## 关键文件

- `backend/src/main/java/com/example/lexiflow/review/service/ReviewEventBus.java`
- `backend/src/main/java/com/example/lexiflow/review/service/ContractReviewService.java`
- `backend/src/main/java/com/example/lexiflow/review/api/ContractReviewController.java`

## 验证方式

- `mvn "-Dmaven.test.skip=true" package` 通过。

## 遗留问题

- 当前事件在单应用进程内分发；多实例部署时需要接入 Redis Pub/Sub、RabbitMQ 或专用事件网关。
