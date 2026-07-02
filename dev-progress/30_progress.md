# 30 桌面J 客户阶段建议更新进度卡

## 当前状态
- 状态：已实现并提交前验证
- 模块：桌面J 客户阶段建议更新
- 入口：WS `PROFILE_SUGGESTIONS`、档案卡加载、`customer:selected`

## 功能签收清单
- [x] 新增纯 TS `StageSuggestionHandler`，不创建独立窗口和独立 UI。
- [x] 监听 `PROFILE_SUGGESTIONS`，过滤 `fieldName === 'customerStage'` 的阶段建议。
- [x] 档案卡 GET 完成后调用 `handleCustomerProfileLoaded(response.data)`，识别已有阶段建议。
- [x] 当前客户匹配时发射 `stage:suggest`。
- [x] 当前客户不匹配时写入内存 `pendingStageSuggestions`，按 `desktop.stage_suggest_pending_ttl_s=300` 过期。
- [x] 使用 `emittedSuggestionIds` 按 suggestionId 去重。
- [x] `stage:suggest` payload 包含 `fromStage/toStage/reason/stageOptionMatch/validOptions/suggestionType=STAGE_CHANGE`。
- [x] 桌面D AI 更新建议区展示橙色竖线和「阶段建议」标签。
- [x] `stageOptionMatch=false` 时展示「此阶段值不在表格当前可选范围内」提醒和 `validOptions`。
- [x] 阶段建议确认走 `confirmStageSuggestion`，仅 PUT `{ customerStage: newStage }`。
- [x] 确认成功后发射 `stage:updated { phone, newStage }`。
- [x] 阶段建议忽略走 `ignoreStageSuggestion`，调用 `suggestions/batch-resolve`，不永久去重。
- [x] 409 冲突时自动 GET 刷新；若阶段已变更则视为成功。
- [x] 失败重试复用 `desktop.save_retry_interval_ms` 和 `desktop.save_max_retries`。

## 验证命令
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_30.py
cd desktop; npm run typecheck
cd desktop; npm run build
```

## 实现备注
- 后端阶段选项比对字段目前以前端消费为主，保持旧版后端兼容：无 `stageOptionMatch` 时按 `true` 处理。
- J 不使用 localStorage / IndexedDB，pending 阶段建议只保存在内存 Map。
