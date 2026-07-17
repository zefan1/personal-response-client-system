# Customer Search Export Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make customer list, pagination, export, and account data scope use the same validated `CustomerFilter` and `CustomerQuerySpec` path.

**Architecture:** Keep `CustomerFilterQueryBuilder` as the single SQL condition builder. Route legacy GET, structured POST, and the new CSV export through `CustomerAdminSearchService`, which validates input and resolves `CustomerAccessScope`; add a focused `CustomerCsvWriter` for deterministic UTF-8 CSV serialization and injection protection.

**Tech Stack:** Spring MVC, JdbcTemplate, JUnit 5, Mockito, H2, Vue 3, TypeScript, Vitest.

---

### Task 1: Lock the service and controller contracts with failing tests

**Files:**
- Modify: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchServiceTest.java`
- Modify: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchControllerTest.java`
- Modify: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryContractTest.java`

- [ ] **Step 1: Add the legacy-scope regression test.**

Add a test that creates the three-argument service, returns a normalized filter from the validator, returns a restricted scope from the resolver, calls `service.search("1111", 1, 20)`, and verifies `repository.search(normalizedFilter, restrictedScope)` rather than the old all-scope overload.

```java
@Test
void legacySearchUsesValidatedFilterAndCurrentScope() {
  CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
  CustomerFilterValidator validator = mock(CustomerFilterValidator.class);
  CustomerAccessScopeResolver scopeResolver = mock(CustomerAccessScopeResolver.class);
  CustomerCsvWriter csvWriter = mock(CustomerCsvWriter.class);
  CustomerAdminSearchService service = new CustomerAdminSearchService(repository, validator, scopeResolver, csvWriter);
  CustomerFilter normalized = CustomerFilter.empty();
  CustomerAccessScope scope = new CustomerAccessScope(false, List.of("keeper-1"), true);
  when(validator.validate(org.mockito.ArgumentMatchers.any(CustomerFilter.class))).thenReturn(normalized);
  when(scopeResolver.currentScope()).thenReturn(scope);
  when(repository.search(normalized, scope)).thenReturn(new CustomerAdminSearchPage(List.of(), 0, 1, 20, 1));

  service.search("1111", 1, 20);

  verify(validator).validate(org.mockito.ArgumentMatchers.any(CustomerFilter.class));
  verify(scopeResolver).currentScope();
  verify(repository).search(normalized, scope);
}
```

- [ ] **Step 2: Add the export service contract test.**

Add a test that stubs the validator, scope resolver, repository count/rows, and `CustomerCsvWriter.write(...)`, calls `service.export(request)`, and asserts the returned bytes contain the UTF-8 BOM and one CSV row. Construct the service with all four dependencies. Also add a test that stubs a count of `10_001` and expects `ApiException` with `导出客户数量超过 10000 条`.

- [ ] **Step 3: Add the export controller contract test.**

Add a standalone MockMvc test for `POST /admin/api/v1/customers/export` with a JSON `CustomerSearchRequest`. Return `"\uFEFF客户ID,手机号\r\n".getBytes(UTF_8)` from the mock service and assert status 200, `Content-Type` containing `text/csv`, `Content-Disposition` containing `customers.csv`, and the exact response bytes.

- [ ] **Step 4: Require the repository export entry point.**

Extend `CustomerAdminSearchRepositoryContractTest` with reflection checks for:

```java
CustomerAdminSearchRepository.class.getMethod(
    "count", CustomerFilter.class, CustomerAccessScope.class);
CustomerAdminSearchRepository.class.getMethod(
    "exportRows", CustomerFilter.class, CustomerAccessScope.class, int.class);
```

- [ ] **Step 5: Run the focused backend tests and verify RED.**

Run:

```powershell
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=com.privateflow.modules.customer.admin.CustomerAdminSearchServiceTest,com.privateflow.modules.customer.admin.CustomerAdminSearchControllerTest,com.privateflow.modules.customer.admin.CustomerAdminSearchRepositoryContractTest test
```

Expected: FAIL because the service export method, controller export mapping, and repository count/export methods do not exist yet; no compilation or test fixture errors are acceptable.

### Task 2: Add repository and CSV serialization tests

**Files:**
- Create: `src/test/java/com/privateflow/modules/customer/admin/CustomerCsvWriterTest.java`
- Modify: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryTest.java`

- [ ] **Step 1: Write CSV writer tests first.**

Create a `CustomerAdminListItem` whose nickname starts with `=`, whose source contains a quote, and whose tags contain category name, display name, and internal code. Assert the writer output has BOM, quoted escaped cells, a leading apostrophe before the formula value, and a tag cell such as `身体关注:腹直肌分离[DIASTASIS]`.

- [ ] **Step 2: Write restricted repository export tests.**

Extend the existing H2 fixture with a third customer assigned to `keeper-2`. Build a filter with the existing ALL tag group and call both `repository.search(filter, new CustomerAccessScope(false, List.of("keeper-1"), true))` and `repository.exportRows(filter, restrictedScope, 100)`. Assert both results contain only customer 1 and have the same tag values.

- [ ] **Step 3: Write the export limit test at repository/service boundary.**

Keep the limit decision in the service test from Task 1; repository tests should only prove that `count` returns the same total used by list queries and that `exportRows` honors the caller-provided limit and order.

- [ ] **Step 4: Run repository and writer tests to verify RED.**

Run:

```powershell
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=com.privateflow.modules.customer.admin.CustomerAdminSearchRepositoryTest,com.privateflow.modules.customer.admin.CustomerCsvWriterTest test
```

Expected: FAIL only on missing repository methods and missing writer class.

### Task 3: Add the frontend POST-blob contract tests

**Files:**
- Modify: `desktop/src/renderer/shared/apiClient.test.ts`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Add the `postBlob` client test.**

Import `postBlob` from `apiClient` after stubbing `fetch` with a CSV `Response`. Assert the request uses `POST`, sends `Content-Type: application/json`, includes the bearer token, serializes the request body, and returns the filename from `Content-Disposition`.

- [ ] **Step 2: Add the AdminConsole export interaction test.**

Add `postBlob` to the hoisted API mock. Mount the console, open “客户数据对接”, click `导出当前查询`, and assert:

```ts
expect(apiMocks.postBlob).toHaveBeenCalledWith('/admin/api/v1/customers/export', expect.objectContaining({
  keyword: '',
  tagGroups: [],
  tagGroupLogic: 'AND'
}));
expect(apiMocks.postBlob.mock.calls[0][1]).not.toHaveProperty('page');
expect(apiMocks.postBlob.mock.calls[0][1]).not.toHaveProperty('pageSize');
```

Return `{ blob: new Blob(['...'], { type: 'text/csv' }), filename: 'customers.csv' }` and assert the success notice is rendered.

- [ ] **Step 3: Run the focused frontend tests and verify RED.**

Run:

```powershell
Set-Location desktop
npm test -- --run src/renderer/shared/apiClient.test.ts src/renderer/modules/admin/AdminConsole.test.ts
```

Expected: FAIL because `postBlob`, the export button, and the export handler do not exist yet.

### Task 4: Implement the unified backend service and GET permission path

**Files:**
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchService.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchController.java`
- Modify: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchControllerTest.java`

- [ ] **Step 1: Remove the production fallback constructor.**

Use constructor injection for `CustomerAdminSearchRepository`, `CustomerFilterValidator`, `CustomerAccessScopeResolver`, and `CustomerCsvWriter`. Update tests that instantiate the service to provide mocks; do not keep a null validator/scope fallback that could silently restore all-customer access.

- [ ] **Step 2: Route legacy GET through validation and scope.**

Build the existing keyword-only `CustomerFilter`, call the same private `validatedFilter` path used by structured POST, resolve `currentScope()`, and call `repository.search(filter, scope)`. Preserve the existing Chinese bounds messages before validation.

- [ ] **Step 3: Implement the export service method.**

Add:

```java
public byte[] export(CustomerSearchRequest request) {
  CustomerFilter filter = filterValidator.validate(request == null ? null : request.toFilter());
  CustomerAccessScope scope = accessScopeResolver.currentScope();
  long total = repository.count(filter, scope);
  if (total > MAX_EXPORT_ROWS) {
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "导出客户数量超过 10000 条，请缩小筛选范围");
  }
  return csvWriter.write(repository.exportRows(filter, scope, (int) total));
}
```

Keep `MAX_EXPORT_ROWS = 10_000` in the service and use the exact same validated filter and scope for count and rows.

- [ ] **Step 4: Add the CSV response mapping.**

Add `POST /admin/api/v1/customers/export` with an optional request body and return `ResponseEntity<byte[]>` using UTF-8 `text/csv`, attachment filename `customers.csv`, and the service bytes. Keep the existing search endpoints unchanged at the URL level.

- [ ] **Step 5: Run the Task 1 tests and verify GREEN.**

Run the Maven command from Task 1. Expected: all service/controller/contract tests pass.

- [ ] **Step 6: Commit the backend contract and service changes.**

```powershell
git add src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchService.java src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchController.java src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchServiceTest.java src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchControllerTest.java src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryContractTest.java
git commit -m "feat: align customer search access scope"
```

### Task 5: Implement the repository query reuse and CSV writer

**Files:**
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepository.java`
- Create: `src/main/java/com/privateflow/modules/customer/admin/CustomerCsvWriter.java`
- Modify: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryTest.java`
- Create: `src/test/java/com/privateflow/modules/customer/admin/CustomerCsvWriterTest.java`

- [ ] **Step 1: Extract a shared count query.**

Implement `count(CustomerFilter, CustomerAccessScope)` by building one `CustomerQuerySpec` and running `SELECT COUNT(*) FROM customers c` plus its where clause and args.

- [ ] **Step 2: Extract shared row loading.**

Implement `exportRows(CustomerFilter, CustomerAccessScope, int limit)` with the same `CustomerQuerySpec`, `ORDER BY query.orderClause()`, `LIMIT ?`, and offset `0` used by list search. Reuse `toListItem` and `loadCurrentTagSummaries` so exported tags are exactly the tags shown by the list.

- [ ] **Step 3: Make list search call the shared count/row helpers.**

Keep `CustomerAdminSearchPage` pagination metadata unchanged. The list path must continue using the validated filter page and size; only the SQL assembly should be shared.

- [ ] **Step 4: Implement deterministic CSV serialization.**

Create `CustomerCsvWriter.write(List<CustomerAdminListItem>)` with a UTF-8 BOM and a fixed header. Serialize each record in the same order as the design document, format tags as `categoryName:displayName[tagValue]` joined by `；`, quote every cell, escape quotes by doubling them, and prefix a single quote when the first character is `=`, `+`, `-`, or `@`.

- [ ] **Step 5: Run repository and writer tests and verify GREEN.**

```powershell
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=com.privateflow.modules.customer.admin.CustomerAdminSearchRepositoryTest,com.privateflow.modules.customer.admin.CustomerCsvWriterTest,com.privateflow.modules.customer.admin.CustomerAdminSearchServiceTest test
```

Expected: all listed tests pass with no warnings or errors.

- [ ] **Step 6: Commit the repository and CSV implementation.**

```powershell
git add src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepository.java src/main/java/com/privateflow/modules/customer/admin/CustomerCsvWriter.java src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryTest.java src/test/java/com/privateflow/modules/customer/admin/CustomerCsvWriterTest.java
git commit -m "feat: add permission-aware customer csv export"
```

### Task 6: Implement the frontend blob download and customer export action

**Files:**
- Modify: `desktop/src/renderer/shared/apiClient.ts`
- Modify: `desktop/src/renderer/shared/apiClient.test.ts`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Add `postBlob` to the API client.**

Mirror `getBlob` with method `POST`, JSON content type, bearer authorization, timeout/abort handling, `readErrorPayload` on non-2xx responses, and `filenameFromDisposition` on success. Keep the existing network failure and auth-expiry behavior.

- [ ] **Step 2: Expose the wrapper in AdminConsole.**

Import `postBlob as requestPostBlob`, add a local `postBlob` wrapper that asserts the error envelope only on errors, and keep the existing `downloadBlob` helper for the actual browser download.

- [ ] **Step 3: Add the customer export button and handler.**

Place `导出当前查询` beside `查询客户`. Implement `customerSearchExportRequest()` by removing `page` and `pageSize` from `customerSearchRequest()`. Call `requestPostBlob('/admin/api/v1/customers/export', payload)` inside `runWithNotice`, then download the returned filename or `customers.csv`.

- [ ] **Step 4: Run focused frontend tests and verify GREEN.**

```powershell
Set-Location desktop
npm test -- --run src/renderer/shared/apiClient.test.ts src/renderer/modules/admin/AdminConsole.test.ts
```

Expected: all existing tests plus the new export tests pass.

- [ ] **Step 5: Commit the frontend implementation.**

```powershell
git add desktop/src/renderer/shared/apiClient.ts desktop/src/renderer/shared/apiClient.test.ts desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts
git commit -m "feat: add customer search csv download"
```

### Task 7: Run regression verification and update progress records

**Files:**
- Modify: `dev-progress/tag_skill_llm_tasklist_056.md`
- Create: `dev-progress/tag_skill_llm_step9e_breakpoint_072.md`

- [ ] **Step 1: Run the targeted backend suite.**

```powershell
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q -Dtest=com.privateflow.modules.customer.admin.**.*Test,com.privateflow.modules.analytics.**.*Test,com.privateflow.modules.followup.**.*Test,com.privateflow.modules.tags.**.*Test,com.privateflow.modules.tablewrite.**.*Test test
```

Expected: zero failures and zero errors; record the exact test count.

- [ ] **Step 2: Run frontend regression checks.**

```powershell
Set-Location desktop
npm test -- --run
npm run typecheck
npm run build
```

Expected: all Vitest files pass, typecheck passes, and production build completes.

- [ ] **Step 3: Run the no-migration and protected-state checks.**

Verify with `git diff` and repository queries that no Flyway migration was added, the six existing `system_tag_suggestions.status=PENDING` rows were not modified, both LLM switches remain `false`, and Step 8 reply-tag behavior is unchanged.

- [ ] **Step 4: Update the task list and breakpoint.**

Mark the Step 9 pagination/export/data-permission checkbox complete. Add `tag_skill_llm_step9e_breakpoint_072.md` with the final HEAD, changed files, test commands/results, service URLs if running, and the remaining Step 9 item for the six pending suggestions plus Step 10 full acceptance.

- [ ] **Step 5: Commit progress documentation.**

```powershell
git add dev-progress/tag_skill_llm_tasklist_056.md dev-progress/tag_skill_llm_step9e_breakpoint_072.md
git commit -m "docs: record step 9e customer export checkpoint"
```
