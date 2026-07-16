# Step 9A 断点：客户搜索与统一标签筛选

日期：2026-07-16  
分支：`feature/tag-step8-reply-tag-context`  
状态：Step 9A 已完成；Step 9B-9D 未开始

## 已完成

- 建立不可变 `CustomerFilter`、`TagFilterGroup`、排序和匹配枚举。
- 建立动态 FILTER 目录校验：分类和值必须存在、启用、未合并且允许筛选；SINGLE 只接受单值 ANY，MULTI 支持 ANY/ALL；分类间支持 AND/OR。
- 建立 `CustomerFilterQueryBuilder`/`CustomerQuerySpec`：关键词、现有客户字段、更新时间、标签 EXISTS/ALL 集合谓词和白名单排序统一生成参数化 SQL。
- 建立 `CustomerAccessScopeResolver`：ADMIN 全量，KEEPER/LEADER 使用现有负责人范围，非管理员排除未分配客户。
- Repository 的 COUNT、分页列表和标签摘要复用同一 QuerySpec；当前页标签摘要批量加载，排除失效、停用和合并目录项。
- 保留 `GET /admin/api/v1/customers/search`，新增 `POST /admin/api/v1/customers/search` 结构化入口。
- 管理后台客户列表改用 POST body，加载动态 FILTER 分类和值，支持多选分类 ANY/ALL、分类间 AND/OR，并展示标签中文名称。

## 提交

- `4cc8dce test: lock customer search compatibility contract`
- `b69a7f0 feat: add customer filter contract and tag validation`
- `300b093 feat: apply unified customer filter query and access scope`
- `71d2db2 feat: expose structured customer search api`
- `72f5360 feat: connect admin customer list to unified filters`
- `docs/tag_skill_llm_step9a_spec_062.md`：`d3d4a33`
- `docs/tag_skill_llm_step9a_plan_063.md`：`eaf0edf`

## 验证

后端：

- `mvn -q test`
- 403 tests，0 failures，0 errors，1 conditional skip。
- 关键覆盖：QuerySpec 关键词/字段/ANY/ALL/AND/OR、H2 COUNT/分页一致性、当前标签摘要、权限 scope、GET/POST controller。

前端：

- `npm test -- --run`：36 个 Vitest 文件，256 tests 全部通过。
- `npm run typecheck`：通过。
- `npm run build`：通过。
- `npm run renderer:smoke`：`renderer_smoke=passed`。
- `npm run electron:smoke`：`electron_smoke=passed`。

## 数据与运行状态

- 未新增数据库迁移，未修改 `system_tag_suggestions` 的 6 条 PENDING 原文/状态。
- 未修改客户标签写入、统计、跟进规则、CSV 导入、外部同步或导出实现。
- `llm.reply_generation.enabled=false`、`llm.profile_extraction.enabled=false` 保持关闭。
- 主后端 8080 和前端 5173/5174 为已有运行实例；本次未启动临时 8082，也未写入真实业务数据。
- 工作区在提交后应保持干净，远端相对状态为本地领先 8 个提交，待后续统一推送。

## 下一步

按 Step 9 设计顺序，后续应在 review 后进入：

1. Step 9B：标签统计与门店/团队/员工/时间/来源/未更新原因/趋势维度。
2. Step 9C：跟进规则动态标签条件。
3. Step 9D：CSV、外部表格同步、写回和导出统一标签校验。
