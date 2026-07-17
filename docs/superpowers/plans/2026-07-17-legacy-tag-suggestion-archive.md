# Legacy Tag Suggestion Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an idempotent V70 data migration that archives the six legacy free-text tag suggestions, preserves their audit data, and changes the two built-in rules to ordinary follow-up reminders.

**Architecture:** Keep all existing rows and raw text. Apply one Flyway data migration from the V69 schema: archive linked unmatched values and suggestions, then change only built-in rules 4 and 5 from `TAG_CHANGE` to `ALERT`/`NOTIFY_LEADER`. Verify the full transition in the existing conditional MariaDB integration test before running the normal module regression suite.

**Tech Stack:** Flyway SQL, MariaDB, JdbcTemplate, JUnit 5, Spring Boot migration integration tests.

---

### Task 1: Add a failing V70 migration integration test

**Files:**
- Modify: `src/test/java/com/privateflow/modules/tags/TagFlywayMariaDbIntegrationTest.java`

- [ ] **Step 1: Change the fresh migration expectation to V70.**

Update the existing fresh-database assertion from `first.targetSchemaVersion == "69"` to `"70"`, and update the history query to check `version='70'`. Do not change the existing V69 structural assertions.

- [ ] **Step 2: Seed the six legacy rows after applying V69 but before V70.**

In a new test method guarded by `@EnabledIfEnvironmentVariable(named = "TAG_FLYWAY_9F_IT", matches = "true")`, use the dedicated `TAG_FLYWAY_9F_URL` datasource so this two-phase test cannot share state with the existing fresh-migration test. Create a Flyway instance targeted at version `69`, call `migrate()`, then use `JdbcTemplate` to insert three customers, six `unmatched_legacy_tag_values` rows, and six `system_tag_suggestions` rows linked to rules 4 and 5. Use the exact legacy values and IDs from the audit:

```java
String url = required("TAG_FLYWAY_9F_URL");
String username = required("TAG_FLYWAY_9F_USERNAME");
String password = System.getenv().getOrDefault("TAG_FLYWAY_9F_PASSWORD", "");
```

```java
String[] phones = {"18800001111", "18800003333", "18800002222"};
String[] tagNames = {"沉睡风险", "可能流失"};
long suggestionId = 1;
long unmatchedId = 16;
for (String phone : phones) {
  jdbcTemplate.update("INSERT INTO customers (phone) VALUES (?)", phone);
  Long customerId = jdbcTemplate.queryForObject(
      "SELECT id FROM customers WHERE phone=?", Long.class, phone);
  for (int index = 0; index < tagNames.length; index++) {
    jdbcTemplate.update("""
        INSERT INTO unmatched_legacy_tag_values (
          id, customer_id, source_type, source_record_id, legacy_field,
          raw_value, raw_value_hash, status
        ) VALUES (?, ?, 'SYSTEM_TAG_SUGGESTION', ?, 'systemTagSuggestion', ?, ?, 'PENDING')
        """, unmatchedId, customerId, suggestionId, tagNames[index],
        String.format("%064d", suggestionId));
    jdbcTemplate.update("""
        INSERT INTO system_tag_suggestions (
          id, phone, customer_id, tag_name, rule_id, validation_status,
          unmatched_legacy_value_id, status
        ) VALUES (?, ?, ?, ?, ?, 'UNMATCHED_LEGACY', ?, 'PENDING')
        """, suggestionId, phone, customerId, tagNames[index], index == 0 ? 4 : 5, unmatchedId);
    suggestionId++;
    unmatchedId++;
  }
}
```

The formatted 64-character value satisfies the schema length and gives the test a stable raw hash to compare before and after V70.

- [ ] **Step 3: Assert the pre-migration baseline.**

Before running the full Flyway migration, assert there are six `PENDING` suggestions, six linked `PENDING` unmatched values, and rules 4/5 still have `action_type='TAG_CHANGE'`.

- [ ] **Step 4: Run V70 and assert the desired transition.**

Call a new full Flyway instance with the same datasource and `migrate()`. Assert:

```java
assertThat(jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM system_tag_suggestions WHERE id BETWEEN 1 AND 6 AND status='IGNORED'",
    Integer.class)).isEqualTo(6);
assertThat(jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM unmatched_legacy_tag_values WHERE id BETWEEN 16 AND 21 AND status='IGNORED'",
    Integer.class)).isEqualTo(6);
assertThat(jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM unmatched_legacy_tag_values WHERE id BETWEEN 16 AND 21 AND resolved_by='SYSTEM_MIGRATION_9F'",
    Integer.class)).isEqualTo(6);
assertThat(jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM followup_rules WHERE id=4 AND action_type='ALERT'",
    Integer.class)).isEqualTo(1);
assertThat(jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM followup_rules WHERE id=5 AND action_type='NOTIFY_LEADER'",
    Integer.class)).isEqualTo(1);
assertThat(jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM customer_tag_assignments",
    Integer.class)).isEqualTo(0);
```

Also query the six rows and assert `tag_name`, `rule_id`, `customer_id`, `unmatched_legacy_value_id`, `raw_value`, and `raw_value_hash` are unchanged.

- [ ] **Step 5: Verify the test fails before V70 exists.**

Run:

```powershell
$env:TAG_FLYWAY_9F_IT='true'
$env:TAG_FLYWAY_9F_URL='jdbc:mariadb://127.0.0.1:3306/private_domain_assistant_smoke_9f'
$env:TAG_FLYWAY_9F_USERNAME='pda_smoke'
$env:TAG_FLYWAY_9F_PASSWORD='pda_smoke_pwd'
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q "-Dtest=com.privateflow.modules.tags.TagFlywayMariaDbIntegrationTest" test
```

Expected: FAIL because migration V70 does not exist and the target version remains V69. If MariaDB is unavailable, the conditional test may skip; in that case use the same SQL assertions against the running smoke database without claiming the integration test passed.

### Task 2: Implement the V70 archive migration

**Files:**
- Create: `src/main/resources/db/migration/V70__archive_legacy_tag_suggestions.sql`

- [ ] **Step 1: Archive linked unmatched values.**

Add this first statement so the original suggestion rows are still `PENDING` while the join selects them:

```sql
UPDATE unmatched_legacy_tag_values u
JOIN system_tag_suggestions s ON s.unmatched_legacy_value_id = u.id
JOIN followup_rules r ON r.id = s.rule_id
SET u.status = 'IGNORED',
    u.resolution_note = COALESCE(NULLIF(u.resolution_note, ''),
      '旧内置跟进规则自由文本，不属于正式客户标签；已由 Step 9F 系统归档'),
    u.resolved_by = COALESCE(NULLIF(u.resolved_by, ''), 'SYSTEM_MIGRATION_9F'),
    u.resolved_at = COALESCE(u.resolved_at, NOW())
WHERE u.status = 'PENDING'
  AND s.status = 'PENDING'
  AND s.validation_status = 'UNMATCHED_LEGACY'
  AND u.source_type = 'SYSTEM_TAG_SUGGESTION'
  AND r.is_builtin = 1
  AND r.id IN (4, 5)
  AND r.action_type = 'TAG_CHANGE';
```

- [ ] **Step 2: Archive the six suggestion rows without deleting history.**

Add a second UPDATE that joins rule 4/5 and changes only pending unmatched legacy suggestions:

```sql
UPDATE system_tag_suggestions s
JOIN followup_rules r ON r.id = s.rule_id
SET s.status = 'IGNORED',
    s.ignored_at = COALESCE(s.ignored_at, NOW())
WHERE s.status = 'PENDING'
  AND s.validation_status = 'UNMATCHED_LEGACY'
  AND s.unmatched_legacy_value_id IS NOT NULL
  AND r.is_builtin = 1
  AND r.id IN (4, 5)
  AND r.action_type = 'TAG_CHANGE';
```

- [ ] **Step 3: Convert only the two built-in legacy rules.**

Use rule IDs, names, and `is_builtin=1` to avoid changing custom rules. Keep `condition_json` and `action_config` untouched:

```sql
UPDATE followup_rules
SET action_type = CASE id WHEN 4 THEN 'ALERT' WHEN 5 THEN 'NOTIFY_LEADER' END
WHERE is_builtin = 1
  AND action_type = 'TAG_CHANGE'
  AND id IN (4, 5)
  AND name IN ('沉睡风险', '可能流失');
```

- [ ] **Step 4: Run the migration integration test and verify GREEN.**

Run the Task 1 command again. Expected: the V70 test passes, the existing fresh migration test reports target version `70`, and the second Flyway run performs zero migrations.

- [ ] **Step 5: Commit the migration and integration test.**

```powershell
git add src/main/resources/db/migration/V70__archive_legacy_tag_suggestions.sql src/test/java/com/privateflow/modules/tags/TagFlywayMariaDbIntegrationTest.java
git commit -m "feat: archive legacy tag suggestions"
```

### Task 3: Add regression coverage for runtime rule behavior and statistics

**Files:**
- Modify: `src/test/java/com/privateflow/modules/followup/service/ActionExecutorTest.java`
- Modify: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java`

- [ ] **Step 1: Add the ordinary-reminder regression test.**

In `ActionExecutorTest`, construct an `ALERT` match using the migrated rule shape and assert `TagSuggestionRepository.upsertPending(...)` is never called, while the ordinary reminder log/event path still runs. Repeat for `NOTIFY_LEADER` with the existing test helper so both migrated action types are covered.

- [ ] **Step 2: Add the archived-unmatched statistics test.**

Extend the analytics H2 fixture with one `unmatched_legacy_tag_values` row in `IGNORED` status and assert the `UNMATCHED_LEGACY_VALUE` reason count remains zero for that customer. Keep the existing `PENDING` row assertion to prove unresolved values still count.

- [ ] **Step 3: Run focused regression tests.**

```powershell
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q "-Dtest=com.privateflow.modules.followup.service.ActionExecutorTest,com.privateflow.modules.analytics.TagAnalyticsRepositoryTest" test
```

Expected: all tests pass.

- [ ] **Step 4: Commit the runtime/statistics regression tests.**

```powershell
git add src/test/java/com/privateflow/modules/followup/service/ActionExecutorTest.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java
git commit -m "test: protect archived legacy tag behavior"
```

### Task 4: Verify protected state and update Step 9F progress

**Files:**
- Modify: `dev-progress/tag_skill_llm_tasklist_056.md`
- Create: `dev-progress/tag_skill_llm_step9f_breakpoint_073.md`

- [ ] **Step 1: Run the related backend regression suite.**

```powershell
C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd -q "-Dtest=com.privateflow.modules.customer.admin.**.*Test,com.privateflow.modules.analytics.**.*Test,com.privateflow.modules.followup.**.*Test,com.privateflow.modules.tags.**.*Test,com.privateflow.modules.tablewrite.**.*Test" test
```

Expected: zero failures and zero errors; record the exact test count and conditional skips.

- [ ] **Step 2: Verify the live database state read-only.**

Run these queries against `private_domain_assistant_smoke` after the migration:

```sql
SELECT COUNT(*) FROM system_tag_suggestions WHERE id BETWEEN 1 AND 6 AND status='IGNORED';
SELECT COUNT(*) FROM unmatched_legacy_tag_values WHERE id BETWEEN 16 AND 21 AND status='IGNORED';
SELECT id, action_type FROM followup_rules WHERE id IN (4,5) ORDER BY id;
SELECT COUNT(*) FROM customer_tag_assignments;
SELECT config_key, config_value FROM system_configs WHERE config_key IN ('llm.reply_generation.enabled','llm.profile_extraction.enabled');
```

Expected: `6`, `6`, `4/ALERT` and `5/NOTIFY_LEADER`, unchanged assignment count, and both LLM values `false`.

- [ ] **Step 3: Mark Step 9F complete and write the breakpoint.**

Record migration version, exact row counts, raw-value preservation, test commands, current service URLs, and the remaining Step 10 work. Explicitly note that no customer tag assignment or old customer field was written.

- [ ] **Step 4: Commit progress documentation.**

```powershell
git add dev-progress/tag_skill_llm_tasklist_056.md dev-progress/tag_skill_llm_step9f_breakpoint_073.md
git commit -m "docs: record step 9f legacy suggestion archive"
```
