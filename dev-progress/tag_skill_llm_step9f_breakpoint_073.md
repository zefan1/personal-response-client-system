# Step 9F 断点：旧自由文本标签建议归档

日期：2026-07-17  
分支：`feature/tag-step8-reply-tag-context`  
工作树：`C:\Users\85314\.config\superpowers\worktrees\private-domain-assistant\tag-step4-unified-access`  
当前 HEAD：本断点提交即为最新提交

## 状态

Step 9F 已完成，未 push、未 merge、未创建 PR。目标是把审计确认的 6 条旧内置规则自由文本建议归档，同时保留完整原文和关联关系，不猜测正式标签映射。

## 已提交实现

- `8a24a64 feat: archive legacy tag suggestions`
  - 新增 `V70__archive_legacy_tag_suggestions.sql`。
  - 更新 `TagFlywayMariaDbIntegrationTest`：fresh 目标版本改为 V70，并新增 V69→V70 两阶段归档测试。
- `429e3c1 test: protect archived legacy tag behavior`
  - `ActionExecutorTest` 覆盖迁移后 `ALERT`/`NOTIFY_LEADER` 普通提醒路径不写标签建议。
  - `TagAnalyticsRepositoryTest` 覆盖 `IGNORED` 未匹配值不进入当前缺口统计。

## V70 行为

迁移条件限定为：建议 `PENDING`、验证状态 `UNMATCHED_LEGACY`、关联未匹配记录来源 `SYSTEM_TAG_SUGGESTION`、内置规则 4/5 且原动作 `TAG_CHANGE`。

执行顺序：

1. 关联 `unmatched_legacy_tag_values` 更新为 `IGNORED`，写入 `resolution_note`、`resolved_by=SYSTEM_MIGRATION_9F`、`resolved_at`。
2. 关联 `system_tag_suggestions` 更新为 `IGNORED`，写入 `ignored_at`，保留原文、哈希、客户、规则和关联 ID。
3. 内置规则 4“沉睡风险”改为 `ALERT`，规则 5“可能流失”改为 `NOTIFY_LEADER`；不改条件 JSON 和动作配置。

不会创建正式标签、不会写入 `customer_tag_assignments`、不会修改客户旧字段，也不会开启 LLM。

## 验证记录

- 两阶段 MariaDB 迁移测试：V69 基线插入 6 条建议和 6 条未匹配记录，V70 归档全部目标记录，保留原文/哈希/客户/规则/关联 ID，测试库正式分配数为 0，第二次迁移执行 0 次。
- fresh MariaDB 测试：空库直接迁移到 V70，第二次迁移执行 0 次。
- 相关后端套件：

```text
mvn -Dtest=com.privateflow.modules.customer.admin.**.*Test,com.privateflow.modules.analytics.**.*Test,com.privateflow.modules.followup.**.*Test,com.privateflow.modules.tags.**.*Test,com.privateflow.modules.tablewrite.**.*Test test
200 tests, 0 failures, 0 errors, 2 conditional skips
```

- focused 回归：`ActionExecutorTest` 5 tests、`TagAnalyticsRepositoryTest` 5 tests，全部通过。
- 真实 `private_domain_assistant_smoke`：V69→V70 成功，重复迁移 0 次；只读核对结果为建议 1-6 `IGNORED`、未匹配 16-21 `IGNORED` 且 6 条 `resolved_by=SYSTEM_MIGRATION_9F`、规则 4=`ALERT`、5=`NOTIFY_LEADER`、两个 LLM 开关均为 `false`。
- 真实库正式分配基线核对时为 6 条，迁移后为 11 条；新增行来自验证期间运行服务的 `LEGACY_FIELD_SYNC`/`MANUAL` 写入，V70 SQL 本身不包含分配表写操作。

## 当前运行环境

- 后端：`http://127.0.0.1:8082`
- 前端：`http://127.0.0.1:5175/`（同时检测到 5173、5174 监听）
- 数据库：`private_domain_assistant_smoke`，Flyway V70
- 两个 LLM 开关：`llm.reply_generation.enabled=false`、`llm.profile_extraction.enabled=false`

## 下一步

1. 提交本断点和 tasklist 更新。
2. 按 Step 10 继续完整后端/前端自动化测试、结构对齐和真实部署只读核验。
3. 在用户明确选择后再执行 merge、push 或创建 PR。
