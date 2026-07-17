# Step 9E 客户查询分页、导出与权限统一设计

日期：2026-07-17  
分支：`feature/tag-step8-reply-tag-context`

## 目标

让客户列表、分页和导出严格复用同一套结构化筛选、排序和数据权限条件，避免旧兼容入口或导出结果绕过账号可见范围。

## 现状与缺口

- `POST /admin/api/v1/customers/search` 已通过 `CustomerFilterValidator`、`CustomerAccessScopeResolver` 和 `CustomerFilterQueryBuilder` 查询。
- 旧的 `GET /admin/api/v1/customers/search` 兼容入口仍直接调用全量权限路径，KEEPER/LEADER 可能看到超出权限范围的数据。
- 客户查询没有后端导出接口，前端无法导出当前完整筛选结果。
- 标签统计和标签目录已有 CSV 下载模式，可复用同步二进制下载方式，不需要新建导出任务表。

## 设计

### 1. 统一查询入口

`CustomerAdminSearchService` 的结构化查询、旧 GET 查询和导出都执行同一顺序：

1. 将请求转换为 `CustomerFilter`。
2. 通过 `CustomerFilterValidator` 规范化分页、排序、列表值和动态标签条件。
3. 通过 `CustomerAccessScopeResolver.currentScope()` 取得当前账号可见范围。
4. 将规范化条件和权限范围交给 `CustomerAdminSearchRepository`。
5. Repository 只通过 `CustomerFilterQueryBuilder.build(filter, scope)` 生成 WHERE、参数和排序。

旧 GET 只构造关键词、页码和页大小，其余条件使用默认值，但不再使用 `CustomerAccessScope.all()`。

### 2. 客户导出接口

新增：`POST /admin/api/v1/customers/export`

- 请求体使用现有 `CustomerSearchRequest`，忽略其中的分页位置，保留筛选和排序。
- 响应为 UTF-8 BOM 的 `text/csv` 文件，文件名为 `customers.csv`。
- 导出字段与客户查询列表保持同一数据来源，包含客户基础资料、跟进/预约信息、来源、更新时间和当前有效标签的中文名称及内部编码。
- 导出只包含当前账号权限范围内的客户。
- 使用固定最大行数 `10,000`，先按同一查询条件统计总量；超过上限返回 `400` 和中文错误信息，不生成部分文件。
- 导出排序与列表排序一致，确保分页浏览和导出顺序可比较。
- CSV 单元格统一进行引号转义和公式注入保护。

### 3. 前端使用方式

在现有“客户查询”区域增加“导出当前查询”按钮：

- 复用 `customerSearchRequest()` 生成请求体，不携带当前页限制。
- 通过 API client 的 `postBlob` 下载二进制响应并触发浏览器下载。
- 成功和超限错误使用现有后台提示机制，不改变列表分页行为。

## 数据流

```text
GET/POST search 或 POST export
        |
CustomerFilterValidator
        |
CustomerAccessScopeResolver
        |
CustomerFilterQueryBuilder
        |
COUNT / page query / export query
        |
同一权限范围和筛选结果
```

## 错误处理

- 无效分页、筛选或标签条件沿用现有 `ApiException` 和中文错误。
- 导出超过 10,000 行返回 `400`，错误说明要求缩小筛选范围。
- 导出查询失败不返回部分 CSV，前端显示现有请求错误。
- 未登录或权限不足继续由现有认证过滤器处理。

## 测试范围

后端：

- GET 兼容查询把当前账号权限传入 Repository。
- 导出 Controller 返回 CSV、UTF-8 内容类型和附件文件名。
- 导出复用结构化筛选和权限范围，并拒绝超过 10,000 行的数据。
- H2 Repository 测试验证列表、分页和导出在 KEEPER/LEADER 范围外客户不可见，且标签筛选保持一致。
- CSV 转义和公式注入保护测试。

前端：

- 客户查询区域显示导出按钮。
- 导出请求使用与列表相同的筛选 body，不携带当前页条件。
- 成功下载文件，失败显示提示。
- 类型检查和构建通过。

## 非目标与保护项

- 不新增数据库迁移或导出任务表。
- 不修改客户标签、LLM 开关、Step 8 回复上下文和现有数据源导入/同步行为。
- 不改变 GET 兼容接口的关键词语义，只修正权限和统一校验路径。
- 不处理 Step 9 中现有 `system_tag_suggestions` PENDING 记录；该项保留到后续验收任务。

## 验收标准

1. ADMIN、LEADER、KEEPER 使用相同筛选条件时，列表、分页和导出返回同一权限范围。
2. 标签筛选、组合条件和排序在列表与导出中一致。
3. 导出超过上限不会生成部分文件。
4. 后端和前端相关测试、类型检查、构建全部通过。
5. 更新 Step 9E 断点和任务清单，记录测试数量与当前服务状态。
