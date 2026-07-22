# 多客户档案确认与回复续跑实施断点

## 当前状态

- 基线提交：`7fb9c7f7fe8aa411a4fd390b91ecf1f87122280f`
- 当前任务：`Task 4 - 登录、刷新和桌面重启恢复，以及待选客户系统提醒。`
- 最近验证：`2026-07-22：在 desktop 运行 App.test.ts、customerProfileStore.test.ts、CustomerProfilePanel.test.ts、ReplySuggestionPanel.test.ts、replySuggestionStore.test.ts、pendingReplyTaskStore.test.ts，6 个文件 106 项通过；npm run typecheck 通过。`
- 未解决阻塞：`无。后端生命周期和 5 个 REST 路由已在当前工作树接通；共享 ChatOrchestrationService/测试含其他会话改动，尚未做隔离提交。待完成桌面端恢复、候选档案预览/确认、登录/重启恢复和提醒。真实桌面截图验收尚未开始。`
- 用户授权：`用户明确要求不新开 worktree，在当前工作区开发。必须保留并兼容已有未提交改动。`

## 完成记录

- [x] Task 0 基线与契约：设计、实施计划、并行边界和本断点已提交（`b595a85`、`c7c93ed`、`7fb9c7f`、`90f9c7e`）。
- [x] Task 1 数据库与状态机：`V75`、`PendingReplyTask*`、`ChatTaskConfig`、原子创建/领取、READY 严格持久化和恢复 SQL 已实现。已验证候选原子领取、事务回滚、超时边界、过期和旧生成不覆盖新领取。
- [x] Task 2 编排与 API：多客户识别仅创建 `WAITING_CUSTOMER`；列表、单任务、确认、重试、取消 5 个 REST 路由已接通。确认/重试共用原聊天与 `CHAT_RECOGNIZE` 生成；活跃生成不会被同进程轮询误恢复，READY 查询不重复调用 Skill/LLM。
- [x] Task 3 桌面候选预览与确认：DTO/恢复 Store 提交 `8848648`；候选回复会话路由提交 `0367fcb`；完整档案预览、严格手机号确认、取消清理、并发任务隔离和自动切到档案页提交 `6dbd854`。
- [ ] Task 4 恢复与桌面提醒
- [ ] Task 5 全量验证与人工验收

## 本次验证证据

- `PendingReplyTaskRepositoryTest`：24 项通过。覆盖原子候选领取、首次确认/失败重试分离、事务创建回滚、READY 会话/手机号/可展示回复校验、活跃生成排除、超时和过期恢复。
- `PendingReplyTaskRepositoryTransactionTest`：2 项通过。覆盖两个 `create` 入口在 Spring 事务代理下回滚候选写入失败。
- `PendingReplyTaskServiceTest`：8 项通过。覆盖恢复列表、候选档案权限过滤、失败重试、取消和本 JVM 活跃生成登记。
- `ChatOrchestrationServiceTest`：39 项通过。覆盖多客户不提前调用 Skill/LLM、确认第二候选、REST 委托、重试原聊天、失败回退、活跃生成保护和 READY 写入。
- `ChatControllerTest`：11 项通过。覆盖 5 个任务 REST 路由的 path/body/JSON 绑定。
- 上述命令的两个 WARN 来自故意模拟“失败状态写入也失败”和“标签读取降级”的测试分支；Maven 退出码为 0。
- 桌面任务 DTO 与恢复 Store：指定 3 个 Vitest 文件 43 项通过，`npm run typecheck` 通过。覆盖多客户不提前显示生成、服务端任务恢复、READY 只展示持久化结果、并发刷新防旧结果覆盖、服务端快照清理和显式打开已关闭任务；提交为 `8848648`。
- 候选预览/确认：指定 6 个 Vitest 文件 106 项通过，`npm run typecheck` 通过。覆盖查看档案不触发确认/生成、完整手机号严格相等、预览失败不授权确认、纯查看不恢复待保存编辑、取消即时清理任务与原会话、A/B 并发确认隔离，以及从回复队列自动切到客户档案页；提交为 `0367fcb`、`6dbd854`。

## 共享文件规则

- 工作台和管理后台正在修改 `App.vue`、`styles.css`、客户档案模块、`ConfigAdminService.java` 和相关测试。
- 本次功能只有在读清已有 diff 后才修改这些文件；禁止重置、检出或覆盖现有改动。
- `customer:selected` 保持既有“打开客户/主动生成”的含义。候选档案预览和确认使用独立的 `reply-task:*` 事件。
- 创建 Flyway 迁移前重新确认当前最大版本，不假设 `V75` 仍可用。

## 恢复命令

1. `git status --short --branch`
2. 阅读本文件、`docs/superpowers/specs/2026-07-21-multi-customer-profile-selection-reply-resume-design.md` 和 `docs/superpowers/plans/2026-07-22-multi-customer-profile-selection-reply-resume.md`。
3. 先运行 `C:\Users\85314\AppData\Local\Temp\codex-maven-20260722\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=PendingReplyTaskRepositoryTest,PendingReplyTaskRepositoryTransactionTest,ChatOrchestrationServiceTest test`，确认当前后端基座仍为绿。
4. 从 `Task 4` 的登录/刷新恢复和 Electron 通知继续；不得重新实现或重复审查已提交的桌面任务 Store、候选预览和确认，除非新测试发现真实回归。
5. 完成一个阶段后，更新本文件中的当前任务、测试证据、未完成项和本功能专属 Git 提交。
