# Reply Tag Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make first-generation, recognized-chat, and regenerate reply requests read the latest effective customer tags and pass one shared Chinese tag snapshot to both Skill and direct LLM reply paths without blocking replies when tag infrastructure fails.

**Architecture:** Add a small immutable reply-tag contract in the Skill request layer and a dedicated builder that joins the unified current-tag query with the cached tag directory. `ChatOrchestrationService` owns access-aware loading and degradation, then passes the same snapshot through `SkillRequest` to both `SkillRequestBuilder` and `LlmReplyGenerationService`. Regenerate rebuilds the request from the latest customer and tags instead of reusing the prior snapshot.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring JDBC, JUnit 5, Mockito, AssertJ, Maven, Vue/Vitest smoke verification.

---

## File Map

- Create `src/main/java/com/privateflow/modules/skill/ReplyTagSnapshot.java`: immutable reply-safe tag context.
- Modify `src/main/java/com/privateflow/modules/skill/SkillRequest.java`: add compatible `currentTags` field.
- Create `src/main/java/com/privateflow/modules/api/chat/ReplyTagSnapshotBuilder.java`: join current assignments with directory metadata and reply-use policy.
- Create `src/test/java/com/privateflow/modules/api/chat/ReplyTagSnapshotBuilderTest.java`: builder behavior and filtering.
- Modify `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`: load, degrade, refresh on regenerate, and persist the exact generated request.
- Modify `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`: initial load, fresh regenerate, and degradation tests.
- Modify `src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java`: emit `current_tags` and fixed non-disclosure guidance.
- Modify `src/test/java/com/privateflow/modules/skill/service/SkillRequestBuilderTest.java`: Skill payload assertions.
- Modify `src/main/java/com/privateflow/modules/llm/LlmReplyGenerationService.java`: emit `currentTags` and fixed non-disclosure guidance.
- Modify `src/test/java/com/privateflow/modules/llm/LlmReplyGenerationServiceTest.java`: LLM payload assertions.
- Modify `dev-progress/tag_skill_llm_tasklist_056.md`: mark Step 8 complete after verification.
- Create `dev-progress/tag_skill_llm_step8_breakpoint_061.md`: Step 8 recovery point.

### Task 1: Add The Reply Tag Snapshot Contract

**Files:**
- Create: `src/main/java/com/privateflow/modules/skill/ReplyTagSnapshot.java`
- Modify: `src/main/java/com/privateflow/modules/skill/SkillRequest.java`
- Test: `src/test/java/com/privateflow/modules/skill/SkillRequestTest.java`

- [ ] **Step 1: Write the failing compatibility and immutability tests**

Create `SkillRequestTest` with these cases:

```java
@Test
void legacyConstructorUsesEmptyReplyTags() {
  SkillRequest request = new SkillRequest(
      Scene.ACTIVE_REPLY, "TUAN_GOU", "18800001111", "hello",
      Map.of(), Map.of(), List.of(), List.of(), "keeper");

  assertThat(request.currentTags()).isEmpty();
}

@Test
void canonicalConstructorCopiesReplyTags() {
  List<ReplyTagSnapshot> tags = new ArrayList<>();
  tags.add(tag("LOYALIST"));
  SkillRequest request = new SkillRequest(
      Scene.ACTIVE_REPLY, "TUAN_GOU", "18800001111", "hello",
      Map.of(), Map.of(), List.of(), List.of(), "keeper", tags);

  tags.clear();
  assertThat(request.currentTags()).extracting(ReplyTagSnapshot::tagValue)
      .containsExactly("LOYALIST");
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q -Dtest=SkillRequestTest test'
```

Expected: test compilation fails because `ReplyTagSnapshot` and `SkillRequest.currentTags()` do not exist.

- [ ] **Step 3: Add the minimal contract**

Create the record:

```java
package com.privateflow.modules.skill;

public record ReplyTagSnapshot(
    String categoryKey,
    String categoryName,
    String tagValue,
    String tagDisplayName,
    String meaning,
    String sourceType,
    String evidenceText,
    boolean manualLocked
) {
}
```

Extend `SkillRequest` with `List<ReplyTagSnapshot> currentTags`, normalize null to `List.of()`, copy the list, and retain this exact legacy constructor:

```java
public SkillRequest(
    Scene scene,
    String leadType,
    String phone,
    String clientMessage,
    Map<String, Object> customer,
    Map<String, Object> systemPrompt,
    List<String> previousSuggestions,
    List<Map<String, String>> chatContext,
    String caller) {
  this(scene, leadType, phone, clientMessage, customer, systemPrompt,
      previousSuggestions, chatContext, caller, List.of());
}

public SkillRequest {
  currentTags = currentTags == null ? List.of() : List.copyOf(currentTags);
}
```

- [ ] **Step 4: Run the contract tests and verify GREEN**

Run the Task 1 command again. Expected: 2 tests pass.

- [ ] **Step 5: Commit the contract**

```bash
git add src/main/java/com/privateflow/modules/skill/ReplyTagSnapshot.java src/main/java/com/privateflow/modules/skill/SkillRequest.java src/test/java/com/privateflow/modules/skill/SkillRequestTest.java
git commit -m "feat: add reply tag snapshot contract"
```

### Task 2: Build Reply Tags From Unified Current Assignments

**Files:**
- Create: `src/main/java/com/privateflow/modules/api/chat/ReplyTagSnapshotBuilder.java`
- Test: `src/test/java/com/privateflow/modules/api/chat/ReplyTagSnapshotBuilderTest.java`

- [ ] **Step 1: Write failing builder tests**

Use mocked `CustomerTagQueryService` and `TagDirectoryService`. Cover:

```java
@Test
void buildsReplySafeChineseTagSnapshot() {
  when(queryService.current(5L)).thenReturn(List.of(currentTag(1L, 11L, true)));
  when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
      List.of(category(1L, true, value(11L, "忠诚型", "重视安全感和专业背书"))),
      Instant.parse("2026-07-16T00:00:00Z")));

  assertThat(builder.build(5L)).singleElement().satisfies(tag -> {
    assertThat(tag.categoryName()).isEqualTo("性格类型");
    assertThat(tag.tagDisplayName()).isEqualTo("忠诚型");
    assertThat(tag.meaning()).isEqualTo("重视安全感和专业背书");
    assertThat(tag.sourceType()).isEqualTo("MANUAL");
    assertThat(tag.manualLocked()).isTrue();
  });
}

@Test
void excludesCategoriesDisabledForReply() {
  when(queryService.current(5L)).thenReturn(List.of(currentTag(1L, 11L, false)));
  when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
      List.of(category(1L, false, value(11L, "内部标签", "仅内部统计"))),
      Instant.parse("2026-07-16T00:00:00Z")));

  assertThat(builder.build(5L)).isEmpty();
}

@Test
void rejectsMissingDirectoryMetadataForCurrentAssignment() {
  when(queryService.current(5L)).thenReturn(List.of(currentTag(1L, 11L, false)));
  when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.empty(
      Instant.parse("2026-07-16T00:00:00Z")));

  assertThatThrownBy(() -> builder.build(5L))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("directory");
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q -Dtest=ReplyTagSnapshotBuilderTest test'
```

Expected: compilation fails because `ReplyTagSnapshotBuilder` does not exist.

- [ ] **Step 3: Implement the builder**

Implement `build(long customerId)` as follows:

```java
public List<ReplyTagSnapshot> build(long customerId) {
  TagDirectorySnapshot directory = directoryService.getSnapshot();
  List<ReplyTagSnapshot> result = new ArrayList<>();
  for (CustomerTagQueryDto assignment : queryService.current(customerId)) {
    TagCategory category = directory.categoriesById().get(assignment.categoryId());
    TagValue value = directory.valuesById().get(assignment.tagValueId());
    if (category == null || value == null || value.categoryId() != category.id()) {
      throw new IllegalStateException("reply tag directory metadata missing");
    }
    if (!category.useForReply()) {
      continue;
    }
    result.add(new ReplyTagSnapshot(
        category.categoryKey(), category.categoryName(), value.tagValue(), value.displayName(),
        value.meaning(), assignment.sourceType(), assignment.evidenceText(), assignment.manualLocked()));
  }
  return List.copyOf(result);
}
```

- [ ] **Step 4: Run the builder tests and verify GREEN**

Run the Task 2 command again. Expected: 3 tests pass.

- [ ] **Step 5: Commit the builder**

```bash
git add src/main/java/com/privateflow/modules/api/chat/ReplyTagSnapshotBuilder.java src/test/java/com/privateflow/modules/api/chat/ReplyTagSnapshotBuilderTest.java
git commit -m "feat: build reply tag snapshots"
```

### Task 3: Load Latest Tags In Chat Orchestration

**Files:**
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- Modify: `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`

- [ ] **Step 1: Add failing initial-generation test**

Add a mocked `ReplyTagSnapshotBuilder` to the test setup and capture the request passed to `skillGatewayService`:

```java
@Test
void generatePassesCurrentReplyTagsToTheReplyRequest() {
  Customer customer = customer("18800001111");
  customer.setId(5L);
  ReplyTagSnapshot tag = replyTag("LOYALIST", "忠诚型");
  when(replyTagSnapshotBuilder.build(5L)).thenReturn(List.of(tag));
  when(skillGatewayService.generateReplies(any())).thenReturn(reply("skill reply"));

  service.generate(new GenerateRequest("18800001111", "ACTIVE_REPLY", "想看案例"));

  ArgumentCaptor<SkillRequest> captor = ArgumentCaptor.forClass(SkillRequest.class);
  verify(skillGatewayService).generateReplies(captor.capture());
  assertThat(captor.getValue().currentTags()).containsExactly(tag);
}
```

- [ ] **Step 2: Run the initial test and verify RED**

Run:

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q -Dtest=ChatOrchestrationServiceTest#generatePassesCurrentReplyTagsToTheReplyRequest test'
```

Expected: failure because the service constructor and generated `SkillRequest` do not use the builder.

- [ ] **Step 3: Add the builder dependency and exact-request persistence**

Add `ReplyTagSnapshotBuilder` to the constructor. Change `GeneratedReplies` to include the exact `SkillRequest`:

```java
private record GeneratedReplies(
    SkillRequest request,
    SkillResponse skill,
    ChatReplySource source
) {
}
```

Create the request once in `generateSkill`, call `generateReplies`, and save `generated.request()` to `RequestContext` instead of reconstructing a second request.

Implement access-aware loading:

```java
private List<ReplyTagSnapshot> loadReplyTags(Customer customer) {
  if (customer == null || customer.getId() == null) {
    return List.of();
  }
  try {
    return replyTagSnapshotBuilder.build(customer.getId());
  } catch (ApiException ex) {
    throw ex;
  } catch (RuntimeException ex) {
    String phone = customer.getPhone();
    log.warn("reply tag snapshot load failed, phoneLast4={}, reason={}", lastFour(phone), ex.getMessage());
    auditLogger.log(
        "CUSTOMER_TAGS_READ_DEGRADED",
        AuthContext.username(),
        "CUSTOMER",
        phone,
        clip(ex.getMessage(), 500));
    return List.of();
  }
}
```

- [ ] **Step 4: Run the initial test and verify GREEN**

Run the Task 3 Step 2 command again. Expected: pass.

- [ ] **Step 5: Add failing degradation test**

```java
@Test
void tagReadFailureKeepsReplyFlowAndRecordsDegradation() {
  Customer customer = customer("18800001111");
  customer.setId(5L);
  when(replyTagSnapshotBuilder.build(5L)).thenThrow(new IllegalStateException("directory unavailable"));
  when(skillGatewayService.generateReplies(any())).thenReturn(reply("ordinary reply"));

  ChatResponse response = service.generate(
      new GenerateRequest("18800001111", "ACTIVE_REPLY", "想了解方案"));

  assertThat(response.skill().suggestions()).extracting(Suggestion::text)
      .containsExactly("ordinary reply");
  verify(auditLogger).log(
      "CUSTOMER_TAGS_READ_DEGRADED", "keeper-1", "CUSTOMER",
      "18800001111", "directory unavailable");
}
```

- [ ] **Step 6: Run degradation test RED, then GREEN with the loader above**

Run the single test, confirm it fails before the loader catch/audit exists, then implement only the loader behavior and rerun. Expected: pass.

- [ ] **Step 7: Add failing regenerate freshness test**

```java
@Test
void regenerateReloadsLatestCustomerTagsInsteadOfReusingStoredSnapshot() {
  Customer latest = customer("18800001111");
  latest.setId(5L);
  ReplyTagSnapshot oldTag = replyTag("LOYALIST", "忠诚型");
  ReplyTagSnapshot latestTag = replyTag("DECISIVE", "果断型");
  SkillRequest previous = requestWithTags(List.of(oldTag));
  when(contextStore.read("keeper-1", "18800001111"))
      .thenReturn(Optional.of(new RequestContext(previous, reply("old"), 0)));
  when(customerQueryService.getByPhone("18800001111")).thenReturn(latest);
  when(replyTagSnapshotBuilder.build(5L)).thenReturn(List.of(latestTag));
  when(skillGatewayService.generateReplies(any())).thenReturn(reply("new"));

  service.regenerate(new RegenerateRequest("18800001111"));

  ArgumentCaptor<SkillRequest> captor = ArgumentCaptor.forClass(SkillRequest.class);
  verify(skillGatewayService).generateReplies(captor.capture());
  assertThat(captor.getValue().currentTags()).containsExactly(latestTag);
}
```

- [ ] **Step 8: Implement latest-customer regenerate and verify GREEN**

In `regenerate`, reload `Customer latest = customerQueryService.getByPhone(request.phone())`; if missing, delegate to `generate`. Build the next request with `customerMap(latest)` and `loadReplyTags(latest)`, while preserving the prior scene inputs, chat context, and previous suggestions.

Run all `ChatOrchestrationServiceTest` tests. Expected: all pass.

- [ ] **Step 9: Commit orchestration changes**

```bash
git add src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java
git commit -m "feat: load latest tags for reply generation"
```

### Task 4: Send The Same Snapshot To Skill And LLM

**Files:**
- Modify: `src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java`
- Modify: `src/test/java/com/privateflow/modules/skill/service/SkillRequestBuilderTest.java`
- Modify: `src/main/java/com/privateflow/modules/llm/LlmReplyGenerationService.java`
- Modify: `src/test/java/com/privateflow/modules/llm/LlmReplyGenerationServiceTest.java`

- [ ] **Step 1: Write failing Skill payload test**

Build a request with one `ReplyTagSnapshot` and assert:

```java
assertThat(payload.get("current_tags")).isEqualTo(List.of(tag));
assertThat(payload.get("system_prompt").toString())
    .contains("标签只用于调整回复方向")
    .contains("不得向客户描述内部标签");
```

- [ ] **Step 2: Run Skill test and verify RED**

Run:

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q -Dtest=SkillRequestBuilderTest test'
```

Expected: `current_tags` and fixed guidance are absent.

- [ ] **Step 3: Implement Skill payload and guidance**

Add:

```java
payload.put("current_tags", request.currentTags());
```

Append a fixed guidance block after configured prompt replacement:

```text
【当前客户标签使用规则】
标签只用于调整回复方向、优先级和语气。
不得向客户描述内部标签、系统判断、把握度、证据、来源或锁定状态。
标签与客户当前消息或真实业务事实冲突时，以当前消息和业务事实为准。
```

Run the Skill test again. Expected: pass.

- [ ] **Step 4: Write failing LLM prompt test**

Capture `LlmRequest` and assert its `userPrompt` contains `"currentTags"`, `"忠诚型"`, and `"重视安全感和专业背书"`; assert it still excludes `18800001111` and contains the fixed non-disclosure guidance.

- [ ] **Step 5: Run LLM test and verify RED**

Run:

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q -Dtest=LlmReplyGenerationServiceTest test'
```

Expected: prompt does not contain `currentTags` or tag meaning.

- [ ] **Step 6: Implement LLM payload and fixed guidance**

Add `payload.put("currentTags", request.currentTags())` and append the same fixed rules to the generated user prompt. Update `DEFAULT_SYSTEM_PROMPT` to repeat the non-disclosure rule without exposing tag evidence in generated text.

Run the LLM test again. Expected: pass.

- [ ] **Step 7: Run combined reply-path tests**

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q -Dtest=ChatOrchestrationServiceTest,SkillRequestBuilderTest,LlmReplyGenerationServiceTest,SkillGatewayServiceImplCircuitTest test'
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit request-path changes**

```bash
git add src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java src/test/java/com/privateflow/modules/skill/service/SkillRequestBuilderTest.java src/main/java/com/privateflow/modules/llm/LlmReplyGenerationService.java src/test/java/com/privateflow/modules/llm/LlmReplyGenerationServiceTest.java
git commit -m "feat: send reply tags to skill and llm"
```

### Task 5: Full Verification, Runtime Acceptance, And Breakpoint

**Files:**
- Modify: `dev-progress/tag_skill_llm_tasklist_056.md`
- Create: `dev-progress/tag_skill_llm_step8_breakpoint_061.md`

- [ ] **Step 1: Run Java full verification**

```bash
wsl.exe -e bash -lc 'cd /mnt/c/Users/85314/.config/superpowers/worktrees/private-domain-assistant/tag-step4-unified-access && mvn -q test'
```

Expected: exit 0; sum Surefire reports and record tests, failures, errors, and skipped counts.

- [ ] **Step 2: Run frontend verification**

From `desktop/` run:

```powershell
npm run test
npm run typecheck
npm run build
npm run renderer:smoke
npm run electron:smoke
```

Expected: 36 Vitest files pass, typecheck/build exit 0, both smoke markers report `passed`.

- [ ] **Step 3: Run real backend acceptance on port 8082**

Start the Step 8 backend against `private_domain_assistant_smoke` with `MOCK_EXTERNALS=false`, without changing or stopping the main 8080 service. Login as the smoke admin and verify:

1. Customer `13900000001` can receive a temporary manual reply-enabled tag.
2. `POST /api/v1/chat/generate` succeeds with Skill fallback while LLM reply generation remains disabled.
3. A second tag change followed by regenerate uses the new tag snapshot, confirmed through a targeted request-capture test and runtime audit/log evidence.
4. Cleanup removes the temporary tag and unlocks the category.
5. Tag infrastructure degradation remains unit-tested; do not damage the real database to force it at runtime.

Stop the temporary 8082 process after acceptance. Keep main 8080 and frontend dev server running.

- [ ] **Step 4: Update Step 8 tasklist and breakpoint**

Mark only Step 8 checkboxes complete. Record:

- exact commit hashes;
- test totals;
- runtime API evidence;
- final feature flags;
- service state;
- explicit statement that Step 9 was not started.

- [ ] **Step 5: Check and commit documentation**

```bash
git diff --check
git add dev-progress/tag_skill_llm_tasklist_056.md dev-progress/tag_skill_llm_step8_breakpoint_061.md
git commit -m "docs: record tag step 8 breakpoint"
```

- [ ] **Step 6: Push and verify remote equality**

```bash
git push -u origin feature/tag-step8-reply-tag-context
git status --porcelain --branch
git rev-parse HEAD
git rev-parse origin/feature/tag-step8-reply-tag-context
```

Expected: clean worktree and identical local/remote hashes.
