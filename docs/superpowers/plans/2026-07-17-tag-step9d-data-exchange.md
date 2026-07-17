# Step 9D Data Exchange Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with checkpoints.

**Goal:** Make CSV import, external sheet synchronization, and external table writeback share one tag parsing/validation path that preserves valid local tags and records unmatched values.

**Architecture:** Add a tag exchange service in the existing `modules.tags` package. It resolves bound fields against the current tag directory, converts exact code/display-name/synonym matches into formal tag codes, delegates final purpose checks to `TagSelectionValidator(IMPORT)`, and returns accepted fields plus unmatched tokens. Inbound callers persist customer data and unmatched history in the same row transaction; outbound callers sanitize the payload before the remote write and never mutate local tags as part of the writeback.

**Tech Stack:** Spring Boot, JdbcTemplate, existing tag directory/validator, JUnit 5 + Mockito, Vitest/Vue AdminConsole.

---

## File Map

Create:

- `src/main/java/com/privateflow/modules/tags/TagExchangeSourceType.java` - fixed source names for CSV, external sync, and table writeback.
- `src/main/java/com/privateflow/modules/tags/TagExchangeUnmatchedValue.java` - immutable unmatched token metadata.
- `src/main/java/com/privateflow/modules/tags/TagExchangeResult.java` - accepted fields, filtered fields, and unmatched values returned by the exchange service.
- `src/main/java/com/privateflow/modules/tags/TagExchangeService.java` - shared exact resolution and `IMPORT` validation.
- `src/main/java/com/privateflow/modules/customer/sync/FieldMappingResult.java` - mapped customer plus accepted tag fields and unmatched results for one sheet row.
- `src/test/java/com/privateflow/modules/tags/TagExchangeServiceTest.java` - exchange parsing and validation contract tests.

Modify:

- `src/main/java/com/privateflow/modules/tags/LegacyCustomerTagSynchronizer.java` - consume normalized accepted tag fields and persist unmatched values with source metadata while retaining the old overload.
- `src/main/java/com/privateflow/modules/customer/infra/CustomerRepository.java` - add an inbound upsert overload that receives the normalized tag result; keep `upsert(Customer)` behavior for unrelated callers.
- `src/main/java/com/privateflow/modules/customer/admin/CsvImportResult.java` - add unmatched count and row summaries without changing existing counters.
- `src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminService.java` - normalize CSV tag fields before upsert and persist row-level unmatched details.
- `src/main/java/com/privateflow/modules/customer/sync/FieldMappingResolver.java` - expose `mapRowResult` and normalize mapped tag fields before creating the `Customer`.
- `src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java` - upsert the mapped result atomically and distinguish data-quality unmatched values from true sync failures.
- `src/main/java/com/privateflow/modules/tablewrite/infra/TableFieldMappingResolver.java` - expose source-to-internal field resolution for manual writeback and keep internal-to-source mapping for automatic writes.
- `src/main/java/com/privateflow/modules/tablewrite/service/ManualSaveHandler.java` - sanitize mapped tag fields before `updateRow` and return filtered-field information.
- `src/main/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdater.java` - sanitize automatic update fields before mapping to the source table.
- `src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java` - sanitize create payloads before remote create and only upsert the local customer after success.
- `src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java` - re-run outbound tag validation for every retry before calling the remote client.
- `src/main/java/com/privateflow/modules/tablewrite/ManualSaveResult.java` - expose filtered fields/unmatched count while preserving `written` and `updatedFields`.
- `desktop/src/renderer/modules/admin/AdminConsole.vue` - render CSV unmatched count and row summaries in the existing import result area.
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts` - cover the new import result fields and preserve existing import/sync UI behavior.

Tests to modify:

- `src/test/java/com/privateflow/modules/customer/admin/DatasourceAdminServiceTest.java`
- `src/test/java/com/privateflow/modules/customer/sync/FieldMappingResolverTest.java`
- `src/test/java/com/privateflow/modules/customer/sync/CustomerSyncSchedulerTest.java`
- `src/test/java/com/privateflow/modules/customer/infra/CustomerRepositoryTest.java`
- `src/test/java/com/privateflow/modules/tags/TagFlywayMariaDbIntegrationTest.java`
- `src/test/java/com/privateflow/modules/tablewrite/infra/TableFieldMappingResolverTest.java`
- `src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorTest.java`
- Add `src/test/java/com/privateflow/modules/tablewrite/service/ManualSaveHandlerTest.java`.
- Add `src/test/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdaterTest.java`.
- Add `src/test/java/com/privateflow/modules/tablewrite/service/QueueRetryManagerTest.java`.

---

### Task 1: Add the exchange result model and exact directory resolver

**Files:**
- Create: `src/main/java/com/privateflow/modules/tags/TagExchangeSourceType.java`
- Create: `src/main/java/com/privateflow/modules/tags/TagExchangeUnmatchedValue.java`
- Create: `src/main/java/com/privateflow/modules/tags/TagExchangeResult.java`
- Create: `src/main/java/com/privateflow/modules/tags/TagExchangeService.java`
- Create: `src/test/java/com/privateflow/modules/tags/TagExchangeServiceTest.java`

- [ ] **Step 1: Write failing tests for exact resolution and purpose validation.**

Add tests using mocked `TagDirectoryService` and `TagSelectionValidator`:

```java
@Test
void resolvesCodeDisplayNameAndSynonymWithoutFuzzyMatching() {
  TagExchangeResult result = service.prepareInbound(
      TagExchangeSourceType.CSV_IMPORT,
      "row-3",
      Map.of("bodyConcerns", "LEAKAGE"));

  assertThat(result.acceptedFields()).containsEntry("bodyConcerns", "LEAKAGE");
  assertThat(result.unmatched()).isEmpty();
  verify(validator).validateCodes(eq(TagCandidatePurpose.IMPORT), eq("body_concerns"), eq(List.of("LEAKAGE")), any());
}

@Test
void recordsUnknownTokenAndDoesNotGuessAValue() {
  TagExchangeResult result = service.prepareInbound(
      TagExchangeSourceType.EXTERNAL_SYNC,
      "row-8",
      Map.of("bodyConcerns", "LEAKAGE,unknown concern"));

  assertThat(result.acceptedFields()).containsEntry("bodyConcerns", "LEAKAGE");
  assertThat(result.unmatched()).singleElement()
      .satisfies(item -> assertThat(item.rawValue()).isEqualTo("unknown concern"));
}
```

Also cover disabled/merged category or value, single invalid value preservation, multi valid-plus-invalid, duplicate token rejection, blank values, and outbound filtering.

- [ ] **Step 2: Run the focused test and verify it fails.**

Run from the backend worktree:

```text
mvn -q -Dtest=com.privateflow.modules.tags.TagExchangeServiceTest test
```

Expected: compilation failure because the exchange types do not exist.

- [ ] **Step 3: Implement the immutable result types.**

Use these signatures:

```java
public enum TagExchangeSourceType {
  CSV_IMPORT,
  EXTERNAL_SYNC,
  TABLE_WRITE
}

public record TagExchangeUnmatchedValue(
    String boundField,
    String rawValue,
    List<String> unmatchedTokens,
    Long categoryId,
    TagExchangeSourceType sourceType,
    String sourceRecordId) {}

public record TagExchangeResult(
    Map<String, Object> acceptedFields,
    List<String> filteredFields,
    List<TagExchangeUnmatchedValue> unmatched) {}
```

Copy collections in compact constructors so callers cannot mutate the result.

- [ ] **Step 4: Implement `TagExchangeService.prepareInbound` and `prepareOutbound`.**

The service must:

1. Load one `TagDirectorySnapshot` for the complete field map.
2. Find a category by `boundField`.
3. Split MULTI values on comma, Chinese comma, semicolon, pipe, CR/LF, or tab; keep SINGLE as one trimmed token.
4. Resolve exact `tagValue`, `displayName`, or configured synonym to a formal code.
5. Call `validator.validateCodes(TagCandidatePurpose.IMPORT, category.categoryKey(), acceptedCodes, new TagSelectionContext(null, 0, null, businessBasis))`.
6. Omit an invalid SINGLE field from `acceptedFields`; retain accepted MULTI codes and report only unmatched tokens.
7. Never use fuzzy matching or write unknown text into `acceptedFields`.

`businessBasis` must include the source type, source record id, and bound field. `prepareOutbound` uses the same result model but places rejected tag fields in `filteredFields`; ordinary fields pass through unchanged.

- [ ] **Step 5: Run the focused tests and commit.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.tags.TagExchangeServiceTest test
```

Expected: all exchange tests pass. Commit:

```text
git add src/main/java/com/privateflow/modules/tags src/test/java/com/privateflow/modules/tags/TagExchangeServiceTest.java
git commit -m "feat: add unified tag exchange validation"
```

### Task 2: Refactor the legacy bridge without changing existing callers

**Files:**
- Modify: `src/main/java/com/privateflow/modules/tags/LegacyCustomerTagSynchronizer.java`
- Modify: `src/main/java/com/privateflow/modules/customer/infra/CustomerRepository.java`
- Modify: `src/test/java/com/privateflow/modules/tags/TagFlywayMariaDbIntegrationTest.java`
- Modify: `src/test/java/com/privateflow/modules/customer/infra/CustomerRepositoryTest.java`

- [ ] **Step 1: Add tests for source-aware synchronization and invalid single preservation.**

Extend the MariaDB integration test with:

```java
@Test
void invalidSingleExternalValueKeepsExistingAssignmentAndCreatesUnmatchedHistory() {
  synchronizer.synchronize(
      "19900006888",
      Map.of("intentLevel", "UNKNOWN"),
      TagExchangeSourceType.EXTERNAL_SYNC,
      "row-22");

  assertThat(activeCodes("19900006888", "intent_level")).containsExactly("HIGH");
  assertThat(unmatched("19900006888", "intentLevel"))
      .anySatisfy(item -> assertThat(item.getString("source_type")).isEqualTo("EXTERNAL_SYNC"));
}
```

Add a repository test proving the existing `upsert(Customer)` overload remains source-compatible and the new overload passes only normalized tag fields to the bridge.

- [ ] **Step 2: Run the focused tests and verify the new test fails.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.tags.TagFlywayMariaDbIntegrationTest,com.privateflow.modules.customer.infra.CustomerRepositoryTest test
```

Expected: the new overload is missing or the invalid value changes the current assignment.

- [ ] **Step 3: Add source-aware bridge overloads.**

Keep the existing compatibility method and its `CUSTOMER_FIELD` history behavior unchanged:

```java
public void synchronize(String phone, Map<String, ?> changedFields) {
  synchronizeLegacyCustomerField(phone, changedFields);
}
```

Add:

```java
public void synchronize(
    String phone,
    Map<String, ?> changedFields,
    TagExchangeSourceType sourceType,
    String sourceRecordId)
```

Inject `TagExchangeService` into the new source-aware overload, normalize once, apply only `acceptedFields` to the legacy bridge, and write every `unmatched` item with the source type and source record id. Keep the current disabled-value invalidation behavior. The old method remains a compatibility path for `ProfileWriter` and existing migration tests; only CSV, external sync, and table writeback use the new exchange path.

- [ ] **Step 4: Add the `CustomerRepository.upsert` overload.**

Add:

```java
@Transactional
public boolean upsert(
    Customer customer,
    TagExchangeResult exchangeResult,
    TagExchangeSourceType sourceType,
    String sourceRecordId)
```

The method must execute the same customer SQL as `upsert(Customer)` and call the bridge with `exchangeResult.acceptedFields()` plus the supplied source metadata. Leave `upsert(Customer)` delegating to the current legacy-field behavior so profile and unrelated callers do not change.

- [ ] **Step 5: Run tests and commit.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.tags.TagFlywayMariaDbIntegrationTest,com.privateflow.modules.customer.infra.CustomerRepositoryTest test
```

Expected: existing tag migration tests and new source-aware tests pass. Commit:

```text
git add src/main/java/com/privateflow/modules/tags/LegacyCustomerTagSynchronizer.java src/main/java/com/privateflow/modules/customer/infra/CustomerRepository.java src/test/java/com/privateflow/modules/tags/TagFlywayMariaDbIntegrationTest.java src/test/java/com/privateflow/modules/customer/infra/CustomerRepositoryTest.java
git commit -m "feat: persist source-aware unmatched tag history"
```

### Task 3: Make CSV import normalize tags and report partial success

**Files:**
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CsvImportResult.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminService.java`
- Modify: `src/test/java/com/privateflow/modules/customer/admin/DatasourceAdminServiceTest.java`

- [ ] **Step 1: Add failing CSV tests.**

Add a multipart CSV test containing `phone,nickname,bodyConcerns` with one recognized token and one unknown token. Stub the exchange service to return accepted `bodyConcerns=LEAKAGE` and one unmatched item. Assert:

```java
assertThat(result.created()).isEqualTo(1);
assertThat(result.skipped()).isZero();
assertThat(result.unmatchedCount()).isEqualTo(1);
assertThat(result.unmatchedRows()).containsExactly(2);
verify(customerRepository).upsert(any(Customer.class), any(TagExchangeResult.class), eq(TagExchangeSourceType.CSV_IMPORT), eq("2"));
```

Add a second test proving an invalid SINGLE tag does not put the raw value into the customer passed to `upsert`.

- [ ] **Step 2: Run the CSV tests and verify they fail.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.customer.admin.DatasourceAdminServiceTest test
```

Expected: missing result fields and missing exchange-service integration.

- [ ] **Step 3: Extend `CsvImportResult` compatibly.**

Add:

```java
public record CsvImportResult(
    int totalRows,
    int created,
    int updated,
    int skipped,
    List<RowError> errors,
    int unmatchedCount,
    List<Integer> unmatchedRows) {}
```

Provide an overloaded five-argument constructor that defaults the new fields to zero and an empty list so existing tests and repository log serialization remain valid.

- [ ] **Step 4: Integrate exchange normalization into `DatasourceAdminService.parseCsv`.**

Inject `TagExchangeService`. For each valid phone row, build a target-field map from headers that match `Customer` writable fields, call `prepareInbound(CSV_IMPORT, String.valueOf(rowNumber), fields)`, set only accepted values on the customer, and call the source-aware `CustomerRepository.upsert` overload. Keep invalid phone, duplicate phone, and max-row behavior unchanged. Add each row number represented by an unmatched result to `unmatchedRows` and increment `unmatchedCount` by unmatched field occurrences.

- [ ] **Step 5: Run tests and commit.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.customer.admin.DatasourceAdminServiceTest test
```

Expected: all datasource tests pass, including partial-success assertions. Commit:

```text
git add src/main/java/com/privateflow/modules/customer/admin/CsvImportResult.java src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminService.java src/test/java/com/privateflow/modules/customer/admin/DatasourceAdminServiceTest.java
git commit -m "feat: validate CSV tag fields during import"
```

### Task 4: Make external sheet sync use the same inbound result

**Files:**
- Create: `src/main/java/com/privateflow/modules/customer/sync/FieldMappingResult.java`
- Modify: `src/main/java/com/privateflow/modules/customer/sync/FieldMappingResolver.java`
- Modify: `src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java`
- Modify: `src/test/java/com/privateflow/modules/customer/sync/FieldMappingResolverTest.java`
- Modify: `src/test/java/com/privateflow/modules/customer/sync/CustomerSyncSchedulerTest.java`

- [ ] **Step 1: Add failing mapping and scheduler tests.**

Define the result contract:

```java
public record FieldMappingResult(
    Customer customer,
    TagExchangeResult tagExchange) {}
```

Add tests asserting `mapRowResult` returns a customer with only accepted tag values and retains an unmatched result for the original row id. Add a scheduler test where the exchange result contains an unmatched value and assert `customerRepository.upsert` is called, while a `customerRepository.upsert` exception is recorded in `SyncFailureRepository` and does not update the cache.

- [ ] **Step 2: Run mapping and scheduler tests to verify failure.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.customer.sync.FieldMappingResolverTest,com.privateflow.modules.customer.sync.CustomerSyncSchedulerTest test
```

Expected: `mapRowResult` and source-aware scheduler upsert are missing.

- [ ] **Step 3: Implement `FieldMappingResolver.mapRowResult`.**

Inject `TagExchangeService`. Preserve `mapRow(String, SheetRow)` as a compatibility wrapper returning `mapRowResult(...).customer()`. In `mapRowResult`, collect only mapped writable fields, normalize tag-bound fields through `prepareInbound(EXTERNAL_SYNC, row.rowId(), fields)`, set accepted values on the customer, normalize `leadType`, and return the `FieldMappingResult`.

- [ ] **Step 4: Update `CustomerSyncScheduler.processRow`.**

Use `FieldMappingResult` and call:

```java
customerRepository.upsert(
    merged,
    mapping.tagExchange(),
    TagExchangeSourceType.EXTERNAL_SYNC,
    row.rowId());
```

Keep missing-phone handling and `NewLeadEvent` behavior. Keep the existing catch boundary so any mapping/database failure records `sync_failure_log`; do not write cache after a failed upsert. An unmatched tag result must not enter the failure log.

- [ ] **Step 5: Run tests and commit.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.customer.sync.FieldMappingResolverTest,com.privateflow.modules.customer.sync.CustomerSyncSchedulerTest test
```

Expected: mapping, partial-success, failure-isolation, and cache assertions pass. Commit:

```text
git add src/main/java/com/privateflow/modules/customer/sync src/test/java/com/privateflow/modules/customer/sync
git commit -m "feat: validate external sheet tags during sync"
```

### Task 5: Sanitize manual and automatic table writeback

**Files:**
- Modify: `src/main/java/com/privateflow/modules/tablewrite/infra/TableFieldMappingResolver.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/ManualSaveHandler.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdater.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/ManualSaveResult.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/service/ManualSaveHandlerTest.java`
- Modify: `src/test/java/com/privateflow/modules/tablewrite/infra/TableFieldMappingResolverTest.java`
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorTest.java`

- [ ] **Step 1: Add failing writeback tests.**

Cover these exact cases:

```java
@Test
void manualSaveWritesOrdinaryFieldsAndAcceptedTagsButDropsUnknownTag() { ... }

@Test
void manualSaveWithOnlyUnknownTagDoesNotCallRemoteClient() { ... }

@Test
void automaticUpdateFailureLeavesLocalCustomerTagsUntouched() { ... }

@Test
void retryRevalidatesPayloadAgainstCurrentDirectory() { ... }
```

Use a customer with an existing valid tag, a mapped source tag field, one ordinary field, and a mocked `WecomTableClient`. Assert that the remote payload excludes the unknown tag and that no customer repository/tag update is invoked by the writeback path.

- [ ] **Step 2: Run focused writeback tests to verify failure.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.tablewrite.service.ManualSaveHandlerTest,com.privateflow.modules.tablewrite.infra.TableFieldMappingResolverTest,com.privateflow.modules.tablewrite.service.NewCustomerRowCreatorTest test
```

Expected: the new test class does not compile and current handlers still send raw tag fields.

- [ ] **Step 3: Add source-to-internal mapping helpers.**

Extend `TableFieldMappingResolver` with:

```java
public Map<String, Object> toInternalFields(String sourceTable, Map<String, Object> sourceFields)

public Map<String, Object> mergeSourceFields(
    String sourceTable,
    Map<String, Object> originalSourceFields,
    Map<String, Object> acceptedInternalFields)
```

The first method returns only configured target fields; the second replaces only accepted mapped fields and preserves unmapped ordinary source fields. Keep `toSourceFields` behavior unchanged for existing callers.

- [ ] **Step 4: Sanitize manual writeback.**

Inject `TagExchangeService` into `ManualSaveHandler`. Resolve source fields to internal fields, call `prepareOutbound(TABLE_WRITE, request.sourceRowId(), internalFields)`, merge accepted fields back to source fields, and call `updateRow` only when the final payload is non-empty. Extend `ManualSaveResult` with filtered fields and unmatched count, and keep a constructor accepting `(boolean, List<String>)` that defaults the new fields. A remote exception still throws the existing `TABLE_WRITE_FAILED` error and does not touch local tags.

- [ ] **Step 5: Sanitize automatic update/create and retry.**

Inject the exchange service into `ExistingCustomerUpdater` and `NewCustomerRowCreator`; call `prepareOutbound` before `toSourceFields`. Keep local customer upsert after successful `createRow`. In `QueueRetryManager`, deserialize the internal payload, re-run `prepareOutbound`, skip a remote call when no fields remain, and preserve the existing retry status/error handling.

- [ ] **Step 6: Run tests and commit.**

Run:

```text
mvn -q -Dtest=com.privateflow.modules.tablewrite.service.ManualSaveHandlerTest,com.privateflow.modules.tablewrite.infra.TableFieldMappingResolverTest,com.privateflow.modules.tablewrite.service.NewCustomerRowCreatorTest,com.privateflow.modules.tablewrite.service.TableWriteOrchestratorTest test
```

Expected: all writeback tests pass and existing queue/orchestrator behavior remains green. Commit:

```text
git add src/main/java/com/privateflow/modules/tablewrite src/test/java/com/privateflow/modules/tablewrite
git commit -m "feat: protect table writeback with tag validation"
```

### Task 6: Surface CSV partial-success results in AdminConsole

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Add a failing renderer test.**

Mock the import endpoint with:

```ts
{
  totalRows: 1,
  created: 1,
  updated: 0,
  skipped: 0,
  errors: [],
  unmatchedCount: 1,
  unmatchedRows: [2]
}
```

After clicking the existing import button, assert that the data-integration section renders the unmatched count and row number while retaining the created/updated/skipped summary.

- [ ] **Step 2: Run the focused renderer test and verify failure.**

Run from `desktop`:

```text
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
```

Expected: the new unmatched-result assertion fails because the UI does not render the fields.

- [ ] **Step 3: Implement the smallest UI change.**

Extend the existing CSV import result state type with optional `unmatchedCount` and `unmatchedRows`. Render a short summary beside the current import result; do not add a new page or alter the existing datasource workflow.

- [ ] **Step 4: Run renderer checks and commit.**

Run:

```text
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
npm run typecheck
npm run build
```

Expected: all AdminConsole tests, typecheck, and production build pass. Commit:

```text
git add desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts
git commit -m "feat: show unmatched CSV tag results"
```

### Task 7: Full verification and Step 9D checkpoint

**Files:**
- Create: `dev-progress/tag_skill_llm_step9d_breakpoint_071.md`
- Modify: `dev-progress/tag_skill_llm_tasklist_056.md`

- [ ] **Step 1: Run the complete backend module suite.**

Run:

```text
mvn -q -Dtest='com.privateflow.modules.followup.*Test,com.privateflow.modules.tags.*Test,com.privateflow.modules.customer.*Test,com.privateflow.modules.tablewrite.*Test' test
```

Expected: zero failures, zero errors, with only the repository's existing conditional skips.

- [ ] **Step 2: Run all required frontend checks.**

Run from `desktop`:

```text
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
npm run typecheck
npm run build
```

Expected: AdminConsole tests pass, typecheck passes, and production build succeeds.

- [ ] **Step 3: Inspect protected data and git state.**

Run:

```text
git diff origin/feature/tag-step8-reply-tag-context -- src/main/resources/db/migration
git status --short --branch
```

Expected: no new migration, no changes to existing PENDING suggestion seed rows, and only intended Step 9D files in the worktree.

- [ ] **Step 4: Write the checkpoint and update the task list.**

The checkpoint must record the final implementation HEAD, exact test commands/results, the no-migration/no-LLM/Step-8 protections, and the remaining state. Mark only the Step 9D exchange-validation items complete in `tag_skill_llm_tasklist_056.md`; leave unrelated future Step 9 items unchecked.

- [ ] **Step 5: Commit documentation only.**

```text
git add dev-progress/tag_skill_llm_step9d_breakpoint_071.md dev-progress/tag_skill_llm_tasklist_056.md
git commit -m "docs: record step 9d data exchange checkpoint"
```

Do not push, merge, or create a PR.

---

## Plan Self-Review

- Spec coverage: the unified service is Task 1; unmatched persistence and transaction safety are Task 2; CSV is Task 3; external sync is Task 4; manual/automatic/retry writeback is Task 5; result visibility is Task 6; regression and protected-state checks are Task 7.
- Placeholder scan: no placeholder markers or unspecified future actions remain; every code-changing task names files, methods, tests, and commands.
- Type consistency: `TagExchangeResult` is passed from the exchange service to `CustomerRepository.upsert`, `FieldMappingResolver.mapRowResult`, and all writeback sanitizers; source metadata always uses `TagExchangeSourceType` plus a string row id.
- Scope check: the plan adds no migration, does not modify Step 8 or existing PENDING suggestions, and keeps the work limited to the three approved data-exchange entry points.
