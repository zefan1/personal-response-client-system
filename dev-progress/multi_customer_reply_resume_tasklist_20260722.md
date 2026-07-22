# 多客户档案确认与回复续跑实施断点

## 当前状态

- 基线提交：`7fb9c7f7fe8aa411a4fd390b91ecf1f87122280f`
- 当前任务：`Task 2 - 后端任务生命周期与 REST 路由`
- 最近验证：`2026-07-22：C:\Users\85314\AppData\Local\Temp\codex-maven-20260722\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=PendingReplyTaskRepositoryTest,PendingReplyTaskRepositoryTransactionTest,ChatOrchestrationServiceTest test`，55 项通过（20 仓储、2 事务、33 编排），0 failures/errors。`
- 未解决阻塞：`无。待完成服务端列表、单任务、重试、取消和 REST；随后接入桌面端任务恢复、候选档案预览/确认和提醒。真实桌面截图验收尚未开始。`
- 用户授权：`用户明确要求不新开 worktree，在当前工作区开发。必须保留并兼容已有未提交改动。`

## 完成记录

- [x] Task 0 基线与契约：设计、实施计划、并行边界和本断点已提交（`b595a85`、`c7c93ed`、`7fb9c7f`、`90f9c7e`）。
- [x] Task 1 数据库与状态机：`V75`、`PendingReplyTask*`、`ChatTaskConfig`、原子创建/领取、READY 严格持久化和恢复 SQL 已实现。已验证候选原子领取、事务回滚、超时边界、过期和旧生成不覆盖新领取。
- [ ] Task 2 编排与 API：多客户识别仅创建 `WAITING_CUSTOMER` 任务；确认客户后按原聊天内容使用 `CHAT_RECOGNIZE` 生成，READY 可按任务恢复。待完成任务列表、单任务全状态、重试、取消和 REST 路由。
- [ ] Task 3 桌面候选预览与确认
- [ ] Task 4 恢复与桌面提醒
- [ ] Task 5 全量验证与人工验收

## 本次验证证据

- `PendingReplyTaskRepositoryTest`：20 项通过。覆盖原子候选领取、事务创建回滚、READY 会话/手机号/可展示回复校验、超时和过期恢复。
- `PendingReplyTaskRepositoryTransactionTest`：2 项通过。覆盖两个 `create` 入口在 Spring 事务代理下回滚候选写入失败。
- `ChatOrchestrationServiceTest`：33 项通过。覆盖多客户不提前调用 Skill/LLM、确认第二候选使用原聊天、失败回退和 READY 写入。
- 上述命令的两个 WARN 来自故意模拟“失败状态写入也失败”和“标签读取降级”的测试分支；Maven 退出码为 0。

## 共享文件规则

- 工作台和管理后台正在修改 `App.vue`、`styles.css`、客户档案模块、`ConfigAdminService.java` 和相关测试。
- 本次功能只有在读清已有 diff 后才修改这些文件；禁止重置、检出或覆盖现有改动。
- `customer:selected` 保持既有“打开客户/主动生成”的含义。候选档案预览和确认使用独立的 `reply-task:*` 事件。
- 创建 Flyway 迁移前重新确认当前最大版本，不假设 `V75` 仍可用。

## 恢复命令

1. `git status --short --branch`
2. 阅读本文件、`docs/superpowers/specs/2026-07-21-multi-customer-profile-selection-reply-resume-design.md` 和 `docs/superpowers/plans/2026-07-22-multi-customer-profile-selection-reply-resume.md`。
3. 先运行 `C:\Users\85314\AppData\Local\Temp\codex-maven-20260722\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=PendingReplyTaskRepositoryTest,PendingReplyTaskRepositoryTransactionTest,ChatOrchestrationServiceTest test`，确认当前后端基座仍为绿。
4. 从 `Task 2` 的“服务端列表、单任务、重试、取消和 REST”继续；不得重新审查已通过的仓储边界，除非新测试发现真实回归。
5. 完成一个阶段后，更新本文件中的当前任务、测试证据、未完成项和本功能专属 Git 提交。
