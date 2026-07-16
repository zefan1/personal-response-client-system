# Step 9A Implementation Plan：客户搜索与统一标签筛选

对应 spec：`dev-progress/tag_skill_llm_step9a_spec_062.md`  
日期：2026-07-16  
分支：`feature/tag-step8-reply-tag-context`  
状态：Draft，等待 plan review

## 0. 执行约束

- 不创建嵌套 worktree，不回退 Step 8，不修改主服务运行配置，不开启任何 LLM 生产开关。
- 手动编辑使用 `apply_patch`；先写失败测试，再写最小实现；每个阶段完成后运行该阶段的定向测试。
- 不新增数据库迁移，除非实现前通过真实 schema/`EXPLAIN` 证明现有索引无法支撑 9A，并单独获得范围确认。
- 保留现有 `GET /admin/api/v1/customers/search` 行为；新结构化入口使用 POST，不替换已有调用方。
- 9A 不实现统计、规则、CSV、外部同步或导出文件，但内部筛选契约必须能被这些模块复用。

## 1. 阶段总览

1. 基线和查询契约测试
2. 筛选模型、规范化和目录校验
3. SQL QuerySpec、数据权限和标签摘要
4. 管理 API（GET 兼容 + POST 结构化）
5. 桌面管理后台列表接入
6. 真实数据库回归、全量验证和断点记录

每个阶段都保持工作区可编译；阶段提交只包含该阶段的文件和测试。

## 2. 阶段 1：基线和查询契约测试

### 目标

锁定现有管理客户搜索的兼容行为、分页边界、稳定排序和响应形状，建立后续重构的回归基线。

### 先写测试

- 扩展 `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchControllerTest.java`：
  - GET `q/page/page_size` 仍返回原字段。
  - 缺省分页、非法分页和过长关键词返回现有错误码。
  - 空结果返回 200 分页结构而不是 404。
- 新增 `CustomerFilterContractTest`：验证默认排序、默认页大小、空列表语义和请求模型 JSON 映射。

### 实现

- 只新增空的领域 record/enum 骨架和兼容转换，不改变 SQL 逻辑。
- 明确 Java 字段名与 JSON 字段名（`pageSize`/`page_size`）的映射策略；GET 参数仍使用下划线。

### 验证和提交

```text
mvn -q -Dtest=CustomerAdminSearchControllerTest,CustomerFilterContractTest test
git diff --check
```

提交：`test: lock customer search compatibility contract`

## 3. 阶段 2：筛选模型、规范化和目录校验

### 目标

实现 spec 中的 `CustomerFilter`、`TagFilterGroup` 和请求校验，使查询构建器只接收已经规范化、已验证的 ID 集合。

### 目标文件

- `src/main/java/com/privateflow/modules/customer/admin/CustomerFilter.java`
- `src/main/java/com/privateflow/modules/customer/admin/TagFilterGroup.java`
- `src/main/java/com/privateflow/modules/customer/admin/CustomerSearchRequest.java`
- `src/main/java/com/privateflow/modules/customer/admin/CustomerFilterValidator.java`
- 如需公开标签摘要：`CustomerTagSummary.java`

### 先写测试

- `CustomerFilterValidatorTest`：
  - null/blank/重复字符串规范化。
  - page/pageSize、关键词长度、日期范围。
  - 分类不存在、停用、合并、`use_for_filter=0`。
  - 值不存在、停用、合并、跨分类引用。
  - SINGLE 分类多值和 ALL 拒绝；MULTI 分类 ANY/ALL 接受。
  - 重复分类组、重复 valueId 和空标签组拒绝。
  - `tagGroupLogic` 默认 AND，未知枚举返回标准 400。
- 使用 `TagDirectoryService` 的受控快照/fixture，不直接访问真实运行数据库。

### 实现顺序

1. 定义 `TagMatchMode`、`TagGroupLogic`、排序枚举。
2. 定义不可变请求/内部模型，统一空值和集合拷贝。
3. 实现 `CustomerFilterValidator`，通过 `TagCandidateBuilder.build(FILTER)` 建立允许的分类和值索引。
4. 把 GET 参数转换为同一 `CustomerFilter`；POST 直接走同一规范化入口。

### 验证和提交

```text
mvn -q -Dtest=CustomerFilterValidatorTest,CustomerFilterContractTest test
```

提交：`feat: add customer filter contract and tag validation`

## 4. 阶段 3：SQL QuerySpec、权限和标签摘要

### 目标

让总数、分页列表、排序和标签条件使用同一 SQL 条件，并把数据权限下沉到数据库查询。

### 目标文件

- `src/main/java/com/privateflow/modules/customer/admin/CustomerFilterQueryBuilder.java`
- `src/main/java/com/privateflow/modules/customer/admin/CustomerAccessScope.java`（或同等内部模型）
- `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepository.java`
- `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminListItem.java`
- `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchPage.java`
- `src/main/java/com/privateflow/modules/customer/service/CustomerAccessService.java`（仅增加可复用权限范围方法，不改变 `canAccess` 语义）
- 必要时新增 `CustomerTagSummaryRepository`，但优先复用一次批量查询。

### 先写测试

- `CustomerFilterQueryBuilderTest`：验证生成的 where 片段和参数顺序：
  - keyword/手机号数字片段。
  - sourceChannel、leadType、assignedKeeper、store、project、stage、更新时间范围。
  - 单标签、ANY、ALL、分类间 AND/OR。
  - 无标签条件不产生多余 JOIN。
  - 排序列只允许白名单，方向只允许 ASC/DESC。
- 扩展 `CustomerAdminSearchRepositoryTest`（H2 fixture）：
  - 当前有效标签命中；历史/失效/停用/合并不命中。
  - `system_tag_suggestions` PENDING 不影响筛选。
  - COUNT 与列表使用同一条件，最后一页和空结果正确。
  - 多标签查询不产生客户重复行。
- `CustomerAccessScopeTest`：ADMIN、KEEPER、LEADER、无权限角色、未分配客户。
- `CustomerTagSummaryRepositoryTest`：当前页批量加载、目录中文名/内部编码、无 N+1 访问。

### 实现顺序

1. 从 `CustomerAccessService` 提取当前用户可见 `assigned_keeper` 集合；非管理员排除空负责人。
2. 实现 QuerySpec：`whereClause + args + orderClause`，所有用户输入均为参数，排序使用白名单。
3. 标签组使用 `EXISTS`：ANY 为至少一个值；ALL 为所选值数量相等的集合谓词；组间按 AND/OR 合并。
4. 让 count 和 page 查询都调用同一 QuerySpec。
5. 先取当前页客户，再批量查询当前有效标签摘要并按 customerId 分组，避免 N+1。
6. 扩展列表响应的 `tags` 字段，保留旧字段和默认排序。

### 数据库检查

- 使用现有测试 schema 覆盖 `customers`、`customer_tag_assignments`、`tag_categories`、`tag_values`。
- 在真实 smoke 数据库只读执行 `SHOW INDEX`/`EXPLAIN`，确认当前索引能够支撑 `customer_id/category_id/tag_value_id/is_active` 访问；不写数据、不执行迁移。

### 验证和提交

```text
mvn -q -Dtest=CustomerFilterQueryBuilderTest,CustomerAdminSearchRepositoryTest,CustomerAccessScopeTest,CustomerTagSummaryRepositoryTest test
git diff --check
```

提交：`feat: apply unified customer filter query and access scope`

## 5. 阶段 4：管理 API

### 目标

接入 GET 兼容入口和 POST 结构化入口，统一错误映射和响应序列化。

### 目标文件

- `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchController.java`
- `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchService.java`
- `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchControllerTest.java`
- 必要时更新全局 API 参数/JSON 错误映射测试。

### 先写测试

- POST 完整标签请求：SINGLE、MULTI ANY、MULTI ALL、组间 AND/OR。
- POST 与 GET 对同一关键词返回相同客户顺序。
- 非法目录 ID、跨分类 valueId、非法日期和分页返回 400。
- ADMIN/KEEPER/LEADER 使用同一 controller contract 验证结果集合。
- response 的 tags 只含中文分类/值名称和内部编码，不含证据、历史分配或隐藏字段。

### 实现顺序

1. Service 负责请求转换、目录校验和权限范围注入。
2. Controller 增加 `@PostMapping`，保留现有 `@GetMapping`。
3. 统一分页响应和错误码；不在 controller 中复制 SQL 或业务校验。
4. 对旧调用方保持现有默认值和字段兼容。

### 验证和提交

```text
mvn -q -Dtest=CustomerAdminSearchControllerTest test
```

提交：`feat: expose structured customer search api`

## 6. 阶段 5：桌面管理后台列表接入

### 目标

让客户列表使用动态 FILTER 标签目录和结构化 POST 请求，提供可见的加载/空结果/失败状态。

### 先探索再编辑

- 先确认 `desktop/src/renderer/modules/admin/AdminConsole.vue` 当前客户列表/管理 API 路由和通用请求封装。
- 复用 `desktop/src/renderer/shared/apiClient.ts` 和现有 admin store 风格，不新增 HTTP 依赖。

### 目标文件

- `desktop/src/renderer/modules/admin/AdminConsole.vue`
- 如当前结构需要，新增 `customerSearchStore.ts`、`customerSearchTypes.ts` 和对应测试。
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts` 及新增 store/component tests。

### 先写测试

- FILTER 目录加载后按分类 `selectionMode` 渲染单选/多选。
- MULTI 分类的 ANY/ALL、多个分类的 AND/OR 序列化为 POST body。
- 搜索、重置、分页、排序保持同一 filter state。
- 结果展示中文标签摘要；内部编码不进入普通用户文案。
- 目录加载失败、400、网络错误、空结果和加载中状态。

### 实现顺序

1. 增加 typed API 函数和请求/响应类型。
2. 增加筛选状态规范化与序列化。
3. 接入目录加载（FILTER purpose）和选择控件。
4. 接入列表、分页、排序、重置及错误状态。
5. 保留现有管理控制台其他模块行为。

### 验证和提交

```text
cd desktop
npm test -- --run
npm run typecheck
npm run build
```

提交：`feat: connect admin customer list to unified filters`

## 7. 阶段 6：全量验证和交付记录

### 后端验证

```text
mvn -q test
```

预期：全量 Java 测试通过，允许已有 conditional skip，不新增 failure/error。

### 前端验证

```text
cd desktop
npm test -- --run
npm run typecheck
npm run build
npm run renderer:smoke
npm run electron:smoke
```

### 真实只读验收

- 保持主后端 8080 不动；如需专用验收服务，使用临时端口并在结束后停止。
- 使用 smoke 数据库动态创建一个 FILTER 分类和值，写入最小测试分配，验证 SINGLE、MULTI ANY/ALL、AND/OR、停用/合并和权限结果。
- 验收后删除临时分配/目录数据或使用事务回滚，确认 `system_tag_suggestions` 的 6 条 PENDING 原文和状态不变。
- 保持 `llm.reply_generation.enabled=false`、`llm.profile_extraction.enabled=false`。

### 文档和状态

- 更新 `dev-progress/tag_skill_llm_tasklist_056.md` 的 Step 9A 勾选项。
- 新增 `dev-progress/tag_skill_llm_step9a_breakpoint_064.md`，记录提交、测试数量、真实验收、运行服务和配置恢复状态。
- 只在所有验收通过后再将 spec 状态改为 Implemented，并推送分支。

## 8. 风险与回退

- 如果 H2 与 MariaDB 对 EXISTS/分页行为存在差异，补充 MariaDB integration test；不通过字符串拼接规避差异。
- 如果现有权限模型无法安全转换为 SQL 范围，先增加只读 scope 方法并保留旧 `canAccess`，禁止退回查询后过滤。
- 如果动态目录加载失败，前端显示失败状态并禁止提交标签条件；不回退为硬编码标签字典。
- 如果性能测试显示当前索引不足，停止扩大功能范围，提交单独的性能评审，不在 9A 临时增加物化索引表。

