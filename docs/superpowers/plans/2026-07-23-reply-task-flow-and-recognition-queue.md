# 回复任务流与识图队列 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将回复助手改为适合多人并发咨询的紧凑任务流：员工可连续提交截图，服务器默认并行识图 4 条，员工始终能从左侧任务区找到并切换最近任务，而不会保存或长期占用原始截图。

**Architecture:** 前端以本地回复会话作为员工临时任务清单，并严格执行未处理 20 条、最近 30 条的容量策略；后端新增仅在进程内存和受控临时目录中存在的识图作业调度器，完成、失败、取消或超过十分钟即删除原图。该计划依赖 `2026-07-23-supervisor-governance-and-metrics.md` 已实现的 `SupervisionEventService` 和 `/api/v1/chat/ai-usage`，以确保复制只有一个语义来源。

**Tech Stack:** Vue 3、TypeScript、Vitest、Electron IPC、Spring Boot、Spring TaskExecutor、JdbcTemplate、JUnit 5、MockMvc。

---

## File Structure

| 文件 | 责任 |
| --- | --- |
| `src/main/resources/db/migration/V77__recognition_queue_config.sql` | 增加识图临时目录与上限配置，不新增任何截图业务表。 |
| `src/main/java/com/privateflow/modules/api/chat/RecognitionJobStatus.java` | 识图作业状态：`QUEUED`、`RECOGNIZING`、`READY`、`WAITING_CUSTOMER`、`FAILED`、`CANCELLED`、`EXPIRED`。 |
| `src/main/java/com/privateflow/modules/api/chat/RecognitionJobView.java` | 员工轮询所需的安全作业视图，不含图片。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatTaskRuntimeConfigResponse.java` | 员工端仅读取的任务容量配置响应。 |
| `src/main/java/com/privateflow/modules/api/chat/TemporaryRecognitionImageStore.java` | 压缩后 JPEG 临时文件的写入、读取、立即删除和十分钟清理。 |
| `src/main/java/com/privateflow/modules/api/chat/RecognitionJobService.java` | 每员工容量检查、FIFO 排队、全局并发限制、轮询和取消。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatRecognitionExecutorConfiguration.java` | 有界识图线程池；线程数热更新通过许可闸门生效。 |
| `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java` | 将同步识图核心提取为作业可调用的服务，不再由 HTTP 请求直接阻塞至模型完成。 |
| `src/main/java/com/privateflow/modules/api/web/ChatController.java` | 提供识图作业提交、查询、取消 API；保留旧 `/recognize` 只供文字通道兼容。 |
| `desktop/src/renderer/modules/chat-recognition/recognitionStore.ts` | 创建本地任务后提交作业、轮询状态、将结果送入回复会话。 |
| `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts` | 统一任务状态、20/30 修剪、当前任务切换和已复制状态。 |
| `desktop/src/renderer/modules/reply-suggestions/ReplyTaskSidebar.vue` | 左侧最近 5 条回复任务和“全部任务”入口。 |
| `desktop/src/renderer/modules/reply-suggestions/ReplyTaskDrawer.vue` | 最近任务完整抽屉、昵称搜索、状态筛选和安全清理已完成任务。 |
| `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue` | 当前任务移动到推荐回复上方，移除重复话术、重复复制按钮和大型队列。 |
| `desktop/src/renderer/App.vue` | 在“批量”和“后台”间挂载回复任务区，不复用跟进待办抽屉。 |
| `desktop/src/renderer/modules/copy-backfill/copyBackfillStore.ts` | 复制后调用 AI 使用记录，而非发送确认。 |

## Task State Contract

| 显示状态 | 前端会话状态 | 后端识图状态 | 是否计入未处理 20 条 |
| --- | --- | --- | --- |
| 排队中 | `QUEUED` | `QUEUED` | 是 |
| 识别中 | `LOADING` | `RECOGNIZING` | 是 |
| 待选择 | `MULTIPLE` | `WAITING_CUSTOMER` | 是 |
| 可复制 | `READY` 或 `FALLBACK` | `READY` | 是 |
| 已复制 | `COPIED` | `READY` | 否 |
| 失败 | `FAILED` | `FAILED` | 否 |

当前激活任务无论完成与否都不被最近 30 条修剪；修剪时只能移除最早的 `COPIED` 或 `FAILED` 非当前任务。若没有可移除任务，拒绝新识图请求，绝不静默丢弃未处理任务。失败后的“重新识别”必须重新捕获当前聊天，不能复用已经删除的截图。

### Task 1: 识图配置、临时文件生命周期与测试

**Files:**
- Create: `src/main/resources/db/migration/V77__recognition_queue_config.sql`
- Create: `src/main/java/com/privateflow/modules/api/chat/TemporaryRecognitionImageStore.java`
- Create: `src/test/java/com/privateflow/modules/api/chat/TemporaryRecognitionImageStoreTest.java`
- Modify: `src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java`
- Modify: `src/test/java/com/privateflow/modules/api/config/ConfigAdminServiceTest.java`

- [ ] **Step 1: 写出临时截图删除的失败测试**

```java
@Test
void deletesImageAfterSuccessfulReadAndLeavesNoBusinessRecord() throws Exception {
  String token = store.put(jpegBytes("customer chat"));
  assertThat(store.read(token)).isEqualTo(jpegBytes("customer chat"));
  store.delete(token);
  assertThat(store.exists(token)).isFalse();
  assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_reply_tasks", Long.class)).isZero();
}

@Test
void removesExpiredFilesAfterTenMinutes() {
  store.cleanupExpired(Instant.parse("2026-07-23T10:10:00Z"));
  assertThat(store.exists("expired-token")).isFalse();
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./mvnw.cmd -Dtest=TemporaryRecognitionImageStoreTest,ConfigAdminServiceTest test`

Expected: FAIL，提示临时存储与 `chat.recognition_temp_*` 配置不存在。

- [ ] **Step 3: 实现受限临时存储和配置**

```sql
INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('chat.recognition_temp_root', 'uploads/temporary-recognition', 'temporary image directory'),
  ('chat.recognition_temp_ttl_seconds', '600', 'temporary screenshot lifetime, fixed range 60-600'),
  ('chat.recognition_temp_max_total_bytes', '104857600', 'temporary screenshot total cap, range 10485760-524288000')
ON DUPLICATE KEY UPDATE description = VALUES(description);
```

```java
public String put(byte[] jpegBytes) {
  requireWithinImageLimit(jpegBytes.length);
  requireWithinTotalCapacity(jpegBytes.length);
  String token = UUID.randomUUID().toString();
  Files.write(pathFor(token), jpegBytes, StandardOpenOption.CREATE_NEW);
  createdAt.put(token, clock.instant());
  return token;
}
```

路径必须由不可预测 UUID 生成并经 `normalize().startsWith(root)` 校验；单图沿用 `ImageConfig.maxSizeBytes()` 默认 5MB，累计上限默认 100MB；写入前使用现有图像压缩逻辑输出 JPEG，任何异常都删除已写入的局部文件。

- [ ] **Step 4: 运行临时存储测试**

Run: `./mvnw.cmd -Dtest=TemporaryRecognitionImageStoreTest,ConfigAdminServiceTest test`

Expected: PASS，且断言原图不进入 `pending_reply_tasks`、监督事件或日志表。

- [ ] **Step 5: 提交临时截图基础**

```bash
git add src/main/resources/db/migration/V77__recognition_queue_config.sql src/main/java/com/privateflow/modules/api/chat/TemporaryRecognitionImageStore.java src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java src/test/java/com/privateflow/modules/api/chat/TemporaryRecognitionImageStoreTest.java src/test/java/com/privateflow/modules/api/config/ConfigAdminServiceTest.java
git commit -m "feat: add temporary recognition image storage"
```

### Task 2: FIFO 调度、全局并发和 HTTP 作业契约

**Files:**
- Create: `src/main/java/com/privateflow/modules/api/chat/RecognitionJobStatus.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/RecognitionJobView.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/ChatTaskRuntimeConfigResponse.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/RecognitionJobService.java`
- Create: `src/main/java/com/privateflow/modules/api/chat/ChatRecognitionExecutorConfiguration.java`
- Modify: `src/main/java/com/privateflow/modules/api/web/ChatController.java`
- Test: `src/test/java/com/privateflow/modules/api/chat/RecognitionJobServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java`

- [ ] **Step 1: 写出 FIFO 和容量拒绝的失败测试**

```java
@Test
void runsOnlyFourJobsAndStartsTheFifthAfterTheFirstCompletes() {
  List<String> ids = submitJobs(5, "keeper-a");
  assertThat(worker.startedIds()).containsExactly(ids.get(0), ids.get(1), ids.get(2), ids.get(3));
  worker.complete(ids.get(0));
  assertThat(worker.startedIds()).containsExactlyElementsOf(ids);
}

@Test
void rejectsNewJobWhenEmployeeHasTwentyUnfinishedTasks() {
  submitJobs(20, "keeper-a");
  assertThatThrownBy(() -> service.submit("keeper-a", request()))
      .hasMessageContaining("回复任务已满");
}
```

- [ ] **Step 2: 运行调度测试确认失败**

Run: `./mvnw.cmd -Dtest=RecognitionJobServiceTest,ChatControllerTest test`

Expected: FAIL，提示作业服务和 `POST /recognition-jobs` 不存在。

- [ ] **Step 3: 实现有界作业服务**

```java
@PostMapping("/recognition-jobs")
public ApiResponse<RecognitionJobView> submitRecognitionJob(@RequestBody ChatRecognizeRequest request) {
  return ApiResponse.ok(recognitionJobService.submit(AuthContext.username(), request));
}

@GetMapping("/recognition-jobs/{jobId}")
public ApiResponse<RecognitionJobView> getRecognitionJob(@PathVariable String jobId) {
  return ApiResponse.ok(recognitionJobService.getOwned(jobId, AuthContext.username()));
}

@GetMapping("/task-runtime-config")
public ApiResponse<ChatTaskRuntimeConfigResponse> taskRuntimeConfig() {
  return ApiResponse.ok(new ChatTaskRuntimeConfigResponse(
      supervisionConfig.unfinishedTaskCap(), supervisionConfig.recentTaskDisplayCap()));
}
```

`RecognitionJobService` 以单个 FIFO `ArrayDeque` 保存已接受作业；每次提交和每次完成后调用 `drainQueue()`，仅当 `running < supervisionConfig.recognitionConcurrency()` 时取队首执行。作业输入只保存临时图片 token、用户名、已裁剪的 `ChatRecognizeRequest` 元数据和 `replySessionId`；`RecognitionJobView` 只返回状态、错误码、`ChatResponse`、`PendingReplyTaskView`、创建/更新时间。取消排队作业立即删临时图并标为 `CANCELLED`；运行中作业不可中止底层 HTTP，但完成后不发布结果且立即删图。

- [ ] **Step 4: 运行并发、所有权和 API 测试**

Run: `./mvnw.cmd -Dtest=RecognitionJobServiceTest,ChatControllerTest test`

Expected: PASS，包括第五条不抢占前四条、非本人查询返回冲突或禁止访问、队满提示明确。

- [ ] **Step 5: 提交调度服务**

```bash
git add src/main/java/com/privateflow/modules/api/chat/RecognitionJobStatus.java src/main/java/com/privateflow/modules/api/chat/RecognitionJobView.java src/main/java/com/privateflow/modules/api/chat/ChatTaskRuntimeConfigResponse.java src/main/java/com/privateflow/modules/api/chat/RecognitionJobService.java src/main/java/com/privateflow/modules/api/chat/ChatRecognitionExecutorConfiguration.java src/main/java/com/privateflow/modules/api/web/ChatController.java src/test/java/com/privateflow/modules/api/chat/RecognitionJobServiceTest.java src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java
git commit -m "feat: queue concurrent recognition jobs"
```

### Task 3: 将识图主链接入作业并保证清理

**Files:**
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/RecognitionJobService.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskService.java`
- Test: `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/api/chat/RecognitionJobServiceTest.java`

- [ ] **Step 1: 写出成功、失败、取消和超时均删除原图的失败测试**

```java
@ParameterizedTest
@ValueSource(strings = { "SUCCESS", "FAILURE", "CANCELLED", "EXPIRED" })
void alwaysDeletesTemporaryImage(String outcome) {
  String jobId = service.submit("keeper-a", imageRequest()).jobId();
  worker.finish(jobId, outcome);
  assertThat(imageStore.exists(tokenFor(jobId))).isFalse();
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./mvnw.cmd -Dtest=RecognitionJobServiceTest,ChatOrchestrationServiceTest test`

Expected: FAIL，当前同步 `recognize` 尚未由作业服务调用。

- [ ] **Step 3: 提取无 HTTP 语义的识图核心并接入作业**

```java
ChatResponse recognizeForJob(ChatRecognizeRequest request) {
  RecognitionResult recognized = recognizeImage(request.imageBase64());
  return recognizeMatchedConversation(request, recognized);
}

private void runJob(RecognitionJob job) {
  try {
    job.markRecognizing();
    ChatResponse response = orchestrationService.recognizeForJob(withImage(job));
    job.complete(response, response.pendingTask() == null ? READY : WAITING_CUSTOMER);
  } catch (ApiException ex) {
    job.fail(ex.getErrorCode(), ex.getMessage());
  } finally {
    imageStore.delete(job.imageToken());
    drainQueue();
  }
}
```

`recognizeForJob` 复用现有客户匹配、Skill、LLM、`PendingReplyTaskService` 逻辑，不改动文字通道的同步 `/recognize` 行为。`finally` 是唯一允许释放临时图片的出口；定时清理将超过十分钟尚未运行的作业置为 `EXPIRED`、删图并返回可理解的错误文案。

- [ ] **Step 4: 运行主链和清理测试**

Run: `./mvnw.cmd -Dtest=RecognitionJobServiceTest,ChatOrchestrationServiceTest,PendingReplyTaskServiceTest test`

Expected: PASS；多客户匹配仍只创建不含截图的 `pending_reply_tasks`。

- [ ] **Step 5: 提交主链接入**

```bash
git add src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java src/main/java/com/privateflow/modules/api/chat/RecognitionJobService.java src/main/java/com/privateflow/modules/api/chat/PendingReplyTaskService.java src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java src/test/java/com/privateflow/modules/api/chat/RecognitionJobServiceTest.java
git commit -m "feat: execute chat recognition through bounded jobs"
```

### Task 4: 前端任务模型、20/30 修剪与轮询

**Files:**
- Modify: `desktop/src/renderer/modules/reply-suggestions/types.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts`
- Modify: `desktop/src/renderer/modules/chat-recognition/recognitionStore.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/pendingReplyTaskStore.ts`
- Test: `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.test.ts`
- Test: `desktop/src/renderer/modules/chat-recognition/recognitionStore.test.ts`

- [ ] **Step 1: 写出容量和状态转换的失败测试**

```ts
it('keeps 20 unfinished sessions and rejects the 21st without deleting one', () => {
  createSessions(20, 'QUEUED');
  expect(replies.canCreateReplyTask()).toBe(false);
  expect(replies.replySuggestionState.sessions).toHaveLength(20);
});

it('keeps the active task while trimming the oldest copied session after the 31st record', () => {
  createCopiedSessions(30);
  replies.activateSession('copied-1');
  replies.createQueuedRecognitionSession({ sessionId: 'queued-31' });
  expect(replies.replySuggestionState.sessions.find((item) => item.sessionId === 'copied-1')).toBeTruthy();
  expect(replies.replySuggestionState.sessions).toHaveLength(30);
});
```

- [ ] **Step 2: 运行前端状态测试确认失败**

Run: `npm run test -- --run replySuggestionStore.test.ts recognitionStore.test.ts`

Expected: FAIL，`QUEUED`、容量检查和作业轮询尚未实现。

- [ ] **Step 3: 实现本地任务状态和轮询**

```ts
export type ReplySessionStatus = 'QUEUED' | 'LOADING' | 'READY' | 'FAILED' | 'FALLBACK' | 'COPIED' | 'MULTIPLE';

export function canCreateReplyTask(): boolean {
  return unfinishedSessions().length < runtimeTaskCaps.unfinishedCap;
}

async function pollRecognitionJob(jobId: string, sessionId: string): Promise<void> {
  const response = await getJson<RecognitionJobView>(`/api/v1/chat/recognition-jobs/${encodeURIComponent(jobId)}`, 5000);
  applyRecognitionJob(sessionId, response.data);
  if (response.data && ['QUEUED', 'RECOGNIZING'].includes(response.data.status)) schedulePoll(jobId, sessionId);
}
```

运行时上限由登录后请求的受控 `GET /api/v1/chat/task-runtime-config` 获得，只读使用 `unfinishedTaskCap` 和 `recentTaskDisplayCap`；员工端没有任何编辑入口。提交截图时先创建 `QUEUED` 会话，再调用 `POST /recognition-jobs`；提交失败恢复为 `FAILED` 并保留明确原因。删除/清理操作只能针对 `COPIED`、`FAILED`，不能移除 `QUEUED`、`LOADING`、`MULTIPLE`、`READY` 或 `FALLBACK`。

- [ ] **Step 4: 运行任务状态和轮询测试**

Run: `npm run test -- --run replySuggestionStore.test.ts recognitionStore.test.ts`

Expected: PASS。

- [ ] **Step 5: 提交前端任务状态**

```bash
git add desktop/src/renderer/modules/reply-suggestions/types.ts desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts desktop/src/renderer/modules/chat-recognition/recognitionStore.ts desktop/src/renderer/modules/reply-suggestions/pendingReplyTaskStore.ts desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.test.ts desktop/src/renderer/modules/chat-recognition/recognitionStore.test.ts
git commit -m "feat: bound local reply task sessions"
```

### Task 5: 当前任务优先、左侧快捷任务和完整任务抽屉

**Files:**
- Create: `desktop/src/renderer/modules/reply-suggestions/ReplyTaskSidebar.vue`
- Create: `desktop/src/renderer/modules/reply-suggestions/ReplyTaskDrawer.vue`
- Create: `desktop/src/renderer/modules/reply-suggestions/ReplyTaskSidebar.test.ts`
- Create: `desktop/src/renderer/modules/reply-suggestions/ReplyTaskDrawer.test.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.test.ts`
- Modify: `desktop/src/renderer/App.vue`
- Modify: `desktop/src/renderer/App.test.ts`

- [ ] **Step 1: 写出布局和切换的失败组件测试**

```ts
expect(wrapper.find('[data-testid="current-reply-task"]').element.compareDocumentPosition(
  wrapper.find('[data-testid="reply-suggestion-list"]').element
)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
expect(wrapper.text()).not.toContain('队列与历史');

await sidebar.get('[data-testid="reply-task-row-reply-2"]').trigger('click');
expect(replies.replySuggestionState.activeSessionId).toBe('reply-2');
```

- [ ] **Step 2: 运行组件测试确认失败**

Run: `npm run test -- --run ReplySuggestionPanel.test.ts ReplyTaskSidebar.test.ts ReplyTaskDrawer.test.ts App.test.ts`

Expected: FAIL，任务组件和测试标识不存在。

- [ ] **Step 3: 实现紧凑任务界面**

```vue
<section data-testid="current-reply-task" class="current-reply-task">
  <strong>{{ currentTask.nickname || '未识别客户' }}</strong>
  <span>{{ currentTask.maskedIdentity }} · {{ currentTask.statusLabel }}</span>
  <small>{{ currentTask.sourceLabel }} · {{ currentTask.timeLabel }}</small>
</section>
<section data-testid="reply-suggestion-list" class="reply-suggestion-list">...</section>
```

`ReplyTaskSidebar` 只渲染最近 5 条，每条只显示昵称和状态；挂载到 `App.vue` 的“批量”按钮后、“后台”按钮前。`ReplyTaskDrawer` 默认显示最近上限条数，提供昵称 `input`、状态 `select`、点击切换、失败任务的“重新识别当前聊天”、已完成任务的“清理已完成”。不得复用 `taskQueueOpen`，它仍是跟进/批量队列。`ReplySuggestionPanel` 删除旧的可滚动会话队列、当前任务中的首条推荐和重复复制按钮；建议卡保留唯一的复制按钮与保存模板入口。

- [ ] **Step 4: 运行组件和视觉稳定性测试**

Run: `npm run test -- --run ReplySuggestionPanel.test.ts ReplyTaskSidebar.test.ts ReplyTaskDrawer.test.ts App.test.ts`

Expected: PASS；按钮文字在固定宽度中换行或省略，不溢出容器。

- [ ] **Step 5: 提交员工任务界面**

```bash
git add desktop/src/renderer/modules/reply-suggestions/ReplyTaskSidebar.vue desktop/src/renderer/modules/reply-suggestions/ReplyTaskDrawer.vue desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue desktop/src/renderer/App.vue desktop/src/renderer/modules/reply-suggestions/*.test.ts desktop/src/renderer/App.test.ts
git commit -m "feat: move reply tasks into compact sidebar"
```

### Task 6: 复制语义与现有模板复制回归

**Files:**
- Modify: `desktop/src/renderer/modules/copy-backfill/copyBackfillStore.ts`
- Modify: `desktop/src/renderer/modules/copy-backfill/copyBackfillStore.test.ts`
- Modify: `desktop/src/renderer/modules/quick-search/quickSearchStore.ts`
- Modify: `desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue`
- Modify: `desktop/src/renderer/modules/quick-search/quickSearchStore.test.ts`

- [ ] **Step 1: 写出复制不发送的失败测试**

```ts
await handleReplySelected({ text: '建议回复', phone: '13800000001', direction: 'NEXT_STEP', isFallback: false });
expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/ai-usage', expect.objectContaining({ copiedText: '建议回复' }), undefined, expect.anything());
expect(postJsonMock).not.toHaveBeenCalledWith('/api/v1/chat/send-confirm', expect.anything());
```

- [ ] **Step 2: 运行复制测试确认失败**

Run: `npm run test -- --run copyBackfillStore.test.ts quickSearchStore.test.ts`

Expected: FAIL，当前代码仍请求 `/send-confirm` 并显示“已发送”。

- [ ] **Step 3: 改为 AI 使用记录，不产生档案副作用**

```ts
await postJson('/api/v1/chat/ai-usage', {
  phone: payload.phone,
  taskId: activeReplySession.value?.pendingTaskId || '',
  replySessionId: activeReplySession.value?.sessionId || '',
  replySource: activeReplySession.value?.replySource?.source || 'UNKNOWN',
  copiedText: payload.text
}, undefined, controller.signal);
copyBackfillState.toast = '已复制到剪贴板，已记录 AI 使用';
```

删除 `reply:send-confirmed`、`followup:completed` 和所有“已发送/跟进记录失败”文案。`quickSearchStore` 的现有模板复制也只记录 `TEMPLATE_COPIED` 使用事件，移除“已发送/未发送”按钮，不调用 `/send-confirm`。复制 API 失败不得回滚剪贴板成功，只显示“已复制，使用记录稍后同步”。

- [ ] **Step 4: 运行复制与模板回归测试**

Run: `npm run test -- --run copyBackfillStore.test.ts quickSearchStore.test.ts QuickSearchOverlay.test.ts`

Expected: PASS，前端请求中没有 `/api/v1/chat/send-confirm`。

- [ ] **Step 5: 提交复制语义修正**

```bash
git add desktop/src/renderer/modules/copy-backfill desktop/src/renderer/modules/quick-search
git commit -m "fix: treat copied replies as AI usage only"
```

### Task 7: 端到端验证与桌面人工验收

**Files:**
- Modify: `docs/superpowers/specs/2026-07-23-reply-workflow-supervision-templates-design.md`
- Create: `docs/manual-tests/reply-recognition-queue-acceptance.md`

- [ ] **Step 1: 写入可复现人工验收步骤**

```markdown
1. 连续点击识图 5 次，确认前 4 条显示识别中、第 5 条显示排队中。
2. 在左侧最近任务中切换任意一条，确认回复助手顶部任务同步变化。
3. 连续保留 20 条未处理任务后再次识图，确认显示队满且原 20 条仍在。
4. 复制建议后确认状态为已复制，客户档案的跟进状态和最后跟进时间不变。
5. 等待或取消任务后检查临时识图目录，不存在对应 JPEG。
```

- [ ] **Step 2: 运行前后端完整自动测试**

Run: `./mvnw.cmd test`

Run: `npm run test -- --run`

Expected: 两套测试均通过；若受环境影响无法跑完整套，记录具体失败命令和报告文件后先修复或明确阻塞。

- [ ] **Step 3: 启动桌面应用并执行人工验收**

Run: `npm run electron:dev`

Expected: 应用可登录、侧栏任务区位于“批量”和“后台”之间、完整任务抽屉可搜索且没有文字溢出。

- [ ] **Step 4: 记录验收结果和截图位置**

在 `docs/manual-tests/reply-recognition-queue-acceptance.md` 中逐项写 `PASS`、实际结果、发现问题的复现步骤和截图绝对路径；不得用“整体正常”替代逐项结果。

- [ ] **Step 5: 提交验收记录**

```bash
git add docs/manual-tests/reply-recognition-queue-acceptance.md docs/superpowers/specs/2026-07-23-reply-workflow-supervision-templates-design.md
git commit -m "docs: record reply queue acceptance"
```

## Self-Review

| 已确认需求 | 覆盖任务 |
| --- | --- |
| 当前任务在推荐前、无重复话术/复制 | Task 5 |
| 左侧 5 条、全部任务、搜索与状态筛选 | Task 5 |
| 未处理 20、最近 30、不静默删除 | Task 4、Task 5 |
| 默认 4 并发、FIFO、明确满载拒绝 | Task 2、Task 3 |
| 原图压缩、最多十分钟、成功失败取消超时都删除 | Task 1、Task 3 |
| 复制只算 AI 使用，不改变跟进或声称发送 | Task 6 |
| 既有多客户匹配和文字通道不回归 | Task 3、Task 7 |

已检查：本计划沿用 `ChatRecognizeRequest`、`ChatResponse`、`PendingReplyTask` 和既有跟进抽屉的真实边界；不把回复任务混入 `taskQueueOpen`；所有配置键与主管治理计划一致；每项实现前均有失败测试和精确验证命令。
