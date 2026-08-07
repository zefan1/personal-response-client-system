# 配置中心 Demo 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将已确认的配置中心 Demo 交互迁移到现有管理后台，保持现有 API、业务任务和 UI 色调不变，并补齐模型与 Skill 的管理操作。

**Architecture:** 以 `AdminConsole.vue` 现有页面和 API 调用为唯一数据入口，新增的弹窗、操作列和渐进展开只复用已有状态与方法，不改变后端契约。模型环境继续区分识图和 LLM；Skill 绑定继续使用现有场景、线索类型、优先级和启停接口。

**Tech Stack:** Vue 3、TypeScript、现有 renderer 样式系统、Vitest。

---

### Task 1: 锁定现有契约与设计令牌

**Files:**
- Inspect: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Inspect: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Inspect: `desktop/src/renderer/styles.css`

- [ ] 记录当前 Skill、LLM、识图、内置任务的 API 方法和响应状态。
- [ ] 记录现有 CSS 变量、按钮、开关、弹窗样式，迁移时只使用这些令牌。

### Task 2: 补充前端行为测试

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] 先加入失败测试，覆盖已启用 Skill 显示停用操作、停用 Skill 显示启用操作、编辑和删除入口。
- [ ] 加入失败测试，覆盖档案提取测试弹窗未开始前不展示结果，提交后展示结果边界说明。
- [ ] 加入失败测试，覆盖识图和 LLM 环境在同一配置视图呈现且不混用测试接口。

### Task 3: 迁移配置中心布局与渐进展开

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/styles.css` only if an existing token/component cannot express the layout

- [ ] 保留现有页面入口、路由和 API 加载逻辑，将识图和 LLM 环境排成响应式左右结构。
- [ ] 将档案提取后台测试移到弹窗，复用现有 `PROFILE_EXTRACTION` 测试方法和脱敏提醒。
- [ ] 保留系统内置五项任务与工作台验收状态，不为没有后端接口的任务新增虚假测试。
- [ ] 将高级规则、术语解释和提示词规则改为默认收起、点击后展开。
- [ ] 使用项目现有中性色、强调色、按钮、开关和弹窗样式，不引入 Demo 的独立配色。

### Task 4: 补齐 Skill 绑定操作

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Test: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] 每条绑定保留测试、编辑、启用/停用、删除操作。
- [ ] 启停、编辑、删除继续调用现有 API，并在成功后刷新绑定列表和当前路由状态。
- [ ] 删除前使用现有确认组件或确认流程，失败时展示现有错误提示。

### Task 5: 验证与迁移入口回归

**Files:**
- No new production files

- [ ] 运行管理后台单元测试和 TypeScript 检查。
- [ ] 运行前端构建，确认迁移入口、旧路由和 API 方法仍可用。
- [ ] 启动本地管理后台，验证模型环境、档案提取弹窗、Skill 启停编辑删除和工作台验收提示。
- [ ] 检查项目工作区 diff，确认没有覆盖无关的并行 UI 或后端改动。
