# Step 9F 历史自由文本标签建议归档设计

日期：2026-07-17  
分支：`feature/tag-step8-reply-tag-context`

## 目标

处理当前数据库中由旧内置跟进规则产生的 6 条自由文本标签建议：保留全部原始内容和关联关系，将它们从待处理队列中归档，并阻止这两条内置规则继续生成不属于正式标签目录的建议。

## 只读审计结果

- `system_tag_suggestions` 中共有 6 条 `status='PENDING'` 且 `validation_status='UNMATCHED_LEGACY'` 的记录，ID 为 1-6。
- 原文只有“沉睡风险”和“可能流失”，分别来自内置规则 ID 4、5。
- 6 条记录通过 `unmatched_legacy_value_id` 关联 `unmatched_legacy_tag_values` ID 16-21。
- 当前正式标签目录没有这两个文本的精确对应值；不能猜测映射为“已流失”或其他标签。
- 现有统计只把 `unmatched_legacy_tag_values.status='PENDING'` 作为“未匹配旧标签”原因，正式标签统计不会读取这些建议。

## 设计

### 1. Flyway V70 数据迁移

新增 `V70__archive_legacy_tag_suggestions.sql`，只处理满足以下全部条件的记录：

- `system_tag_suggestions.status = 'PENDING'`
- `system_tag_suggestions.validation_status = 'UNMATCHED_LEGACY'`
- `unmatched_legacy_value_id` 关联 `source_type = 'SYSTEM_TAG_SUGGESTION'`
- 规则是内置规则 4 或 5，且原动作类型为 `TAG_CHANGE`

迁移步骤：

1. 将关联的 `unmatched_legacy_tag_values` 更新为 `status='IGNORED'`。
2. 写入 `resolution_note`，说明这是旧内置规则自由文本，不属于正式客户标签。
3. 写入 `resolved_by='SYSTEM_MIGRATION_9F'` 和 `resolved_at=NOW()`。
4. 将 6 条 `system_tag_suggestions` 更新为 `status='IGNORED'`、`ignored_at=COALESCE(ignored_at, NOW())`；保留 `tag_name`、`rule_id`、`customer_id`、`validation_status` 和 `unmatched_legacy_value_id`。
5. 将规则 4 的 `action_type` 更新为 `ALERT`，规则 5 的 `action_type` 更新为 `NOTIFY_LEADER`；保留条件、名称、优先级、启用状态和原 `action_config` 文本。

所有 UPDATE 都使用当前状态和内置规则条件作为 WHERE，Flyway 只执行一次；重复启动不会再次覆盖已处理时间或说明。

### 2. 运行时边界

- 不新增正式分类或标签。
- 不向 `customer_tag_assignments` 写入任何记录。
- 不修改客户四个旧标签字段。
- 不改变自定义旧文本规则的兼容路径；只有内置规则 4、5 通过迁移停止产生自由文本标签建议。
- 保留原 `tag_legacy_value_mappings` 的 `UNMATCHED` 历史映射，便于审计原文没有被正式映射。

### 3. 统计与审计

- 正式标签统计结果保持不变，6 条建议不参与正式标签数量。
- “未匹配旧标签”当前缺口统计不再把这 6 条已归档记录计入，因为关联未匹配记录不再是 `PENDING`。
- 原始文本、哈希、客户、规则和关联 ID 全部保留；归档原因通过 `resolution_note` 和 `resolved_by` 留痕。

## 数据流

```text
V70 启动迁移
    |
关联 SYSTEM_TAG_SUGGESTION 未匹配记录 -> IGNORED + 处理说明
    |
系统标签建议记录 -> IGNORED，保留原文和关联
    |
内置自由文本 TAG_CHANGE 规则 -> ALERT / NOTIFY_LEADER
    |
后续扫描只产生普通跟进提醒，不再生成这两类无效标签建议
```

## 测试范围

### MariaDB 迁移集成测试

- 从 V69 基线插入 6 条目标建议、关联未匹配记录和规则 4/5。
- 应用 V70 后验证建议为 `IGNORED`，原文和关联 ID 不变。
- 验证未匹配记录为 `IGNORED`，`resolved_by`、`resolved_at`、`resolution_note` 已写入。
- 验证规则 4/5 动作分别为 `ALERT`、`NOTIFY_LEADER`，条件和动作 JSON 原文仍存在。
- 再次执行迁移验证结果、时间和说明不发生重复覆盖。
- 验证 `customer_tag_assignments` 数量和客户旧标签字段不变。

### Repository/Service 回归

- 统计查询只计算当前有效正式标签，6 条归档建议不进入正式统计。
- “未匹配旧标签”原因只统计仍为 `PENDING` 的未匹配记录。
- 自定义 `UNVALIDATED_RULE_TEXT` 规则仍保持原兼容路径。

## 非目标与保护项

- 不删除任何记录，不清空原文，不删除规则。
- 不新增前端页面或人工映射流程；本轮只完成已审计的 6 条历史记录归档。
- 不开启任何 LLM 配置，不修改 Step 8 回复标签上下文。
- 不在应用启动时执行迁移之外的手工 SQL 或补数据脚本。

## 验收标准

1. 6 条目标建议从 PENDING 归档为 IGNORED，所有原始字段可查询。
2. 16-21 号未匹配记录归档并留下系统处理说明。
3. 内置规则 4/5 不再使用 TAG_CHANGE 生成自由文本标签建议。
4. 正式标签分配、客户旧字段、LLM 开关和 Step 8 行为不变。
5. V70 重复执行幂等，相关迁移和回归测试通过。
