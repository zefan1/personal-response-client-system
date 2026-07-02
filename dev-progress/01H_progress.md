# 01H API 层 + WebSocket 进度卡

## 模块边界
- 模块：后端 H API 层 + WebSocket
- 强依赖：A/B/C/D/E/F/G 已有代码 checkpoint；H 通过 Java 方法和 Spring Event 编排，不在 H 内重写业务逻辑。
- API 前缀：桌面端 `/api/v1/`，运营后台 `/admin/api/v1/`，WebSocket `/ws/v1/desktop`。
- 错误码：`80-10001` 参数错误，`80-10002` 认证失败，`80-10003` 权限不足，`80-10004` 系统错误，`80-10005` WS 连接失败。

## 功能签收清单
- [x] SF-H01 桌面端登录 `/api/v1/auth/login`，签发 JWT + refreshToken。
- [x] SF-H02 运营后台登录 `/admin/api/v1/auth/login`，拒绝 KEEPER。
- [x] SF-H03 JWT Filter 保护 `/api/v1/**` 与 `/admin/api/v1/**`，公开登录接口除外。
- [x] SF-H04 `/api/v1/auth/refresh` 刷新 Token。
- [x] SF-H05 `/api/v1/chat/recognize` 编排 C -> D -> B，识图/匹配失败可降级。
- [x] SF-H06 `/api/v1/chat/generate` 跳过 C/D，调用 A -> B。
- [x] SF-H07 `/api/v1/chat/regenerate` 读取上次请求上下文，连续 3 次提示求助。
- [x] SF-H08 `/api/v1/chat/send-confirm` 发布 `CustomerMessageSentEvent`，供 E/G 异步消费。
- [x] SF-H09 `/api/v1/help/request` 推送 `HELP_REQUEST`，组长离线时入 WS 离线队列。
- [x] SF-H10 `/api/v1/help/resolve` 推送 `HELP_RESPONSE` 给发起人。
- [x] SF-H11 WebSocket `/ws/v1/desktop` 使用 JWT 握手，维护 `sessionMap`，新连接踢旧连接。
- [x] SF-H12 WS 支持 `PING` / `PONG`、超时清理和关闭回调清理。
- [x] SF-H13 WS 离线队列双写 MySQL `ws_offline_queue` 与内存队列，重连按 `lastMessageId` 补推。
- [x] SF-H14 E/F/C 事件桥接到 WS：`PROFILE_SUGGESTIONS`、`FOLLOWUP_REMIND`、`NEW_LEAD_ALERT`、`IMAGE_SERVICE_STATUS`。
- [x] SF-H15 配置管理 `/admin/api/v1/configs` 与 `/admin/api/v1/configs/{key}`，更新后发布 `ConfigChangedEvent` 并广播 `CONFIG_REFRESH`。
- [x] SF-H16 健康端点 `/admin/api/v1/health` 聚合 MySQL、Redis、active alerts。
- [x] SF-H17 系统告警表 `system_alerts` 支持识图 DOWN/UP 写入与恢复。
- [x] SF-H18 全局异常处理包装为统一 `ApiResponse`，不暴露堆栈。
- [x] SF-H19 独立线程池：`wsBroadcastExecutor`、`auditLogExecutor`、`apiOrchestrationExecutor`。
- [x] SF-H20 15 项 `system.*` 配置写入 `system_configs` 且 `application.yml` 有默认值。

## 表 / 配置 / API / WS / 事件
- [x] 表：`accounts`
- [x] 表：`ws_offline_queue`
- [x] 表：`system_alerts`
- [x] 配置：`system.jwt_secret`
- [x] 配置：`system.jwt_expire_hours`
- [x] 配置：`system.jwt_refresh_days`
- [x] 配置：`system.ws_heartbeat_s`
- [x] 配置：`system.ws_timeout_s`
- [x] 配置：`system.ws_replay_queue_size`
- [x] 配置：`system.request_total_timeout_ms`
- [x] 配置：`system.audit_log_retention_days`
- [x] 配置：`system.login_fail_limit`
- [x] 配置：`system.login_lock_minutes`
- [x] 配置：`system.request_context_ttl_s`
- [x] 配置：`system.ws_offline_retention_days`
- [x] 配置：`system.alert_retention_days`
- [x] 配置：`system.config_change_channel`
- [x] 配置：`system.ws_push_channel`
- [x] API：`POST /api/v1/auth/login`
- [x] API：`POST /api/v1/auth/refresh`
- [x] API：`POST /admin/api/v1/auth/login`
- [x] API：`POST /api/v1/chat/recognize`
- [x] API：`POST /api/v1/chat/generate`
- [x] API：`POST /api/v1/chat/regenerate`
- [x] API：`POST /api/v1/chat/send-confirm`
- [x] API：`POST /api/v1/help/request`
- [x] API：`POST /api/v1/help/resolve`
- [x] API：`GET /admin/api/v1/health`
- [x] API：`GET /admin/api/v1/configs`
- [x] API：`PUT /admin/api/v1/configs/{key}`
- [x] WS：`/ws/v1/desktop`
- [x] WS 类型：`FOLLOWUP_REMIND`、`NEW_LEAD_ALERT`、`HELP_REQUEST`、`HELP_RESPONSE`、`PROFILE_SUGGESTIONS`、`IMAGE_SERVICE_STATUS`、`CONFIG_REFRESH`
- [x] 发布事件：`CustomerMessageSentEvent`
- [x] 发布事件：`ConfigChangedEvent`
- [x] 消费事件：`FollowupWsMessageReadyEvent`
- [x] 消费事件：`ProfileSuggestionsReadyEvent`
- [x] 消费事件：`ImageServiceStatusEvent`

## 验证命令
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_h.py
```

## 当前结果
- 静态契约验证脚本已补充：`scripts/verify_module_h.py`
- 当前环境仍缺少 `mvn` / `mvnw` / `javac`，真实 Java 编译需安装工具链后复跑。
