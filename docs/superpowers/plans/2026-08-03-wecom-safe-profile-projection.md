# WeCom Safe Profile Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Project the complete local customer profile to the WeCom Smart Table after a phone assignment without allowing invalid select values to block valid fields.

**Architecture:** Build the local profile field map from `Customer`, convert it through the existing datasource mapping, and filter only invalid select values against the cached WeCom field catalog. The existing orchestrator keeps create/update selection and queue retry behavior; only transport failures enter `pending_table_writes`.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, MariaDB datasource mappings, WeCom Smart Sheet API.

---

### Task 1: Cover Full Customer Profile Field Construction

**Files:**
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorTest.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java`

- [ ] **Step 1: Write the failing test**

Add a customer with a phone, nickname, lead type, customer stage, body concerns, follow-up notes, internal note, profile summary, next follow-up time, next follow-up direction, and all tracking captures. Assert `newCustomerFields(customer)` contains each nonblank internal field and excludes null fields.

```java
assertThat(creator.newCustomerFields(customer)).containsEntry("customerStage", "意向初筛")
    .containsEntry("bodyConcerns", "腰痛")
    .containsEntry("followupNotes", "首次咨询")
    .containsEntry("customerProfileSummary", "产后客户")
    .containsEntry("nextFollowupDir", "安排体验");
```

- [ ] **Step 2: Run the single test and verify it fails**

Run:

```bash
mvn -o -Dproject.build.directory=/tmp/pda-profile-projection-test -Dtest=NewCustomerRowCreatorTest test
```

Expected: failure because the phone-assignment field builder currently returns only `phone` and `nickname`.

- [ ] **Step 3: Implement complete local field construction**

Extend `newCustomerFields(Customer)` using a `LinkedHashMap`, adding only nonblank fields:

```java
fields.put("phone", customer.getPhone());
fields.put("nickname", customer.getNickname());
fields.put("leadType", LeadTypes.normalize(customer.getLeadType()));
fields.put("customerStage", customer.getCustomerStage());
fields.put("bodyConcerns", customer.getBodyConcerns());
fields.put("followupNotes", customer.getFollowupNotes());
fields.put("internalNote", customer.getInternalNote());
fields.put("customerProfileSummary", customer.getCustomerProfileSummary());
```

Add `nextFollowupAt`, `nextFollowupDir`, and the three tracking captures, then remove null and blank string values before returning.

- [ ] **Step 4: Run the single test and verify it passes**

Run the same Maven command. Expected: `NewCustomerRowCreatorTest` passes with zero failures.

- [ ] **Step 5: Commit the focused change**

```bash
git add src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorTest.java
git commit -m "feat(wecom): include full customer profile projection"
```

### Task 2: Filter Invalid Select Values Without Dropping Valid Fields

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/service/ProfileProjectionFieldFilter.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/service/ProfileProjectionFieldFilterTest.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java`
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorTest.java`

- [ ] **Step 1: Write failing filter tests**

Test a mapped single-select `客户阶段` with only `待联系` allowed and a text `客户关注点`. The input stage `意向初筛` must be omitted while the text concern remains. Add a second case where `待联系` is retained.

```java
assertThat(result.fields()).containsEntry("客户关注点", "腰痛")
    .doesNotContainKey("客户阶段");
assertThat(result.skippedSelectableFields()).contains("客户阶段");
```

- [ ] **Step 2: Run the filter tests and verify they fail**

Run:

```bash
mvn -o -Dproject.build.directory=/tmp/pda-profile-projection-test -Dtest=ProfileProjectionFieldFilterTest test
```

Expected: compilation failure because `ProfileProjectionFieldFilter` does not exist.

- [ ] **Step 3: Implement the filter**

Create a service that receives `TableFieldMappingResolver`, `WecomSmartSheetFieldCatalog`, and `TableConfigProvider`. It maps internal fields to source titles, loads `visibleFields(timeout)`, and removes only source fields whose type is `FIELD_TYPE_SINGLE_SELECT` or `FIELD_TYPE_SELECT` and whose normalized value is absent from `optionIdsByText`.

```java
if (isSelectable(field) && !field.optionIdsByText().containsKey(value.toString().trim())) {
  skipped.add(sourceField);
  continue;
}
accepted.put(sourceField, value);
```

Text, phone, date, and unmapped internal fields retain their existing behavior. The filter returns accepted source fields and skipped source field titles; it never mutates the source customer.

- [ ] **Step 4: Integrate the filter at the create boundary**

Replace direct `mappingResolver.toSourceFields(...)` in `NewCustomerRowCreator.createRow` with the filter result. Log the customer ID and skipped titles without logging field values. If no fields remain, retain the existing `TABLE_WRITE_FAILED` behavior.

- [ ] **Step 5: Run focused tests and verify they pass**

Run:

```bash
mvn -o -Dproject.build.directory=/tmp/pda-profile-projection-test -Dtest=ProfileProjectionFieldFilterTest,NewCustomerRowCreatorTest test
```

Expected: all tests pass, and the prior minimal phone/nickname behavior is replaced by full valid-field projection.

- [ ] **Step 6: Commit the filter**

```bash
git add src/main/java/com/privateflow/modules/tablewrite/service/ProfileProjectionFieldFilter.java src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java src/test/java/com/privateflow/modules/tablewrite/service/ProfileProjectionFieldFilterTest.java src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorTest.java
git commit -m "fix(wecom): skip invalid profile select values"
```

### Task 3: Preserve Retry Semantics for Safe Projection

**Files:**
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/TableWriteOrchestratorTest.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/TableWriteOrchestrator.java`

- [ ] **Step 1: Write the failing queue test**

Make an existing-row phone-assignment synchronization throw a transport exception after valid fields have been built. Assert the queued `PendingWritePayload` contains full local fields, not only phone and nickname.

```java
assertThat(capturedPayload.fields()).containsEntry("bodyConcerns", "腰痛")
    .containsEntry("followupNotes", "首次咨询");
```

- [ ] **Step 2: Run the orchestrator test and verify it fails**

Run:

```bash
mvn -o -Dproject.build.directory=/tmp/pda-profile-projection-test -Dtest=TableWriteOrchestratorTest test
```

Expected: failure because `syncFields(customer)` currently supplies only phone and nickname for an existing row.

- [ ] **Step 3: Implement consistent queued field construction**

Expose the same complete customer field map from `NewCustomerRowCreator` and use it for both new-row and existing-row phone-assignment synchronization. Keep the queued action type and source row ID unchanged. Do not enqueue a skipped-select validation result, because no transport failure occurred.

- [ ] **Step 4: Run the focused suite and verify it passes**

Run:

```bash
mvn -o -Dproject.build.directory=/tmp/pda-profile-projection-test -Dtest=ProfileProjectionFieldFilterTest,NewCustomerRowCreatorTest,TableWriteOrchestratorTest,CustomerPhoneAssignmentServiceTest,CustomerControllerTest test
```

Expected: all selected tests pass with zero failures or errors.

- [ ] **Step 5: Commit retry behavior**

```bash
git add src/main/java/com/privateflow/modules/tablewrite/service/TableWriteOrchestrator.java src/test/java/com/privateflow/modules/tablewrite/service/TableWriteOrchestratorTest.java
git commit -m "fix(wecom): retain profile fields in sync retries"
```

### Task 4: Live Acceptance for Customer 56

**Files:**
- No source changes required.

- [ ] **Step 1: Restart the isolated backend with encrypted Smart Table and relay configuration**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\WecomSmartSheet.ps1 -Mode Start
```

Expected: backend health endpoint reports success and the existing MariaDB customer data remains intact.

- [ ] **Step 2: Submit one customer-56 synchronization request**

Use the existing authenticated phone-assignment synchronization flow or the repaired queue path. Do not create a second customer row; update `source_row_id=4p1ph3`.

- [ ] **Step 3: Read back only the target Smart Table record**

Verify row `4p1ph3` has phone `13434567622`, nickname `少花`, and all supported text fields. Verify customer stage is present only if `意向初筛` is a current Smart Table option.

- [ ] **Step 4: Verify local state and Git state**

Run:

```bash
mysql -u pda_smoke -D private_domain_assistant_smoke -e "SELECT id, phone, source_row_id FROM customers WHERE id=56"
git status --short --branch
```

Expected: customer 56 retains its phone and row ID, queue has no pending entry for this synchronization, and generated Python cache remains untracked and excluded from commits.

- [ ] **Step 5: Push the tested branch**

```bash
git push origin feat/wecom-server-relay
```

