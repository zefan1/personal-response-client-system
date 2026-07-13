# 本地真机测试断点 052

时间：2026-07-13 09:20

## 问题

管理后台接口提示“登录已过期，请重新登录”，但页面仍停留在后台，没有自动返回登录页。

## 根本原因

- 统一 API 客户端只解析并返回认证失败响应。
- 管理后台组件将认证失败当作普通页面错误展示。
- App 登录壳没有收到认证失效通知，因此本地 token 和页面会话没有被清除。

## 已修复

- `desktop/src/renderer/shared/apiClient.ts`
  - 已有 token 的非登录请求收到 HTTP 401、`80-10002` 或 `80-10008` 时，广播全局 `auth:expired` 事件。
  - 登录接口自身的密码错误不会触发自动退出。
- `desktop/src/renderer/App.vue`
  - 收到 `auth:expired` 后立即清除 access token、账号角色和桌面状态。
  - 自动切回登录页并显示后端中文提示。
- 新增 `desktop/src/renderer/shared/apiClient.test.ts`。
- `desktop/src/renderer/App.test.ts` 增加自动返回登录页测试。

## 验证结果

- Vitest：2 个测试文件，10 个用例通过，0 失败。
- `npm run typecheck` 通过。
- 本地前端和后端均正常运行。
- 在当前 Chrome 管理后台用过期登录态刷新验证，页面已返回登录页，不再停留在后台。

## 当前状态

- Chrome 停在 `http://127.0.0.1:5173/#/admin` 登录页。
- 需要重新输入管理员账号和密码继续管理后台测试。
