# Realistic Customer Fixtures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 12 rich, fully synthetic customer profiles to the local manual-test database without changing any existing customer records.

**Architecture:** Use one repeatable SQL seed script. Customer rows use a reserved phone range and no-op duplicate handling; dynamic tags are inserted by joining category/value codes and are guarded against duplicate active assignments. The script runs inside one transaction, followed by read-only verification of preservation, profile completeness, tags, and followup grouping.

**Tech Stack:** MariaDB/MySQL, Flyway-managed local schema, WSL MySQL client, Spring Boot followup classification rules.

---

## File Structure

- Create `scripts/seed_realistic_customer_fixtures.sql`: transactionally insert the 12 synthetic customers and their dynamic tags.
- Read `docs/superpowers/specs/2026-07-21-realistic-customer-fixtures-design.md`: source of truth for personas and expected grouping.
- Do not create a Flyway migration: these records belong only to the local manual-test database and must never enter production deployments.

### Task 1: Create the Repeatable Seed Script

**Files:**
- Create: `scripts/seed_realistic_customer_fixtures.sql`

- [ ] **Step 1: Add transaction and customer inserts**

Create one `START TRANSACTION` block inserting phones `18810001001` through `18810001012`. Populate all fields specified by the design, use `CURRENT_DATE`-relative followup and appointment dates, and set:

```sql
assigned_keeper = 'admin'
source_table = '本地模拟客户档案'
source_row_id = 'FT20260721-CUST-001' ... 'FT20260721-CUST-012'
version = 0
```

Use this duplicate behavior so rerunning the script never modifies an existing row:

```sql
ON DUPLICATE KEY UPDATE phone = VALUES(phone);
```

- [ ] **Step 2: Add guarded dynamic-tag inserts**

Use a derived fixture table containing `phone`, `category_key`, `tag_value`, and evidence text. Join it to `customers`, `tag_categories`, and `tag_values`, then insert only assignments for which no active assignment already exists:

```sql
WHERE NOT EXISTS (
  SELECT 1
  FROM customer_tag_assignments existing
  WHERE existing.customer_id = customer.id
    AND existing.category_id = category.id
    AND existing.tag_value_id = tag_value.id
    AND existing.is_active = 1
)
```

Set `source_type='MANUAL'`, `operator_account='admin'`, `customer_version=customer.version`, and use each category's real `selection_mode`.

- [ ] **Step 3: Commit the transaction**

End the script with `COMMIT`. Do not add delete or update statements targeting customers outside the reserved phone range.

### Task 2: Preflight the Target Database

- [ ] **Step 1: Verify the database and reserved range**

Run:

```powershell
wsl.exe -d Ubuntu -- mysql -uroot --batch --raw -D private_domain_assistant_smoke -e "SELECT COUNT(*) FROM customers; SELECT phone FROM customers WHERE phone BETWEEN '18810001001' AND '18810001012';"
```

Expected: count `11`; no rows in the reserved range.

- [ ] **Step 2: Capture the preservation fingerprint**

Run a read-only checksum over all existing customers outside the reserved range using stable profile fields. Save the returned count and checksum in the execution log for comparison after seeding.

### Task 3: Execute the Transactional Seed

- [ ] **Step 1: Execute the SQL file**

Run:

```powershell
wsl.exe -d Ubuntu -- bash -lc "mysql -uroot --default-character-set=utf8mb4 private_domain_assistant_smoke < '/mnt/c/Users/85314/Desktop/私域工具/私域辅助系统/scripts/seed_realistic_customer_fixtures.sql'"
```

Expected: exit code `0` with no SQL error; transaction committed.

### Task 4: Verify Data Quality and Classification

- [ ] **Step 1: Verify counts and preservation**

Expected:

```text
total customers = 23
new fixture customers = 12
existing customer count and checksum unchanged
```

- [ ] **Step 2: Verify profile completeness**

Query the 12 rows and assert that all have nonblank nickname, source channel, lead type, assigned keeper, intended store, intended project, postpartum months, body concerns, intent level, customer stage, followup notes, source table, and source row id.

- [ ] **Step 3: Verify followup grouping**

Using the same conditions as `FollowupTodayService`, verify exactly:

```text
OVERDUE = 林晓雯, 赵欣怡, 梁静雯
DUE_TODAY = 陈雨晴, 黄思敏, 何佩珊
APPOINTMENT = 周雅婷, 吴佳宁
NO_TODAY_TASK = 郑婉婷, 罗子晴, 方雪莹, 彭梦琪
```

- [ ] **Step 4: Verify dynamic tags**

Query active assignments for the 12 new customers. Verify every customer has exactly one active intent-level tag, no single-select duplicate, and body/worry tags match the design.

- [ ] **Step 5: Verify runtime visibility**

Call the current local followup API using the existing logged-in desktop session or refresh the application. Confirm the new names appear in the expected tabs and the original 11 rows remain searchable.

## Repository Handling

Do not commit, push, merge, or discard work. The current branch contains substantial pre-existing user changes and the approved workflow keeps them in place.
