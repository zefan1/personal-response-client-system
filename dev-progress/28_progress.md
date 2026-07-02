# 28 桌面H 保存到表格进度卡

## 当前状态
- 状态：已实现并提交前验证
- 模块：桌面H 保存到表格
- 入口：桌面D 调用 `saveProfile(phone, editedFields, version, hasTableRow)`

## 功能签收清单
- [x] 新增独立桌面H保存服务，导出 `saveProfile`、`syncProfileToTable`、`recoverPendingSave`。
- [x] 桌面D 保存改为调用桌面H，不直接写 PUT 细节。
- [x] PUT `/api/v1/customers/{phone}` 只提交变更字段 + `version`。
- [x] 同一 phone 保存中返回 `BUSY`，不排队、不合并。
- [x] 409 返回 `CONFLICT`，桌面D 刷新档案并通过 `editingSnapshot` 保留当前编辑框内容。
- [x] 非 409 网络/5xx/超时按 `desktop.save_max_retries=3`、`desktop.save_retry_interval_ms=5000` 静默重试。
- [x] 第 2 次失败后写入 `pending_saves` 本地暂存，最终失败保留黄色提示条。
- [x] 打开档案卡时检查并自动恢复 pending save。
- [x] 启动时清理超过 `desktop.save_pending_expire_hours=24` 小时的暂存记录。
- [x] MySQL 保存成功且存在 `sourceRowId` 时展示 15s 表格同步 toast，提供「同步 / 暂不」。
- [x] 表格同步调用 `POST /api/v1/customers/{phone}/save-to-table`，失败不影响 MySQL 成功结果。
- [x] 新增配置默认值：`desktop.save_to_table_timeout_ms=15000`、`desktop.save_retry_interval_ms=5000`、`desktop.save_max_retries=3`、`desktop.save_pending_expire_hours=24`。

## 验证命令
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_28.py
cd desktop; npm run typecheck
cd desktop; npm run build
```

## 实现备注
- 模块M尚未实现，`pending_saves` 暂以 `localStorage` 键空间持久化，命名保持 `pending_saves:{phone}`，后续模块M统一迁移 IndexedDB。
- 后端 `save-to-table` 当前契约仍要求 `sourceTable/sourceRowId/fields`，桌面H按现有后端实现传参。
