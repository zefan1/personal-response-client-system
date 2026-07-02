# 31 桌面K 异常敏感提醒进度卡

## 基线
- 模块：桌面K 异常/敏感提醒
- 依赖：后端B/F/H 已由前序模块提供 WS `ABNORMAL_ALERT` 契约；桌面B、D 已存在并可消费事件。
- 共享契约：`ABNORMAL_ALERT`，`abnormal:alert`，`CUSTOMER_COMPLAINT`，`CHURN_RISK`，`ERROR/WARN/INFO`，`alert_history`。

## 功能签收清单
- [x] 监听 raw WS 事件 `ABNORMAL_ALERT`，K 作为唯一桌面路由入口。
- [x] 校验 11 位手机号、告警类型、级别、非空 message、occurredAt。
- [x] 生成 `k_alert_{timestamp}_{random4}` 前端告警 ID。
- [x] 写入响应式内存 `Map<phone, Alert[]>`。
- [x] 使用 IndexedDB `siliang_desktop` v4 的 `alert_history` store 持久化历史，不使用 localStorage。
- [x] 派发 `abnormal:alert` 给桌面B和桌面D。
- [x] 全局提醒铃铛展示未确认数量、最近列表、历史入口。
- [x] “已知晓”将提醒标记为 acknowledged，并再次派发 `abnormal:alert` 让 B/D 清除横幅。
- [x] 桌面B改为消费 `abnormal:alert`，并按当前客户匹配提醒。
- [x] 桌面D新增档案卡警告条，按当前客户匹配提醒。
- [x] 默认配置：`alertHistoryMaxCount=50`、`alertHistoryRetentionDays=7`、`alertBellRefreshIntervalS=86400`。

## 验证命令
- `python scripts/verify_module_31.py`
- `cd desktop; npm run typecheck`
- `cd desktop; npm run build`
- `git diff --check`

## 待确认
- IndexedDB 数据库名按模块31手册使用 `siliang_desktop`；共享契约后续模块M提到统一库名可能调整，当前不提前改跨模块契约。
