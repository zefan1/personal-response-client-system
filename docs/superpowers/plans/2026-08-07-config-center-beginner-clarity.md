# Config Center Beginner Clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the configuration center understandable for first-time users while preserving existing configuration keys and adding a one-link verification flow for the API-created WeCom Smart Sheet.

**Architecture:** Keep the existing admin configuration contracts intact and add one focused Smart Sheet verification endpoint under datasource administration. The endpoint accepts a full browser URL, verifies that it identifies the already configured API-owned document/sheet/view through the live WeCom API, then stores only the verified URL for display. Frontend changes remain in the existing admin console and progressively disclose technical settings.

**Tech Stack:** Vue 3, TypeScript, Vitest, Spring Boot, Java 21, JdbcTemplate, JUnit 5, Mockito, Flyway, CSS.

---

### Task 1: Verify API-created Smart Sheet links in the backend

**Files:**
- Create: `src/main/java/com/privateflow/modules/customer/admin/SmartSheetConnectionRequest.java`
- Create: `src/main/java/com/privateflow/modules/customer/admin/SmartSheetConnectionResult.java`
- Create: `src/main/java/com/privateflow/modules/customer/admin/SmartSheetConnectionService.java`
- Create: `src/test/java/com/privateflow/modules/customer/admin/SmartSheetConnectionServiceTest.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminController.java`
- Create: `src/main/resources/db/migration/V92__smart_sheet_document_url.sql`

- [ ] **Step 1: Write failing service tests**

Cover these cases with mocked `WecomSmartSheetApiClient`: blank/malformed URL, URL not containing the configured API-owned document ID, configured document missing the required child sheet, configured view missing, and success returning a Chinese display name. The success test must verify `ConfigAdminService.update("table.document_url", Map.of("value", url))`.

```java
@Test
void verifiesAndStoresConfiguredApiOwnedDocumentUrl() {
  when(api.post(eq("get_sheet"), any(), any())).thenReturn(sheetResponse("sheet-api", "客户表"));
  when(api.post(eq("get_views"), any(), any())).thenReturn(viewResponse("view-api"));

  SmartSheetConnectionResult result = service.verifyAndSave(
      new SmartSheetConnectionRequest("https://doc.weixin.qq.com/smartsheet/doc-api"));

  assertThat(result.connected()).isTrue();
  assertThat(result.tableName()).isEqualTo("客户表");
  verify(configAdminService).update("table.document_url", Map.of(
      "value", "https://doc.weixin.qq.com/smartsheet/doc-api"));
}
```

- [ ] **Step 2: Run the backend test and verify RED**

Run:

```bash
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统' && mvn -Dtest=SmartSheetConnectionServiceTest test"
```

Expected: FAIL because the request, result, and service types do not exist.

- [ ] **Step 3: Add the request, result, migration, and service**

Use records with a narrow contract:

```java
public record SmartSheetConnectionRequest(String documentUrl) {}

public record SmartSheetConnectionResult(
    boolean connected,
    String tableName,
    String documentId,
    String sheetId,
    String viewId,
    String documentUrl) {}
```

`SmartSheetConnectionService.verifyAndSave` must:

1. Require an absolute `http` or `https` URI.
2. Call `WecomSmartSheetConfig.requireConfigured()`.
3. Require the normalized link to contain the configured `documentId`; otherwise throw `ApiException(BAD_REQUEST, "该表格不是本系统通过企业微信 API 创建并纳入的数据表")`.
4. Call `get_sheet` and require the configured `sheetId`.
5. Call `get_views` and require the configured `viewId`.
6. Save `table.document_url` only after all checks pass.
7. Return a Chinese table name when WeCom provides `sheet_name`, `title`, or `name`; otherwise return `已连接的 API 表格`.

Add Flyway seed:

```sql
INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.document_url', '', 'Verified browser URL for the API-owned WeCom Smart Sheet'
WHERE NOT EXISTS (
  SELECT 1 FROM system_configs WHERE config_key = 'table.document_url'
);
```

- [ ] **Step 4: Add the controller endpoint**

Add:

```java
@PostMapping("/admin/api/v1/datasources/smart-sheet-connection")
public ApiResponse<SmartSheetConnectionResult> verifySmartSheet(
    @RequestBody SmartSheetConnectionRequest request) {
  return ApiResponse.ok(smartSheetConnectionService.verifyAndSave(request));
}
```

Inject `SmartSheetConnectionService` alongside the existing datasource service.

- [ ] **Step 5: Run focused backend tests and verify GREEN**

Run:

```bash
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统' && mvn -Dtest=SmartSheetConnectionServiceTest,DatasourceAdminControllerTest test"
```

Expected: PASS.

### Task 2: Lock the beginner-facing behavior with frontend tests

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Write failing tests for the ten requested changes**

Add assertions for:

```ts
expect(promptEditor.textContent).toContain('每行一条，按回车换行，不使用分号');
expect(promptEditor.textContent).toContain('已有默认内容时通常无需修改');
expect(findButton(host, '管理不同 Skill')).toBeFalsy();
expect(findButton(host, '新建')).toBeTruthy();
expect(mainText(host)).not.toContain('新增环境');
expect(mainText(host)).not.toContain('自动同步 Cron');
expect(mainText(host)).not.toContain('客户缓存 TTL');
expect(mainText(host)).toContain('仅支持由企业微信 API 创建并纳入本系统的数据表');
```

Open the advanced section and assert that the primary Smart Sheet card has one URL input and one `检测并保存` button, while gateway fields appear only after `展开服务器部署配置`.

Assert that the image selector options come from `/admin/api/v1/image-environments`, and that an empty API response produces a disabled select with only `未配置`.

Assert that consecutive question marks are not rendered and the configured model name is used as fallback.

Open a work-task modal and assert the visible labels are `模型不可用时改用原有助手`, `回答变化程度`, `回答长度`, and `给模型的任务说明`; assert the raw prompt editor is hidden until `查看高级任务说明` is clicked.

- [ ] **Step 2: Run the focused frontend test and verify RED**

Run:

```bash
cd desktop && npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
```

Expected: FAIL on missing labels, duplicate Skill card, text input instead of select, and missing Smart Sheet endpoint call.

### Task 3: Implement the configuration-center behavior

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`

- [ ] **Step 1: Replace duplicate and unclear top-level UI**

Remove only `.ops-skill-scene-entry` from the configuration-center template. Keep the left navigation section, `skillBindings`, API loading, and independent Skill page unchanged.

Rename both environment actions from `新增环境` to `新建`.

Add helpers:

```ts
function hasBrokenEncoding(value: unknown): boolean {
  return /\?{2,}/.test(String(value ?? ''));
}

function environmentDisplayName(environment: AnyRecord, fallback: string): string {
  const name = String(environment.envName ?? '').trim();
  if (name && !hasBrokenEncoding(name)) return name;
  const model = String(environment.model ?? '').trim();
  return model || fallback;
}
```

Use the helper in environment cards and summary metrics.

- [ ] **Step 2: Add the image-environment selector**

Add `selectedImageEnvironmentId` derived from the active environment. Render a `<select aria-label="使用哪个识图模型">` whose options are readable environment names. When saved, activate the selected environment through the existing `/admin/api/v1/image-environments/{id}/activate` endpoint before saving the existing image runtime keys. If the list is empty, render one disabled option `未配置` and a nearby `新建` button.

- [ ] **Step 3: Add the one-link Smart Sheet flow**

Add reactive state:

```ts
const smartSheetConnectionDraft = reactive({ documentUrl: '', connectedName: '' });
const tableServerSettingsExpanded = ref(false);
```

Hydrate `documentUrl` from `table.document_url`. Submit:

```ts
const response = await postJson<unknown>(
  '/admin/api/v1/datasources/smart-sheet-connection',
  { documentUrl: smartSheetConnectionDraft.documentUrl.trim() }
);
const result = recordFromResponse(response);
smartSheetConnectionDraft.connectedName = String(result.tableName || '已连接的 API 表格');
```

The visible copy must explain copying the full browser URL and must state that manually created ordinary Smart Sheets are unsupported. Move all existing gateway fields into a disclosure labelled `服务器部署配置`.

- [ ] **Step 4: Clarify prompt and sync settings**

For red lines, add `每行一条，按回车换行，不使用分号。` and a multiline Chinese placeholder. For the output template, add a Chinese example and `已有默认内容时通常无需修改。`.

Rename sync labels without changing models or save keys:

- `自动同步 Cron` -> `自动同步时间规则`
- `客户缓存 TTL（秒）` -> `客户资料多久重新读取一次（秒）`
- `同步 API 超时（毫秒）` -> `同步多久没响应算失败（毫秒）`
- `映射版本保留数` -> `保留最近多少次字段设置`
- `CSV 单次导入行数` -> `一次最多导入多少行`
- `手动同步超时（秒）` -> `手动同步最多等待多久（秒）`
- `同步状态刷新（秒）` -> `同步进度多久刷新一次（秒）`

Keep only `自动同步时间规则` and `一次最多导入多少行` visible; place the other five controls in `同步异常时再调整`.

- [ ] **Step 5: Simplify work-task modals**

Rename labels as specified by tests. Replace the free numeric temperature input with a select offering `使用模型默认设置`, `回答更稳定`, `平衡`, and `表达更多变化`, while preserving an existing custom value as a temporary `当前设置` option. Replace the raw max-token label with `回答长度` and plain helper text. Put the prompt textarea inside a collapsed disclosure `查看高级任务说明`.

- [ ] **Step 6: Run the focused frontend test and verify GREEN**

Run:

```bash
cd desktop && npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
```

Expected: all `AdminConsole.test.ts` tests pass.

### Task 4: Align and polish the existing visual system

**Files:**
- Modify: `desktop/src/renderer/styles.css`

- [ ] **Step 1: Add stable task-row and disclosure layout**

Keep the existing warm neutral and orange tokens. Give the test/status slot a stable width and center alignment so `后台测试` and `需工作台验收` occupy the same column. Add styles for the Smart Sheet connection row, nested disclosures, helper text, and the disabled `未配置` selector. Do not introduce blue accents.

- [ ] **Step 2: Run the design detector**

Run:

```bash
node C:/Users/85314/.agents/skills/impeccable/scripts/detect.mjs --json desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/styles.css
```

Expected: no new blocking design findings caused by these changes.

### Task 5: Full verification and live acceptance

**Files:**
- Verify only; no planned production edits.

- [ ] **Step 1: Run full frontend verification**

```bash
cd desktop && npm test -- --run
cd desktop && npm run typecheck
cd desktop && npm run build
```

Expected: all tests pass, typecheck exits 0, and build exits 0. The existing Vite chunk-size warning is non-blocking.

- [ ] **Step 2: Run backend regression tests**

```bash
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统' && mvn -Dtest=SmartSheetConnectionServiceTest,DatasourceAdminControllerTest,AiEnvironmentServiceTest,AiConfigControllerTest test"
```

Expected: PASS.

- [ ] **Step 3: Check the worktree diff**

```bash
git diff --check -- desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts desktop/src/renderer/styles.css src/main/java/com/privateflow/modules/customer/admin src/test/java/com/privateflow/modules/customer/admin src/main/resources/db/migration/V92__smart_sheet_document_url.sql
```

Expected: no whitespace errors. Do not revert unrelated user changes in these already modified frontend files.

- [ ] **Step 4: Verify the real admin page**

At `http://127.0.0.1:5173/#/admin`, verify desktop layout, warm orange palette, prompt instructions, image selector, aligned task rows, collapsed advanced prompt, removed duplicate Skill card, readable environment fallback names, and the Smart Sheet link flow. Do not submit a real external table link without explicit acceptance data.

### Task 6: Correct the configuration-center information hierarchy

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/styles.css`

- [ ] **Step 1: Write the failing hierarchy test**

Before opening advanced configuration, assert that both `企业微信连接方式` and `连接企业微信智能表格` are visible and carry the shared `configuration-connection-panel` class. Assert that `LLM 场景路由`, `LLM 调用统计`, `Skill 运行参数`, `识图运行参数`, and `数据同步策略` are absent.

After clicking `展开高级运行配置`, assert that the five low-frequency group buttons appear while their forms remain absent. Click `模型分工与调用统计` and assert only LLM route/statistics appear; click `识图运行限制` and assert the LLM forms disappear while `识图运行参数` appears.

- [ ] **Step 2: Run the focused frontend test and verify RED**

```bash
cd desktop && npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
```

Expected: FAIL because connection panels are still behind the advanced disclosure and all advanced forms render together.

- [ ] **Step 3: Add one-at-a-time advanced group state**

Add a nullable `activeAdvancedConfiguration` state with keys `llm`, `skillEnvironment`, `skillRuntime`, `imageRuntime`, and `datasource`. The main advanced disclosure reveals only five group buttons. Selecting a button toggles that group; closing the main disclosure clears the active group without changing form drafts.

- [ ] **Step 4: Move daily connection controls to the main layer**

Render the existing enterprise-WeCom connection article and Smart Sheet connection article whenever the configuration-center page is active. Give both articles `configuration-connection-panel`, remove `wide`, and use the existing two-column page grid so they share one desktop row and stack on narrow screens.

- [ ] **Step 5: Gate existing low-frequency forms by group**

Keep existing form markup and handlers. Replace the broad `advancedConfigurationExpanded` conditions so route and statistics use `llm`, Skill environments use `skillEnvironment`, Skill runtime uses `skillRuntime`, image runtime uses `imageRuntime`, and synchronization uses `datasource`.

- [ ] **Step 6: Run focused and full verification**

Run the focused admin test, full frontend tests, typecheck, build, design detector, and live browser screenshots at desktop and mobile widths. Expected: all automated checks pass, both connection panels share a desktop row, and no advanced form appears until its group is selected.
