# 01G 表格写入服务进度卡

## 模块边界
- 模块：后端 G 表格写入服务
- 强依赖：模块 A `CustomerQueryService` / `CustomerRepository` 已存在；企微智能表格 API 通过 `WecomTableClient` 接口接入，开发环境可用 `MOCK_EXTERNALS=true` 的 mock client。
- 触发源：消费 `CustomerMessageSentEvent`。
- 对外 API：`POST /api/v1/customers/{phone}/save-to-table`。
- 发布事件：新客户建行成功后发布 `ProfileUpdatedEvent { phone, updatedFields[] }`。

## 功能签收清单
- [x] SF-G01 读取 `CustomerMessageSentEvent`，异步进入表格写入编排。
- [x] SF-G02 新客户自动创建企微表格行。
- [x] SF-G03 新客户建行成功后写入 MySQL `customers` 并发布 `ProfileUpdatedEvent`。
- [x] SF-G04 存量客户只更新企微表格跟进字段，不改 E 负责的 MySQL 档案字段。
- [x] SF-G05 自动写入失败立即重试 1 次。
- [x] SF-G06 自动写入仍失败时写入 `pending_table_writes`。
- [x] SF-G07 队列表按 `table.retry_interval_s` 定时重试。
- [x] SF-G08 达到 `table.retry_max_count` 后标记 FAILED。
- [x] SF-G09 FAILED 超过 `table.alert_failure_hours` 后记录告警日志，通知目标使用 `table.alert_notify_target`。
- [x] SF-G10 手动保存接口同步写企微表格，失败不入队。
- [x] SF-G11 手动保存返回 `{ written, updatedFields }`。
- [x] SF-G12 队列积压超过 warn/alert 阈值分别记录 WARN / 拒绝入队。
- [x] SF-G13 复用 `datasource_field_mappings` 做内部字段到表格字段的反向映射。
- [x] SF-G14 `table.*` 7 项默认配置进入 `system_configs` 且 `application.yml` 有默认值。
- [x] SF-G15 使用独立 `tableWriteExecutor`，core=2 max=4 queue=200，CallerRunsPolicy，关闭等待 30s。

## 表 / 配置 / API / 事件
- [x] 表：`pending_table_writes`
- [x] 索引：`idx_status_retry(status, next_retry_at)`、`idx_phone(phone)`
- [x] 配置：`table.write_timeout_ms=10000`
- [x] 配置：`table.retry_max_count=5`
- [x] 配置：`table.retry_interval_s=60`
- [x] 配置：`table.alert_failure_hours=1`
- [x] 配置：`table.alert_notify_target=ADMIN`
- [x] 配置：`table.queue_warn_threshold=100`
- [x] 配置：`table.queue_alert_threshold=1000`
- [x] API：`POST /api/v1/customers/{phone}/save-to-table`
- [x] 消费事件：`CustomerMessageSentEvent`
- [x] 发布事件：`ProfileUpdatedEvent`

## 验证命令
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_g.py
```

## 当前结果
- 静态契约验证脚本已补充：`scripts/verify_module_g.py`
- 队列重试成功后会恢复完整业务副作用：INSERT 补写 `customers` 并发布 `ProfileUpdatedEvent`；UPDATE 缺少 `sourceRowId` 时重新查询 A 的客户来源行。
- Maven 编译/测试仍依赖本机安装 `mvn`；当前环境此前显示 `mvn` 不可识别。
