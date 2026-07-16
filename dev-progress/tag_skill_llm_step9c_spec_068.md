# Step 9C 规格：跟进规则动态标签条件

日期：2026-07-16  
状态：已批准，待编写实施计划  
前置断点：[Step 9B 断点](tag_skill_llm_step9b_breakpoint_067.md)

## 1. 目标

在现有跟进规则引擎中增加正式动态标签条件，使运营人员可以按照统一标签目录配置提醒、通知组长和标签建议规则。

规则条件必须与客户搜索和标签统计使用相同的正式标签语义：分类和值来自标签目录，分类和值的启停、合并和业务用途实时影响规则保存和命中。

本步骤不直接修改客户正式标签。`TAG_CHANGE` 继续生成待确认的系统建议；正式标签写入和外部数据交换不属于本步骤。

## 2. 范围与非目标

### 2.1 本步骤范围

- 跟进规则条件增加动态标签条件。
- 条件支持分类内 `ANY/ALL` 和分类间 `AND/OR`。
- 新建/编辑规则时从启用的动态目录选择分类和值。
- 保存时校验分类和值仍允许用于跟进规则。
- 运行时只匹配当前有效正式标签分配。
- 标签停用、合并或取消跟进规则用途后，规则不再命中且不抛出业务错误。
- `TAG_CHANGE` 目标从自由文本升级为正式分类和值引用。
- 标签合并时更新规则条件和动作中的正式引用。
- 兼容现有只有 `tagName` 的内置/历史规则。

### 2.2 非目标

- 不新增数据库迁移。
- 不修改现有 6 条 `system_tag_suggestions` 的原文、状态或关联数据。
- 不把规则动作改成直接写入 `customer_tag_assignments`。
- 不修改 Step 8 回复标签上下文行为。
- 不实现 Step 9D 的 CSV 客户导入、外部表格同步、写回和统一标签导出校验。
- 不重构旧字段条件；`leadType`、`lastFollowupHours`、`appointmentDate` 等现有条件继续可用。

## 3. 条件契约

现有条件 JSON 外壳保持不变。动态标签使用新的叶子条件：

```json
{
  "field": "tag",
  "op": "MATCH",
  "categoryId": 50,
  "valueIds": [51, 52],
  "match": "ANY"
}
```

字段约束：

- `field` 固定为 `tag`。
- `op` 固定为 `MATCH`。
- `categoryId` 必须为正整数。
- `valueIds` 必须为同一分类下的正整数数组。
- `match` 只能是 `ANY` 或 `ALL`。
- `conditions` 中可以存在多个标签条件；根对象 `operator` 决定这些条件之间是 `AND` 还是 `OR`。
- 旧条件叶子和旧 `operator/orGroups` 结构保持兼容。

示例：

```json
{
  "operator": "AND",
  "conditions": [
    { "field": "leadType", "op": "EQ", "value": "XIAN_SUO" },
    {
      "field": "tag",
      "op": "MATCH",
      "categoryId": 50,
      "valueIds": [51, 52],
      "match": "ANY"
    }
  ]
}
```

多个分类的 `AND/OR` 语义由根 `operator` 表达；同一分类内的 `ANY/ALL` 由标签条件的 `match` 表达。管理后台不得硬编码分类 ID、值 ID 或分类名称。

## 4. 保存校验

`RuleAdminService` 保存规则时继续执行 JSON 语法、复杂度和旧字段白名单校验，并新增标签条件校验：

1. 读取每个 `field=tag/op=MATCH` 节点。
2. 调用 `TagSelectionValidator.validateIds(TagCandidatePurpose.FOLLOWUP_RULE, categoryId, valueIds, context)`。
3. `context.businessBasis` 使用规则名称和标签条件摘要，保证 `FOLLOWUP_RULE` 用途的业务依据非空。
4. 分类必须启用、未合并、`use_for_followup_rules=1`。
5. 标签值必须启用、未合并、属于所选分类。
6. SINGLE 分类只能有一个值；MULTI 分类至少有一个值。
7. 任何失败都返回现有统一 400/`CONDITION_PARSE_FAILED` 或业务校验错误，不写入规则。

`TAG_CHANGE` 新规则的 `actionConfig` 使用正式目标：

```json
{
  "tagCategoryId": 50,
  "tagCategoryKey": "intent_level",
  "tagValueId": 51,
  "tagValue": "HIGH",
  "tagName": "高意向",
  "notifyLeader": false
}
```

新建或编辑 `TAG_CHANGE` 规则时，正式目标也通过 `FOLLOWUP_RULE` 用途校验。已有只有 `tagName` 的规则不强制回填，保留兼容路径。

## 5. 运行时命中

### 5.1 当前有效标签上下文

`RuleMatcher` 为每个客户加载一次规则所需的当前标签上下文，再将上下文传给 `ConditionEvaluator`。标签读取必须满足：

- `customer_tag_assignments.customer_id` 等于当前客户。
- `is_active=1`。
- 分类启用、未合并、`use_for_followup_rules=1`。
- 标签值启用、未合并且属于当前分类。

同一客户的多条规则共享这一次标签上下文，不按“规则 × 条件”重复查询。

### 5.2 ANY/ALL 与 AND/OR

- `match=ANY`：分类中至少命中一个 `valueId`。
- `match=ALL`：分类中必须命中全部 `valueIds`。
- 根 `operator=AND`：所有条件都命中。
- 根 `operator=OR`：任一条件命中。
- 现有 `orGroups` 语义继续按旧实现执行，标签叶子可以出现在其中。

目录停用、合并或取消 `use_for_followup_rules` 后，当前有效标签上下文不会包含该项，规则返回不命中而不是异常。

### 5.3 读取失败

- 标签目录服务优先使用最近一次成功快照；首次加载失败返回空快照。
- 单条规则解析或标签读取失败时，`RuleMatcher` 记录规则 ID 并跳过该规则。
- 其他规则和同一客户的普通字段条件不受影响。

## 6. TAG_CHANGE 建议

`ActionExecutor` 保持“待确认建议”行为：

- 正式目标存在时，调用建议仓储写入 `categoryId/tagValueId/tagName`，并将新建议标记为目录校验通过。
- 目标在运行时已停用、合并或不再允许用于跟进规则时，不生成建议，并记录跳过原因。
- 旧规则只有 `tagName` 时继续调用旧兼容路径，状态保持 `UNVALIDATED_RULE_TEXT`。
- 不更新客户当前正式标签，不改变已有 PENDING 建议。
- 去重仍使用现有客户、目标标签和规则维度。

数据库已经存在 `system_tag_suggestions.tag_value_id`、`validation_status` 等字段，本步骤直接复用，不新增迁移。

## 7. 合并引用

现有 `TagRuleReferenceService` 继续负责规则引用统计和结构化替换：

- 条件中的 `categoryId`、`categoryKey`、`valueIds`、`tagValueId`、`tagValue` 一并更新。
- 动作中的 `tagCategoryId`、`tagCategoryKey`、`tagValueId`、`tagValue`、`tagName` 一并更新。
- 无法建立源值到目标值的映射时，沿用现有合并保护，不生成悬空引用。
- 合并完成后重新加载规则，后续命中使用目标分类和值。

## 8. 管理后台

跟进规则表单保留现有普通条件字段，并增加动态标签条件编辑器：

- 从 `tagCategoryOptionsCache` 读取 `useForFollowupRules=true` 的启用未合并分类和值。
- 支持添加多个标签条件组。
- 每组选择分类、一个或多个值和 `ANY/ALL`。
- 提供分类间 `AND/OR` 组合选择。
- `TAG_CHANGE` 动作显示正式目标分类和值选择器；选择后自动填充中文名和内部编码。
- 编辑旧文本规则时显示兼容提示，不强制修改，保存为旧格式除非用户明确选择正式目标。
- 目录请求失败时显示错误并保留现有表单数据，不把条件清空。

## 9. 错误契约

保存阶段：

- 非法 JSON、未知字段、非法标签分类/值、停用/合并目录项和用途不允许，返回 400。
- 错误消息包含分类和值的中文名称或内部 ID，便于后台定位。

运行阶段：

- 规则不命中时不生成提醒或建议。
- 单规则错误写日志，不影响其他规则。
- 旧文本建议仍可按兼容逻辑运行。

## 10. 测试与验收

后端必须覆盖：

- 标签条件 JSON 解析和字段白名单。
- SINGLE/MULTI、ANY/ALL、分类 AND/OR。
- 保存时启用、未合并和 `use_for_followup_rules` 校验。
- 当前有效分配命中；失效、停用、合并和用途关闭不命中。
- 同一客户多个标签条件共享一次上下文读取。
- 正式 TAG_CHANGE 建议写入目标 ID，旧文本规则兼容。
- 标签目录刷新失败、规则错误隔离和合并引用更新。

桌面端必须覆盖：

- 动态分类和值加载。
- 多条件、ANY/ALL、AND/OR 序列化。
- TAG_CHANGE 正式目标序列化。
- 旧文本规则编辑兼容和目录加载失败保留表单。

验收命令：

```text
mvn -q -Dtest='com.privateflow.modules.followup.*Test,com.privateflow.modules.tags.*Test' test
cd desktop; npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
cd desktop; npm run typecheck
cd desktop; npm run build
```

## 11. 保护项

- 不修改 Step 8。
- 不开启 `llm.reply_generation.enabled` 或 `llm.profile_extraction.enabled`。
- 不修改现有 6 条 `system_tag_suggestions` PENDING 数据。
- 不新增 Flyway migration。
- 不进入 Step 9D 数据交换和写回范围。
