# Step 9D 断点：CSV、外部表格同步与写回统一标签校验

日期：2026-07-17  
分支：`feature/tag-step8-reply-tag-context`  
前置规格：`tag_skill_llm_step9d_spec_070.md`  
实施计划：`docs/superpowers/plans/2026-07-17-tag-step9d-data-exchange.md`  
实现代码 HEAD：`cadb6ef`

状态：Step 9D 已完成，未 push、未 merge、未创建 PR。

## 本轮结果

- 新增统一标签交换层：按正式编码、显示名称、同义词做精确解析，最终调用 `TagSelectionValidator` 的 `IMPORT` 用途。
- CSV 导入支持标签字段校验；普通资料继续导入，返回未匹配数量和行号。
- 外部表格同步使用同一入站结果；标签未匹配属于部分成功，拉取/映射/数据库失败时不留下本地部分更新。
- 手工写回、自动写回和重试队列使用同一出站过滤；无效标签不发送，远端失败不修改本地有效标签。
- 旧 `CUSTOMER_FIELD` 标签桥接路径保持兼容；来源感知记录保留 CSV、外部同步和表格写回的来源类型。
- 未匹配记录继续使用现有 `unmatched_legacy_tag_values`，跨来源按客户、字段和原文保持幂等；非数字外部行号记录到现有 `resolution_note`，没有新增字段。
- AdminConsole 在现有 CSV 导入结果区域显示未匹配标签数量和涉及行号。

## 主要提交

- `9f91a66` `feat: add unified tag exchange validation`
- `dacb33f` `feat: persist source-aware unmatched tag history`
- `9024eec` `feat: validate CSV tag fields during import`
- `7cdb1d1` `feat: validate external sheet tags during sync`
- `92c9746` `feat: protect table writeback with tag validation`
- `bfaa128` `feat: show unmatched CSV tag results`
- `0630356` `fix: preserve unmatched tag exchange results`
- `cadb6ef` `fix: keep tag exchange retries and history idempotent`

## 验证记录

后端递归模块测试：

```text
mvn -q -Dtest=com.privateflow.modules.followup.**.*Test,com.privateflow.modules.tags.**.*Test,com.privateflow.modules.customer.**.*Test,com.privateflow.modules.tablewrite.**.*Test test
```

结果：48 个测试套件、183 tests，0 failures、0 errors、1 conditional skip。

前端验证：

```text
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
npm run typecheck
npm run build
```

结果：AdminConsole 38 tests 通过，typecheck 通过，生产构建通过。

## 保护项核对

- 未新增 Flyway migration。
- 未写入或更新现有 6 条 `system_tag_suggestions.status=PENDING` 建议。
- `llm.reply_generation.enabled=false`、`llm.profile_extraction.enabled=false` 保持关闭。
- Step 8 回复标签上下文行为未修改。
- 未执行 merge、push 或创建 PR。

## 下一步

Step 9D 已完成。下一步回到 Step 9 总任务中尚未完成的分页/导出查询条件统一和其他全量真实部署核验；本断点之后不自动扩大到新的数据交换类型。
