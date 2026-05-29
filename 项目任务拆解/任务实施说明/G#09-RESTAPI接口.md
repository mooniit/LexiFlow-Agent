# G#09 REST API 接口

## 任务目标

提供登录、合同上传、创建审查任务、查询审查详情/步骤等 MVP REST 接口。

## 实现路径

- 复用已有 `/auth/login` 与 `/auth/me`。
- 新增 `/contracts` 系列接口：上传、列表、详情、原文、解析、归档、删除。
- 新增 `/reviews` 系列接口：创建、列表、详情、步骤、取消、失败重跑。
- API 响应沿用 `ApiResponse`。

## 关键接口

- `POST /api/contracts`
- `GET /api/contracts`
- `GET /api/contracts/{id}`
- `GET /api/contracts/{id}/original`
- `POST /api/contracts/{id}/parse`
- `POST /api/contracts/{id}/archive`
- `DELETE /api/contracts/{id}`
- `POST /api/reviews`
- `GET /api/reviews`
- `GET /api/reviews/{id}`
- `GET /api/reviews/{id}/steps`
- `POST /api/reviews/{id}/cancel`
- `POST /api/reviews/{id}/rerun`

## 验证方式

- `mvn "-Dmaven.test.skip=true" package` 通过。

## 遗留问题

- 规则上传、审批操作和工具配置管理接口属于后续规则库、审批和工具配置任务继续扩展。
