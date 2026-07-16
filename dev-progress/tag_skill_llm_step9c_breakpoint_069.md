# Step 9C 断点：跟进规则动态标签条件

日期：2026-07-16
分支：`feature/tag-step8-reply-tag-context`
实现代码 HEAD（最终补强提交后）：`ab7d0e2`
状态：Step 9C 已完成；Step 9D 未开始

## 本轮结果

跟进规则已接入统一动态标签目录。运营人员可以在后台选择标签分类和值，配置分类内 `ANY/ALL` 和条件间 `AND/OR`；后端保存和运行时都会重新校验标签状态。

运行时只使用客户当前 active、分类和值启用、未合并且 `use_for_followup_rules=1` 的正式标签。标签停用、合并或取消跟进规则用途后，规则返回不命中，不会抛出业务错误或影响普通字段规则。

`TAG_CHANGE` 仍然只生成待确认建议，不直接修改客户正式标签。新规则写入正式 `tag_value_id` 和 `validation_status=VALIDATED`；旧的只有 `tagName` 的规则继续走 `UNVALIDATED_RULE_TEXT` 兼容路径。

## 条件与动作契约

标签条件：

```json
{
  "field": "tag",
  "op": "MATCH",
  "categoryId": 50,
  "valueIds": [51, 52],
  "match": "ANY"
}
```

正式标签建议目标：

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

## 实现提交

- `c574b56` `feat: evaluate followup tag conditions`
- `370da7b` `feat: load effective followup tags once`
- `f2b1c8b` `feat: validate followup tag rule selections`
- `fb7f299` `feat: persist validated followup tag suggestions`
- `4c5278a` `test: cover formal followup tag references`
- `fb79a1c` `feat: configure followup tag rules in admin`
- `ab7d0e2` `fix: tighten formal followup tag suggestions`

## 验证记录

后端：

```text
mvn -q -Dtest='com.privateflow.modules.followup.*Test,com.privateflow.modules.tags.*Test' test
```

结果：22 个 Surefire 测试套件，128 tests，0 failures，0 errors，1 conditional skip。

前端：

```text
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
npm run typecheck
npm run build
```

结果：AdminConsole 38 tests 全部通过；typecheck 通过；生产构建通过。

## 保持不变的范围

- 未新增 Flyway migration。
- 未执行任何更新现有 `system_tag_suggestions` PENDING 数据的 SQL；现有 6 条记录未被本轮代码修改。
- 未开启 `llm.reply_generation.enabled` 或 `llm.profile_extraction.enabled`。
- 未修改 Step 8 回复标签上下文行为。
- 未 merge、push 或创建 PR。
- 未进入 Step 9D 的 CSV、外部表格同步、写回和统一导出校验。

## 下一步

进入 Step 9D 前重新执行 brainstorming，确认 CSV 导入、外部表格同步/写回、未匹配值和失败保护的业务边界，再编写独立规格与实施计划。
