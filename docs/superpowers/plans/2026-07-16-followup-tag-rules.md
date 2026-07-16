# Step 9C 跟进规则动态标签实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让跟进规则使用统一标签目录配置条件和正式标签建议目标，并在运行时只匹配客户当前有效、允许用于跟进规则的标签。

**Architecture:** 保留现有条件 JSON 外壳和旧字段兼容，在 `ConditionEvaluator` 中新增 `tag/MATCH` 叶子条件。`RuleMatcher` 为每个客户读取一次当前标签上下文并共享给所有规则；`RuleAdminService` 保存时用 `TagSelectionValidator` 校验分类和值。`TAG_CHANGE` 的新格式写入正式 `categoryId/tagValueId/tagName` 和目录校验状态，只有旧的 `tagName` 规则走旧兼容路径。后台表单用现有 `tagCategoryOptionsCache` 生成动态条件和正式动作目标。

**Tech Stack:** Java 17、Spring Boot、Spring JDBC、Jackson、JUnit 5、Mockito、AssertJ、Vue 3、Vitest、TypeScript。

---

## 文件地图

- 修改 `src/main/java/com/privateflow/modules/followup/service/ConditionEvaluator.java`：增加 `tag/MATCH` 白名单、结构校验和 ANY/ALL 计算。
- 新增 `src/main/java/com/privateflow/modules/followup/service/FollowupTagContext.java`：封装客户当前有效标签值集合及一次读取结果。
- 新增 `src/main/java/com/privateflow/modules/followup/service/FollowupTagContextLoader.java`：读取客户当前标签并按目录状态过滤。
- 修改 `src/main/java/com/privateflow/modules/followup/service/RuleMatcher.java`：每个客户只加载一次标签上下文，单规则错误隔离。
- 修改 `src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java`：保存时校验标签条件和正式 `TAG_CHANGE` 目标。
- 修改 `src/main/java/com/privateflow/modules/followup/service/ActionExecutor.java`：正式目标写入已校验建议，失效目标跳过；旧文本规则保持兼容。
- 修改 `src/main/java/com/privateflow/modules/followup/infra/TagSuggestionRepository.java`：新增正式目标 `upsertPending` 重载，不改旧数据。
- 修改 `src/main/java/com/privateflow/modules/tags/TagRuleReferenceService.java`：明确支持 `valueIds` 数组和正式动作字段的统计与合并重写。
- 修改 `src/test/java/com/privateflow/modules/followup/service/*Test.java`、`src/test/java/com/privateflow/modules/followup/infra/TagSuggestionRepositoryTest.java`、`src/test/java/com/privateflow/modules/tags/TagRuleReferenceServiceTest.java`：覆盖后端契约。
- 修改 `desktop/src/renderer/modules/admin/AdminConsole.vue`：动态标签条件组、ANY/ALL、AND/OR、正式 `TAG_CHANGE` 目标和旧规则兼容显示。
- 修改 `desktop/src/renderer/modules/admin/AdminConsole.test.ts`：覆盖动态标签序列化、正式动作序列化、目录加载失败保留表单。
- 新增 `dev-progress/tag_skill_llm_step9c_breakpoint_069.md`：记录验证结果和未进入 Step 9D 的断点。

## Task 1：条件契约和失败测试

**Files:**
- Modify: `src/main/java/com/privateflow/modules/followup/service/ConditionEvaluator.java`
- Test: `src/test/java/com/privateflow/modules/followup/service/ConditionEvaluatorTest.java`

- [ ] **Step 1: 先写失败测试。** 在现有测试类中加入以下行为：

```java
@Test
void tagMatchAnyAndAllUseCurrentContext() throws Exception {
  Customer customer = customer();
  FollowupTagContext tags = FollowupTagContext.of(Map.of(
      50L, Set.of(51L, 53L)));
  assertThat(evaluator.matches(customer,
      "{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51,52],\"match\":\"ANY\"}", tags)).isTrue();
  assertThat(evaluator.matches(customer,
      "{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51,52],\"match\":\"ALL\"}", tags)).isFalse();
}

@Test
void invalidTagLeafIsRejected() throws Exception {
  JsonNode node = mapper.readTree("{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":0,\"valueIds\":[],\"match\":\"XOR\"}");
  assertThatThrownBy(() -> evaluator.validateDefinition(node))
      .isInstanceOf(FollowupException.class)
      .extracting("code").isEqualTo(FollowupErrorCodes.CONDITION_PARSE_FAILED);
}

@Test
void legacyLeafStillMatchesWithoutTagContext() {
  assertThat(evaluator.matches(customer(),
      "{\"field\":\"leadType\",\"op\":\"EQ\",\"value\":\"XIAN_SUO\"}")).isTrue();
}
```

- [ ] **Step 2: 运行失败测试。**

```text
mvn -q -Dtest=ConditionEvaluatorTest test
```

预期：编译或断言失败，因为尚无 `FollowupTagContext`、`MATCH` 和带上下文的 `matches` 重载。

- [ ] **Step 3: 实现最小契约。** 新增不可变类型：

```java
public record FollowupTagContext(Map<Long, Set<Long>> valueIdsByCategory) {
  public FollowupTagContext {
    valueIdsByCategory = valueIdsByCategory == null ? Map.of() : valueIdsByCategory.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
  }
  public static FollowupTagContext empty() { return new FollowupTagContext(Map.of()); }
  public static FollowupTagContext of(Map<Long, Set<Long>> values) { return new FollowupTagContext(values); }
  public boolean containsAny(long categoryId, Collection<Long> values) {
    Set<Long> current = valueIdsByCategory.getOrDefault(categoryId, Set.of());
    return values.stream().anyMatch(current::contains);
  }
  public boolean containsAll(long categoryId, Collection<Long> values) {
    return valueIdsByCategory.getOrDefault(categoryId, Set.of()).containsAll(values);
  }
}
```

`ConditionEvaluator` 保持旧签名 `matches(Customer, String)`，委托到 `matches(Customer, String, FollowupTagContext.empty())`；`FIELD_WHITELIST` 增加 `tag`，`OP_WHITELIST` 增加 `MATCH`。标签叶子必须满足 `categoryId > 0`、`valueIds` 为非空正整数数组、`match` 为 `ANY` 或 `ALL`；`MATCH` 用上下文执行交集判断。其他叶子和 `operator/orGroups` 继续使用现有逻辑。

- [ ] **Step 4: 重跑测试并确认通过。**

```text
mvn -q -Dtest=ConditionEvaluatorTest test
```

预期：新增和旧兼容测试全部通过。

- [ ] **Step 5: 提交条件契约。**

```text
git add src/main/java/com/privateflow/modules/followup/service/ConditionEvaluator.java src/main/java/com/privateflow/modules/followup/service/FollowupTagContext.java src/test/java/com/privateflow/modules/followup/service/ConditionEvaluatorTest.java
git commit -m "feat: evaluate followup tag conditions"
```

## Task 2：有效标签上下文和一次读取

**Files:**
- Create: `src/main/java/com/privateflow/modules/followup/service/FollowupTagContextLoader.java`
- Modify: `src/main/java/com/privateflow/modules/followup/service/RuleMatcher.java`
- Tests: `src/test/java/com/privateflow/modules/followup/service/FollowupTagContextLoaderTest.java`, `RuleMatcherTest.java`

- [ ] **Step 1: 写失败测试。** 使用 `CustomerTagFoundationRepository` 和 `TagDirectoryService` mock，验证：启用、未合并、`useForFollowupRules` 分类及启用未合并值才进入上下文；同一客户三条规则只调用一次 `load(customer)`；目录读失败返回空上下文并记录规则 ID。

```java
@Test
void filtersDisabledMergedAndWrongPurposeAssignments() {
  when(repository.findCurrentTagDetails(7L)).thenReturn(List.of(active(50L, 51L), disabledCategory(50L, 52L), mergedValue(50L, 53L)));
  when(directory.getSnapshot()).thenReturn(snapshot(category(50L, true, null, value(51L, true, null))));
  assertThat(loader.load(customer(7L)).valueIdsByCategory()).containsEntry(50L, Set.of(51L));
}

@Test
void matcherLoadsTagContextOncePerCustomer() {
  when(loader.load(any())).thenReturn(FollowupTagContext.of(Map.of(50L, Set.of(51L))));
  matcher.match(customer(7L), List.of(ruleWithTag(1L), ruleWithTag(2L), ruleWithTag(3L)));
  verify(loader).load(any());
}
```

- [ ] **Step 2: 运行失败测试。**

```text
mvn -q -Dtest=FollowupTagContextLoaderTest,RuleMatcherTest test
```

预期：类型和构造器不存在，测试失败。

- [ ] **Step 3: 实现读取器并接入匹配器。** `FollowupTagContextLoader.load(Customer)` 先校验客户 ID，再调用 `CustomerTagFoundationRepository.findCurrentTagDetails(id)` 和 `TagDirectoryService.getSnapshot()`；对每个 assignment 以目录中的分类和值为准，过滤 `!active`、分类停用、分类合并、`!useForFollowupRules`、值停用、值合并及分类不一致。任何目录异常返回 `FollowupTagContext.empty()` 并由 `RuleMatcher` 针对当前规则记录 `followup rule tag context unavailable`。`RuleMatcher` 在循环前加载一次上下文，并将上下文传给 `conditionEvaluator.matches(customer, json, context)`。

- [ ] **Step 4: 重跑测试。**

```text
mvn -q -Dtest=FollowupTagContextLoaderTest,RuleMatcherTest test
```

预期：过滤、一次读取、失效标签不命中测试通过。

- [ ] **Step 5: 提交运行时上下文。**

```text
git add src/main/java/com/privateflow/modules/followup/service/FollowupTagContextLoader.java src/main/java/com/privateflow/modules/followup/service/RuleMatcher.java src/test/java/com/privateflow/modules/followup/service/FollowupTagContextLoaderTest.java src/test/java/com/privateflow/modules/followup/service/RuleMatcherTest.java
git commit -m "feat: load effective followup tags once"
```

## Task 3：保存阶段校验

**Files:**
- Modify: `src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java`
- Tests: `src/test/java/com/privateflow/modules/followup/service/RuleAdminServiceTest.java`

- [ ] **Step 1: 写失败测试。** 覆盖有效 SINGLE、有效 MULTI、重复值、停用分类、合并值、关闭 `useForFollowupRules`、缺少 `businessBasis`、正式 `TAG_CHANGE` 目标不存在和旧 `tagName` 兼容。

```java
@Test
void savesTagConditionOnlyWhenDirectorySelectionIsValid() {
  when(selectionValidator.validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L)), any()))
      .thenReturn(accepted(category50(), value51()));
  FollowupRule result = service.create(requestWith(
      "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51],\"match\":\"ANY\"}]}",
      "{}"));
  assertThat(result).isNotNull();
}

@Test
void rejectsInvalidTagConditionWithBadRequest() {
  when(selectionValidator.validateIds(any(), eq(50L), eq(List.of(51L)), any()))
      .thenReturn(rejected(TagSelectionValidationReason.CATEGORY_DISABLED));
  assertThatThrownBy(() -> service.create(requestWith(tagJson(50L, 51L), "{}")))
      .isInstanceOf(FollowupException.class)
      .extracting("code").isEqualTo(FollowupErrorCodes.BAD_REQUEST);
}
```

- [ ] **Step 2: 运行失败测试。**

```text
mvn -q -Dtest=RuleAdminServiceTest test
```

- [ ] **Step 3: 接入 `TagSelectionValidator`。** 在构造器注入 `TagSelectionValidator`；解析 condition 后递归扫描 `conditions` 和 `orGroups`，每个 `field=tag/op=MATCH` 取 `categoryId/valueIds`，构造 `TagSelectionContext.empty()` 之外的 `businessBasis`：`request.name() + " / " + leaf.toString()`。调用 `validateIds(FOLLOWUP_RULE, categoryId, valueIds, context)`，结果不 accepted 时抛 `FollowupException(BAD_REQUEST, "标签分类/值校验失败：" + result.message())`。对 `TAG_CHANGE`，当 action 含 `tagValueId` 时用 `tagCategoryId` 和单值列表调用同一校验器；同时要求 `tagCategoryKey/tagValue/tagName` 与返回的正式目录对象一致或为空时由服务补全。只有 `tagName` 的旧动作不调用正式校验。

- [ ] **Step 4: 运行保存测试并确认旧规则兼容。**

```text
mvn -q -Dtest=RuleAdminServiceTest test
```

预期：保存校验、错误码、正式动作和旧文本兼容全部通过。

- [ ] **Step 5: 提交保存校验。**

```text
git add src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java src/test/java/com/privateflow/modules/followup/service/RuleAdminServiceTest.java
git commit -m "feat: validate followup tag rule selections"
```

## Task 4：正式 TAG_CHANGE 建议和旧数据兼容

**Files:**
- Modify: `src/main/java/com/privateflow/modules/followup/infra/TagSuggestionRepository.java`
- Modify: `src/main/java/com/privateflow/modules/followup/service/ActionExecutor.java`
- Tests: `src/test/java/com/privateflow/modules/followup/infra/TagSuggestionRepositoryTest.java`, `src/test/java/com/privateflow/modules/followup/service/ActionExecutorTest.java`

- [ ] **Step 1: 写失败测试。** 断言正式动作写入 `tag_value_id`、`validation_status='VALIDATED'`，新建议仍为 `PENDING`，旧 `upsertPending(phone, tagName, ...)` SQL 和 6 条现有待确认数据不被更新；正式目标无效时不写建议。

```java
@Test
void formalTargetStoresDirectoryIdsAndValidatedStatus() {
  repository.upsertPending("13800000000", 50L, 51L, "高意向", 9L, 7);
  verify(jdbc).update(contains("tag_value_id"), eq("13800000000"), eq(50L), eq(51L), eq("高意向"), eq(9L));
}

@Test
void executorKeepsLegacyTextPath() {
  executor.execute(customer(), List.of(legacyTagChangeMatch()));
  verify(repository).upsertPending("13800000000", "旧文本", 9L, 7);
  verify(repository, never()).upsertPending(anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyInt());
}
```

- [ ] **Step 2: 运行失败测试。**

```text
mvn -q -Dtest=TagSuggestionRepositoryTest,ActionExecutorTest test
```

- [ ] **Step 3: 实现正式重载。** 新增 `upsertPending(String phone, long categoryId, long valueId, String tagName, long ruleId, int dedupDays)`，去重条件使用 `phone + tag_value_id`，插入字段包含 `customer_id/category_id(若表无该列则不添加)/tag_value_id/tag_name/rule_id/status/validation_status`；根据现有 schema 只复用已存在列，正式校验状态固定 `VALIDATED`。`ActionExecutor` 解析 `tagCategoryId/tagValueId/tagName`，通过注入的 `TagSelectionValidator` 再次用 `FOLLOWUP_RULE` 校验；失败记录 debug 并跳过；成功调用正式重载并构造现有 `TagSuggestionPayload`。动作只有 `tagName` 时继续旧路径。

- [ ] **Step 4: 运行并确认建议行为。**

```text
mvn -q -Dtest=TagSuggestionRepositoryTest,ActionExecutorTest test
```

预期：正式建议、无效目标跳过、旧文本建议和已有 PENDING 兼容全部通过。

- [ ] **Step 5: 提交建议路径。**

```text
git add src/main/java/com/privateflow/modules/followup/infra/TagSuggestionRepository.java src/main/java/com/privateflow/modules/followup/service/ActionExecutor.java src/test/java/com/privateflow/modules/followup/infra/TagSuggestionRepositoryTest.java src/test/java/com/privateflow/modules/followup/service/ActionExecutorTest.java
git commit -m "feat: persist validated followup tag suggestions"
```

## Task 5：合并引用回归

**Files:**
- Modify: `src/main/java/com/privateflow/modules/tags/TagRuleReferenceService.java`
- Modify: `src/test/java/com/privateflow/modules/tags/TagRuleReferenceServiceTest.java`

- [ ] **Step 1: 增加回归测试。** 让规则 JSON 同时包含条件 `categoryId/valueIds` 和动作 `tagCategoryId/tagValueId/tagName`，验证 `countReferences` 统计分类和值各一次；执行 `rewriteValue` 和 `rewriteCategory` 后断言数组中的每个 ID、正式动作 ID、编码、名称都被替换，JSON 中不存在旧 ID。

```java
@Test
void rewritesValueIdsInsideConditionArraysAndFormalAction() {
  seedRule("{\"conditions\":[{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51,52]}]}",
      "{\"tagCategoryId\":50,\"tagCategoryKey\":\"intent_level\",\"tagValueId\":51,\"tagValue\":\"HIGH\",\"tagName\":\"高意向\"}");
  service.rewriteValue(sourceValue51(), category50(), targetValue61(), category60());
  JsonNode saved = readRule(9L);
  assertThat(saved.toString()).doesNotContain("51").contains("61");
}
```

- [ ] **Step 2: 运行回归测试并按需修正字段集合。**

```text
mvn -q -Dtest=TagRuleReferenceServiceTest test
```

实现中将 `valueIds` 作为数组递归处理；保留现有 `tagValueId/tagValue/tagName` 和 `categoryId/categoryKey` 替换规则，不对旧自由文本生成悬空正式引用。

- [ ] **Step 3: 提交合并回归。**

```text
git add src/main/java/com/privateflow/modules/tags/TagRuleReferenceService.java src/test/java/com/privateflow/modules/tags/TagRuleReferenceServiceTest.java
git commit -m "test: cover formal followup tag references"
```

## Task 6：后台动态规则表单

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: 写失败测试。** 在规则创建测试中从 `tagCategoryOptionsCache` mock 返回两个启用且 `useForFollowupRules` 的分类和值，选择两个标签条件，断言保存 payload：

```ts
expect(JSON.parse(String(payload.conditionJson))).toEqual({
  operator: 'AND',
  conditions: [
    { field: 'leadType', op: 'EQ', value: 'XIAN_SUO' },
    { field: 'lastFollowupHours', op: 'GT', value: 12 },
    { field: 'tag', op: 'MATCH', categoryId: 50, valueIds: [51, 52], match: 'ALL' },
    { field: 'tag', op: 'MATCH', categoryId: 60, valueIds: [61], match: 'ANY' }
  ]
});
expect(JSON.parse(String(payload.actionConfig))).toMatchObject({
  tagCategoryId: 50, tagCategoryKey: 'intent_level', tagValueId: 51, tagValue: 'HIGH', tagName: '高意向'
});
```

另测旧 `tagName` 规则编辑时显示兼容提示，目录请求失败后已有 `formDraft` 字段值保持不变。

- [ ] **Step 2: 运行前端失败测试。**

```text
cd desktop
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
```

- [ ] **Step 3: 实现表单状态和序列化。** 在 `ruleDraft` 增加 `tagConditions`（`{ categoryId: string; valueIds: string[]; match: 'ANY'|'ALL' }[]`）、`tagGroupOperator`、`tagCategoryId`、`tagValueId`；从 `parseRuleCondition` 解析所有 `field=tag/op=MATCH`，旧条件没有标签时给空数组。`formMeta('rule')` 增加动态分类/值选择器、添加/删除条件按钮和组间 AND/OR；值选项只取 `tagCategoryOptionsCache` 中 `isEnabled && mergedIntoId == null && useForFollowupRules` 的分类和值。`buildRulePayload` 把标签条件追加到普通条件后，输出正式动作字段并自动填充 `tagCategoryKey/tagValue/tagName`；未选正式目标时保留旧 `tagName` 文本兼容路径。提交前端目录请求失败只更新 notice，不清空草稿。

- [ ] **Step 4: 运行前端测试和类型检查。**

```text
cd desktop
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
npm run typecheck
```

预期：动态条件、ANY/ALL、AND/OR、正式动作和旧规则兼容测试通过。

- [ ] **Step 5: 提交后台表单。**

```text
git add desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts
git commit -m "feat: configure followup tag rules in admin"
```

## Task 7：定向验证、断点和范围保护

**Files:**
- Create: `dev-progress/tag_skill_llm_step9c_breakpoint_069.md`

- [ ] **Step 1: 运行后端定向测试。**

```text
mvn -q -Dtest='com.privateflow.modules.followup.*Test,com.privateflow.modules.tags.*Test' test
```

预期：退出码 0；记录测试总数、失败、错误和跳过数量。

- [ ] **Step 2: 运行前端验证。**

```text
cd desktop
npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts
npm run typecheck
npm run build
```

预期：规则管理测试、类型检查和构建均成功。

- [ ] **Step 3: 做静态保护检查。**

```text
git diff --check
rg -n "llm\.reply_generation\.enabled|llm\.profile_extraction\.enabled|V[0-9]+__" src desktop dev-progress
git status --short
```

预期：没有新增 Flyway migration，没有开启两个 LLM 开关，没有修改现有六条 `system_tag_suggestions PENDING` 数据；Step 8 文件只读不改。

- [ ] **Step 4: 写断点文档。** 记录实现提交、测试命令和结果、正式/旧动作兼容、失效标签不命中、前端动态表单状态，并明确 Step 9D 未开始。

- [ ] **Step 5: 提交文档。**

```text
git add dev-progress/tag_skill_llm_step9c_breakpoint_069.md
git commit -m "docs: record followup tag rules breakpoint"
```

不执行 merge、push、PR 或数据库迁移。

## 计划自审

- **规格覆盖：** 条件 `MATCH`、ANY/ALL、AND/OR、保存校验、运行时有效标签、正式 `TAG_CHANGE`、旧文本兼容、合并引用、后台表单和验收命令分别落在 Task 1-7；Step 9D 和数据库迁移明确排除。
- **占位符扫描：** 计划中没有未填写的实现占位；每个代码变更步骤给出了具体类型、方法、字段或命令。
- **类型一致性：** `FollowupTagContext` 由 Task 1 定义，Task 2 传入 `ConditionEvaluator.matches`；Task 3 使用同一 `TagSelectionValidator`；Task 4 的正式重载字段与 Task 3 的动作 JSON 一致；Task 6 序列化字段与后端契约一致。
