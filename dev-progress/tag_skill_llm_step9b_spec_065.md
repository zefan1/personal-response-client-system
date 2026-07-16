# Step 9B Spec：标签统计与分析维度

日期：2026-07-16  
分支：`feature/tag-step8-reply-tag-context`  
状态：Draft，等待 review

## 1. 目标

在现有运营分析看板中增加标签统计能力，让运营人员基于与客户列表一致的客户筛选条件，查看当前正式标签的覆盖、分布、来源、未更新原因和变化趋势。

Step 9B 必须复用 Step 9A 已落地的 `CustomerFilter`、`CustomerFilterValidator`、`CustomerAccessScopeResolver`、`CustomerFilterQueryBuilder` 和 `CustomerQuerySpec`。客户列表与标签统计不得分别解释门店、来源、管家、客户更新时间或标签组合条件。

本步骤只扩展标签统计，不重构现有 analytics 全部报表，不新增统计快照表，不修改标签写入行为，不开启 LLM 生产开关，也不处理 Step 9C/9D 的规则和数据交换范围。

## 2. 已确认的业务口径

### 2.1 两类时间范围

- 客户范围使用 `customers.updated_at`：`customerFilter.updatedFrom/updatedTo` 决定哪些客户进入当前统计范围。
- 标签事件使用标签表自身时间：`tagFrom/tagTo` 决定来源、未更新原因和趋势的统计窗口。
- 当前有效标签快照不因 `tagFrom/tagTo` 被截断。一个标签即使在窗口前创建，只要现在仍然有效且客户命中 `customerFilter`，仍计入当前数量。
- 趋势新增使用 `customer_tag_assignments.created_at`；趋势失效使用 `customer_tag_assignments.invalidated_at`；净变化为新增数减失效数。
- 标签事件窗口使用服务端业务时区，边界为包含 `tagFrom`、包含 `tagTo`。默认窗口以请求时刻为终点向前取 7 天。

### 2.2 当前有效正式标签

只有同时满足以下条件的分配才能计入当前标签数量：

- `customer_tag_assignments.is_active = 1`
- `tag_categories.is_enabled = 1`
- `tag_categories.merged_into_id IS NULL`
- `tag_categories.use_for_statistics = 1`
- `tag_values.is_enabled = 1`
- `tag_values.merged_into_id IS NULL`
- `tag_values.category_id = customer_tag_assignments.category_id`

以下数据不得进入当前正式标签数量：

- 历史失效分配
- 已停用或已合并的分类和值
- `use_for_statistics = 0` 的分类
- `unmatched_legacy_tag_values`
- `system_tag_suggestions` 的任何记录，包括当前 6 条 `PENDING`
- 旧 `personality_tags` 第二字典记录

### 2.3 维度定义

- 门店：`customers.intended_store`；空值统一展示为“未填写门店”。
- 团队：通过 `customers.assigned_keeper` 匹配启用账号，再按该账号的 `leader_id` 归属组长团队；无法匹配账号或没有组长的记录统一进入“未归属团队”。
- 员工：`customers.assigned_keeper`；可匹配账号时返回账号展示名和内部账号标识，空值进入“未分配员工”。
- 客户来源：`customers.source_channel`，作为共享客户筛选条件并保留现有来源分析语义。
- 标签来源：`customer_tag_assignments.source_type`，至少区分 `SYSTEM_INFERENCE`、`MANUAL`、`LEGACY_MIGRATION`，未知值原样返回并由前端提供兜底展示。

## 3. 用户场景

1. 运营人员在分析看板选择客户更新时间、线索类型、客户来源、意向门店、团队、员工和标签条件。
2. 标签统计只分析与客户列表同一筛选条件能够命中的客户集合。
3. 运营人员查看匹配客户数、已有正式标签客户数、当前有效标签分配数和覆盖率。
4. 运营人员按标签分类、标签值、门店、团队和员工查看当前有效数量。
5. 运营人员在标签事件时间范围内查看系统自动添加、员工手动添加或修改、历史迁移等来源数量。
6. 运营人员查看系统已经判断但没有完成标签更新的数量和原因。
7. 运营人员按天查看新增、失效和净变化趋势。
8. 运营人员导出当前看板时，标签统计与页面当前筛选结果保持一致。

## 4. HTTP API

### 4.1 新增接口

新增：

```text
POST /admin/api/v1/analytics/tags
```

保留现有 `/admin/api/v1/analytics/*` GET 接口及其响应，不在 9B 中迁移、删除或批量改写旧接口。

请求体：

```json
{
  "customerFilter": {
    "keyword": "",
    "sourceChannels": [],
    "leadTypes": [],
    "assignedKeepers": [],
    "intendedStores": [],
    "intendedProjects": [],
    "customerStages": [],
    "updatedFrom": null,
    "updatedTo": null,
    "tagGroups": [],
    "tagGroupLogic": "AND"
  },
  "teamLeaderIds": [],
  "tagFrom": "2026-07-01T00:00:00",
  "tagTo": "2026-07-16T23:59:59",
  "granularity": "DAY"
}
```

约束：

- `customerFilter` 使用与 `CustomerSearchRequest` 相同的谓词字段，不包含分页和排序语义。
- 服务层把 `customerFilter` 转换为内部 `CustomerFilter`，分页和排序固定为安全默认值后交给现有 `CustomerFilterValidator`。
- `teamLeaderIds` 是 analytics 额外的团队筛选。服务层先解析团队中的启用员工账号，再与 `customerFilter.assignedKeepers` 求交集，最终仍通过 `CustomerFilter.assignedKeepers` 进入统一查询条件。
- 只选择团队时使用该团队的启用员工集合；只选择员工时使用显式员工集合；两者同时存在时取交集；交集为空必须生成空范围，不能忽略其中一个条件。
- `tagFrom/tagTo` 均可为空；为空时默认最近 7 天。只提供一端时，另一端按 7 天窗口补齐。
- `tagFrom` 不得晚于 `tagTo`；单次标签事件窗口最长 90 天。
- `granularity` 在 9B 只支持 `DAY`。不提前实现小时、周、月粒度。
- 新接口继续遵守当前运营后台 ADMIN-only 的 HTTP 权限，不在 9B 放宽后台登录或路由权限。内部客户范围仍由 `CustomerAccessScopeResolver` 生成，避免绕过 Step 9A 的数据范围规则。

### 4.2 响应结构

```json
{
  "summary": {
    "matchedCustomerCount": 120,
    "taggedCustomerCount": 92,
    "activeAssignmentCount": 168,
    "coverageRate": 0.7667,
    "systemAddedCount": 34,
    "manualAddedOrChangedCount": 12,
    "systemDecidedNoUpdateCount": 9
  },
  "categories": [],
  "tags": [],
  "stores": [],
  "teams": [],
  "employees": [],
  "tagSources": [],
  "unupdatedReasons": [],
  "trend": [],
  "filterOptions": {
    "stores": [],
    "teams": [],
    "employees": [],
    "customerSources": [],
    "tagSources": []
  },
  "appliedWindow": {
    "tagFrom": "2026-07-10T00:00:00",
    "tagTo": "2026-07-16T23:59:59",
    "granularity": "DAY"
  }
}
```

字段语义：

- `matchedCustomerCount`：统一客户条件和权限范围命中的去重客户数。
- `taggedCustomerCount`：命中客户中至少有一个当前有效正式标签的去重客户数。
- `activeAssignmentCount`：命中客户当前有效正式标签分配总数。多标签客户会贡献多条。
- `coverageRate`：`taggedCustomerCount / matchedCustomerCount`，分母为 0 时返回 0。
- `systemAddedCount`：事件窗口内 `source_type = SYSTEM_INFERENCE` 的新增分配数。
- `manualAddedOrChangedCount`：事件窗口内 `source_type = MANUAL` 的新增分配数；人工替换产生的新分配计一次，不另外重复计算其失效旧分配。
- `systemDecidedNoUpdateCount`：事件窗口内系统分析结果没有形成有效写入的判断数。

分类、标签、门店、团队和员工行至少返回：内部 ID/代码、中文展示名、`activeAssignmentCount`、`taggedCustomerCount`。标签行还需返回分类信息；同一客户同一标签只能计一次。

`tagSources` 按事件窗口内新增分配的 `source_type` 分组，返回 `addedAssignmentCount` 和 `affectedCustomerCount`。

来源和趋势只统计属于当前 `TagCandidatePurpose.STATISTICS` 目录的正式 assignment 事件，但不要求事件行当前仍为 active；因此窗口内创建后又失效的正式标签会同时贡献新增和失效，停用、合并或不允许统计的目录项不会重新进入报表。

`trend` 每天返回：

- `date`
- `addedAssignmentCount`
- `invalidatedAssignmentCount`
- `netChange`
- `systemAddedCount`
- `manualAddedOrChangedCount`

没有事件的日期也要补零，保证折线图日期连续。

## 5. 未更新原因

“未更新原因”只使用现有数据库事实，不根据客户文本或标签内容猜测。

返回行统一包含：

```text
reasonCode
reasonLabel
customerCount
decisionCount
sampleReason
```

9B 至少覆盖以下稳定原因：

- `NO_ANALYSIS`：命中客户从未产生 `tag_analysis_runs`。
- `LATEST_RUN_REJECTED`：最近一次分析运行状态为 `REJECTED`，包括客户版本冲突。
- `LATEST_RUN_NO_CHANGE`：最近一次分析运行状态为 `NO_CHANGE`。
- `UNMATCHED_LEGACY_VALUE`：客户仍有未解决的 `unmatched_legacy_tag_values`。
- `CUSTOMER_UPDATED_AFTER_TAG_CHANGE`：客户在最近一次有效标签新增/失效之后又被更新，但没有后续标签变化。

`NO_ANALYSIS`、`UNMATCHED_LEGACY_VALUE` 和 `CUSTOMER_UPDATED_AFTER_TAG_CHANGE` 是当前客户范围的覆盖缺口快照，不受 `tagFrom/tagTo` 截断；`LATEST_RUN_REJECTED`、`LATEST_RUN_NO_CHANGE`、`systemDecidedNoUpdateCount` 和系统判断明细只统计事件窗口内完成的分析运行。响应需要通过 `scope = CURRENT_GAP | EVENT_WINDOW` 区分两类原因。

对于事件窗口内的系统判断未更新明细，还应按 `tag_analysis_results.validation_reason`、运行 `error_message`、`result_type` 和 `requested_action` 生成 `sampleReason`。不得把任意自由文本拼入 SQL 分组键以外的逻辑判断；优先按结构化状态分类，原始原因仅用于展示和审计。

同一客户可以命中多个原因；`unupdatedReasons` 的各行客户数不承诺可直接相加等于总客户数。前端必须明确显示为“原因命中数”，避免误解为互斥分类。

## 6. 服务与 Repository 设计

### 6.1 服务层

新增标签 analytics 服务职责，名称可按现有风格调整：

1. 校验 ADMIN 权限。
2. 规范化标签事件窗口。
3. 解析 `teamLeaderIds` 对应的启用员工标识，并与显式员工筛选求交集。
4. 调用 `CustomerFilterValidator` 校验客户和标签筛选。
5. 调用 `CustomerAccessScopeResolver.currentScope()` 获取不可覆盖的客户范围。
6. 调用 `CustomerFilterQueryBuilder.build(...)` 生成唯一 `CustomerQuerySpec`。
7. 把同一个 QuerySpec 交给标签统计 Repository 的全部聚合查询。

团队筛选解析失败、团队不存在或团队内没有启用员工时，返回合法空统计，不得退化成全量统计。

### 6.2 Repository

- Repository 只接收已校验的 `CustomerQuerySpec` 和标签事件窗口，不重新解释客户筛选字段。
- 以 `customers c` 和 QuerySpec 为客户范围基表；复杂统计可使用派生表/CTE，但每个查询必须先限制客户范围。
- 当前快照查询统一复用有效正式标签 JOIN 条件，避免分类、标签、门店、团队和员工各写一套略有差异的有效性判断。
- `COUNT(DISTINCT c.id)` 用于客户数；分配数使用满足有效性条件的 assignment 行数。
- 当前快照不得 JOIN `system_tag_suggestions`。
- 来源和趋势查询只读 `customer_tag_assignments` 的事件字段，不把 `updated_at` 当作失效时间。
- 未更新原因优先使用 `tag_analysis_runs`、`tag_analysis_results`、`unmatched_legacy_tag_values` 和客户/标签时间比较，不新增推断字段或迁移。
- 返回顺序稳定：数量降序，其次中文展示名/内部代码升序；趋势按日期升序。
- 不使用 `SELECT *`，不逐客户循环查询，不产生 N+1。

### 6.3 旧漏斗标签语义

`AnalyticsRepository` 当前在线索漏斗中写死 `intent_level IN ('HIGH', 'MEDIUM')`。9B 要移除这类旧字段标签语义依赖：

- “意向确认”必须通过统一标签目录中绑定 `intentLevel` 的当前有效正式标签判断。
- 值不能通过中文展示名判断；应使用当前目录内部代码，且只接受启用、未合并、允许统计的标签值。
- 其他非标签业务条件，如已购项目、到店状态，继续使用现有客户字段。
- 本修改只替换写死标签语义，不重构整个漏斗算法。

## 7. 前端设计

- 在现有“运营分析看板”中增加一个独立的“标签统计”区块，不新建落地页，也不拆分现有九个 analytics 区块。
- 顶部筛选增加客户更新时间、标签事件时间、客户来源、意向门店、团队、员工和动态标签条件。
- 动态标签条件复用 Step 9A 已有分类、值、单/多选、`ANY/ALL` 和分类间 `AND/OR` 语义，不硬编码分类和值。
- 团队和员工使用账号数据；门店、客户来源和标签来源使用接口返回的 `filterOptions`，不只从当前页数据推导选项。
- `filterOptions` 只受当前账号访问范围限制，不受本次已选门店、员工、来源或标签条件影响，避免选中项在刷新后从下拉框消失。
- 筛选变化只刷新标签统计接口；不强制把新增标签筛选传给旧 analytics GET 接口，避免 9B 扩展成旧看板大改造。
- 标签统计接口失败时保留现有 analytics 区块和上次成功的标签数据，显示局部错误、失败摘要和重试按钮。
- 当前有效数量、来源、未更新原因和趋势都提供空状态；零值显示为 `0`，不能显示成缺失。
- 页面显示中文分类名和标签展示名；内部代码保留在导出列或辅助信息中，不把英文代码作为主文案。
- 现有 `downloadAnalyticsCsv()` 将标签统计作为新增数据段导出。导出使用当前已渲染数据，不重新发起请求，确保页面与 CSV 一致。
- CSV 至少包含当前筛选摘要、概览、分类、标签、门店、团队、员工、标签来源、未更新原因和趋势。

## 8. 错误处理与性能边界

- 非法客户筛选、标签目录项、团队 ID、时间范围或趋势粒度返回统一 400。
- 权限失败保持现有 403 行为。
- 无命中客户返回完整零值响应，不返回 404。
- 某个统计查询失败时整个标签统计接口失败并记录服务端错误；前端做区块级降级，不清空旧数据。
- 单次事件窗口最多 90 天；结果不返回逐客户明细。分类、标签、门店、团队、员工和来源必须返回全部非零聚合行，不能用任意 Top N 截断；未更新原因按固定原因码返回，原始示例原因只保留每类一条。
- 9B 不新增数据库迁移和统计快照。只有定向性能测试证明现有索引不足时，才单独评审索引变更，不在实现计划中预设迁移。
- 9B 不引入 Redis analytics 缓存。现有 5 分钟前端刷新频率保持不变，先以受限窗口和聚合 SQL 验证性能。

## 9. 测试要求

### 9.1 后端

- 请求契约：空请求、默认 7 天、完整客户筛选、团队筛选、非法日期和超过 90 天。
- 统一查询：统计与客户搜索在相同客户条件和访问范围下返回一致客户集合。
- 当前有效性：有效、历史失效、分类停用、值停用、分类合并、值合并、`use_for_statistics = 0`。
- 正式数据边界：`unmatched_legacy_tag_values`、`personality_tags` 和 6 条 `PENDING system_tag_suggestions` 不进入正式数量。
- 维度：分类、标签、门店、团队、员工、空值桶和去重客户数。
- 来源：系统推断、人工、历史迁移和未知来源；人工替换不重复计数。
- 未更新原因：无分析、`REJECTED`、`NO_CHANGE`、未匹配旧值、客户更新晚于标签变化，以及同一客户多原因命中。
- 趋势：新增、失效、净变化、连续日期补零、窗口边界和时区边界。
- 旧漏斗：移除 `intent_level IN ('HIGH','MEDIUM')`，改用当前有效统一标签，停用/合并标签不命中。
- Controller：200、400、403、零值响应和 `ApiResponse` 契约。
- Repository：无 N+1、参数顺序稳定、空员工/空团队不会退化成全量。

### 9.2 前端

- 标签统计筛选请求序列化：客户更新时间、标签事件时间、来源、门店、团队、员工和动态标签组合。
- 概览、分类、标签、维度、来源、原因和趋势渲染。
- 动态目录加载、空状态、零值、局部失败、保留旧数据和重试。
- CSV 包含当前筛选和全部标签统计数据段，且不额外请求接口。
- 标签统计失败不影响既有 analytics 区块。
- 中文显示名与内部代码不会混作主文案。

### 9.3 全量验证

- 后端 `mvn -q test`
- 前端 `npm test -- --run`
- 前端 `npm run typecheck`
- 前端 `npm run build`
- `npm run renderer:smoke`
- `npm run electron:smoke`

## 10. 验收标准

- 客户列表和标签统计对相同客户筛选、标签条件和权限范围的命中集合一致。
- 看板可以查看当前有效标签总量及分类/标签分布，并按门店、团队和员工分析。
- 指定标签事件窗口后，系统添加、人工添加或修改、未更新原因和新增/失效趋势正确。
- 当前数量不包含历史失效、停用、合并、不允许统计、未匹配旧值或系统建议记录。
- 现有 6 条 `PENDING system_tag_suggestions` 原文、状态和关联数据不被修改，并且不计入正式统计。
- 旧线索漏斗不再依赖写死的 `intent_level IN ('HIGH','MEDIUM')` 标签语义。
- 标签统计局部失败不会拖垮现有 analytics 看板；CSV 与当前页面筛选和已渲染数据一致。
- 不新增数据库迁移，不修改 Step 8 行为，不开启 `llm.reply_generation.enabled` 或 `llm.profile_extraction.enabled`。
- 后端和桌面端全量验证通过。

## 11. 非目标与后续衔接

- 9B 不实现 Step 9C 的跟进规则动态标签条件。
- 9B 不实现 Step 9D 的 CSV 客户导入、外部表格同步、写回或统一客户导出校验。
- 9B 不创建统计快照、BI 数据仓库或新的 analytics 平台。
- 9B 不修改标签自动判断、员工锁定、手工更新或目录合并行为。
- 9B 不扩大运营后台角色权限。
- 9B 不处理或自动确认现有 `system_tag_suggestions` PENDING 数据。

## 12. Review 问题

1. 是否接受“当前有效快照”和“标签事件窗口”分离的统计口径？
2. 是否接受团队筛选先解析为员工集合，再复用统一 `assignedKeepers` 查询条件？
3. 是否接受未更新原因允许同一客户命中多个原因，并明确显示为原因命中数？
4. 是否接受 9B 只增加一个标签统计接口和区块，不把全部旧 analytics API 改成结构化 POST？
