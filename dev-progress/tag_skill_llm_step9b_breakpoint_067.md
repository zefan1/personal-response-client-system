# Step 9B 断点：标签统计与运营看板

日期：2026-07-16  
分支：`feature/tag-step8-reply-tag-context`  
实现代码最终 HEAD（断点文档提交前）：`077a6b6`  
状态：Step 9B 已完成；Step 9C/9D 未开始

## 本轮结果

Step 9B 已完成。运营分析看板新增正式标签统计区块，后端提供结构化 POST 接口，查询条件、权限范围和客户列表复用 Step 9A 的统一 QuerySpec。

### API 边界

`POST /admin/api/v1/analytics/tags`

请求：

- `customerFilter`：关键词、来源、线索类型、员工、门店、项目、阶段、客户更新时间、动态标签分组和 `AND/OR` 组合。
- `teamLeaderIds`：团队 ID；服务层解析为启用员工并与显式员工条件取交集。
- `tagFrom/tagTo`：标签事件时间窗口，默认最近 7 天，最大 90 天。
- `granularity`：当前只接受 `DAY`。

响应：

- `summary`：匹配客户、已打标签客户、当前有效分配、覆盖率、系统新增、人工新增/修改、系统判断未更新。
- `categories/tags`：统计目录中的分类和标签值。
- `stores/teams/employees`：当前有效标签按门店、团队、员工的聚合。
- `tagSources`：事件窗口内正式 assignment 的来源。
- `unupdatedReasons`：`NO_ANALYSIS`、`LATEST_RUN_REJECTED`、`LATEST_RUN_NO_CHANGE`、`UNMATCHED_LEGACY_VALUE`、`CUSTOMER_UPDATED_AFTER_TAG_CHANGE`，允许同一客户多原因命中，并标注 `CURRENT_GAP` 或 `EVENT_WINDOW`。
- `trend`：新增、失效、净变化和来源拆分的连续日趋势，窗口内无事件日期补零。
- `filterOptions`：权限范围内门店、团队、员工、客户来源和标签来源选项。

正式数据边界：当前统计只使用 active、启用、未合并、`use_for_statistics=1` 的 `customer_tag_assignments` 及其统计目录；不 JOIN `system_tag_suggestions`，不把 `unmatched_legacy_tag_values` 或旧 `customers.intent_level` 当作正式标签数量。

## 提交

- `d231b7d` `docs: add step 9b tag analytics spec`
- `bc12b5b` `docs: add step 9b implementation plan`
- `669542e` `feat: define tag analytics contract`
- `1049c24` `feat: build unified tag analytics query scope`
- `221aece` `feat: expose tag analytics api`
- `8b94419` `feat: aggregate current effective tags`
- `0c0fa9c` `feat: add tag analytics dimensions`
- `49a5b7a` `feat: add tag source and trend analytics`
- `a617029` `feat: explain missing tag updates`
- `67c9794` `fix: use unified intent tags in analytics funnel`
- `3bcbecb` `feat: add tag analytics frontend helpers`
- `b4ffe42` `feat: add tag analytics admin dashboard`
- `077a6b6` `feat: add tag analytics dev action`

## 新增与修改文件

后端新增请求/响应/窗口类型、`TagAnalyticsService`、`TagAnalyticsRepository` 聚合，以及控制器 POST 入口；补充仓库、服务、控制器和漏斗回归测试。

桌面端新增 `tagAnalytics.ts` helper 和测试；修改 `AdminConsole.vue`、`AdminConsole.test.ts`、`AdminDevConsole.vue`、`AdminDevConsole.test.ts`、`styles.css`，接入筛选、动态标签、局部失败重试和 CSV。

未新增 Flyway migration。

## 验证记录

后端：

- `mvn -q test`
- 105 个 Surefire 测试套件，414 tests，0 failures，0 errors，1 skipped（conditional）。

前端：

- `npm test -- --run`：37 个 Vitest 文件，261 tests，全部通过。
- `npm run typecheck`：通过。
- `npm run build`：通过。
- `npm run renderer:smoke`：`renderer_smoke=passed`。
- `npm run electron:smoke`：`electron_smoke=passed`。

数据库只读核验：

- `private_domain_assistant_smoke.system_tag_suggestions` 仍有 6 条 `status='PENDING'`，ID 1-6 的原文为“沉睡风险/可能流失”，关联未匹配记录 16-21；本轮未修改这些记录。
- `llm.profile_extraction.enabled=false`。
- `llm.reply_generation.enabled=false`。

## 保持不变的范围

- 未新增数据库迁移。
- 未修改 6 条 `system_tag_suggestions` PENDING 数据。
- 未开启 `llm.reply_generation.enabled` 或 `llm.profile_extraction.enabled`。
- 未修改 Step 8 回复标签上下文行为。
- 未 merge、push 或创建 PR。

## 下一步

进入 Step 9C 前，重新执行 `superpowers:brainstorming`，确认跟进规则动态标签条件的业务边界，再编写规格和实施计划。Step 9D 的 CSV 客户导入、外部表格同步、写回和统一标签校验仍未开始。
