# 断点 019：发送确认后端事件与表格写入链路核对

时间：2026-07-09 17:24-17:40  
状态：已完成

## 本轮目标

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 核对 `reply:selected -> copy-backfill -> /api/v1/chat/send-confirm -> CustomerMessageSentEvent` 后，后端是否能继续触发档案更新和企微表格写入。
- 为真实接口联调前的关键契约补测试，避免前端再次把脱敏手机号传入后端。

## 核对结果

- `/api/v1/chat/send-confirm` 会发布 `CustomerMessageSentEvent`：
  - 使用请求里的完整 `phone`。
  - 携带 `sentText`、`selectedDirection`、`followupSuggest`、`rawMessages`、`sourceTable`、`leadType` 和当前操作人。
  - 返回 `{ accepted: true }`，表示发送确认事件已被接收。
- 档案更新链路已接事件：
  - `ProfileUpdateOrchestrator` 监听 `CustomerMessageSentEvent`。
  - 通过完整手机号查客户。
  - 抽取高置信字段后写入档案，并把中置信字段进入建议队列。
  - 同时写入 `lastFollowupAt` 和 `followupNotes`。
- 表格写入链路已接事件：
  - `TableWriteOrchestrator` 监听 `CustomerMessageSentEvent`。
  - 有客户且不是新客时走现有行更新。
  - 找不到客户或标记为新客时走新行创建。
  - 写入失败时会立即重试一次，再进入待写队列。

## 本轮新增守护测试

- `ChatOrchestrationServiceTest.sendConfirmPublishesFullPhoneEventForProfileAndTableWriteConsumers`
  - 验证 send-confirm 发布的事件使用完整手机号。
  - 验证已发送内容、跟进建议、操作人和审计日志被正确带出。
- `TableWriteOrchestratorTest.updatesExistingCustomerWithFullPhoneAndFallsBackToPendingQueueOnFailure`
  - 验证表格写入用完整手机号命中现有客户。
  - 验证现有行更新失败后会重试一次。
  - 验证重试失败后进入待写队列，并保留 `sourceTable`、`sourceRowId` 和待写字段。

## 验证结果

- 前端/桌面侧在断点 018 已通过：
  - 侧边栏回归 16 个测试文件、115 个用例通过。
  - `npm run typecheck` 通过。
  - `npm run renderer:smoke` 通过。
- 后端 Java 测试本轮已补充，但当前本机没有可用 Maven：
  - 项目只有 `pom.xml`，没有 `mvnw`。
  - `Get-Command mvn,mvnw` 未找到可执行命令。
  - 因此 `ChatOrchestrationServiceTest` 和 `TableWriteOrchestratorTest` 暂未能在本机执行。

## 当前可手工验证

- 在回复助手点击 `复制`：
  - 前端 send-confirm 请求应带完整手机号。
  - 成功后应提示 `已复制并记录发送，档案正在刷新`。
- 后端有 Maven/JDK 执行环境后，优先运行：
  - `mvn test -Dtest=ChatOrchestrationServiceTest,TableWriteOrchestratorTest`
- 如果真实企微表格接口尚未配置：
  - 表格写入应走 mock 或待写队列。
  - 后续需要在侧边栏/后台把“已排队、等待重试、失败原因”展示清楚，避免用户以为保存没有反应。

## 下一步

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“表格写入/待写队列/降级状态”前端可见性：
  - 保存或发送确认后，用户能否看到表格同步状态。
  - 接口失败或真实 key 未配置时，是否有明确提示。
  - 是否需要在工作台或客户档案里增加待写队列入口。
