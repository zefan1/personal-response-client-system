# 01F 后端F 跟进规则引擎进度卡

## 权威输入

- 手册：`C:\Users\85314\Desktop\私域工具\01F_后端_跟进规则引擎_开发实现手册.md`
- 共享契约：`C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- 依赖拓扑：`C:\Users\85314\Desktop\私域工具\DEPENDENCIES.md`

## 依赖检查

- 强依赖 A：已实现，提供 `CustomerQueryService.getByPhone` 和 `scanActiveCustomers`
- 强依赖 H：未实现；本模块先发布 `FollowupWsMessageReadyEvent` 表达 `FOLLOWUP_REMIND` / `NEW_LEAD_ALERT`，待 H 后续监听并推 WS

## 功能签收清单

- [x] SF-F01 三张表：`followup_rules` / `reminder_sent_log` / `system_tag_suggestions`
- [x] SF-F02 内置 8 条规则初始化，含 ALERT / TAG_CHANGE / NOTIFY_LEADER
- [x] SF-F03 `followup.*` 15 个配置项写入 Flyway 并支持热更新
- [x] SF-F04 reminderType 枚举：OVERDUE / DUE_TODAY / APPOINTMENT / NEW_LEAD / TAG_SUGGESTION
- [x] SF-F05 规则加载器按 enabled + priority 加载，定时刷新并响应 ConfigChangedEvent
- [x] SF-F06 条件解析器支持 AND/OR、字段白名单和操作符白名单
- [x] SF-F07 全量扫描器按 cron 扫描活跃客户并执行规则
- [x] SF-F08 轻量扫描器按 cron 扫描时效性规则
- [x] SF-F09 `NewLeadEvent` 监听并生成 `NEW_LEAD_ALERT`
- [x] SF-F10 动作执行器生成 `FOLLOWUP_REMIND`，写入同日去重日志
- [x] SF-F11 TAG_CHANGE 写入 `system_tag_suggestions`，忽略后去重
- [x] SF-F12 今日清单 `GET /api/v1/followups/today`
- [x] SF-F13 运营规则接口：GET/POST/PUT/DELETE/toggle `/admin/api/v1/rules`
- [x] SF-F14 内置规则禁止删除，返回权限错误
- [x] SF-F15 不直接修改 customers，不越界改客户阶段

## 新增/修改文件

- 新增：`src/main/java/com/privateflow/modules/followup/**`
- 新增：`src/main/java/com/privateflow/common/events/FollowupWsMessageReadyEvent.java`
- 新增：`src/main/resources/db/migration/V6__module_f_followup_rules.sql`
- 新增：`scripts/verify_module_f.py`
- 修改：`application.yml` 增加 `followup.*` 默认配置

## 实现假设

- H 未实现时，F 只发布内部 `FollowupWsMessageReadyEvent`，后续 H 监听后负责真实 WebSocket 推送和离线补推。
- A 当前提供的是 `scanActiveCustomers(ScanFilter)`，没有 offset/cursor 级分页接口；F 先按 A 的 batch limit 扫描，保留 `followup.cursor_ttl_s` 配置，后续 H/Redis 基础完善后再做断点续扫。
- 今日清单先从 A 活跃客户扫描 + 今日 NEW_LEAD 日志组装，身份认证未实现前用可选 `keeperId` 查询参数替代 JWT 身份。

## 验证命令

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_f.py
```

## 验证结果

- 已通过：

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py
```

- 输出摘要：A/B/C/D/E/F 静态验证全部 passed。

## Maven/JDK 状态

- 阻塞：

```powershell
mvn test
```

- 当前输出：`mvn : The term 'mvn' is not recognized as the name of a cmdlet...`
- 结论：不能声明 Java 编译测试已通过。
