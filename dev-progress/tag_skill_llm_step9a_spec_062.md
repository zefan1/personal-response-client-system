# Step 9A Spec：客户搜索与统一标签筛选

日期：2026-07-16  
分支：`feature/tag-step8-reply-tag-context`  
状态：Draft，等待 review

## 1. 目标

为客户列表提供可复用的结构化筛选契约，支持动态标签目录、单/多标签、任一/全部匹配，以及现有客户条件的组合查询。客户分页列表、后续导出和统计必须共享同一个筛选语义，避免接口各自解释标签条件。

9A 只交付客户搜索和列表接入；9B-9D 复用本 spec 定义的内部筛选契约，不在 9A 中提前实现统计、跟进规则、CSV 导入或外部同步。

## 2. 现状与约束

- 现有管理接口为 `GET /admin/api/v1/customers/search`，只支持 `q`、`page`、`page_size`。
- 现有 Repository 直接查询 `customers`，没有标签 JOIN、结构化字段条件或数据库级数据权限。
- 当前有效标签由 `customer_tag_assignments.is_active=1`、启用且未合并的 `tag_categories` 和 `tag_values` 共同定义。
- 标签目录已经提供 `use_for_filter`、分类选择模式和动态值，筛选只能使用 `TagCandidatePurpose.FILTER` 语义允许的目录项。
- `CustomerAccessService` 当前规则为：ADMIN 全量；KEEPER 只看本人负责客户；LEADER 看本人及启用下属管家；其他非管理员角色不能访问；非管理员不应看到未分配客户。
- 不新增客户标签索引表，不改变现有标签写入链路，不开启 LLM 生产开关，不回退 Step 8。

## 3. 用户场景

1. 运营人员按昵称、手机号、来源、门店、项目、管家、客户阶段或来源行标识搜索客户。
2. 运营人员从动态标签目录选择一个或多个分类和值进行筛选。
3. 单选分类只能选择一个值；多选分类可以选择多个值，并指定分类内 `ANY` 或 `ALL`。
4. 多个分类条件可以使用 `AND` 或 `OR` 组合，并与关键词/现有字段条件共同生效。
5. 翻页、排序和总数必须基于同一完整条件，不得先取一页再在内存中过滤。
6. 非管理员用户看到的总数、列表和标签摘要必须同时受到相同数据权限约束。
7. 列表结果带有当前有效标签的精简摘要，使操作者能够核对筛选结果。

## 4. 查询契约

### 4.1 内部模型

新增内部不可变查询模型，名称可按现有代码风格调整，但语义保持一致：

```text
CustomerFilter
  keyword: String?
  sourceChannels: List<String>
  leadTypes: List<String>
  assignedKeepers: List<String>
  intendedStores: List<String>
  intendedProjects: List<String>
  customerStages: List<String>
  updatedFrom: LocalDateTime?
  updatedTo: LocalDateTime?
  tagGroups: List<TagFilterGroup>
  tagGroupLogic: AND | OR
  sortBy: UPDATED_AT | CREATED_AT | NICKNAME | ID
  sortDirection: ASC | DESC
  page: int
  pageSize: int

TagFilterGroup
  categoryId: long
  valueIds: List<long>
  match: ANY | ALL
```

字段列表只包含当前客户模型已有字段；9A 不新增自由 SQL、任意字段名或任意运算符。关键词继续覆盖现有管理搜索字段和手机号数字片段。空列表代表不施加该字段条件。

### 4.2 标签匹配语义

- 每个 `TagFilterGroup` 至少包含一个值；重复分类或重复值在服务层规范化并拒绝歧义输入。
- 分类必须存在、启用、未合并，并且 `use_for_filter=1`；值必须属于该分类、启用、未合并。
- 分类为 `SINGLE` 时只能有一个 `valueId`，`ANY` 与单值等价；提交多个值或 `ALL` 组合返回 400。
- 分类为 `MULTI` 时：
  - `ANY`：客户当前有效标签中命中任意一个所选值即可。
  - `ALL`：客户当前有效标签必须同时命中全部所选值。
- `tagGroupLogic=AND` 要求每个分类组都满足；`OR` 要求至少一个分类组满足。
- 只统计当前有效分配；停用/合并目录项、历史分配、失效分配和未匹配的 `system_tag_suggestions` 均不命中。
- 使用 `EXISTS`/`NOT EXISTS` 或等价的集合谓词实现，避免多标签 JOIN 造成客户重复行；总数和列表必须使用同一个谓词。

### 4.3 数据权限

查询服务在构建 SQL 前解析当前 `AuthContext`，将权限约束作为 `CustomerFilter` 的不可覆盖部分：

- ADMIN：不增加负责人条件。
- KEEPER：`assigned_keeper` 只允许当前用户名。
- LEADER：`assigned_keeper` 只允许当前用户名和 `AccountRepository` 返回的启用下属管家。
- 其他非管理员角色：返回空结果或统一拒绝，行为与现有服务约定一致。
- 非管理员永远不匹配 `assigned_keeper IS NULL OR blank` 的客户。

权限条件必须进入 `COUNT` 和分页 SQL，不能依赖查询后 `CustomerAccessService.canAccess` 过滤。

## 5. HTTP API

### 5.1 兼容入口

保留 `GET /admin/api/v1/customers/search`：

- 继续接受 `q`、`page`、`page_size`。
- 将参数转换为 `CustomerFilter`，默认排序为 `updatedAt DESC, id DESC`。
- 现有调用方不需要迁移，响应字段保持兼容；新增 `tags` 字段为空数组或当前标签摘要。

### 5.2 结构化入口

新增 `POST /admin/api/v1/customers/search`，请求体为 `CustomerSearchRequest`，字段与 `CustomerFilter` 一一对应，分页和排序也在请求体中表达。响应为统一分页结构：

```json
{
  "items": [
    {
      "id": 1,
      "phone": "188****1111",
      "nickname": "示例客户",
      "assignedKeeper": "keeper-1",
      "updatedAt": "2026-07-16T10:20:30",
      "tags": [
        {
          "categoryId": 12,
          "categoryKey": "personality_type",
          "categoryName": "性格类型",
          "valueId": 101,
          "valueCode": "LOYALIST",
          "displayName": "忠诚型"
        }
      ]
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20,
  "totalPages": 1
}
```

结构化入口只返回动态标签的中文分类/值名称和内部编码，不披露标签内部推理证据、隐藏字段或历史失效分配。

### 5.3 校验与错误

- `page` 从 1 开始；`pageSize` 默认 20，范围 1-50。
- 关键词最大 100 字符；列表字段去空格、去重，空字符串忽略。
- 日期范围必须满足 `updatedFrom <= updatedTo`。
- 标签分类/值不存在、已停用、已合并、用途不允许或分类和值不匹配时返回标准 400 API 错误。
- 不因无结果返回 404；权限过滤后的空结果仍返回合法分页结构。
- 保持现有 `ApiResponse` 和全局异常映射，不吞数据库或目录读取异常。

## 6. Repository 设计

- 抽取 `CustomerFilterQueryBuilder`（或同等职责组件），产出带参数的 `WHERE` 片段和排序白名单；禁止拼接用户提供的列名或 SQL。
- 客户列表和总数调用同一 QuerySpec；分页只在列表查询末尾追加 `LIMIT/OFFSET`。
- 标签谓词统一使用 `customer_tag_assignments` 与目录表的有效性 JOIN/EXISTS，至少覆盖 `category_id`、`tag_value_id`、`is_active`、目录启用/合并状态。
- 标签摘要使用一次可控的批量查询或聚合查询，避免 N+1；排序和分页完成后只为当前页加载摘要。
- 标签目录校验集中在服务层，查询构建器只接受已规范化、已验证的 ID 集合。
- 结果映射继续复用 `CustomerRowMapper` 能力，但 `CustomerAdminListItem` 扩展不可变 `tags` 字段。

## 7. 前端接入边界

- 在现有管理后台客户列表中增加动态标签目录加载，目录来源使用 FILTER purpose，不硬编码分类和值。
- 分类选择控件根据 `selectionMode` 限制单选/多选；多选分类提供 `ANY/ALL`；多个分类提供 `AND/OR`。
- 搜索、重置、分页、排序都通过同一个结构化请求模型发起；保留旧 GET 调用的兼容能力。
- 列表展示标签中文名称，必要时在详情/调试信息中保留内部编码；不展示内部推理证据。
- 加载、空结果、目录加载失败、400 校验失败和网络失败必须有可见状态，不得静默吞错。

## 8. 测试要求

### 后端

- `CustomerFilter` 规范化和校验：空值、去重、日期范围、分页范围、单选/多选约束、目录用途和合并/停用值。
- QuerySpec：关键词、现有字段条件、标签组 `ANY`/`ALL`、组间 `AND`/`OR`、无标签条件、组合条件。
- 数据权限：ADMIN、KEEPER、LEADER、未分配客户和无权限角色；验证 `COUNT` 与列表一致。
- 标签有效性：只命中当前有效分配；历史、失效、停用、合并和 `PENDING system_tag_suggestions` 不命中。
- Controller：GET 兼容入口、POST 结构化入口、标准 400 错误和分页响应。
- 标签摘要批量加载不产生 N+1，列表稳定排序和最后一页边界。

### 前端

- 目录加载和 FILTER purpose 过滤。
- 单选/多选以及 ANY/ALL、AND/OR 状态序列化。
- 搜索、重置、分页、排序和空结果状态。
- 400、网络错误和目录加载失败提示。
- 结果标签摘要展示及内部编码不泄露到普通用户文案。

## 9. 验收标准

- 使用动态创建的启用分类和值，可以完成单标签、多标签、分类内 ANY/ALL、分类间 AND/OR 查询。
- 修改或停用标签目录后，筛选行为遵循最新目录快照；合并值不会被新查询命中。
- 同一请求的 `total`、分页结果、排序结果一致，跨页无重复客户。
- ADMIN、KEEPER、LEADER 的结果集合与既有 `CustomerAccessService` 规则一致。
- GET 兼容入口行为不回退，POST 结构化入口覆盖完整 9A 契约。
- 后端自动化测试全绿；前端 typecheck、build、Vitest 和 renderer/electron smoke 全绿。
- 不新增 9B-9D 范围内的统计、规则、导入导出实现，不修改 Step 8 行为。

## 10. 非目标与后续衔接

- 9A 不实现标签统计、趋势、来源/未更新原因分析。
- 9A 不实现跟进规则条件编辑器。
- 9A 不实现 CSV 导入、外部表格同步、写回和导出文件；这些模块必须在后续接入同一个 `CustomerFilter` 和标签目录校验器。
- 9A 不引入物化检索索引表；只有在实际查询规模和性能测试证明必要时，另行评审 9A-Performance 变体。

## 11. Review 问题

1. 是否接受 GET 兼容入口 + POST 结构化入口的双入口设计？
2. 是否接受分类内 `ANY/ALL`、分类间 `AND/OR` 的标签匹配语义？
3. 是否接受把数据权限约束下沉到 COUNT/分页 SQL，而不是查询后过滤？

