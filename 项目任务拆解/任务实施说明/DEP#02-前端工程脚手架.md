# DEP#02-前端工程脚手架

## 任务目标

建立 React + TypeScript + Vite + Ant Design 前端工作台基础。

## 本次实现

- 新建 `frontend` 工程。
- 配置 Vite dev server 和 `/api` 代理。
- 接入 Ant Design、lucide-react。
- 新增登录页，默认填充开发账号 `admin/admin123`。
- 新增工作台基础布局、指标卡片、Agent 时间线和当前权限展示。
- 新增前端 API client 和 SSE EventSource 创建入口。
- Docker Compose 增加 `frontend` 服务，放入 `app` profile。

## 验证方式

```bash
cd frontend
npm install
npm run dev
```

## 后续补充

- 路由。
- 合同列表、上传、审查详情、审批中心、知识库问答、Agent Trace 页面。
- SSE 客户端。
