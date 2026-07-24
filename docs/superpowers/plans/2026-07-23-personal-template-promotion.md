# 个人模板与团队推广 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让员工把生成回复编辑后立即保存到“我的模板”，同时在后台安静地形成可推广候选；主管可选择发布到全员可用的团队模板库或暂不发布，员工始终不看到审批、退回或拒绝状态。

**Architecture:** 个人模板和候选快照使用独立表，候选快照不可变；发布到团队模板时复用现有 `quick_search_items` 发布和 WebSocket 刷新链路，而不是复制一套公共模板机制。员工模板库只有“我的模板”和“团队模板”两个入口；复制模板只记录使用，不声称已发送，依赖主管治理计划的监督事件服务。

**Tech Stack:** Spring Boot 3、JdbcTemplate、MariaDB/Flyway、Vue 3、Vitest、JUnit 5、MockMvc、现有 Quick Search WebSocket 刷新。

---

## File Structure

| 文件 | 责任 |
| --- | --- |
| `src/main/resources/db/migration/V78__personal_templates_and_promotions.sql` | 创建个人模板、候选快照、团队发布映射及索引。 |
| `src/main/java/com/privateflow/modules/templates/PersonalTemplate.java` | 员工当前可复用的个人模板。 |
| `src/main/java/com/privateflow/modules/templates/TemplatePromotionCandidate.java` | 不可变候选快照及主管决策状态。 |
| `src/main/java/com/privateflow/modules/templates/PersonalTemplateRepository.java` | 个人模板、候选快照、使用次数与团队发布映射 SQL。 |
| `src/main/java/com/privateflow/modules/templates/PersonalTemplateService.java` | 员工保存、列表、删除、使用记录，强制所属人边界。 |
| `src/main/java/com/privateflow/modules/templates/TemplatePromotionService.java` | 管理员查询候选、发布到 `quick_search_items`、暂不发布。 |
| `src/main/java/com/privateflow/modules/templates/TemplateController.java` | 员工个人/团队模板和复制使用 API。 |
| `src/main/java/com/privateflow/modules/templates/TemplatePromotionAdminController.java` | 管理员候选、发布、暂不发布 API。 |
| `src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminService.java` | 提供可复用的团队模板发布方法和现有刷新事件。 |
| `desktop/src/renderer/modules/templates/templateLibraryStore.ts` | 我的/团队模板数据、保存编辑器状态和复制使用。 |
| `desktop/src/renderer/modules/templates/TemplateLibraryOverlay.vue` | 只有“我的模板”“团队模板”两个标签的员工模板库。 |
| `desktop/src/renderer/modules/templates/PersonalTemplateEditor.vue` | 从 AI 建议或团队模板另存时的标题、正文、适用信息确认页。 |
| `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue` | 每张 AI 建议卡增加“保存为模板”。 |
| `desktop/src/renderer/App.vue` | 模板快捷按钮打开新的模板库，保留已有快捷内容的非模板访问入口。 |
| `desktop/src/renderer/modules/admin/AdminConsole.vue` | 主管的“可推广模板”页面。 |

## Data and Permission Contract

- `personal_templates` 的唯一归属为当前认证用户名。员工只能读取、修改、删除自己的模板，不能在请求体指定其他员工。
- 每次个人模板新建或更新都插入一条新的 `template_promotion_candidates` 快照；快照保存原始 AI 回复、员工编辑后的标题/正文、频道、场景、适用标签 JSON、员工和创建时间，之后永不覆盖。
- 候选状态仅供主管读取：`CANDIDATE`、`PUBLISHED`、`NOT_PUBLISHED`。员工 API 不返回候选表或这些状态，也不返回主管备注。
- 团队发布仅创建 `quick_search_items.content_type='TEMPLATE'`，并用 `team_template_publications` 将候选快照与公共条目关联。发布后复用 `QuickSearchAdminService.publishRefresh()` 让所有客户端刷新。
- 频道、场景、线索类型、业务标签选项来自数据库和现有配置接口；前端不将“企微、抖音、成交、线索”等业务文本写成不可变枚举。
- 模板复制只记 `TEMPLATE_COPIED` 使用事件，并更新对应个人模板使用次数；不调用 `/api/v1/chat/send-confirm`、不修改跟进完成状态。

### Task 1: 个人模板与候选快照数据库契约

**Files:**
- Create: `src/main/resources/db/migration/V78__personal_templates_and_promotions.sql`
- Create: `src/test/java/com/privateflow/modules/templates/TemplateFlywayMariaDbIntegrationTest.java`
- Create: `src/main/java/com/privateflow/modules/templates/PersonalTemplate.java`
- Create: `src/main/java/com/privateflow/modules/templates/TemplatePromotionCandidate.java`

- [ ] **Step 1: 写出数据库快照不可变的失败测试**

```java
@Test
void savesPersonalTemplateAndImmutablePromotionSnapshot() {
  long templateId = repository.insertPersonal(owner, "开场", "编辑后的正文", metadata());
  long candidateId = repository.insertCandidate(templateId, owner, "AI 原始回复", "开场", "编辑后的正文", metadata());

  repository.updatePersonal(templateId, owner, "新标题", "新正文", metadata());
  assertThat(repository.findCandidate(candidateId).orElseThrow().editedBody()).isEqualTo("编辑后的正文");
}
```

- [ ] **Step 2: 运行迁移测试确认失败**

Run: `./mvnw.cmd -Dtest=TemplateFlywayMariaDbIntegrationTest test`

Expected: FAIL，表和数据模型不存在。

- [ ] **Step 3: 创建表、索引和映射关系**

```sql
CREATE TABLE personal_templates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_username VARCHAR(64) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  channel_code VARCHAR(100) NULL,
  scene VARCHAR(100) NULL,
  lead_type VARCHAR(100) NULL,
  labels_json TEXT NOT NULL,
  source_reply_session_id VARCHAR(80) NULL,
  usage_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id), KEY idx_personal_template_owner_time (owner_username, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE template_promotion_candidates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  personal_template_id BIGINT NOT NULL,
  owner_username VARCHAR(64) NOT NULL,
  original_ai_reply TEXT NOT NULL,
  edited_title VARCHAR(120) NOT NULL,
  edited_body TEXT NOT NULL,
  metadata_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
  decided_by VARCHAR(64) NULL,
  decided_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id), KEY idx_candidate_status_time (status, created_at),
  CONSTRAINT fk_candidate_personal_template FOREIGN KEY (personal_template_id) REFERENCES personal_templates(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

在同一迁移创建 `team_template_publications(candidate_id UNIQUE, quick_search_item_id UNIQUE, published_by, published_at)`，不得向候选表加入截图、Base64 或原始聊天文本列。

- [ ] **Step 4: 运行数据库契约测试**

Run: `./mvnw.cmd -Dtest=TemplateFlywayMariaDbIntegrationTest test`

Expected: PASS。

- [ ] **Step 5: 提交模板数据层**

```bash
git add src/main/resources/db/migration/V78__personal_templates_and_promotions.sql src/main/java/com/privateflow/modules/templates/PersonalTemplate.java src/main/java/com/privateflow/modules/templates/TemplatePromotionCandidate.java src/test/java/com/privateflow/modules/templates/TemplateFlywayMariaDbIntegrationTest.java
git commit -m "feat: add personal template promotion storage"
```

### Task 2: 员工个人模板 API 与候选快照写入

**Files:**
- Create: `src/main/java/com/privateflow/modules/templates/PersonalTemplateRequest.java`
- Create: `src/main/java/com/privateflow/modules/templates/PersonalTemplateRepository.java`
- Create: `src/main/java/com/privateflow/modules/templates/PersonalTemplateService.java`
- Create: `src/main/java/com/privateflow/modules/templates/TemplateController.java`
- Test: `src/test/java/com/privateflow/modules/templates/PersonalTemplateServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/templates/TemplateControllerTest.java`

- [ ] **Step 1: 写出员工只能保存和读取自己的模板的失败测试**

```java
@Test
void savesImmediatelyForEmployeeAndCreatesCandidateSilently() {
  TemplateView saved = service.save(new PersonalTemplateRequest("开场", "你好", "AI 建议", metadata(), "reply-1"));
  assertThat(service.listMine()).extracting(TemplateView::title).containsExactly("开场");
  assertThat(repository.countCandidatesForOwner(AuthContext.username())).isEqualTo(1);
}

@Test
void employeeListNeverContainsCandidateStatusOrDecision() {
  assertThat(json(service.listMine())).doesNotContain("CANDIDATE", "NOT_PUBLISHED", "decidedBy");
}
```

- [ ] **Step 2: 运行服务和控制器测试确认失败**

Run: `./mvnw.cmd -Dtest=PersonalTemplateServiceTest,TemplateControllerTest test`

Expected: FAIL，个人模板端点尚未定义。

- [ ] **Step 3: 实现员工端 API 与输入校验**

```java
@PostMapping("/api/v1/templates/personal")
public ApiResponse<TemplateView> save(@RequestBody PersonalTemplateRequest request) {
  return ApiResponse.ok(personalTemplateService.save(request));
}

@GetMapping("/api/v1/templates/personal")
public ApiResponse<List<TemplateView>> mine() {
  return ApiResponse.ok(personalTemplateService.listMine());
}
```

```java
public TemplateView save(PersonalTemplateRequest request) {
  String owner = AuthContext.username();
  validateTitleAndBody(request.title(), request.body());
  long id = repository.upsertPersonal(owner, request);
  repository.insertCandidate(id, owner, clip(request.originalAiReply(), 4000), request.title().trim(), request.body().trim(), request.metadata());
  supervisionEventService.recordTemplateSaved(id, owner, request.metadata());
  return repository.findPersonal(id, owner).orElseThrow();
}
```

标题限制 1-120 字，正文限制 1-4000 字，`metadata.labels` 为最多 20 个每项 80 字的字符串；所有频道、场景、线索类型仅作后台数据值保存。更新接口必须在更新当前模板后另插入候选快照，不能更新旧候选。

- [ ] **Step 4: 运行员工模板测试**

Run: `./mvnw.cmd -Dtest=PersonalTemplateServiceTest,TemplateControllerTest test`

Expected: PASS，员工响应中没有审核相关字段。

- [ ] **Step 5: 提交员工模板服务**

```bash
git add src/main/java/com/privateflow/modules/templates src/test/java/com/privateflow/modules/templates/PersonalTemplateServiceTest.java src/test/java/com/privateflow/modules/templates/TemplateControllerTest.java
git commit -m "feat: save personal reply templates"
```

### Task 3: 团队模板发布复用既有 Quick Search 链路

**Files:**
- Create: `src/main/java/com/privateflow/modules/templates/PublishTeamTemplateRequest.java`
- Create: `src/main/java/com/privateflow/modules/templates/TemplatePromotionService.java`
- Create: `src/main/java/com/privateflow/modules/templates/TemplatePromotionAdminController.java`
- Modify: `src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminService.java`
- Modify: `src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminRepository.java`
- Test: `src/test/java/com/privateflow/modules/templates/TemplatePromotionServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/templates/TemplatePromotionAdminControllerTest.java`
- Test: `src/test/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminServiceTest.java`

- [ ] **Step 1: 写出发布一次、全员可见与暂不发布的失败测试**

```java
@Test
void publishesCandidateOnceThroughQuickSearchAndBroadcastsRefresh() {
  service.publish(candidateId, new PublishTeamTemplateRequest("团队开场", "TM102", "GENERAL", true));
  assertThat(quickSearchRepository.findEnabledItems()).extracting(QuickSearchItem::title).contains("团队开场");
  verify(wsPushService).broadcastWs(argThat(message -> message.type().equals("CONFIG_REFRESH")));
  assertThatThrownBy(() -> service.publish(candidateId, request())).hasMessageContaining("already published");
}

@Test
void notPublishingChangesOnlySupervisorCandidateState() {
  service.markNotPublished(candidateId);
  assertThat(personalTemplateService.listMine()).isNotEmpty();
  assertThat(publicTemplates()).doesNotContain(candidateBody);
}
```

- [ ] **Step 2: 运行发布测试确认失败**

Run: `./mvnw.cmd -Dtest=TemplatePromotionServiceTest,TemplatePromotionAdminControllerTest,QuickSearchAdminServiceTest test`

Expected: FAIL，候选发布 API 不存在。

- [ ] **Step 3: 实现管理员发布和静默不发布**

```java
@PostMapping("/admin/api/v1/template-promotion-candidates/{id}/publish")
public ApiResponse<Map<String, Object>> publish(@PathVariable long id, @RequestBody PublishTeamTemplateRequest request) {
  return ApiResponse.ok(templatePromotionService.publish(id, request));
}

@PostMapping("/admin/api/v1/template-promotion-candidates/{id}/not-publish")
public ApiResponse<Void> notPublish(@PathVariable long id) {
  templatePromotionService.markNotPublished(id);
  return ApiResponse.ok(null);
}
```

`publish` 必须 `requireAdmin()`，锁定 `CANDIDATE` 行，调用 `QuickSearchAdminService.createTeamTemplate(...)` 写入 `quick_search_items`，插入发布映射并把候选改为 `PUBLISHED`；快捷码必须为 2-20 位字母数字，缺省时生成 `TM` 加候选 ID 的大写 36 进制。`not-publish` 只写 `NOT_PUBLISHED/decided_by/decided_at`，不改个人模板，不发 WebSocket，不向员工通知。

- [ ] **Step 4: 运行发布、权限和刷新测试**

Run: `./mvnw.cmd -Dtest=TemplatePromotionServiceTest,TemplatePromotionAdminControllerTest,QuickSearchAdminServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交团队推广服务**

```bash
git add src/main/java/com/privateflow/modules/templates src/main/java/com/privateflow/modules/quicksearch/admin src/test/java/com/privateflow/modules/templates src/test/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminServiceTest.java
git commit -m "feat: promote personal templates to team library"
```

### Task 4: 模板使用事件与团队模板读取接口

**Files:**
- Modify: `src/main/java/com/privateflow/modules/templates/PersonalTemplateRepository.java`
- Modify: `src/main/java/com/privateflow/modules/templates/PersonalTemplateService.java`
- Modify: `src/main/java/com/privateflow/modules/templates/TemplateController.java`
- Modify: `src/main/java/com/privateflow/modules/quicksearch/QuickSearchRepository.java`
- Test: `src/test/java/com/privateflow/modules/templates/PersonalTemplateServiceTest.java`
- Test: `src/test/java/com/privateflow/modules/templates/TemplateControllerTest.java`

- [ ] **Step 1: 写出复制使用不发送的失败测试**

```java
service.recordPersonalTemplateUse(personalTemplateId, "13800000001", "粘贴正文");
assertThat(repository.usageCount(personalTemplateId, owner)).isEqualTo(1);
verify(supervisionEventService).recordTemplateCopied(eq("13800000001"), eq(personalTemplateId), eq("PERSONAL"));
verifyNoInteractions(followupConfirmationService);
```

- [ ] **Step 2: 运行使用记录测试确认失败**

Run: `./mvnw.cmd -Dtest=PersonalTemplateServiceTest,TemplateControllerTest test`

Expected: FAIL，使用记录端点与团队读取端点不存在。

- [ ] **Step 3: 实现两类模板的安全读取和使用记录**

```java
@GetMapping("/api/v1/templates/team")
public ApiResponse<List<TeamTemplateView>> team() {
  return ApiResponse.ok(personalTemplateService.listTeamTemplates());
}

@PostMapping("/api/v1/templates/personal/{id}/use")
public ApiResponse<Map<String, Object>> usePersonal(@PathVariable long id, @RequestBody TemplateUseRequest request) {
  return ApiResponse.ok(personalTemplateService.recordPersonalTemplateUse(id, request));
}
```

团队模板查询只返回 `quick_search_items.content_type='TEMPLATE'` 且已发布映射存在的条目和元数据；员工没有团队修改/删除接口。使用记录只允许当前员工拥有的个人模板，或公开团队模板；模板正文复制成功后异步记录，记录失败不影响剪贴板。候选列表查询以事件表聚合 `TEMPLATE_COPIED` 为使用次数，不能修改不可变快照正文。

- [ ] **Step 4: 运行使用和权限测试**

Run: `./mvnw.cmd -Dtest=PersonalTemplateServiceTest,TemplateControllerTest,TemplatePromotionServiceTest test`

Expected: PASS，未产生发送确认或跟进完成事件。

- [ ] **Step 5: 提交模板使用链路**

```bash
git add src/main/java/com/privateflow/modules/templates src/main/java/com/privateflow/modules/quicksearch/QuickSearchRepository.java src/test/java/com/privateflow/modules/templates
git commit -m "feat: record template copy usage"
```

### Task 5: 员工模板库、编辑确认和 AI 回复入口

**Files:**
- Create: `desktop/src/renderer/modules/templates/templateTypes.ts`
- Create: `desktop/src/renderer/modules/templates/templateLibraryStore.ts`
- Create: `desktop/src/renderer/modules/templates/TemplateLibraryOverlay.vue`
- Create: `desktop/src/renderer/modules/templates/PersonalTemplateEditor.vue`
- Create: `desktop/src/renderer/modules/templates/templateLibraryStore.test.ts`
- Create: `desktop/src/renderer/modules/templates/TemplateLibraryOverlay.test.ts`
- Create: `desktop/src/renderer/modules/templates/PersonalTemplateEditor.test.ts`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue`
- Modify: `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.test.ts`
- Modify: `desktop/src/renderer/App.vue`

- [ ] **Step 1: 写出员工可立即保存且看不到审核状态的失败测试**

```ts
await wrapper.get('[data-testid="save-reply-template"]').trigger('click');
await editor.get('textarea[name="body"]').setValue('我已微调的正文');
await editor.get('form').trigger('submit');
expect(postJsonMock).toHaveBeenCalledWith('/api/v1/templates/personal', expect.objectContaining({ body: '我已微调的正文' }));
expect(library.text()).toContain('我的模板');
expect(library.text()).toContain('团队模板');
expect(library.text()).not.toMatch(/待审核|退回|拒绝|主管意见/);
```

- [ ] **Step 2: 运行前端模板测试确认失败**

Run: `npm run test -- --run templateLibraryStore.test.ts TemplateLibraryOverlay.test.ts PersonalTemplateEditor.test.ts ReplySuggestionPanel.test.ts`

Expected: FAIL，模板模块和保存入口尚不存在。

- [ ] **Step 3: 实现两标签模板库和编辑确认页**

```vue
<nav class="template-library-tabs" aria-label="模板分类">
  <button :class="{ active: state.tab === 'PERSONAL' }" @click="setTemplateTab('PERSONAL')">我的模板</button>
  <button :class="{ active: state.tab === 'TEAM' }" @click="setTemplateTab('TEAM')">团队模板</button>
</nav>
```

`PersonalTemplateEditor` 预填建议卡文本，提交前要求标题和正文非空，频道/场景/标签通过后端元数据加载。建议卡的“保存为模板”只打开编辑器，不会复制、发送或改变任务状态。团队模板卡显示“复制”和“另存为我的模板”；后者用团队内容预填编辑器并在保存后形成新的个人模板和候选。模板按钮打开该库；已有非模板快捷内容保持通过原 Quick Search 的独立入口访问，不将审批状态混入员工界面。

- [ ] **Step 4: 运行员工模板组件测试**

Run: `npm run test -- --run templateLibraryStore.test.ts TemplateLibraryOverlay.test.ts PersonalTemplateEditor.test.ts ReplySuggestionPanel.test.ts`

Expected: PASS。

- [ ] **Step 5: 提交员工模板界面**

```bash
git add desktop/src/renderer/modules/templates desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.test.ts desktop/src/renderer/App.vue
git commit -m "feat: add personal and team template library"
```

### Task 6: 主管候选审核界面与回归验证

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Create: `docs/manual-tests/template-promotion-acceptance.md`

- [ ] **Step 1: 写出候选发布与暂不发布的失败前端测试**

```ts
await wrapper.get('[data-testid="candidate-publish-42"]').trigger('click');
expect(postJsonMock).toHaveBeenCalledWith('/admin/api/v1/template-promotion-candidates/42/publish', expect.anything());
await wrapper.get('[data-testid="candidate-not-publish-43"]').trigger('click');
expect(postJsonMock).toHaveBeenCalledWith('/admin/api/v1/template-promotion-candidates/43/not-publish', {});
```

- [ ] **Step 2: 运行后台组件测试确认失败**

Run: `npm run test -- --run AdminConsole.test.ts`

Expected: FAIL，主管候选页面不存在。

- [ ] **Step 3: 实现主管专属候选页**

在 `AdminConsole.vue` 的“数据源与内容”组增加 `template-promotion-candidates` 页面：行内容包括员工、来源渠道、场景、原始 AI 回复、员工调整版、个人模板当前使用次数、创建时间；按钮只有“发布到团队模板库”和“暂不发布”。发布弹层可调整团队标题、快捷码、线索类型和启用状态；频道/场景/标签显示来自候选元数据。员工端无此页面、无通知和无结果状态。

- [ ] **Step 4: 运行前后端模板回归**

Run: `./mvnw.cmd -Dtest=PersonalTemplateServiceTest,TemplatePromotionServiceTest,TemplateControllerTest,TemplatePromotionAdminControllerTest test`

Run: `npm run test -- --run templateLibraryStore.test.ts TemplateLibraryOverlay.test.ts PersonalTemplateEditor.test.ts AdminConsole.test.ts QuickSearchOverlay.test.ts`

Expected: PASS。

- [ ] **Step 5: 执行人工验收并提交**

在 `docs/manual-tests/template-promotion-acceptance.md` 记录：保存个人模板立即可见；员工不见审核状态；主管可看候选快照；发布后另一员工刷新可见；暂不发布不影响原员工；团队模板不能被直接编辑但能另存为个人模板；所有复制不出现已发送确认。

```bash
git add desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts docs/manual-tests/template-promotion-acceptance.md
git commit -m "feat: review and publish template candidates"
```

## Self-Review

| 已确认需求 | 覆盖任务 |
| --- | --- |
| 员工编辑后立即保存个人模板 | Task 2、Task 5 |
| 后台保存原始 AI 与员工编辑后的不可变候选快照 | Task 1、Task 2 |
| 员工看不到待审核、退回、拒绝、主管意见 | Task 2、Task 5 |
| 主管可发布全员模板或暂不发布 | Task 3、Task 6 |
| 团队模板复用现有 Quick Search 同步而非重复系统 | Task 3、Task 4 |
| 团队模板只读，员工可另存个人版本 | Task 4、Task 5 |
| 模板使用/复制不等同于发送 | Task 4、Task 5、Task 6 |
| 频道、场景、标签不硬编码 | Task 2、Task 5、Task 6 |

已检查：发布映射、表名、状态名和端点在全篇一致；候选快照没有更新路径；员工和管理员的接口严格分离；所有功能任务均先写失败测试、再写最小实现并执行验证。
