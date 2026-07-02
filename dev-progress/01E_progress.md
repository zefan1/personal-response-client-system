# 01E 后端E 客户档案更新服务进度卡

## 权威输入

- 手册：`C:\Users\85314\Desktop\私域工具\01E_后端_客户档案更新服务_开发实现手册.md`
- 共享契约：`C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- 依赖拓扑：`C:\Users\85314\Desktop\私域工具\DEPENDENCIES.md`

## 依赖检查

- 强依赖 A：已实现，提供 `CustomerQueryService.getByPhone`
- 强依赖 B：已实现，提供 `SkillGatewayService.extractProfile`
- 间接依赖 H：未实现；本模块已先定义并消费 `CustomerMessageSentEvent`，发布 `ProfileSuggestionsReadyEvent`，待 H 后续接 REST 编排/WS 推送

## 功能签收清单

- [x] SF-E01 新增 `CustomerMessageSentEvent` 契约并用 `@Async("profileUpdateExecutor")` 异步消费
- [x] SF-E02 事件去重：phone + conversation MD5，5 秒窗口，30 秒过期，最大 200 条
- [x] SF-E03 调 A 查询客户；客户不存在时跳过，不越权建行
- [x] SF-E04 调 B `extractProfile(ProfileExtractRequest)` 获取字段建议，失败降级为空更新
- [x] SF-E05 `profile.extract_fields` 18 字段白名单过滤，超出字段忽略
- [x] SF-E06 置信度路由：HIGH 自动写入，MEDIUM 入队，LOW 丢弃
- [x] SF-E07 固定字段每次更新 `lastFollowupAt` 与 `followupNotes`
- [x] SF-E08 `ProfileWriter` 乐观锁更新 customers，成功发布 `ProfileUpdatedEvent { phone, updatedFields }`
- [x] SF-E09 `profile_update_suggestions` 表：PENDING/CONFIRMED/REJECTED/CONFLICT_SKIPPED 生命周期
- [x] SF-E10 `GET /api/v1/customers/{phone}` 增强返回 `pendingSuggestions`
- [x] SF-E11 `PUT /api/v1/customers/{phone}` 手动编辑保存，version 冲突返回 50-10002
- [x] SF-E12 `POST /api/v1/customers/{phone}/suggestions/batch-resolve` 批量确认/拒绝
- [x] SF-E13 定时过期清理：`profile.suggestion_cleanup_cron`
- [x] SF-E14 审计日志异步写入 `audit_logs`，action=`UPDATE_PROFILE`
- [x] SF-E15 `profile.*` 8 个配置项写入 Flyway 并支持热更新

## 新增/修改文件

- 新增：`src/main/java/com/privateflow/modules/profile/**`
- 新增：`src/main/java/com/privateflow/common/events/CustomerMessageSentEvent.java`
- 新增：`src/main/java/com/privateflow/common/events/ProfileSuggestionsReadyEvent.java`
- 新增：`src/main/resources/db/migration/V5__module_e_profile_update.sql`
- 修改：`ProfileUpdatedEvent` 增加 `updatedFields`，保留单参构造兼容 A
- 修改：D 的 `CustomerController` 接入 E 的 PUT、batch-resolve 与 pendingSuggestions

## 实现假设

- B 当前 `ProfileUpdates` 契约只有 fields，没有 conversationSummary；E 先使用事件 conversationSummary 或 rawMessages/sentText 回退摘要。
- H 未实现时，E 先发布 `ProfileSuggestionsReadyEvent`，后续 H 监听后负责 WS `PROFILE_SUGGESTIONS` 推送。
- 手动更新请求体用 `{ version, fields, operator }`，H 后续可把前端“扁平字段 body”转换为该结构后调用 E。

## 验证命令

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_e.py
```

## 验证结果

- 已通过：

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py
```

- 输出摘要：A/B/C/D/E 静态验证全部 passed。

## Maven/JDK 状态

- 阻塞：

```powershell
mvn test
```

- 当前输出：`mvn : The term 'mvn' is not recognized as the name of a cmdlet...`
- 结论：不能声明 Java 编译测试已通过。
