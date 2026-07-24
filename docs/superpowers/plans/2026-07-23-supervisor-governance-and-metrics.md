# 主管监督、指标与治理配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立不依赖任何聊天平台会话存档的监督事件、指标、保留清理和管理员配置，使主管可按员工、渠道、线索来源和动态转化目标核对 AI 使用与业务结果。

**Architecture:** 新增 `supervision` 后端模块，以不可变事件表记录识图、生成、复制、重生成、求助和模板使用；事件中仅保留业务上下文及话术文本快照，绝不保存原始截图。指标服务以事件表和 `customers` 的真实归属、来源、阶段字段计算分子与分母；所有留存、并发和上限从 `system_configs` 读取，管理员界面仅调用受管理员权限保护的 API。

**Tech Stack:** Spring Boot 3、JdbcTemplate、MariaDB/Flyway、Spring Scheduler、Vue 3、Vitest、JUnit 5、MockMvc。

---

## File Structure

| 文件 | 责任 |
| --- | --- |
| `src/main/resources/db/migration/V76__supervision_events_and_governance_config.sql` | 创建监督事件、月度汇总表和所有本阶段配置默认值。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionEventType.java` | 监督事件的稳定枚举，包含待处理客户进入事件。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionEventCommand.java` | 服务内部写入命令，禁止携带截图字节或 Base64。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionEventRepository.java` | 事件写入、筛选、清理和月度汇总 SQL。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionConfig.java` | 从 `system_configs` 热加载监督参数。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionEventService.java` | 从认证用户和客户档案补全事件，不信任客户端员工、渠道或归属字段。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionMetricsRepository.java` | 五项指标的分子、分母和按维度聚合查询。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionMetricsService.java` | 管理员鉴权、动态转化目标、指标 DTO 和月度快照。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionCleanupScheduler.java` | 每日清理监督记录、技术调用日志和已过期多客户任务。 |
| `src/main/java/com/privateflow/modules/supervision/SupervisionAdminController.java` | 管理员的指标、事件明细、配置元数据接口。 |
| `src/main/java/com/privateflow/modules/api/web/ChatController.java` | 增加员工复制 AI 话术的使用记录接口，不再命名或调用发送确认。 |
| `src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java` | 校验本计划新增的数值与转化阶段 JSON 配置。 |
| `desktop/src/renderer/modules/admin/AdminConsole.vue` | 新增“监督记录”和“数据保留与任务设置”后台页面。 |
| `src/test/java/com/privateflow/modules/supervision/*.java` | 事件、指标、清理、权限和控制器测试。 |
| `desktop/src/renderer/modules/admin/AdminConsole.test.ts` | 后台页面的指标、权限与配置范围测试。 |

## Contract Decisions

- `supervision_events` 的 `event_type` 只能为 `TASK_CREATED`、`PENDING_ENTERED`、`RECOGNITION_SUCCEEDED`、`RECOGNITION_FAILED`、`CUSTOMER_SELECTED`、`REPLY_GENERATED`、`REPLY_COPIED`、`REPLY_REGENERATED`、`HELP_REQUESTED`、`TEMPLATE_SAVED`、`TEMPLATE_COPIED`。任何原始图片、Base64、OCR 原文均不得进入列或 `metadata_json`。
- `REPLY_COPIED` 只代表 “AI 已使用 / 已复制”，不代表已发送，不调用 `sendConfirm`，也不触发 `FollowupConfirmationService`、客户资料更新或跟进完成。
- 统计的客户键使用客户档案 `phone`；没有匹配客户的识图事件可记录工作量，但不进入以客户为分母的转化率。
- 转化目标使用 `supervision.conversion_target_stages_json` 保存管理员从数据库现有 `customers.customer_stage` 值中选择的字符串数组。没有目标时，所有转化率返回 `numerator=0`、`denominator` 正常，并附带 `conversionTargetConfigured=false`，不伪造“成交”。
- 当期“应处理客户”和“新进入待处理客户”均由 `PENDING_ENTERED` 事件去重计算；该事件由后续任务流在首次接受一个客户处理任务时按 `customer_phone + date` 幂等写入。处理效率的“按时”定义为同一客户在 `supervision.processing_sla_minutes` 内出现 `REPLY_GENERATED` 或 `REPLY_COPIED`。

### Task 1: 建立数据库契约与配置测试

**Files:**
- Create: `src/main/resources/db/migration/V76__supervision_events_and_governance_config.sql`
- Create: `src/test/java/com/privateflow/modules/supervision/SupervisionFlywayMariaDbIntegrationTest.java`
- Modify: `src/test/java/com/privateflow/modules/api/config/ConfigAdminServiceTest.java`

- [ ] **Step 1: 写出 Flyway 与配置范围的失败测试**

```java
@Test
void createsEventAndMonthlySnapshotTablesWithoutScreenshotColumns() {
  jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supervision_events", Long.class);
  jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supervision_monthly_metric_snapshots", Long.class);
  assertThat(columns("supervision_events")).doesNotContain("image_base64", "screenshot", "raw_image");
}

@Test
void rejectsOutOfRangeGovernanceConfig() {
  assertThatThrownBy(() -> service.update("supervision.record_retention_days", Map.of("value", "29")))
      .hasMessageContaining("range is 30-730");
}
```

- [ ] **Step 2: 运行测试确认迁移和配置尚不存在**

Run: `./mvnw.cmd -Dtest=SupervisionFlywayMariaDbIntegrationTest,ConfigAdminServiceTest test`

Expected: FAIL，提示 `supervision_events` 不存在，且新配置键没有范围校验。

- [ ] **Step 3: 写入最小 Flyway 迁移与配置键**

```sql
CREATE TABLE supervision_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id CHAR(36) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  operator_username VARCHAR(64) NOT NULL,
  customer_phone VARCHAR(32) NULL,
  channel_code VARCHAR(100) NULL,
  channel_account VARCHAR(255) NULL,
  lead_source VARCHAR(255) NULL,
  assigned_keeper VARCHAR(64) NULL,
  scene VARCHAR(64) NULL,
  task_id VARCHAR(80) NULL,
  reply_session_id VARCHAR(80) NULL,
  reply_source VARCHAR(32) NULL,
  dedupe_key VARCHAR(160) NULL,
  generated_reply_snapshot TEXT NULL,
  copied_reply_snapshot TEXT NULL,
  metadata_json TEXT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_supervision_event_id (event_id), UNIQUE KEY uk_supervision_dedupe_key (dedupe_key),
  KEY idx_supervision_operator_time (operator_username, occurred_at),
  KEY idx_supervision_customer_time (customer_phone, occurred_at),
  KEY idx_supervision_channel_time (channel_code, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('supervision.record_retention_days', '180', 'supervisor record retention days, range 30-730'),
  ('supervision.technical_log_retention_days', '30', 'LLM and Skill technical log retention days, range 7-180'),
  ('supervision.processing_sla_minutes', '1440', 'AI processing SLA minutes, range 15-10080'),
  ('supervision.conversion_target_stages_json', '[]', 'configured customer_stage values counted as conversions'),
  ('chat.expired_reply_task_retention_days', '3', 'expired reply task physical retention days, range 1-14'),
  ('chat.unfinished_task_cap', '20', 'per employee unfinished reply task cap, range 10-50'),
  ('chat.recent_task_display_cap', '30', 'per employee recent task display cap, range 20-100'),
  ('chat.recognition_concurrency', '4', 'global image recognition concurrency, range 1-16')
ON DUPLICATE KEY UPDATE description = VALUES(description);
```

在同一迁移中创建 `supervision_monthly_metric_snapshots`，唯一键为 `(metric_month, dimension_type, dimension_value, metric_key)`，并在 `ConfigAdminService.validateIntegerRange` 中精确加入上述数值范围及 JSON 数组校验。

- [ ] **Step 4: 重新运行迁移与配置测试**

Run: `./mvnw.cmd -Dtest=SupervisionFlywayMariaDbIntegrationTest,ConfigAdminServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交数据库基础**

```bash
git add src/main/resources/db/migration/V76__supervision_events_and_governance_config.sql src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java src/test/java/com/privateflow/modules/supervision/SupervisionFlywayMariaDbIntegrationTest.java src/test/java/com/privateflow/modules/api/config/ConfigAdminServiceTest.java
git commit -m "feat: add supervision governance storage"
```

### Task 2: 事件模型、客户上下文补全与复制使用接口

**Files:**
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionEventType.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionEventCommand.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionEventRepository.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionEventService.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/AiUsageRequest.java`
- Modify: `src/main/java/com/privateflow/modules/api/web/ChatController.java`
- Test: `src/test/java/com/privateflow/modules/supervision/SupervisionEventServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java`

- [ ] **Step 1: 写出复制只记 AI 使用的失败测试**

```java
@Test
void recordsCopiedReplyWithoutCallingSendConfirmationOrChangingCustomer() {
  service.recordCopiedReply(new AiUsageRequest("13800000001", "task-1", "reply-1", "LLM", "建议回复"));

  assertThat(events.findByType("REPLY_COPIED")).singleElement()
      .satisfies(event -> assertThat(event.copiedReplySnapshot()).isEqualTo("建议回复"));
  verifyNoInteractions(followupConfirmationService, customerMessageSentPublisher);
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./mvnw.cmd -Dtest=SupervisionEventServiceTest,ChatControllerTest test`

Expected: FAIL，提示 `AiUsageRequest` 与 `/ai-usage` 尚未定义。

- [ ] **Step 3: 实现不可变事件写入与受控复制 API**

```java
public record AiUsageRequest(String phone, String taskId, String replySessionId, String replySource, String copiedText) {}

@PostMapping("/ai-usage")
public ApiResponse<Map<String, Object>> recordAiUsage(@RequestBody AiUsageRequest request) {
  return ApiResponse.ok(supervisionEventService.recordCopiedReply(request));
}

public Map<String, Object> recordCopiedReply(AiUsageRequest request) {
  Customer customer = requireAccessibleCustomer(request.phone());
  repository.insert(enrich(new SupervisionEventCommand(
      SupervisionEventType.REPLY_COPIED, customer.getPhone(), request.taskId(), request.replySessionId(),
      request.replySource(), null, clip(request.copiedText(), 4000), Map.of("copyOnly", true))));
  return Map.of("recorded", true, "semantic", "COPIED_AI_REPLY");
}
```

`enrich` 必须从 `Customer` 填充 `sourceChannel`、`assignedKeeper`、`sourceTable`，从 `AuthContext.username()` 填充操作者；拒绝 `copiedText` 为空、超长文本和无访问权客户。仓库使用参数化 SQL，`metadata_json` 只允许字符串、数字、布尔值和空集合。

- [ ] **Step 4: 运行事件与控制器测试**

Run: `./mvnw.cmd -Dtest=SupervisionEventServiceTest,ChatControllerTest test`

Expected: PASS，且测试断言中不存在 `sendConfirm` 调用。

- [ ] **Step 5: 提交事件基础与接口**

```bash
git add src/main/java/com/privateflow/modules/supervision src/main/java/com/privateflow/modules/api/chat/AiUsageRequest.java src/main/java/com/privateflow/modules/api/web/ChatController.java src/test/java/com/privateflow/modules/supervision/SupervisionEventServiceTest.java src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java
git commit -m "feat: record AI usage as supervision events"
```

### Task 3: 热加载治理配置与过期数据清理

**Files:**
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionConfig.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionCleanupScheduler.java`
- Modify: `src/main/java/com/privateflow/modules/supervision/SupervisionEventRepository.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskRepository.java`
- Modify: `src/main/java/com/privateflow/modules/llm/LlmCallAnalyticsRepository.java`
- Modify: `src/main/java/com/privateflow/modules/skill/admin/SkillCallAnalyticsRepository.java`
- Test: `src/test/java/com/privateflow/modules/supervision/SupervisionCleanupSchedulerTest.java`
- Test: `src/test/java/com/privateflow/modules/api/chat/PendingReplyTaskRepositoryTest.java`

- [ ] **Step 1: 写出保留边界的失败测试**

```java
@Test
void deletesOnlyExpiredReplyTasksOlderThanConfiguredRetention() {
  repository.deletePhysicallyExpiredBefore(LocalDateTime.of(2026, 7, 20, 0, 0));
  assertThat(task("expired-old")).isNull();
  assertThat(task("ready-old")).isNull();
  assertThat(task("expired-recent")).isNotNull();
}

@Test
void technicalCleanupDeletesLlmAndSkillRowsButKeepsSupervisorEventsForTheirOwnRetention() {
  scheduler.cleanupAt(LocalDateTime.of(2026, 7, 23, 4, 0));
  assertThat(count("llm_call_logs")).isEqualTo(0);
  assertThat(count("skill_call_logs")).isEqualTo(0);
  assertThat(count("supervision_events")).isEqualTo(1);
}
```

- [ ] **Step 2: 运行清理测试确认失败**

Run: `./mvnw.cmd -Dtest=SupervisionCleanupSchedulerTest,PendingReplyTaskRepositoryTest test`

Expected: FAIL，提示没有物理清理方法或技术日志清理 SQL。

- [ ] **Step 3: 实现配置快照和按类别清理**

```java
@Scheduled(cron = "0 10 4 * * *")
public void cleanup() {
  cleanupAt(LocalDateTime.now());
}

void cleanupAt(LocalDateTime now) {
  repository.deleteEventsBefore(now.minusDays(config.recordRetentionDays()));
  llmCallAnalyticsRepository.deleteBefore(now.minusDays(config.technicalLogRetentionDays()));
  skillCallAnalyticsRepository.deleteBefore(now.minusDays(config.technicalLogRetentionDays()));
  pendingReplyTaskRepository.recoverExpiredAndStalledTasks(now, config.pendingReplyGeneratingTimeoutSeconds());
  pendingReplyTaskRepository.deletePhysicallyExpiredBefore(now.minusDays(config.expiredReplyTaskRetentionDays()));
}
```

`deletePhysicallyExpiredBefore` 的 SQL 必须限制 `expires_at < ? AND status IN ('EXPIRED','CANCELLED','READY','FAILED')`，不得删除 `WAITING_CUSTOMER` 或 `GENERATING` 任务。这样已过期的已生成/失败多客户任务在三天后被物理删除，而正在处理任务不会被误删。`SupervisionConfig` 监听 `ConfigChangedEvent`，解析失败时保留最后一次有效快照；默认值严格为 `180/30/3/20/30/4/1440`。

- [ ] **Step 4: 运行清理与配置回归测试**

Run: `./mvnw.cmd -Dtest=SupervisionCleanupSchedulerTest,PendingReplyTaskRepositoryTest,ConfigAdminServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交清理功能**

```bash
git add src/main/java/com/privateflow/modules/supervision src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskRepository.java src/main/java/com/privateflow/modules/llm/LlmCallAnalyticsRepository.java src/main/java/com/privateflow/modules/skill/admin/SkillCallAnalyticsRepository.java src/test/java/com/privateflow/modules/supervision/SupervisionCleanupSchedulerTest.java src/test/java/com/privateflow/modules/api/chat/PendingReplyTaskRepositoryTest.java
git commit -m "feat: enforce supervision retention policies"
```

### Task 4: 实现可核对的主管指标与月度汇总

**Files:**
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionMetric.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionMetricsQuery.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionMetricsRepository.java`
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionMetricsService.java`
- Modify: `src/main/java/com/privateflow/modules/supervision/SupervisionEventRepository.java`
- Test: `src/test/java/com/privateflow/modules/supervision/SupervisionMetricsServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/supervision/SupervisionMetricsRepositoryTest.java`

- [ ] **Step 1: 写出五项指标分子和分母的失败测试**

```java
assertThat(report.metric("AI_USAGE_RATE")).isEqualTo(new SupervisionMetric(1, 2, 0.5));
assertThat(report.metric("AI_COVERAGE")).isEqualTo(new SupervisionMetric(2, 4, 0.5));
assertThat(report.metric("PROCESSING_EFFICIENCY")).isEqualTo(new SupervisionMetric(1, 4, 0.25));
assertThat(report.metric("EMPLOYEE_CONVERSION")).isEqualTo(new SupervisionMetric(1, 3, 1.0 / 3));
assertThat(report.metric("AI_ASSOCIATED_CONVERSION")).isEqualTo(new SupervisionMetric(1, 2, 0.5));
```

- [ ] **Step 2: 运行失败测试**

Run: `./mvnw.cmd -Dtest=SupervisionMetricsServiceTest,SupervisionMetricsRepositoryTest test`

Expected: FAIL，提示指标服务与查询 DTO 尚未定义。

- [ ] **Step 3: 实现严格的五个 SQL 口径**

```java
public record SupervisionMetric(long numerator, long denominator, double rate) {
  static SupervisionMetric of(long numerator, long denominator) {
    return new SupervisionMetric(numerator, denominator, denominator == 0 ? 0.0 : (double) numerator / denominator);
  }
}
```

实现时使用以下固定口径并在响应中返回 `numeratorLabel`、`denominatorLabel`：

| 指标键 | 分子查询 | 分母查询 |
| --- | --- | --- |
| `AI_USAGE_RATE` | 所选期间内 `REPLY_COPIED` 的去重 `customer_phone` | `REPLY_GENERATED` 的去重 `customer_phone` |
| `AI_COVERAGE` | 有 `REPLY_GENERATED` 或 `REPLY_COPIED` 的去重客户 | `PENDING_ENTERED` 的去重客户 |
| `PROCESSING_EFFICIENCY` | `PENDING_ENTERED` 后 SLA 内有生成或复制的客户 | `PENDING_ENTERED` 的去重客户 |
| `EMPLOYEE_CONVERSION` | `customers.customer_stage` 属于配置目标且归属员工的客户 | 所选范围内归属员工的客户 |
| `AI_ASSOCIATED_CONVERSION` | 有 AI 使用且当前阶段属于配置目标的去重客户 | 有 `REPLY_COPIED` 的去重客户 |

所有查询必须对 `operator_username`、`channel_code`、`lead_source` 采用相同的可选筛选条件，并在 `denominator=0` 时返回 `rate=0.0`。每月 1 日 04:20 对 `ALL`、每名员工、每个渠道、每个线索来源分别 UPSERT 快照，月度汇总不参与监督事件清理。

- [ ] **Step 4: 运行指标与月度汇总测试**

Run: `./mvnw.cmd -Dtest=SupervisionMetricsServiceTest,SupervisionMetricsRepositoryTest test`

Expected: PASS，测试中不出现“已发送”字段或发送率。

- [ ] **Step 5: 提交指标实现**

```bash
git add src/main/java/com/privateflow/modules/supervision src/test/java/com/privateflow/modules/supervision/SupervisionMetricsServiceTest.java src/test/java/com/privateflow/modules/supervision/SupervisionMetricsRepositoryTest.java
git commit -m "feat: add supervisor workflow metrics"
```

### Task 5: 管理员 API、权限与后台界面

**Files:**
- Create: `src/main/java/com/privateflow/modules/supervision/SupervisionAdminController.java`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Test: `src/test/java/com/privateflow/modules/supervision/SupervisionAdminControllerTest.java`

- [ ] **Step 1: 写出管理员与普通员工的失败测试**

```java
mockMvc.perform(get("/admin/api/v1/supervision/metrics").with(admin()))
    .andExpect(status().isOk());
mockMvc.perform(get("/admin/api/v1/supervision/metrics").with(keeper()))
    .andExpect(status().isForbidden());
```

```ts
expect(wrapper.text()).toContain('数据保留与任务设置');
expect(wrapper.text()).toContain('主管监督记录');
expect(requests).toContain('/admin/api/v1/supervision/metrics');
```

- [ ] **Step 2: 运行控制器和后台组件测试确认失败**

Run: `./mvnw.cmd -Dtest=SupervisionAdminControllerTest test`

Run: `npm run test -- --run AdminConsole.test.ts`

Expected: FAIL，页面和路由不存在。

- [ ] **Step 3: 实现受管理员保护的读写边界和界面**

```java
@GetMapping("/admin/api/v1/supervision/metrics")
public ApiResponse<Map<String, Object>> metrics(SupervisionMetricsQuery query) {
  return ApiResponse.ok(metricsService.report(query));
}

@GetMapping("/admin/api/v1/supervision/events")
public ApiResponse<Map<String, Object>> events(SupervisionEventQuery query) {
  return ApiResponse.ok(metricsService.events(query));
}
```

在 `AdminConsole.vue` 新增 `supervision-dashboard` 与 `governance-settings` 两个 `SectionKey`，放入“分析与系统”组。前者显示五个指标的分子、分母、百分比、员工/渠道/线索来源筛选和事件明细；后者只显示并编辑本计划列出的七项配置与转化目标阶段。目标阶段选项必须请求 `/admin/api/v1/supervision/metadata` 的数据库去重值，不能写死“成交、到店”等文本。`tagManagementOnly` 模式不得显示这两个页面。

- [ ] **Step 4: 运行权限、前端和 API 测试**

Run: `./mvnw.cmd -Dtest=SupervisionAdminControllerTest,SupervisionMetricsServiceTest,ConfigAdminServiceTest test`

Run: `npm run test -- --run AdminConsole.test.ts`

Expected: PASS。

- [ ] **Step 5: 提交主管后台功能**

```bash
git add src/main/java/com/privateflow/modules/supervision desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts src/test/java/com/privateflow/modules/supervision/SupervisionAdminControllerTest.java
git commit -m "feat: add supervisor governance console"
```

### Task 6: 事件接入点与端到端回归

**Files:**
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskService.java`
- Modify: `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`
- Modify: `src/test/java/com/privateflow/modules/api/chat/PendingReplyTaskServiceTest.java`
- Modify: `src/test/java/com/privateflow/modules/api/audit/AuditLogRepositoryTest.java`

- [ ] **Step 1: 写出自动事件接入的失败测试**

```java
verify(supervisionEventService).recordGeneratedReply(eq(customer), eq("CHAT_RECOGNIZE"), any(), any());
verify(supervisionEventService).recordCustomerSelected(eq(customer), eq("task-1"), any());
verify(supervisionEventService, never()).recordCopiedReply(any());
```

- [ ] **Step 2: 运行接入测试确认失败**

Run: `./mvnw.cmd -Dtest=ChatOrchestrationServiceTest,PendingReplyTaskServiceTest test`

Expected: FAIL，事件服务尚未被调用。

- [ ] **Step 3: 接入生成、候选选择和失败事件**

在 `ChatOrchestrationService` 成功生成回复后调用 `recordGeneratedReply`，在识图失败捕获处调用 `recordRecognitionFailed`，在多匹配任务创建后调用 `recordTaskCreated`。首次识别到确定客户或员工从多匹配候选中选定客户时，用 `dedupe_key = "PENDING:" + customerPhone + ":" + LocalDate.now()` 幂等调用 `recordPendingEntered`；在 `confirmPendingReplyTask` 取得 `Customer` 后调用 `recordCustomerSelected`。所有 `RuntimeException` 的公开错误码写入失败事件的元数据，原始异常堆栈不入库。

- [ ] **Step 4: 运行后端相关回归**

Run: `./mvnw.cmd -Dtest=ChatOrchestrationServiceTest,PendingReplyTaskServiceTest,SupervisionEventServiceTest,SupervisionMetricsServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交事件接入**

```bash
git add src/main/java/com/privateflow/modules/api/chat src/test/java/com/privateflow/modules/api/chat
git commit -m "feat: capture reply workflow supervision events"
```

## Self-Review

| 已确认需求 | 覆盖任务 |
| --- | --- |
| 主管监督记录、质量快照、员工/渠道/来源维度 | Task 2、Task 4、Task 5、Task 6 |
| 五项指标及每项分子/分母 | Task 4 |
| 动态转化目标，不能写死业务阶段 | Task 1、Task 4、Task 5 |
| 180/30/3 天及 20/30/4 的管理员配置范围 | Task 1、Task 3、Task 5 |
| 技术日志、监督记录、临时任务分别清理 | Task 3 |
| 复制不等于已发送、无员工发送确认 | Task 2、Task 6 |
| 原图永不进入监督或日志存储 | Task 1、Task 2、Task 6 |

已逐项检查：所有文件路径、事件名、配置键和 DTO 名在本计划内首次定义后保持一致；实现步骤都含明确测试、命令与预期结果；无未定义的占位实现。
