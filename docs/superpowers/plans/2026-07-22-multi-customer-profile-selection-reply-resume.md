# 多客户档案确认与回复续跑 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让截图识别匹配到多个客户时先提醒员工查看档案并明确确认，再基于正确档案自动调用 Skill 和回复 LLM；任务在员工忙碌、桌面端重启或请求中断后仍可恢复。

**Architecture:** 后端数据库保存待确认回复任务，是唯一真实来源。`POST /api/v1/chat/recognize` 在多客户分支只保存任务并返回候选摘要，绝不调用 Skill 或回复 LLM；员工确认后由新接口以原识别聊天内容和最新客户档案继续同一条回复会话。桌面端把“查看档案”和“确认是此客户”分成独立动作，并通过任务查询接口和 Electron 通知恢复待办。

**Tech Stack:** Spring Boot、JdbcTemplate、MariaDB/Flyway、Redis（只复用生成完成后的既有上下文缓存）、Vue 3、TypeScript、Vitest、Electron IPC、JUnit 5、Mockito。

---

## 实施前的边界

本计划基于已确认的设计：[多客户档案确认与回复续跑设计](../specs/2026-07-21-multi-customer-profile-selection-reply-resume-design.md)。

本次只解决多客户确认与续跑。不要顺带修改图片识别提示词、客户匹配算法、截图来源记录、识别超时、复制即发送副作用、工作台排序或跟进规则。

### 与正在进行的工作台修复的关系

编写或阅读本计划不会修改工作台代码，不会冲突。

真正实施前，必须先把当前工作台修复整理为一个可回退的 Git 提交。当前工作区中的下列文件与本计划可能重叠，不能在同一个脏工作区里直接混改：

- `desktop/src/renderer/App.vue`
- `desktop/src/renderer/App.test.ts`
- `desktop/src/renderer/styles.css`
- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts`
- 对应的前端测试文件

工作台提交完成后，从该提交创建独立 worktree 再执行本计划。合并时保留工作台已有的 `customer:selected` 行为：它只表示既有的客户打开/主动生成流程；本计划的候选预览和确认不得复用或改变它。

```powershell
git status --short --branch
git log -1 --oneline
$baseline = git rev-parse HEAD
git worktree add ..\private-domain-assistant-reply-task -b feat/pending-reply-task $baseline
```

预期：新 worktree 初始状态干净；原工作区的工作台改动仍原样保留。

## 文件清单

| 文件 | 变更职责 |
|---|---|
| `src/main/resources/db/migration/V75__pending_reply_tasks.sql` | 新建持久化回复任务表、候选表和两项配置默认值。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskStatus.java` | 任务状态枚举。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTask.java` | 后端任务领域记录，不包含原始截图。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskDraft.java` | 创建任务时从识别结果提取的最小不可变输入。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskRepository.java` | JdbcTemplate 读写、原子状态领取、超时清理。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskService.java` | 任务创建、所有权校验、候选校验、恢复和状态转换。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskView.java` | 给桌面端的任务、候选和已有回复视图。 |
| `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskSelectRequest.java` | 确认客户请求。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatTaskConfig.java` | `chat.*` 配置读取和热更新。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatResponse.java` | 增加可选的 `pendingTask` 字段。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatRecognizeRequest.java` | 增加客户端回复会话编号。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java` | 多客户分支只建任务；确认、重试和恢复后按原识别场景生成。 |
| `src/main/java/com/privateflow/modules/api/web/ChatController.java` | 增加查询、确认、重试和取消待处理任务的 REST 路由。 |
| `src/test/java/com/privateflow/modules/api/chat/*` | 任务状态机与编排回归测试。 |
| `src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java` | 新 REST 路由与请求校验测试。 |
| `desktop/src/renderer/modules/reply-suggestions/types.ts` | 声明任务 DTO、扩展识别响应与回复会话字段。 |
| `desktop/src/renderer/modules/reply-suggestions/pendingReplyTaskStore.ts` | 服务端任务恢复、任务计数、通知打开和同会话结果回填。 |
| `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts` | 多客户状态保存 `taskId`，确认后进入生成态，完成后显示结果。 |
| `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue` | 显示持久的“待选择客户”数量与可恢复任务入口。 |
| `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts` | 候选预览、返回候选、明确确认和取消；预览不发送 `customer:selected`。 |
| `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue` | 候选摘要、查看档案、返回候选、确认客户按钮。 |
| `desktop/src/renderer/App.vue` | 登录成功、令牌刷新成功后请求恢复待处理任务；仅在工作台改动合并后编辑。 |
| `desktop/src/main/main.ts`、`desktop/src/preload/preload.cts`、`desktop/src/renderer/shared/desktopBridge.ts`、`desktop/src/renderer/types/desktop.ts` | 通知任务待选择、点击通知回到对应任务的最小 IPC 桥接。 |
| `desktop/src/renderer/styles.css` | 仅补候选预览/确认/待办计数样式；仅在工作台样式合并后编辑。 |
| `SHARED_CONTRACTS.md`、`DEPENDENCIES.md`、`decisions.md`、`questions.md` | 回填新契约、依赖和已确认业务决策。 |

## 固定契约

### 任务状态

```java
public enum PendingReplyTaskStatus {
  WAITING_CUSTOMER,
  GENERATING,
  READY,
  FAILED,
  CANCELLED,
  EXPIRED
}
```

允许的转换：

```text
WAITING_CUSTOMER -> GENERATING | CANCELLED | EXPIRED
GENERATING       -> READY | FAILED
FAILED           -> GENERATING | CANCELLED | EXPIRED
READY/CANCELLED/EXPIRED -> 无后继状态
```

`GENERATING` 的领取必须是条件更新：相同任务被重复点击、网络重试或两个窗口同时点击时，最多一个请求能把它改成 `GENERATING`，也就最多调用一次 Skill 和回复 LLM。

### 数据库结构

任务表只保存继续生成所需的文字和标识，绝不保存截图、Base64、文件路径或原始图像字节。

```sql
CREATE TABLE IF NOT EXISTS pending_reply_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id CHAR(36) NOT NULL,
  reply_session_id VARCHAR(80) NOT NULL,
  username VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  recognized_nickname VARCHAR(255) NULL,
  recognized_phone VARCHAR(32) NULL,
  platform_identifier VARCHAR(255) NULL,
  lead_type VARCHAR(32) NULL,
  source_table VARCHAR(255) NULL,
  client_message TEXT NOT NULL,
  chat_context_json MEDIUMTEXT NOT NULL,
  selected_phone VARCHAR(32) NULL,
  result_json MEDIUMTEXT NULL,
  error_code VARCHAR(32) NULL,
  generation_started_at DATETIME(6) NULL,
  finished_at DATETIME(6) NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pending_reply_task_id (task_id),
  KEY idx_pending_reply_owner_status (username, status, expires_at),
  KEY idx_pending_reply_recovery (status, generation_started_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pending_reply_task_candidates (
  task_id BIGINT NOT NULL,
  phone VARCHAR(32) NOT NULL,
  rank_no SMALLINT NOT NULL,
  PRIMARY KEY (task_id, phone),
  CONSTRAINT fk_pending_reply_candidate_task
    FOREIGN KEY (task_id) REFERENCES pending_reply_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

配置采用 `chat.*` 前缀：

| Key | 默认值 | 合法范围 | 含义 |
|---|---:|---:|---|
| `chat.pending_reply_ttl_hours` | `24` | `1-72` | 员工确认客户前，任务和结构化聊天文字保留时间。 |
| `chat.pending_reply_generating_timeout_s` | `120` | `30-600` | 服务中断后，多长时间的 `GENERATING` 任务可恢复为 `FAILED`。 |

### REST 路由

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/v1/chat/reply-tasks` | 当前员工恢复 `WAITING_CUSTOMER`、`GENERATING`、`FAILED`、`READY` 任务。 |
| `GET` | `/api/v1/chat/reply-tasks/{taskId}` | 获取单个任务，处理确认请求超时后的结果恢复。 |
| `POST` | `/api/v1/chat/reply-tasks/{taskId}/confirm` | 明确确认候选客户并立即生成回复。 |
| `POST` | `/api/v1/chat/reply-tasks/{taskId}/retry` | 对已确认但生成失败的任务再次生成。 |
| `POST` | `/api/v1/chat/reply-tasks/{taskId}/cancel` | 员工主动放弃待选或失败任务。 |

确认请求体固定为：

```json
{ "phone": "18800001111" }
```

确认接口必须依次校验：任务存在、任务属于当前员工、任务未失效、状态可领取、手机号属于原候选集合、客户档案仍存在、当前员工仍有档案访问权限。任何校验失败都不得调用 Skill 或回复 LLM。

## Task 0: 建立可恢复的实施基线

**Files:**

- Create: `dev-progress/multi_customer_reply_resume_tasklist_20260722.md`
- Modify: none
- Test: none

- [ ] **Step 1: 在开始编码前固定工作台基线**

运行：

```powershell
git status --short --branch
git log -1 --oneline
```

预期：记录当前工作台变更所在分支和提交；若 `App.vue`、`styles.css`、客户档案模块仍是未提交改动，不在该工作区写本计划的生产代码。

- [ ] **Step 2: 新建实施任务清单**

创建 `dev-progress/multi_customer_reply_resume_tasklist_20260722.md`，内容固定包含以下可更新字段：

```markdown
# 多客户档案确认与回复续跑实施断点

## 当前状态
- 基线提交：执行 `git rev-parse HEAD` 后写入得到的 40 位哈希
- 当前任务：`Task 0`
- 最近验证：`尚未执行`
- 未解决阻塞：`无`

## 完成记录
- [ ] Task 0 基线与契约
- [ ] Task 1 数据库与状态机
- [ ] Task 2 编排与 API
- [ ] Task 3 桌面候选预览与确认
- [ ] Task 4 恢复与桌面提醒
- [ ] Task 5 全量验证与人工验收

## 恢复命令
1. `git status --short --branch`
2. 阅读本文件、设计文档和实施计划。
3. 从第一个未完成复选框继续，先运行其失败测试。
```

- [ ] **Step 3: 记录防冲突规则**

在任务清单的“未解决阻塞”下面增加：

```markdown
- 工作台共享文件：`App.vue`、`styles.css`、客户档案面板。实施前先合并工作台提交；候选预览不得发送 `customer:selected`。
```

- [ ] **Step 4: 提交文档基线**

```powershell
git add dev-progress/multi_customer_reply_resume_tasklist_20260722.md
git commit -m "docs: add reply task implementation checkpoint"
```

预期：提交只包含任务清单；不包含工作台未提交代码。

## Task 1: 数据库、配置和任务状态机

**Files:**

- Create: `src/main/resources/db/migration/V75__pending_reply_tasks.sql`
- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskStatus.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTask.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskDraft.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskRepository.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/ChatTaskConfig.java`
- Create: `src/test/java/com/privateflow/modules/api/chat/PendingReplyTaskRepositoryTest.java`
- Modify: `src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java`
- Modify: `src/test/java/com/privateflow/modules/api/config/ConfigAdminServiceTest.java`

- [ ] **Step 1: 写数据库任务状态的失败测试**

在 `PendingReplyTaskRepositoryTest` 使用现有 MariaDB/JdbcTemplate 测试模式，写入两个候选手机号并验证：

```java
@Test
void onlyOneConcurrentClaimCanMoveWaitingTaskToGenerating() {
  PendingReplyTask task = repository.create(task("keeper-1", WAITING_CUSTOMER));
  repository.insertCandidates(task.id(), List.of("18800001111", "18800002222"));

  assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
  assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isFalse();
  assertThat(repository.findOwned(task.taskId(), "keeper-1").orElseThrow().status())
      .isEqualTo(GENERATING);
}
```

运行：

```powershell
mvn -q -Dtest=PendingReplyTaskRepositoryTest test
```

预期：失败，因为迁移、仓储和状态机尚未存在。

- [ ] **Step 2: 新建 Flyway 迁移和可配置时限**

按本计划“数据库结构”创建 `V75__pending_reply_tasks.sql`，并追加：

```sql
INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('chat.pending_reply_ttl_hours', '24', 'pending reply task retention hours, range 1-72'),
  ('chat.pending_reply_generating_timeout_s', '120', 'pending reply generating recovery timeout seconds, range 30-600')
ON DUPLICATE KEY UPDATE description = VALUES(description);
```

禁止在任务表中新增 `image_base64`、`screenshot_path`、`image_url` 或类似字段。

- [ ] **Step 3: 实现状态、领域记录和配置读取**

`PendingReplyTask` 至少包含如下字段，使用不可变 `record`：

```java
public record PendingReplyTask(
    long id,
    String taskId,
    String replySessionId,
    String username,
    PendingReplyTaskStatus status,
    String recognizedNickname,
    String recognizedPhone,
    String platformIdentifier,
    String leadType,
    String sourceTable,
    String clientMessage,
    List<Map<String, String>> chatContext,
    List<String> candidatePhones,
    String selectedPhone,
    ChatResponse result,
    String errorCode,
    LocalDateTime generationStartedAt,
    LocalDateTime expiresAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

`PendingReplyTaskDraft` 只表达建任务所需输入，避免 `ChatOrchestrationService` 把图片或 Spring 对象交给仓储：

```java
public record PendingReplyTaskDraft(
    String replySessionId,
    String username,
    String recognizedNickname,
    String recognizedPhone,
    String platformIdentifier,
    String leadType,
    String sourceTable,
    String clientMessage,
    List<Map<String, String>> chatContext,
    List<CustomerSummary> candidates
) {
  public static PendingReplyTaskDraft from(
      String replySessionId,
      String username,
      RecognitionResult recognized,
      ChatRecognizeRequest request,
      String clientMessage,
      List<Map<String, String>> chatContext,
      List<CustomerSummary> candidates) {
    return new PendingReplyTaskDraft(
        replySessionId, username,
        recognized == null ? null : recognized.nickname(),
        recognized == null ? null : recognized.phone(),
        recognized == null ? null : recognized.customerIdentifier(),
        request.leadType(), request.sourceTable(), clientMessage, chatContext, candidates);
  }
}
```

`ChatTaskConfig` 必须监听 `ConfigChangedEvent`，读取 `chat.` 前缀，并对两个值分别限制为 `1-72` 和 `30-600`。在 `ConfigAdminService` 增加同样的输入校验，避免后台保存非法值。

- [ ] **Step 4: 实现仓储原子操作**

领取 SQL 必须以状态和有效期为条件，不能先查再无条件更新：

```sql
UPDATE pending_reply_tasks
SET status = 'GENERATING', selected_phone = ?, generation_started_at = NOW(6),
    error_code = NULL, updated_at = NOW(6), version = version + 1
WHERE task_id = ? AND username = ?
  AND status IN ('WAITING_CUSTOMER', 'FAILED')
  AND expires_at > NOW(6)
```

`claim` 返回 `true` 仅当受影响行数为 `1`。领取前通过 `pending_reply_task_candidates` 验证手机号属于该任务；`READY` 时写入 `result_json`；`FAILED` 时只写公开错误码；`CANCELLED`、`EXPIRED` 和恢复查询都保留审计所需的时间字段。

- [ ] **Step 5: 运行迁移、仓储和配置测试**

```powershell
mvn -q -Dtest=PendingReplyTaskRepositoryTest,ConfigAdminServiceTest test
```

预期：通过；重复领取只成功一次，候选集合校验有效，非法 `chat.*` 配置被拒绝。

- [ ] **Step 6: 提交数据库与状态机**

```powershell
git add src/main/resources/db/migration/V75__pending_reply_tasks.sql src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskStatus.java src/main/java/com/privateflow/modules/api/chat/PendingReplyTask.java src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskRepository.java src/main/java/com/privateflow/modules/api/chat/ChatTaskConfig.java src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java src/test/java/com/privateflow/modules/api/chat/PendingReplyTaskRepositoryTest.java src/test/java/com/privateflow/modules/api/config/ConfigAdminServiceTest.java
git commit -m "feat: persist pending reply tasks"
```

## Task 2: 后端任务服务和识别主线修复

**Files:**

- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskService.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskView.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskSelectRequest.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatRecognizeRequest.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatResponse.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- Modify: `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`

- [ ] **Step 1: 写“多客户不得提前生成”的失败测试**

在 `ChatOrchestrationServiceTest` 新增：

```java
@Test
void recognizeMultipleCreatesWaitingTaskWithoutCallingSkillOrReplyLlm() {
  when(imageRecognitionService.recognize(any(), any())).thenReturn(recognition("同名客户", null));
  when(customerMatchService.match(any())).thenReturn(multiple("18800001111", "18800002222"));

  ChatResponse response = service.recognize(recognizeRequest("reply-100"));

  assertThat(response.pendingTask()).isNotNull();
  assertThat(response.pendingTask().status()).isEqualTo("WAITING_CUSTOMER");
  assertThat(response.skill()).isNull();
  verify(skillGatewayService, never()).generateReplies(any());
  verify(llmReplyGenerationService, never()).tryGenerate(any(), any());
}
```

运行：

```powershell
mvn -q -Dtest=ChatOrchestrationServiceTest#recognizeMultipleCreatesWaitingTaskWithoutCallingSkillOrReplyLlm test
```

预期：失败，因为当前 `recognize()` 会调用 `firstCustomer(match)` 后生成回复。

- [ ] **Step 2: 扩展请求和响应但保持旧路径兼容**

在 `ChatRecognizeRequest` 最后一项增加 `String replySessionId`。前端会传入已有的 `reply-...` 编号；空值时后端生成 UUID，避免旧客户端请求失败。

在 `ChatResponse` 最后一项增加：

```java
PendingReplyTaskView pendingTask
```

所有既有 `new ChatResponse(...)` 调用必须补 `null`，保证唯一客户、无档案、主动生成和换一组的 JSON 结构不变，只多出可选空字段。

- [ ] **Step 3: 实现任务服务的所有权和恢复行为**

`PendingReplyTaskService` 必须提供以下方法：

```java
PendingReplyTaskView createWaitingTask(PendingReplyTaskDraft draft);
List<PendingReplyTaskView> listRecoverable(String username);
PendingReplyTaskView getRecoverable(String taskId, String username);
PendingReplyTask claimForGeneration(String taskId, String username, String phone);
PendingReplyTask claimRetry(String taskId, String username);
void markReady(PendingReplyTask task, ChatResponse response);
void markFailed(PendingReplyTask task, String publicErrorCode);
void cancel(String taskId, String username);
```

`listRecoverable` 调用仓储清理：过期的 `WAITING_CUSTOMER`/`FAILED` 改为 `EXPIRED`；开始生成时间超过 `chat.pending_reply_generating_timeout_s` 的任务改为 `FAILED`。不使用无限循环或常驻线程。

- [ ] **Step 4: 改写 `recognize()` 的多客户分支**

在 `ChatOrchestrationService.recognize()` 中，先获得 `clientMessage` 和 `chatContext`，再按匹配结果分支：

```java
if (match.matchType() == MatchType.MULTIPLE) {
  PendingReplyTaskView task = pendingReplyTaskService.createWaitingTask(
      PendingReplyTaskDraft.from(
          request.replySessionId(), AuthContext.username(), recognized, request,
          clientMessage, chatContext, match.customers()));
  return new ChatResponse(null, nickname, false, match, null, null, null, task);
}

Customer customer = firstCustomer(match);
GeneratedReplies generated = generateSkill(
    Scene.CHAT_RECOGNIZE, request.leadType(), customer, phone, clientMessage, List.of(), chatContext);
```

只有 `MULTIPLE` 直接返回。`EXACT`、`FUZZY` 和 `NONE` 继续现有生成逻辑。绝不以“前端会隐藏结果”为理由保留多客户下的 `generateSkill()`。

- [ ] **Step 5: 新增确认和重试编排**

确认后使用任务内保存的原识别聊天内容，而不是调用 `generate(new GenerateRequest(...))`：

```java
public ChatResponse confirmPendingReplyTask(String taskId, PendingReplyTaskSelectRequest request) {
  PendingReplyTask task = pendingReplyTaskService.claimForGeneration(
      taskId, AuthContext.username(), request.phone());
  Customer customer = requireAccessibleSelectedCustomer(task);
  try {
    GeneratedReplies generated = generateSkill(
        Scene.CHAT_RECOGNIZE, task.leadType(), customer, customer.getPhone(),
        task.clientMessage(), List.of(), task.chatContext());
    ChatResponse response = new ChatResponse(
        customer.getPhone(), customer.getNickname(), false, null,
        generated.skill(), null, generated.source(), pendingReplyTaskService.readyView(task, generated));
    pendingReplyTaskService.markReady(task, response);
    saveContext(customer.getPhone(), generated, 0);
    return response;
  } catch (RuntimeException ex) {
    pendingReplyTaskService.markFailed(task, publicTaskErrorCode(ex));
    throw ex;
  }
}
```

`requireAccessibleSelectedCustomer` 必须再次加载当前客户档案并执行 `customerAccessService.canAccess(customer)`。客户被删或权限失效时，把任务恢复为 `WAITING_CUSTOMER` 并返回可刷新候选，而不是退回到候选第一位。

错误码转换只保存可公开的已有错误码：

```java
private String publicTaskErrorCode(RuntimeException ex) {
  return ex instanceof ApiException apiException
      ? apiException.getErrorCode()
      : ApiErrorCodes.INTERNAL_ERROR;
}
```

- [ ] **Step 6: 运行编排回归测试**

```powershell
mvn -q -Dtest=ChatOrchestrationServiceTest test
```

预期：多客户不调用 Skill/LLM；唯一客户和无档案不回归；确认第二个候选时 SkillRequest 使用第二个客户和原始聊天上下文；重复确认不生成两次。

- [ ] **Step 7: 提交后端编排**

```powershell
git add src/main/java/com/privateflow/modules/api/chat src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java
git commit -m "fix: defer reply generation until customer confirmation"
```

## Task 3: REST 路由、授权和可恢复结果

**Files:**

- Modify: `src/main/java/com/privateflow/modules/api/web/ChatController.java`
- Modify: `src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java`
- Modify: `src/main/java/com/privateflow/modules/api/audit/AuditLogService.java`（若需要登记新动作展示名）
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`

- [ ] **Step 1: 写控制器失败测试**

至少覆盖确认和查询：

```java
@Test
void confirmPendingReplyTaskPassesPathAndSelectedPhoneToService() throws Exception {
  when(service.confirmPendingReplyTask(eq("task-1"), any()))
      .thenReturn(responseWithReply("18800001111"));

  mockMvc.perform(post("/api/v1/chat/reply-tasks/task-1/confirm")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"phone\":\"18800001111\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.phone").value("18800001111"));
}
```

运行：

```powershell
mvn -q -Dtest=ChatControllerTest#confirmPendingReplyTaskPassesPathAndSelectedPhoneToService test
```

预期：失败，因为路由不存在。

- [ ] **Step 2: 添加控制器端点**

```java
@GetMapping("/reply-tasks")
public ApiResponse<List<PendingReplyTaskView>> replyTasks() {
  return ApiResponse.ok(orchestrationService.listPendingReplyTasks());
}

@PostMapping("/reply-tasks/{taskId}/confirm")
public ApiResponse<ChatResponse> confirmReplyTask(
    @PathVariable String taskId,
    @RequestBody PendingReplyTaskSelectRequest request) {
  return ApiResponse.ok(orchestrationService.confirmPendingReplyTask(taskId, request));
}
```

用同一模式补齐 `GET /reply-tasks/{taskId}`、`POST /retry` 和 `POST /cancel`。空 `taskId`、空手机号、非法状态、非本人任务、过期任务和无权限客户使用已登记 API 错误域并在 `SHARED_CONTRACTS.md` 登记具体错误码。

- [ ] **Step 3: 验证确认请求超时后的恢复**

新增服务测试：确认接口已经成功写入 `READY` 但客户端模拟超时后，`GET /reply-tasks/{taskId}` 返回同一 `replySessionId`、`READY` 状态和已保存回复。前端因此不能重复调用确认接口。

- [ ] **Step 4: 运行 Web 和授权回归**

```powershell
mvn -q -Dtest=ChatControllerTest,ChatOrchestrationServiceTest test
```

预期：非法选择不调用 Skill；请求超时可查询结果；取消任务不可再次确认。

- [ ] **Step 5: 提交 REST 闭环**

```powershell
git add src/main/java/com/privateflow/modules/api/web/ChatController.java src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java
git commit -m "feat: add pending reply task APIs"
```

## Task 4: 前端任务契约和识别后的待选状态

**Files:**

- Create: `desktop/src/renderer/modules/reply-suggestions/pendingReplyTaskStore.ts`
- Create: `desktop/src/renderer/modules/reply-suggestions/pendingReplyTaskStore.test.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/types.ts`
- Modify: `desktop/src/renderer/modules/chat-recognition/types.ts`
- Modify: `desktop/src/renderer/modules/chat-recognition/recognitionStore.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.test.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.test.ts`

- [ ] **Step 1: 写多客户响应不显示回复的失败测试**

在 `recognitionStore.test.ts` 和 `replySuggestionStore.test.ts` 增加以下断言：

```ts
expect(events).toContainEqual(expect.objectContaining({
  type: 'recognize:multiple',
  payload: expect.objectContaining({
    sessionId: 'reply-100-1',
    taskId: 'task-1'
  })
}));
expect(reply.activeReplySession.value?.status).toBe('MULTIPLE');
expect(reply.activeReplySession.value?.suggestions).toEqual([]);
```

预期：旧响应没有 `taskId`，并且旧流程可能已经产生了错误客户回复。

- [ ] **Step 2: 增加统一的 TypeScript DTO**

在 `reply-suggestions/types.ts` 定义：

```ts
export type PendingReplyTaskStatus =
  | 'WAITING_CUSTOMER' | 'GENERATING' | 'READY'
  | 'FAILED' | 'CANCELLED' | 'EXPIRED';

export type PendingReplyTask = {
  taskId: string;
  replySessionId: string;
  status: PendingReplyTaskStatus;
  candidates: ReplyCandidate[];
  selectedPhone?: string | null;
  response?: ChatResponse | null;
  errorCode?: string | null;
  expiresAt: string;
};
```

`ChatResponse` 增加可选 `pendingTask?: PendingReplyTask | null`。`ReplySession` 增加 `pendingTaskId` 和 `pendingTaskStatus`，默认空字符串和 `null`，以便本地会话在刷新后可被后端任务覆盖。

- [ ] **Step 3: 修改识别请求和多客户事件**

`recognitionStore.triggerRecognize()` 发出的 JSON 必须包含已生成的 `sessionId`：

```ts
const response = await postJson<ChatRecognizeResponse>('/api/v1/chat/recognize', {
  imageBase64: content.imageBase64,
  textMessage: content.textMessage,
  customerIdentifier: content.customerIdentifier,
  replySessionId: sessionId
});
```

收到 `MULTIPLE` 时，不发 `recognize:progress` 的“正在生成回复”，而发：

```ts
eventBus.emit('recognize:multiple', {
  sessionId,
  taskId: data.pendingTask?.taskId,
  candidates: data.pendingTask?.candidates ?? data.match?.customers ?? [],
  matchInfo: data.match
});
```

缺少 `pendingTask.taskId` 的多客户响应视为协议错误，停止加载并提示重新识别，不能退回旧的“先生成再选择”逻辑。

- [ ] **Step 4: 实现服务端任务恢复 Store**

`pendingReplyTaskStore.ts` 的核心函数必须是：

```ts
export async function refreshPendingReplyTasks(): Promise<void> {
  const response = await getJson<PendingReplyTask[]>('/api/v1/chat/reply-tasks', 5000);
  if (!response.success || !response.data) return;
  replacePendingTasks(response.data);
  response.data.forEach(syncTaskIntoReplySession);
}
```

`syncTaskIntoReplySession(task)` 的规则：

- `WAITING_CUSTOMER`：创建或更新同一 `replySessionId` 的 `MULTIPLE` 会话，保存 `taskId` 和候选，不调用生成接口。
- `GENERATING`：创建加载会话，文案为“正在生成回复”。
- `READY`：向原 `replySessionId` 发送 `recognize:result`，只展示服务端保存的 `response`，不重新生成。
- `FAILED`：保留已选客户、显示“重新生成”和“取消任务”。
- `CANCELLED`、`EXPIRED`：移除本地待选入口；过期显示“请重新识别聊天”。

- [ ] **Step 5: 运行识别和回复会话测试**

```powershell
npm test -- --run src/renderer/modules/chat-recognition/recognitionStore.test.ts src/renderer/modules/reply-suggestions/replySuggestionStore.test.ts src/renderer/modules/reply-suggestions/pendingReplyTaskStore.test.ts
```

预期：多个客户任务没有回复卡片；刷新后同一会话恢复；`READY` 只展示已保存结果。

- [ ] **Step 6: 提交前端任务状态**

```powershell
git add desktop/src/renderer/modules/chat-recognition desktop/src/renderer/modules/reply-suggestions
git commit -m "feat: restore pending reply task sessions"
```

## Task 5: 候选档案预览和明确确认

**Files:**

- Modify: `desktop/src/renderer/modules/customer-profile/types.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/customerProfileStore.test.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- Modify: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue`

- [ ] **Step 1: 写“查看不等于确认”的失败测试**

把现有“候选行点击即选择”测试替换为三段测试：

```ts
await profile.previewCandidate(summary('18800002222'));
expect(getJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800002222', expect.any(Number), expect.anything());
expect(postJsonMock).not.toHaveBeenCalledWith(expect.stringContaining('/reply-tasks/'), expect.anything());
expect(selected).toEqual([]);

await profile.confirmPreviewedCandidate();
expect(postJsonMock).toHaveBeenCalledWith(
  '/api/v1/chat/reply-tasks/task-1/confirm',
  { phone: '18800002222' }
);
```

预期：当前 `chooseCandidate()` 会立即关闭候选并触发 `customer:selected`，测试失败。

- [ ] **Step 2: 拆分候选状态和档案预览状态**

在 `customerProfileState` 增加：

```ts
candidateTaskId: '',
candidatePreviewPhone: '',
candidatePreviewing: false,
candidateConfirming: false,
```

把 `chooseCandidate` 拆为：

```ts
export async function previewCandidate(candidate: CustomerSummary): Promise<void>;
export function returnToCandidates(): void;
export async function confirmPreviewedCandidate(): Promise<void>;
export async function cancelCandidateTask(): Promise<void>;
```

`previewCandidate` 仅调用 `openProfile(..., { emitCustomerSelected: false })`；因此不得发送 `customer:selected`，也不得触发 `POST /api/v1/chat/generate`。

- [ ] **Step 3: 在档案预览中明确确认**

当 `candidateTaskId` 非空且当前档案手机号等于 `candidatePreviewPhone` 时，档案界面显示两个命令：

- “返回候选客户”：回到原候选列表，任务仍为 `WAITING_CUSTOMER`。
- “确认是此客户”：先发 `reply-task:generating` 让回复面板进入加载态，再调用确认 API；成功后发 `recognize:result`，失败后恢复候选入口或显示可重试错误。

确认实现固定为：

```ts
eventBus.emit('reply-task:generating', { sessionId, taskId, phone });
const response = await postJson<ChatResponse>(
  `/api/v1/chat/reply-tasks/${encodeURIComponent(taskId)}/confirm`,
  { phone }
);
if (response.success && response.data) {
  eventBus.emit('recognize:result', { sessionId, source: 'PENDING_REPLY_TASK', response: response.data });
}
```

不要发送 `customer:selected`。这条规则防止工作台、跟进清单和阶段建议模块把“查看候选档案”误解为原有的客户选择操作。

- [ ] **Step 4: 让回复面板接收确认加载事件**

`ReplySuggestionPanel.vue` 增加 `reply-task:generating` 订阅，调用一个接收 `{ sessionId, phone }` 的专用 `startPendingTaskGeneration()`。该函数只更新对应会话，不调用 HTTP；HTTP 调用只在客户档案模块的“确认是此客户”命令中发生。

- [ ] **Step 5: 运行候选和面板测试**

```powershell
npm test -- --run src/renderer/modules/customer-profile/customerProfileStore.test.ts src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts src/renderer/modules/reply-suggestions/ReplySuggestionPanel.test.ts
```

预期：预览不会生成；返回候选可查看另一人；确认第二个候选后结果出现在原回复会话；取消后任务消失。

- [ ] **Step 6: 提交预览/确认分离**

```powershell
git add desktop/src/renderer/modules/customer-profile desktop/src/renderer/modules/reply-suggestions
git commit -m "feat: require explicit customer confirmation for replies"
```

## Task 6: 登录恢复、桌面提醒和共享文件合并

**Files:**

- Modify: `desktop/src/renderer/App.vue`
- Modify: `desktop/src/renderer/App.test.ts`
- Modify: `desktop/src/main/main.ts`
- Modify: `desktop/src/preload/preload.cts`
- Modify: `desktop/src/renderer/shared/desktopBridge.ts`
- Modify: `desktop/src/renderer/shared/desktopBridge.test.ts`
- Modify: `desktop/src/renderer/types/desktop.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/pendingReplyTaskStore.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- Modify: `desktop/src/renderer/styles.css`

- [ ] **Step 1: 合并工作台基线后再触碰共享文件**

运行：

```powershell
git status --short --branch
$baseline = (Select-String -Path dev-progress/multi_customer_reply_resume_tasklist_20260722.md -Pattern '基线提交：`([0-9a-f]{40})`').Matches[0].Groups[1].Value
git merge-base --is-ancestor $baseline HEAD
```

预期：命令退出码为 `0`；如果不为 `0`，先把 workbench 提交合并/rebase 到当前 worktree，再继续。不得通过覆盖 `App.vue` 或 `styles.css` 解决冲突。

- [ ] **Step 2: 写认证恢复失败测试**

在 `App.test.ts` 模拟登录成功和访问令牌刷新成功，断言：

```ts
expect(refreshPendingReplyTasksMock).toHaveBeenCalledTimes(1);
```

再模拟未登录状态，断言不请求 `/api/v1/chat/reply-tasks`。

- [ ] **Step 3: 在已认证入口恢复任务**

在 `App.vue` 的 `login()` 成功路径和 `initializeAuthenticatedSession()` 成功路径中，在桌面状态刷新完成后调用：

```ts
await refreshPendingReplyTasks();
```

`refreshPendingReplyTasks` 内部吞掉网络错误并保留现有本地会话；401 必须交给既有登录失效处理，不能把任务读取失败伪装成空任务列表。

- [ ] **Step 4: 增加最小 Electron 通知桥接**

在 `main.ts` 从 Electron 导入 `Notification`，注册 IPC：

```ts
ipcMain.handle('reply-task:notify', (_event, payload: { taskId?: string; title?: string; body?: string }) => {
  if (!payload.taskId || !Notification.isSupported()) return { success: false };
  const notification = new Notification({
    title: payload.title || '请选择对应客户',
    body: payload.body || '识别到多个客户，请查看档案后确认。'
  });
  notification.on('click', () => {
    if (mainWindow?.isMinimized()) mainWindow.restore();
    mainWindow?.show();
    mainWindow?.focus();
    mainWindow?.webContents.send('reply-task:open', { taskId: payload.taskId });
  });
  notification.show();
  return { success: true };
});
```

在 preload、`desktopBridge.ts` 和 `desktop.ts` 同时增加 `notifyReplyTask`、`onReplyTaskOpen`，并使用可注销监听器。网页模式返回 no-op，不能依赖 Electron 才能选择客户。

- [ ] **Step 5: 只在用户不在助手窗口时提醒**

任务进入 `WAITING_CUSTOMER` 时，若 `document.hasFocus()` 为 `false`，调用 `notifyReplyTask(task)`；若窗口在前台，则直接打开候选列表，不重复弹系统通知。通知点击后由 `onReplyTaskOpen` 调用 `openRecoveredReplyTask(taskId)`，从任务 store 找到任务并打开对应候选。

- [ ] **Step 6: 补充最小、隔离的样式**

只新增以下用途的类，并避免修改工作台现有选择器：

```css
.pending-reply-task-count {}
.candidate-preview-actions {}
.candidate-confirm-action {}
```

要求：窄窗口 360px 下按钮不溢出；“查看档案”“返回候选客户”“确认是此客户”文本完整可读；确认按钮在预览中始终可见且不会与档案编辑按钮重叠。

- [ ] **Step 7: 运行桥接、应用和窄窗口测试**

```powershell
npm test -- --run src/renderer/App.test.ts src/renderer/shared/desktopBridge.test.ts src/renderer/modules/reply-suggestions/pendingReplyTaskStore.test.ts
npm run typecheck
```

预期：登录恢复一次，Electron 桥接可安全降级，TypeScript 无错误。

- [ ] **Step 8: 提交恢复和通知**

```powershell
git add desktop/src/main/main.ts desktop/src/preload/preload.cts desktop/src/renderer/App.vue desktop/src/renderer/App.test.ts desktop/src/renderer/shared/desktopBridge.ts desktop/src/renderer/shared/desktopBridge.test.ts desktop/src/renderer/types/desktop.ts desktop/src/renderer/modules/reply-suggestions desktop/src/renderer/modules/customer-profile desktop/src/renderer/styles.css
git commit -m "feat: restore and notify pending reply tasks"
```

## Task 7: 契约回填、全量验证和人工验收

**Files:**

- Modify: `SHARED_CONTRACTS.md`
- Modify: `DEPENDENCIES.md`
- Modify: `decisions.md`
- Modify: `questions.md`
- Modify: `20_桌面A_聊天识别_开发实现手册.md`
- Modify: `21_桌面B_回复建议面板_开发实现手册.md`
- Modify: `23_桌面D_客户搜索与档案卡_开发实现手册.md`
- Modify: `dev-progress/multi_customer_reply_resume_tasklist_20260722.md`
- Create: `dev-progress/manual-tests/multi_customer_reply_resume_20260722.md`

- [ ] **Step 1: 回填公共契约**

在 `SHARED_CONTRACTS.md` 登记：

- 5 个 `/api/v1/chat/reply-tasks` 路由。
- `PendingReplyTaskStatus` 六个值。
- 两项 `chat.*` 配置及范围。
- `pending_reply_tasks` 和 `pending_reply_task_candidates` 两张表。
- 新事件 `reply-task:generating`，payload 为 `{ sessionId, taskId, phone }`。
- `recognize:multiple` 扩展字段 `taskId`。
- 本次使用的错误码和审计动作。

在 `DEPENDENCIES.md` 明确：桌面 A 触发多客户任务，桌面 D 预览/确认，桌面 B 管理同会话的等待和回复展示；不得把它写成工作台模块依赖。

在 `decisions.md` 追加已确认决策：多客户必须明确确认；查看档案不等于确认；等待任务保留 24 小时；原始截图不保存；通知只提醒不自动选择。

`questions.md` 不新增业务问题。两项时限已经确认，若实现时遇到新的业务规则才追加。

- [ ] **Step 2: 写真实人工验收清单**

创建 `dev-progress/manual-tests/multi_customer_reply_resume_20260722.md`，逐项记录截图、任务编号、候选手机号后四位、实际状态和结果：

```markdown
- [ ] 微信截图：唯一客户直接生成。
- [ ] 企业微信截图：同昵称两人，不调用 Skill/LLM，直接出现候选。
- [ ] 抖音网页截图：先查看两个档案，未确认前无回复结果。
- [ ] 确认第二位候选：回复使用第二位档案，原回复会话展示结果。
- [ ] 关闭并重启桌面端：任务仍在，点击提醒可回到候选。
- [ ] 生成期间关闭界面：重新打开后 READY 任务展示保存结果，不重复生成。
- [ ] 过期任务：提示重新识别，不允许用旧聊天文字生成。
- [ ] 客户删除或权限撤销：返回候选，不选择第一位。
```

- [ ] **Step 3: 全量自动化验证**

从后端根目录运行：

```powershell
mvn test
```

从 `desktop` 目录运行：

```powershell
npm run typecheck
npm test
```

预期：所有测试通过。若 Maven 不在 PATH，记录缺失工具并使用团队标准 Maven 运行时；不要把“命令无法启动”记为测试失败。

- [ ] **Step 4: 完成真实桌面端验收**

启动后端、前端和 Electron，按人工清单完成真实微信、企业微信和抖音网页截图。只要任一项未完成，就在任务清单中保留未完成状态，不能用模拟文字识别代替真实截图验收。

- [ ] **Step 5: 更新中断恢复信息并提交**

在任务清单写入最后完成任务、实际执行命令、测试结果、人工验收链接和下一条待办。提交：

```powershell
git add SHARED_CONTRACTS.md DEPENDENCIES.md decisions.md questions.md 20_桌面A_聊天识别_开发实现手册.md 21_桌面B_回复建议面板_开发实现手册.md 23_桌面D_客户搜索与档案卡_开发实现手册.md dev-progress/multi_customer_reply_resume_tasklist_20260722.md dev-progress/manual-tests/multi_customer_reply_resume_20260722.md
git commit -m "docs: document pending reply task flow"
```

## 中断后的恢复规则

每次上下文压缩、桌面端关闭、模型切换或新 AI 接手时，禁止根据聊天记忆猜测进度。按下面顺序恢复：

1. 运行 `git status --short --branch`，确认当前 worktree、分支和未提交文件。
2. 阅读本计划、设计文档和 `dev-progress/multi_customer_reply_resume_tasklist_20260722.md`。
3. 检查任务清单中的“当前任务”和“最近验证”。
4. 读取该任务指定的生产文件和测试文件，确认调用关系仍与计划一致；如果工作台已改动共享文件，以工作台的已提交版本为基线合并，不回退用户改动。
5. 从第一个未勾选步骤开始，先运行该步骤的失败测试或验证命令。
6. 每完成一个任务，立刻更新任务清单的复选框、提交哈希和测试证据，再进入下一任务。
7. 若发现需求与本计划冲突，停止实现，在设计文档新增一条明确决策后再继续；不要私自把“查看档案”改回“点击即确认”，也不要让多客户重新提前调用 Skill/LLM。

## 完成定义

只有同时满足以下条件才算完成：

1. 多客户识别时后端测试证明没有调用 Skill 或回复 LLM。
2. 员工可以查看多个完整档案，预览不会生成回复。
3. 明确确认一个客户后，系统自动在同一会话生成并展示回复。
4. 忘记处理、刷新、重新登录和重启桌面端后，待确认任务仍可恢复。
5. 重复确认、网络超时、客户删除、权限变化、生成失败和服务中断都有可验证的正确结果。
6. 原始截图没有进入数据库、Redis、文件或日志。
7. 后端全量、前端类型检查、前端全量测试和真实截图人工验收都有最新证据。
