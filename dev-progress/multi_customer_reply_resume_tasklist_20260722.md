# 多客户档案确认与回复续跑实施断点

## 当前状态

- 基线提交：`7fb9c7f7fe8aa411a4fd390b91ecf1f87122280f`
- 当前任务：`Task 5 - 使用 2026-07-22 23:24 最新桌面包复测无档案 Skill 超时后的重新识别；继续真实微信/企微/抖音截图和安装态 Windows 通知点击验收。`
- 最近验证：`2026-07-22：后端全量 549 项，失败 0、错误 0、跳过 2；前端全量 40 个文件 363/363 通过；npm run typecheck 通过。`
- 未解决阻塞：`真实企业微信当前是内部群、Chrome 当前不是抖音页、微信没有可见主窗口，均不能作为客户截图验收。Windows 开发态 Notification.isSupported() 为 true，但独立探测通知也未进入通知中心；Electron 官方要求的开始菜单快捷方式/ToastActivator 注册尚未在安装包中形成闭环。`
- 用户授权：`用户明确要求不新开 worktree，在当前工作区开发。必须保留并兼容已有未提交改动。`

## 完成记录

- [x] Task 0 基线与契约：设计、实施计划、并行边界和本断点已提交（`b595a85`、`c7c93ed`、`7fb9c7f`、`90f9c7e`）。
- [x] Task 1 数据库与状态机：`V75`、`PendingReplyTask*`、`ChatTaskConfig`、原子创建/领取、READY 严格持久化和恢复 SQL 已实现。已验证候选原子领取、事务回滚、超时边界、过期和旧生成不覆盖新领取。
- [x] Task 2 编排与 API：多客户识别仅创建 `WAITING_CUSTOMER`；列表、单任务、确认、重试、取消 5 个 REST 路由已接通。确认/重试共用原聊天与 `CHAT_RECOGNIZE` 生成；活跃生成不会被同进程轮询误恢复，READY 查询不重复调用 Skill/LLM。
- [x] Task 3 桌面候选预览与确认：DTO/恢复 Store 提交 `8848648`；候选回复会话路由提交 `0367fcb`；完整档案预览、严格手机号确认、取消清理、并发任务隔离和自动切到档案页提交 `6dbd854`。
- [x] Task 4 恢复与桌面提醒：登录、有效会话启动、令牌刷新均恢复服务端任务；账号切换清理、旧 refresh/login 竞态隔离、前后台提醒和通知点击恢复已提交（`2ccf950`）。
- [x] Task 4.1 真实运行时恢复修复：8081 闭环发现候选表保存了脱敏手机号，刷新后无法重新匹配候选；已按 RED/GREEN 改为持久化 `phoneFull`，提交 `3b425b9`。刷新后仍能读取 4 个候选并确认第二位，READY 回到原 `replySessionId`，连续查询未重复调用 Skill/LLM。
- [x] Task 4.2 Windows 通知身份前置：主进程在 `app.whenReady()` 前、仅在 Windows 设置 AppUserModelId；未打包运行使用 `process.execPath`，打包运行使用稳定产品 ID。代码前置条件已通过 2 项回归测试和复审，提交 `f38a725`；开始菜单/ToastActivator 注册与真实通知点击仍属于 Task 5。
- [x] Task 4.3 无档案 Skill 超时反馈修复：真实截图识别到“小倩”且匹配为 `NONE` 后，Skill 调用超时进入系统兜底；旧逻辑仍显示“正在自动重试”，但 `NONE` 会话无法使用要求已匹配客户的重新生成接口。现改为无档案识别兜底时停止无效计时器并显示“重新识别”，由员工明确点击后重新截取当前聊天；已匹配客户仍保留原自动重试。代码与测试提交为 `5a06c32`。
- [ ] Task 5 全量验证与人工验收

## 本次验证证据

- `PendingReplyTaskRepositoryTest`：25 项通过。新增验证创建任务时保存候选完整手机号而非脱敏展示手机号；其余覆盖原子候选领取、首次确认/失败重试分离、事务创建回滚、READY 会话/手机号/可展示回复校验、活跃生成排除、超时和过期恢复。
- `PendingReplyTaskRepositoryTransactionTest`：2 项通过。覆盖两个 `create` 入口在 Spring 事务代理下回滚候选写入失败。
- `PendingReplyTaskServiceTest`：8 项通过。覆盖恢复列表、候选档案权限过滤、失败重试、取消和本 JVM 活跃生成登记。
- `ChatOrchestrationServiceTest`：39 项通过。覆盖多客户不提前调用 Skill/LLM、确认第二候选、REST 委托、重试原聊天、失败回退、活跃生成保护和 READY 写入。
- `ChatControllerTest`：11 项通过。覆盖 5 个任务 REST 路由的 path/body/JSON 绑定。
- 上述命令的两个 WARN 来自故意模拟“失败状态写入也失败”和“标签读取降级”的测试分支；Maven 退出码为 0。
- 桌面任务 DTO 与恢复 Store：指定 3 个 Vitest 文件 43 项通过，`npm run typecheck` 通过。覆盖多客户不提前显示生成、服务端任务恢复、READY 只展示持久化结果、并发刷新防旧结果覆盖、服务端快照清理和显式打开已关闭任务；提交为 `8848648`。
- 候选预览/确认：指定 6 个 Vitest 文件 106 项通过，`npm run typecheck` 通过。覆盖查看档案不触发确认/生成、完整手机号严格相等、预览失败不授权确认、纯查看不恢复待保存编辑、取消即时清理任务与原会话、A/B 并发确认隔离，以及从回复队列自动切到客户档案页；提交为 `0367fcb`、`6dbd854`。
- 恢复与桌面提醒：从 Git 索引导出的精确提交快照运行 `App.test.ts`、`customerProfileStore.test.ts`、`CustomerProfilePanel.test.ts`、`ReplySuggestionPanel.test.ts`、`replySuggestionStore.test.ts`、`pendingReplyTaskStore.test.ts`，6 个文件 119/119 通过；`npm run typecheck` 通过；`git diff --cached --check` 通过；提交为 `2ccf950`。
- 候选手机号修复定向验证：`C:\Users\85314\AppData\Local\Temp\codex-maven-20260722\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=PendingReplyTaskRepositoryTest,PendingReplyTaskRepositoryTransactionTest,PendingReplyTaskServiceTest,ChatOrchestrationServiceTest,ChatControllerTest test` 退出码 0；5 个测试类合计 85 项，失败 0、错误 0、跳过 0。
- 全量后端：`C:\Users\85314\AppData\Local\Temp\codex-maven-20260722\apache-maven-3.9.11\bin\mvn.cmd -q test` 退出码 0；127 份 Surefire 报告合计 549 项，失败 0、错误 0、跳过 2。
- 8081 桌面辅助闭环（不替代真实截图）：文字通道任务 `e0752950-...` 持久状态从 `WAITING_CUSTOMER` 到 `READY`，原会话 `reply-1784728183083-1`，4 个候选后四位为 `2810/2921/5754/5922`。先后预览第一、第二档案时 `CALL_SKILL=0`；明确确认第二位后 `selected_phone` 后四位为 `2921`、`CALL_SKILL=1`；退出并重新登录后 READY 回填同一会话，审计数仍为 1。
- Windows 通知真实探测：Electron 已确认失去前台焦点，`Notification.isSupported()` 为 true；补充 AppUserModelId 后，应用通知和同一 Electron 可执行文件发出的独立探测通知均未进入 Windows 通知中心。临时探测脚本和开始菜单快捷方式已清理，不能把通知点击记为通过。
- 无档案 Skill 超时诊断：`2026-07-22 22:55:18` 的 `CHAT_RECOGNIZE` 调用记录为 `20-10001 Skill request timed out`，耗时 3138ms；截图识图和昵称提取已成功，数据库不存在“小倩”档案，未进入回复 LLM。外部 Skill 地址随后通过 DNS、443 和 HTTP 可达性检查。
- 无档案兜底重试 TDD：首次新增 2 项用例时，2 个文件为 2 失败/45 通过，分别证明旧页面仍声称自动重试且没有“重新识别”按钮；最小修复后定向 2 个文件 47/47 通过。追加“识图读到手机号但匹配仍为 NONE”的边界后再次确认 RED，再修正为按 `CHAT_RECOGNIZE + NONE` 判断并恢复 47/47 通过。
- 全量前端：`npm test` 最终 40 个文件、363/363 通过；`npm run typecheck` 通过。已有工作台、管理后台未提交改动也在同一工作区通过全量门禁。
- 最新桌面测试包：`npm run package:verify` 退出码 0；产物为 `desktop/release/Private Domain Assistant-win32-x64/Private Domain Assistant.exe`，生成时间 `2026-07-22 23:24`，`app.asar` 为 42524772 字节、SHA-256 `4b64ee21c6f1ff2f475d363a8ac71af60e8b2e46408762e14eb77ea33bc8379b`。打包烟测和前台截图原生模块加载均通过；包未签名，只能作为本地测试包。新包已启动，主进程 PID `29620`，当前连接保留的 `8080` 后端。
- 公共契约、模块依赖、业务决策和桌面 A/B/D 开发手册已在父目录 `C:\Users\85314\Desktop\私域工具` 增量回填；这些共享文档不属于嵌套 Git 仓库，不能随本仓库提交。
- 真实人工验收清单：`dev-progress/manual-tests/multi_customer_reply_resume_20260722.md` 已记录辅助闭环和通知阻塞；真实平台截图项目仍全部未完成。

## 共享文件规则

- 工作台和管理后台正在修改 `App.vue`、`styles.css`、客户档案模块、`ConfigAdminService.java` 和相关测试。
- 本次功能只有在读清已有 diff 后才修改这些文件；禁止重置、检出或覆盖现有改动。
- `customer:selected` 保持既有“打开客户/主动生成”的含义。候选档案预览和确认使用独立的 `reply-task:*` 事件。
- 创建 Flyway 迁移前重新确认当前最大版本，不假设 `V75` 仍可用。

## 恢复命令

1. `git status --short --branch`
2. 阅读本文件、`docs/superpowers/specs/2026-07-21-multi-customer-profile-selection-reply-resume-design.md` 和 `docs/superpowers/plans/2026-07-22-multi-customer-profile-selection-reply-resume.md`。
3. 先运行 `C:\Users\85314\AppData\Local\Temp\codex-maven-20260722\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=PendingReplyTaskRepositoryTest,PendingReplyTaskRepositoryTransactionTest,ChatOrchestrationServiceTest test`，确认当前后端基座仍为绿。
4. 从 `Task 5` 的真实桌面验收继续；按 `dev-progress/manual-tests/multi_customer_reply_resume_20260722.md` 使用真实微信、企业微信和抖音网页截图，并验证 Windows 通知点击。不得用模拟文字或自动化测试代替真实截图证据。
5. 完成一个阶段后，更新本文件中的当前任务、测试证据、未完成项和本功能专属 Git 提交。
