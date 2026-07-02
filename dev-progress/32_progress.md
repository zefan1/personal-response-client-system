# 32 桌面L 工作台视图进度卡

## 基线
- 模块：桌面L 工作台视图
- 依赖：后端F `/api/v1/followups/today`、后端H WS `FOLLOWUP_REMIND` / `SYSTEM_NOTICE`、桌面D/E/E+/F/G/A/J。
- 共享契约：`OVERDUE`、`DUE_TODAY`、`APPOINTMENT`、`NEW_LEAD`、`TUAN_GOU`、`XIAN_SUO`、`PENDING`、`sourceFrom=DASHBOARD`。

## 功能签收清单
- [x] App 启动后挂载 `WorkbenchPanel` 作为默认工作台区域。
- [x] 调用既有 `GET /api/v1/followups/today`，不新增后端 API。
- [x] 计算三张概览卡：待跟进、今日预约、新客资，并按团购/线索拆分。
- [x] 跟进精简列表取 `OVERDUE + DUE_TODAY`，前端按逾期优先、团购优先、时间/昵称排序。
- [x] 新客资精简区优先读取 E+ 的 `newLeadToastState` 共享状态，空时从 `followups/today` 的 `NEW_LEAD` 降级计算。
- [x] 消费 `FOLLOWUP_REMIND` 增量更新概览和精简列表。
- [x] 消费 `NEW_LEAD_ALERT` 增量更新新客资数据。
- [x] 消费 `SYSTEM_NOTICE` 展示公告，支持 `noticeId` 去重、`expireAt` 过期过滤、关闭当前会话公告。
- [x] 消费 `stage:updated` 标记 `followupDataDirty`，下次条件刷新。
- [x] 条件刷新使用 `desktop.workbench_refresh_interval_s=300`。
- [x] 列表上限配置：`workbenchFollowupListLimit=5`、`workbenchNewLeadListLimit=3`、`workbenchMaxNotices=3`。
- [x] 点击客户通过 `customer:selected` 且 `sourceFrom=DASHBOARD` 打开档案。
- [x] “查看全部跟进/新客资”切换到 E 对应 tab。
- [x] 快捷入口：识别聊天、快线模板、批量发模板，复用 A/F/E/G 现有入口。

## 验证命令
- `python scripts/verify_module_32.py`
- `cd desktop; npm run typecheck`
- `cd desktop; npm run build`
- `git diff --check`

## 待确认
- 当前项目无 Vue Router/Tab 容器，L 以页面顶部工作台区挂载，并通过 eventBus 导航到已有模块。
