# 33 桌面M 离线可用进度卡

## 基线
- 模块：桌面M 离线可用
- 依赖：Electron Main Process IPC、WebSocket 连接层、桌面 D/E/F/H/K/L 的本地缓存约定
- 共享契约：`network:offline`、`network:online`、`ws:disconnected`、`ws:reconnected`、`ws:status-change`、`cowork-desktop`

## 功能签收清单
- [x] 提供全局响应式状态：`isOnline`、`isWsConnected`、`lastOnlineAt`、`offlineReason`。
- [x] 发布 `network:offline`，payload 为 `{ reason, lastOnlineAt }`，reason 使用 `OS_OFFLINE` / `API_CONSECUTIVE_FAIL` / `WS_AND_API_FAILED` 枚举。
- [x] 发布 `network:online`，payload 为 `{ offlineDurationMs }`。
- [x] 发布 `ws:disconnected` 和 `ws:reconnected`，并区分 WS 断线与全局离线。
- [x] 消费 Electron IPC：`app:get-online-status` 和 `app:online-status`。
- [x] 消费 WS 连接层事件：`ws:status-change`。
- [x] 在 API 客户端中统计连续网络层失败，达到 `offlineApiFailCount=3` 后进入离线。
- [x] 在 WS 连接打开时发送 `RECONNECT`，携带 `lastMessageId`。
- [x] 创建统一 IndexedDB：`cowork-desktop`，包含 `customers_cache`、`quick_search_cache`、`followups_cache`、`pending_saves`、`alert_history`、`edit_logs`、`workbench_cache`。
- [x] 支持缓存版本键 `cowork_db_version`，版本变化时删除并重建本地缓存库。
- [x] 提供 `registerOfflineCapability()` 离线能力声明入口。
- [x] App mount 前初始化 M，确保其他模块创建时可读到初始在线状态。
- [x] 提供全局顶部 UI：离线黄条、WS 灰条、恢复在线绿条，恢复提示按 `onlineToastDurationMs=2000` 自动消失。
- [x] 不新增后端 REST API，不新增后端 WS 消息类型。

## 验证命令
- `python scripts/verify_module_33.py`
- `python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py; python scripts/verify_module_g.py; python scripts/verify_module_h.py; python scripts/verify_module_20.py; python scripts/verify_module_21.py; python scripts/verify_module_22.py; python scripts/verify_module_23.py; python scripts/verify_module_24.py; python scripts/verify_module_25.py; python scripts/verify_module_26.py; python scripts/verify_module_27.py; python scripts/verify_module_28.py; python scripts/verify_module_29.py; python scripts/verify_module_30.py; python scripts/verify_module_31.py; python scripts/verify_module_32.py; python scripts/verify_module_33.py`
- `cd desktop; npm run typecheck`
- `cd desktop; npm run build`
- `git diff --check`

## 假设与传播记录
- 当前 Electron 类型未暴露 `app.on('online'/'offline')`，主进程采用 `net.isOnline()` 每 1 秒轮询变化后通过同一 IPC 契约推送。
- 当前桌面端 package version 未注入 renderer，缓存版本按 `desktop/package.json` 当前版本 `0.1.0` 固化到 M 的版本常量；后续构建链可注入时再替换为环境变量。
- 现有 K 模块仍使用历史独立库 `siliang_desktop` 保存提醒历史。本次先按 M 手册建立统一 `cowork-desktop` schema，未迁移 K 数据读写，避免扩大已验证模块的行为面。
