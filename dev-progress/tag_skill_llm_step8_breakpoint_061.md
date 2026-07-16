# Step 8 断点：回复生成读取最新标签

日期：2026-07-16  
分支：`feature/tag-step8-reply-tag-context`

## 已完成

- 新增不可变 `ReplyTagSnapshot`，并将 `currentTags` 接入 `SkillRequest`，保留旧构造器兼容。
- 新增 `ReplyTagSnapshotBuilder`：先执行有权限校验的当前标签查询，再关联目录中文分类名、标签名、含义、来源、依据和人工锁定状态；过滤停用/合并/不用于回复的分类和值；目录元数据缺失时抛错。
- `ChatOrchestrationService` 在识别、首次生成和 regenerate 前读取最新客户标签；regenerate 重新查询客户和标签，不复用旧快照；标签读取运行时异常降级为空快照并写入 `CUSTOMER_TAGS_READ_DEGRADED`，`ApiException` 原样抛出。
- Skill payload 增加 `current_tags`；直连 LLM payload 增加 `currentTags`；两条路径都追加固定的不披露内部标签规则。
- 上下文保存使用本次生成的完整 `SkillRequest`，不会丢失标签快照。

## 提交

- `845022a feat: add reply tag snapshot contract`
- `83dff53 feat: build reply tag snapshots`
- `ab90789 feat: load latest tags for reply generation`
- `21c818d test: preserve tag access errors`
- `cf74e9d feat: send reply tags to skill and llm`

## 验证

- Java：`mvn -q test`，390 tests，0 failures，0 errors，1 conditional skip。
- 前端：36 Vitest 文件、255 tests；`npm run typecheck`、`npm run build`、`renderer_smoke=passed`、`electron_smoke=passed`。
- 真实验收：临时 8082 使用真实 smoke 数据库和 `MOCK_EXTERNALS=false`，受控 fake Skill provider 仅提供 Skill HTTP 返回。客户 `13900000001` 的标签序列为 `LOYALIST -> DECISIVE -> 空`；首次生成和 regenerate 均返回 `SKILL`，最后分类锁已解除。

## 运行状态与恢复

- 主后端 8080 保持运行；前端 5173/5174 保持运行。
- 临时后端 8082 和 fake provider 已停止。
- `private_domain_assistant_smoke` 的 `llm.reply_generation.enabled=false`、`llm.profile_extraction.enabled=false` 保持关闭；本次验收临时写入的 `skill.api_base_url`/`skill.api_key` 已恢复为空。
- 未执行 Step 9；未修改数据库迁移、搜索、统计、跟进规则、导入、同步或导出。

## 继续点

下一次接手先检查本文件、`git status` 和分支远端是否一致；若继续推进，应先明确开启新的 Step 9 设计，不要把搜索/统计等行为混入 Step 8。
