# Step 10 断点：全量测试和真实运行验收

日期：2026-07-17  
分支：`feature/tag-step8-reply-tag-context`  
工作树：`C:\Users\85314\.config\superpowers\worktrees\private-domain-assistant\tag-step4-unified-access`  
状态：本轮自动化和本地真实验收完成；尚未 merge、push 或创建 PR。

## 验证结果

- 后端全量：`mvn test`，455 tests，0 failures，0 errors，2 条条件式 MariaDB 测试跳过。
- 桌面端全量：`npm run test`，37 个文件、264 tests 全部通过。
- 桌面端类型检查：`npm run typecheck` 通过。
- 桌面端构建：`npm run build` 通过。
- 构建运行时：`npm run renderer:smoke` 输出 `renderer_smoke=passed`；`npm run electron:smoke` 输出 `electron_smoke=passed`。
- 数据库对齐：42 张表、24 张必需表、41 张迁移表；0 缺失表、0 缺失配置、0 Repository 列违规、0 列属性违规、0 枚举违规。
- API mapping：143 个 endpoint，126 个直接或模式匹配覆盖，17 个明确记录为 controller/module 测试或 live-provider 专属的 intentional gaps，未分类缺口 0。
- Controller 覆盖：21/21；桌面组件覆盖：15/15；Admin 产品面检查通过；真实外部 source readiness 检查通过。
- 真实后台批次 B：39/39，通过且 cleanup 通过。
- 真实侧边栏批次 A：15/15，通过。
- 完整 backend API acceptance：168/168，通过；覆盖 conflict/create/download/invalid/permission/read/representative/update 场景。

## 真实数据库核对

数据库：`private_domain_assistant_smoke`，Flyway V70。

- `system_tag_suggestions` ID 1-6：`IGNORED` 共 6 条。
- `unmatched_legacy_tag_values` ID 16-21：`IGNORED` 共 6 条，`resolved_by=SYSTEM_MIGRATION_9F` 共 6 条。
- 内置规则：4=`ALERT`，5=`NOTIFY_LEADER`。
- `llm.profile_extraction.enabled=false`。
- `llm.reply_generation.enabled=false`。
- V70 未写入正式标签分配；Step10 运行服务产生的临时 `LEGACY_FIELD_SYNC/MANUAL` 分配与迁移结果分开记录。

## 本轮脚本修正

- `verify_api_mapping_coverage.py` 现在扫描后台、侧边栏和 LLM failover 验收脚本，且能解析 `request_json`/`delete_ok` 动态路径；未覆盖 live-only 或破坏性操作的 endpoint 均有明确 intentional gap 原因。
- `verify_enum_contract_alignment.py` 与 `verify_database_alignment.py` 将 Skill `GENERAL` lead type 纳入契约，和 `SkillAdminService` 现有校验一致。
- `acceptance_backend_api.py` 将未知客户的空建议批量确认验证为预期 400，并将 Skill 非法 lead type 夹具改为 `UNKNOWN`。

## 当前服务

- 8082 后端：`http://127.0.0.1:8082`
- 8080 后端：`http://localhost:8080`（后台/侧边栏/API acceptance 使用）
- 桌面端：`http://127.0.0.1:5175/`，另有历史 5173/5174 监听。

## 尚未完成的外部条件

- 真实 Skill、图像识别、企微表格和真实 LLM provider live acceptance 仍需用户提供有效 endpoint/key 和样本。
- 生产签名桌面安装包仍未配置证书；`verify_production_blockers.py` 当前报告 `LIVE_EXTERNAL_PROVIDER_ACCEPTANCE` P0 和 `SIGNED_RELEASE_PACKAGE` P1。
- 尚未执行服务重启后的完整人工点击回归，也未将本分支 merge/push/创建 PR。

## 下一步

等待真实 provider/签名发布条件，或继续补默认标签、合并/导出和重启后人工点击覆盖；在用户明确授权前保持当前分支不变。
