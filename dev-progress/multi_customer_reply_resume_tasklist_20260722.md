# 多客户档案确认与回复续跑实施断点

## 当前状态

- 基线提交：`7fb9c7f7fe8aa411a4fd390b91ecf1f87122280f`
- 当前任务：`Task 0 - 建立可恢复的实施基线`
- 最近验证：`尚未执行`
- 未解决阻塞：`本机尚未找到 Maven 可执行文件；前端和历史后端报告不替代本次验证。`
- 用户授权：`用户明确要求不新开 worktree，在当前工作区开发。必须保留并兼容已有未提交改动。`

## 完成记录

- [ ] Task 0 基线与契约
- [ ] Task 1 数据库与状态机
- [ ] Task 2 编排与 API
- [ ] Task 3 桌面候选预览与确认
- [ ] Task 4 恢复与桌面提醒
- [ ] Task 5 全量验证与人工验收

## 共享文件规则

- 工作台和管理后台正在修改 `App.vue`、`styles.css`、客户档案模块、`ConfigAdminService.java` 和相关测试。
- 本次功能只有在读清已有 diff 后才修改这些文件；禁止重置、检出或覆盖现有改动。
- `customer:selected` 保持既有“打开客户/主动生成”的含义。候选档案预览和确认使用独立的 `reply-task:*` 事件。
- 创建 Flyway 迁移前重新确认当前最大版本，不假设 `V75` 仍可用。

## 恢复命令

1. `git status --short --branch`
2. 阅读本文件、`docs/superpowers/specs/2026-07-21-multi-customer-profile-selection-reply-resume-design.md` 和 `docs/superpowers/plans/2026-07-22-multi-customer-profile-selection-reply-resume.md`。
3. 从第一个未完成复选框继续，先运行该步骤指定的失败测试或验证命令。
4. 完成一个任务后，更新本文件中的当前任务、测试证据和 Git 提交。
