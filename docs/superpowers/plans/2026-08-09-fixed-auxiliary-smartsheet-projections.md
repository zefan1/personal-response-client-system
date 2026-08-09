# Fixed Auxiliary Smart Sheet Projections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send assignment and appointment or arrival snapshots to the two fixed API-created WeCom Smart Sheets through the configured server relay.

**Architecture:** Add two optional fixed targets from six server environment variables. A small writer upserts by `联系方式`; `CustomerSyncScheduler` invokes it only after its MariaDB upsert. The existing pending-write queue retries the two fixed target names and leaves every primary-table path unchanged.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, existing WeCom Smart Sheet API client, MariaDB pending-write queue.

---

## File Structure

- Create `src/main/java/com/privateflow/modules/tablewrite/config/AuxiliarySmartSheetTarget.java`: immutable target IDs loaded from environment.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/AuxiliarySmartSheetWriter.java`: direct WeCom upsert for a fixed target.
- Create `src/main/java/com/privateflow/modules/tablewrite/service/AuxiliarySmartSheetProjectionService.java`: two explicit customer-to-field payloads.
- Modify `src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java`: invoke the projection after local persistence.
- Modify `src/main/java/com/privateflow/modules/tablewrite/service/WriteQueueManager.java`: enqueue the two fixed payload names with the existing retry API.
- Modify `src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java`: retry `ASSIGNMENT` and `ARRIVAL` payloads with the new writer.
- Modify `tools/WecomSmartSheet.ps1`: prepare the two already-created tables with the fixed text columns.

### Task 1: Add Two Fixed Target Values

**Files:**
- Create `src/main/java/com/privateflow/modules/tablewrite/config/AuxiliarySmartSheetTarget.java`
- Create `src/test/java/com/privateflow/modules/tablewrite/config/AuxiliarySmartSheetTargetTest.java`

- [ ] **Step 1: Write the failing target test**

```java
@Test
void returnsAssignmentOnlyWhenAllItsIdentifiersExist() {
  Map<String, String> env = Map.of(
      "WECOM_ASSIGNMENT_SMARTSHEET_DOC_ID", "assignment-doc",
      "WECOM_ASSIGNMENT_SMARTSHEET_SHEET_ID", "assignment-sheet",
      "WECOM_ASSIGNMENT_SMARTSHEET_VIEW_ID", "assignment-view");

  assertThat(AuxiliarySmartSheetTarget.assignment(env)).contains(
      new AuxiliarySmartSheetTarget("ASSIGNMENT", "assignment-doc", "assignment-sheet", "assignment-view"));
  assertThat(AuxiliarySmartSheetTarget.arrival(env)).isEmpty();
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetTargetTest test`

Expected: compilation failure because the target record does not exist.

- [ ] **Step 3: Implement the record**

```java
public record AuxiliarySmartSheetTarget(String name, String documentId, String sheetId, String viewId) {
  public static Optional<AuxiliarySmartSheetTarget> assignment(Map<String, String> env) {
    return from(env, "ASSIGNMENT", "WECOM_ASSIGNMENT_SMARTSHEET_");
  }
  public static Optional<AuxiliarySmartSheetTarget> arrival(Map<String, String> env) {
    return from(env, "ARRIVAL", "WECOM_ARRIVAL_SMARTSHEET_");
  }
}
```

`from` trims `DOC_ID`, `SHEET_ID`, and `VIEW_ID`, returning empty unless all three values are nonblank.

- [ ] **Step 4: Run the target test and commit**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetTargetTest test`

Expected: `BUILD SUCCESS`.

```powershell
git add src/main/java/com/privateflow/modules/tablewrite/config/AuxiliarySmartSheetTarget.java src/test/java/com/privateflow/modules/tablewrite/config/AuxiliarySmartSheetTargetTest.java
git commit -m "feat: add fixed auxiliary table targets"
```

### Task 2: Upsert Rows Without Changing the Primary Client

**Files:**
- Create `src/main/java/com/privateflow/modules/tablewrite/client/AuxiliarySmartSheetWriter.java`
- Create `src/test/java/com/privateflow/modules/tablewrite/client/AuxiliarySmartSheetWriterTest.java`

- [ ] **Step 1: Write failing create and update tests**

```java
@Test
void addsRowWhenContactIsNotPresent() {
  when(apiClient.post("get_fields", any(), any())).thenReturn(fields("联系方式", "客户ID", "分配管家"));
  when(apiClient.post("get_records", any(), any())).thenReturn(records());
  when(apiClient.post("add_records", any(), any())).thenReturn(added("row-1"));

  writer.upsert(assignment, Map.of("联系方式", "13800000000", "客户ID", "12", "分配管家", "keeper"));

  verify(apiClient).post(eq("add_records"), requestFor("assignment-doc", "assignment-sheet"), any());
}

@Test
void updatesMatchedRowInsteadOfAddingAnother() {
  when(apiClient.post("get_fields", any(), any())).thenReturn(fields("联系方式", "是否到店"));
  when(apiClient.post("get_records", any(), any())).thenReturn(records("row-1", "13800000000"));

  writer.upsert(arrival, Map.of("联系方式", "13800000000", "是否到店", "是"));

  verify(apiClient).post(eq("update_records"), requestFor("arrival-doc", "arrival-sheet"), any());
  verify(apiClient, never()).post(eq("add_records"), any(), any());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetWriterTest test`

Expected: compilation failure because the writer does not exist.

- [ ] **Step 3: Implement direct target-specific upsert**

```java
public void upsert(AuxiliarySmartSheetTarget target, Map<String, Object> values) {
  String contact = requiredContact(values);
  Map<String, Field> fields = fieldsByTitle(target);
  Map<String, JsonNode> encoded = encodeTextValues(fields, values);
  String recordId = findByContact(target, fields.get("联系方式"), contact);
  if (recordId == null) add(target, encoded); else update(target, recordId, encoded);
}
```

Use `WecomSmartSheetApiClient.post` for `get_fields`, `get_records`, `add_records`, and `update_records`. Include the target document, sheet, and view IDs in lookup requests. Send only columns present in the target and throw when `联系方式` is missing. Do not modify `WecomSmartSheetRecordClient`.

- [ ] **Step 4: Run the writer test and commit**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetWriterTest test`

Expected: `BUILD SUCCESS`.

```powershell
git add src/main/java/com/privateflow/modules/tablewrite/client/AuxiliarySmartSheetWriter.java src/test/java/com/privateflow/modules/tablewrite/client/AuxiliarySmartSheetWriterTest.java
git commit -m "feat: write fixed auxiliary smart sheets"
```

### Task 3: Project After the Existing Customer Sync

**Files:**
- Create `src/main/java/com/privateflow/modules/tablewrite/service/AuxiliarySmartSheetProjectionService.java`
- Modify `src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java`
- Modify `src/main/java/com/privateflow/modules/tablewrite/service/WriteQueueManager.java`
- Create `src/test/java/com/privateflow/modules/tablewrite/service/AuxiliarySmartSheetProjectionServiceTest.java`
- Modify `src/test/java/com/privateflow/modules/customer/sync/CustomerSyncSchedulerTest.java`

- [ ] **Step 1: Write the failing route tests**

```java
@Test
void sendsAssignmentPayloadOnlyWhenTheCustomerHasAKeeper() {
  Customer customer = customer("13800000000");
  customer.setAssignedKeeper("keeper");

  service.project(customer);

  verify(writer).upsert(eq(assignment), eq(Map.of("联系方式", "13800000000", "分配管家", "keeper")));
  verify(writer, never()).upsert(eq(arrival), any());
}

@Test
void sendsArrivalPayloadWhenAppointmentDataExists() {
  Customer customer = customer("13800000000");
  customer.setAppointmentStore("万江店");

  service.project(customer);

  verify(writer).upsert(eq(arrival), eq(Map.of("联系方式", "13800000000", "预约门店", "万江店")));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetProjectionServiceTest test`

Expected: compilation failure because the projection service does not exist.

- [ ] **Step 3: Implement the two explicit payloads and hook them into sync**

```java
public void project(Customer customer) {
  assignment.ifPresent(target -> {
    if (!blank(customer.getAssignedKeeper())) writer.upsert(target, assignmentFields(customer));
  });
  arrival.ifPresent(target -> {
    if (hasArrivalData(customer)) writer.upsert(target, arrivalFields(customer));
  });
}
```

`assignmentFields` writes `联系方式`, `客户ID`, `客户昵称`, `客资类型`, `分配管家`, `意向门店`, `分配状态`, and `更新时间`. `arrivalFields` writes `联系方式`, `客户ID`, `客户昵称`, `预约日期`, `预约门店`, `预约项目`, `是否到店`, `衔接管家`, and `更新时间`. Omit null values and format dates with `toString()`.

Inject `WriteQueueManager` into the projection service. When `writer.upsert` throws, call `queueManager.enqueue(customer.getId(), customer.getPhone(), TableWriteActionType.UPDATE, new PendingWritePayload(target.name(), null, fields), exception.getMessage())`, then return normally. Inject the projection service into `CustomerSyncScheduler`. After `customerRepository.upsert(...)` and cache write succeed, call `projectionService.project(merged)`; this call must not enter `SyncFailureRepository` or undo the local customer write.

- [ ] **Step 4: Run tests and commit**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetProjectionServiceTest,CustomerSyncSchedulerTest test`

Expected: `BUILD SUCCESS`.

```powershell
git add src/main/java/com/privateflow/modules/tablewrite/service/AuxiliarySmartSheetProjectionService.java src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java src/main/java/com/privateflow/modules/tablewrite/service/WriteQueueManager.java src/test/java/com/privateflow/modules/tablewrite/service/AuxiliarySmartSheetProjectionServiceTest.java src/test/java/com/privateflow/modules/customer/sync/CustomerSyncSchedulerTest.java
git commit -m "feat: project synced customers to auxiliary sheets"
```

### Task 4: Reuse the Existing Retry Queue for Two Names

**Files:**
- Modify `src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java`
- Modify `src/test/java/com/privateflow/modules/tablewrite/service/QueueRetryManagerTest.java`

- [ ] **Step 1: Write the failing queue-route test**

```java
@Test
void retriesAssignmentPayloadWithAuxiliaryWriter() {
  PendingWritePayload payload = new PendingWritePayload(
      "ASSIGNMENT", null, Map.of("联系方式", "13800000000", "分配管家", "keeper"));
  when(repository.due(anyInt())).thenReturn(List.of(pending(payload)));

  manager.retryDueWrites();

  verify(auxiliaryWriter).upsert(eq(assignment), eq(payload.fields()));
  verify(repository).markResolved(1L);
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=QueueRetryManagerTest test`

Expected: failure because every existing payload routes to the primary table client.

- [ ] **Step 3: Add the two fixed branches before existing retry behavior**

```java
if ("ASSIGNMENT".equals(payload.sourceTable())) {
  auxiliaryWriter.upsert(assignmentTarget.orElseThrow(), payload.fields());
  return;
}
if ("ARRIVAL".equals(payload.sourceTable())) {
  auxiliaryWriter.upsert(arrivalTarget.orElseThrow(), payload.fields());
  return;
}
```

The projection service queues its failed payload using `ASSIGNMENT` or `ARRIVAL`; existing insert and update handling stays unchanged.

- [ ] **Step 4: Run queue and primary-table regressions, then commit**

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=QueueRetryManagerTest,TableWriteOrchestratorTest,WecomSmartSheetRecordClientTest test`

Expected: `BUILD SUCCESS`.

```powershell
git add src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java src/test/java/com/privateflow/modules/tablewrite/service/QueueRetryManagerTest.java
git commit -m "feat: retry auxiliary smart sheet writes"
```

### Task 5: Prepare the Two Created Tables and Validate the Relay

**Files:**
- Modify `tools/WecomSmartSheet.ps1`
- Modify `tools/tests/WecomSmartSheetBootstrap.Tests.ps1`
- Modify `docs/superpowers/specs/2026-08-09-two-auxiliary-smartsheet-projections-design.md`

- [ ] **Step 1: Write a failing PowerShell preparation test**

```powershell
It 'prepares the two fixed auxiliary documents without replacing the primary configuration' {
  $result = & $launcherPath -Mode PrepareAuxiliary -AssignmentDocumentId 'assignment-doc' -ArrivalDocumentId 'arrival-doc' `
    -PrepareSheet { param($connection, $documentId) [pscustomobject]@{ documentId = $documentId } }

  $result.Assignment.documentId | Should Be 'assignment-doc'
  $result.Arrival.documentId | Should Be 'arrival-doc'
}
```

- [ ] **Step 2: Run the Pester test to verify it fails**

Run: `Invoke-Pester .\\tools\\tests\\WecomSmartSheetBootstrap.Tests.ps1 -Output Detailed`

Expected: parameter-set failure because `PrepareAuxiliary` does not exist.

- [ ] **Step 3: Add one-time column preparation and document the server settings**

The `PrepareAuxiliary` mode obtains the default Smart Sheet and grid view for each supplied document, adds the Task 3 text columns when missing, and prints document, sheet, and view IDs. It does not alter `wecom-smartsheet.clixml` or require a formula column.

Set these server environment variables from its output: `WECOM_ASSIGNMENT_SMARTSHEET_DOC_ID`, `WECOM_ASSIGNMENT_SMARTSHEET_SHEET_ID`, `WECOM_ASSIGNMENT_SMARTSHEET_VIEW_ID`, `WECOM_ARRIVAL_SMARTSHEET_DOC_ID`, `WECOM_ARRIVAL_SMARTSHEET_SHEET_ID`, and `WECOM_ARRIVAL_SMARTSHEET_VIEW_ID`.

- [ ] **Step 4: Run complete focused verification**

Run: `Invoke-Pester .\\tools\\tests\\WecomSmartSheetBootstrap.Tests.ps1 -Output Detailed`

Run: `wsl.exe --cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 --exec mvn -Dtest=AuxiliarySmartSheetTargetTest,AuxiliarySmartSheetWriterTest,AuxiliarySmartSheetProjectionServiceTest,CustomerSyncSchedulerTest,QueueRetryManagerTest test`

Expected: Pester passes and Maven reports `BUILD SUCCESS`.

- [ ] **Step 5: Run live acceptance and commit**

Use one controlled contact in each target: create it, change one field, submit the same contact again, and read it back. Confirm the relay server's outbound IP is in the WeCom application's trusted-IP list. Do not delete these controlled rows.

```powershell
git add tools/WecomSmartSheet.ps1 tools/tests/WecomSmartSheetBootstrap.Tests.ps1 docs/superpowers/specs/2026-08-09-two-auxiliary-smartsheet-projections-design.md
git commit -m "feat: connect fixed auxiliary smart sheets"
```
