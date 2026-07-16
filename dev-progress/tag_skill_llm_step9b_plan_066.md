# Step 9B Tag Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在运营分析看板增加与客户列表共用筛选和权限的正式标签统计，覆盖当前有效数量、门店/团队/员工、来源、未更新原因、趋势、CSV 和旧漏斗标签语义替换。

**Architecture:** 新增类型化的 `TagAnalyticsRequest/Response`、独立 `TagAnalyticsService` 和 `TagAnalyticsRepository`。服务层复用 Step 9A 的 `CustomerFilterValidator`、`CustomerAccessScopeResolver`、`CustomerFilterQueryBuilder`，Repository 只接收已经生成的 `CustomerQuerySpec`；前端把标签统计类型、payload 和 CSV 组装放到独立 helper，再由现有 `AdminConsole.vue` 接入。

**Tech Stack:** Java 17、Spring Boot 3.3、JdbcTemplate、H2 MySQL mode、JUnit 5、Mockito、Vue 3、TypeScript、Vitest、Electron/Vite。

---

## 实施约束

- 工作目录固定为 `C:\Users\85314\.config\superpowers\worktrees\private-domain-assistant\tag-step4-unified-access`。
- 不创建嵌套 worktree，不自动 merge、push 或创建 PR。
- 不新增 Flyway 迁移，不修改 `system_tag_suggestions` 当前 6 条 `PENDING` 数据。
- 不修改 Step 8 行为，不开启 `llm.reply_generation.enabled` 或 `llm.profile_extraction.enabled`。
- 所有业务修改严格采用测试先行；每个任务测试通过后单独提交。

## 文件结构

### 后端新增

- `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRequest.java`：HTTP 请求模型。
- `src/main/java/com/privateflow/modules/analytics/TagAnalyticsResponse.java`：类型化响应和维度行模型。
- `src/main/java/com/privateflow/modules/analytics/TagAnalyticsWindow.java`：规范化后的事件窗口。
- `src/main/java/com/privateflow/modules/analytics/TagTrendGranularity.java`：趋势粒度枚举，9B 只允许 `DAY`。
- `src/main/java/com/privateflow/modules/analytics/TagAnalyticsService.java`：权限、时间、团队交集和统一 QuerySpec 编排。
- `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java`：当前快照、维度、事件、原因和选项聚合。
- `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRequestTest.java`
- `src/test/java/com/privateflow/modules/analytics/TagAnalyticsServiceTest.java`
- `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java`
- `src/test/java/com/privateflow/modules/analytics/AnalyticsRepositoryTagFunnelTest.java`

### 后端修改

- `src/main/java/com/privateflow/modules/analytics/AnalyticsController.java`：新增 POST 标签统计入口。
- `src/main/java/com/privateflow/modules/analytics/AnalyticsRepository.java`：移除漏斗对旧 `customers.intent_level` 的标签语义依赖。
- `src/test/java/com/privateflow/modules/analytics/AnalyticsControllerTest.java`：锁定新接口契约。

### 前端新增

- `desktop/src/renderer/modules/admin/tagAnalytics.ts`：响应类型、请求 payload、展示标签和 CSV 数据段。
- `desktop/src/renderer/modules/admin/tagAnalytics.test.ts`：helper 单元测试。

### 前端修改

- `desktop/src/renderer/modules/admin/AdminConsole.vue`：标签统计筛选、加载、展示、失败重试和导出。
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`：看板集成测试。
- `desktop/src/renderer/modules/admin/AdminDevConsole.vue`：开发调试台注册新接口。
- `desktop/src/renderer/modules/admin/AdminDevConsole.test.ts`：调试台请求契约。
- `desktop/src/renderer/styles.css`：标签统计筛选和响应式布局。

### 进度文档

- `dev-progress/tag_skill_llm_tasklist_056.md`
- `dev-progress/tag_skill_llm_step9b_breakpoint_067.md`

---

### Task 1: 锁定标签统计请求与响应类型

**Files:**
- Create: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRequest.java`
- Create: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsResponse.java`
- Create: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsWindow.java`
- Create: `src/main/java/com/privateflow/modules/analytics/TagTrendGranularity.java`
- Test: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRequestTest.java`

- [ ] **Step 1: 写失败的类型契约测试**

```java
package com.privateflow.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.admin.CustomerSearchRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagAnalyticsRequestTest {

  @Test
  void defensivelyCopiesTeamIdsAndCreatesZeroResponse() {
    ArrayList<Long> teamIds = new ArrayList<>(List.of(11L));
    TagAnalyticsRequest request = new TagAnalyticsRequest(
        new CustomerSearchRequest(null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null),
        teamIds,
        null,
        null,
        null);
    teamIds.add(12L);

    TagAnalyticsWindow window = new TagAnalyticsWindow(
        LocalDateTime.of(2026, 7, 10, 12, 0),
        LocalDateTime.of(2026, 7, 16, 12, 0),
        TagTrendGranularity.DAY);
    TagAnalyticsResponse response = TagAnalyticsResponse.empty(window);

    assertThat(request.teamLeaderIds()).containsExactly(11L);
    assertThat(response.summary().matchedCustomerCount()).isZero();
    assertThat(response.categories()).isEmpty();
    assertThat(response.appliedWindow().tagFrom()).isEqualTo(window.from());
  }
}
```

- [ ] **Step 2: 运行测试，确认因类型不存在而失败**

Run: `mvn -q -Dtest=TagAnalyticsRequestTest test`  
Expected: FAIL，提示 `TagAnalyticsRequest`、`TagAnalyticsResponse` 等类不存在。

- [ ] **Step 3: 创建最小且完整的类型模型**

```java
// TagTrendGranularity.java
package com.privateflow.modules.analytics;

public enum TagTrendGranularity {
  DAY
}
```

```java
// TagAnalyticsWindow.java
package com.privateflow.modules.analytics;

import java.time.LocalDateTime;

public record TagAnalyticsWindow(
    LocalDateTime from,
    LocalDateTime to,
    TagTrendGranularity granularity) {
}
```

```java
// TagAnalyticsRequest.java
package com.privateflow.modules.analytics;

import com.privateflow.modules.customer.admin.CustomerSearchRequest;
import java.time.LocalDateTime;
import java.util.List;

public record TagAnalyticsRequest(
    CustomerSearchRequest customerFilter,
    List<Long> teamLeaderIds,
    LocalDateTime tagFrom,
    LocalDateTime tagTo,
    TagTrendGranularity granularity) {

  public TagAnalyticsRequest {
    teamLeaderIds = teamLeaderIds == null ? List.of() : List.copyOf(teamLeaderIds);
  }
}
```

`TagAnalyticsResponse.java` 使用以下完整嵌套记录，所有列表在规范构造器中 `List.copyOf`：

```java
package com.privateflow.modules.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TagAnalyticsResponse(
    Summary summary,
    List<CategoryRow> categories,
    List<TagRow> tags,
    List<DimensionRow> stores,
    List<DimensionRow> teams,
    List<DimensionRow> employees,
    List<SourceRow> tagSources,
    List<ReasonRow> unupdatedReasons,
    List<TrendRow> trend,
    FilterOptions filterOptions,
    AppliedWindow appliedWindow) {

  public TagAnalyticsResponse {
    categories = copy(categories);
    tags = copy(tags);
    stores = copy(stores);
    teams = copy(teams);
    employees = copy(employees);
    tagSources = copy(tagSources);
    unupdatedReasons = copy(unupdatedReasons);
    trend = copy(trend);
  }

  public static TagAnalyticsResponse empty(TagAnalyticsWindow window) {
    return new TagAnalyticsResponse(
        new Summary(0, 0, 0, 0.0, 0, 0, 0),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        FilterOptions.empty(),
        new AppliedWindow(window.from(), window.to(), window.granularity()));
  }

  private static <T> List<T> copy(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public record Summary(
      long matchedCustomerCount,
      long taggedCustomerCount,
      long activeAssignmentCount,
      double coverageRate,
      long systemAddedCount,
      long manualAddedOrChangedCount,
      long systemDecidedNoUpdateCount) {
  }

  public record CategoryRow(
      long categoryId,
      String categoryKey,
      String categoryName,
      long activeAssignmentCount,
      long taggedCustomerCount) {
  }

  public record TagRow(
      long categoryId,
      String categoryKey,
      String categoryName,
      long valueId,
      String valueCode,
      String displayName,
      long activeAssignmentCount,
      long taggedCustomerCount) {
  }

  public record DimensionRow(
      String key,
      String label,
      long activeAssignmentCount,
      long taggedCustomerCount) {
  }

  public record SourceRow(
      String sourceType,
      String sourceLabel,
      long addedAssignmentCount,
      long affectedCustomerCount) {
  }

  public record ReasonRow(
      String reasonCode,
      String reasonLabel,
      String scope,
      long customerCount,
      long decisionCount,
      String sampleReason) {
  }

  public record TrendRow(
      LocalDate date,
      long addedAssignmentCount,
      long invalidatedAssignmentCount,
      long netChange,
      long systemAddedCount,
      long manualAddedOrChangedCount) {
  }

  public record ValueOption(String value, String label) {
  }

  public record TeamOption(long leaderId, String label) {
  }

  public record EmployeeOption(String account, String label, Long leaderId) {
  }

  public record FilterOptions(
      List<ValueOption> stores,
      List<TeamOption> teams,
      List<EmployeeOption> employees,
      List<ValueOption> customerSources,
      List<ValueOption> tagSources) {

    public FilterOptions {
      stores = copy(stores);
      teams = copy(teams);
      employees = copy(employees);
      customerSources = copy(customerSources);
      tagSources = copy(tagSources);
    }

    public static FilterOptions empty() {
      return new FilterOptions(List.of(), List.of(), List.of(), List.of(), List.of());
    }
  }

  public record AppliedWindow(
      LocalDateTime tagFrom,
      LocalDateTime tagTo,
      TagTrendGranularity granularity) {
  }
}
```

- [ ] **Step 4: 运行测试，确认类型契约通过**

Run: `mvn -q -Dtest=TagAnalyticsRequestTest test`  
Expected: PASS，1 test。

- [ ] **Step 5: 提交类型契约**

```bash
git add src/main/java/com/privateflow/modules/analytics/TagAnalytics*.java src/main/java/com/privateflow/modules/analytics/TagTrendGranularity.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsRequestTest.java
git commit -m "feat: define tag analytics contract"
```

---

### Task 2: 服务层复用统一客户条件并规范化团队与时间

**Files:**
- Create: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsService.java`
- Create: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java`
- Test: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsServiceTest.java`

- [ ] **Step 1: 写失败的服务编排测试**

测试固定 `Clock`，设置 ADMIN `AuthContext`，使用真实 `CustomerFilterValidator` 和 `CustomerFilterQueryBuilder`，mock `TagAnalyticsRepository`。至少包含以下三个测试：

```java
@Test
void defaultsToSevenDaysAndIntersectsTeamWithExplicitEmployees() {
  CustomerSearchRequest filter = new CustomerSearchRequest(
      null, List.of("企微"), null, List.of("keeper-2", "keeper-3"),
      null, null, null, null, null, null, null, null, null, null, null);
  TagAnalyticsRequest request = new TagAnalyticsRequest(
      filter, List.of(9L), null, null, null);
  when(repository.resolveEnabledKeeperPhones(List.of(9L)))
      .thenReturn(List.of("keeper-1", "keeper-2"));
  TagAnalyticsWindow expectedWindow = new TagAnalyticsWindow(
      LocalDateTime.of(2026, 7, 9, 12, 0),
      LocalDateTime.of(2026, 7, 16, 12, 0),
      TagTrendGranularity.DAY);
  when(repository.analyze(any(), any(), eq(expectedWindow)))
      .thenReturn(TagAnalyticsResponse.empty(expectedWindow));

  service.analyze(request);

  ArgumentCaptor<CustomerQuerySpec> dataSpec = ArgumentCaptor.forClass(CustomerQuerySpec.class);
  verify(repository).analyze(dataSpec.capture(), any(), eq(expectedWindow));
  assertThat(dataSpec.getValue().whereClause()).contains("c.source_channel IN (?)");
  assertThat(dataSpec.getValue().args()).contains("企微", "keeper-2");
  assertThat(dataSpec.getValue().args()).doesNotContain("keeper-1", "keeper-3");
}

@Test
void emptyTeamEmployeeIntersectionProducesNoMatchSpec() {
  when(repository.resolveEnabledKeeperPhones(List.of(9L))).thenReturn(List.of("keeper-1"));
  TagAnalyticsRequest request = new TagAnalyticsRequest(
      requestFilter(List.of("keeper-2")), List.of(9L), null, null, TagTrendGranularity.DAY);
  TagAnalyticsWindow window = defaultWindow();
  when(repository.analyze(any(), any(), eq(window))).thenReturn(TagAnalyticsResponse.empty(window));

  service.analyze(request);

  ArgumentCaptor<CustomerQuerySpec> spec = ArgumentCaptor.forClass(CustomerQuerySpec.class);
  verify(repository).analyze(spec.capture(), any(), eq(window));
  assertThat(spec.getValue().whereClause()).isEqualTo(" WHERE 1=0");
}

@Test
void rejectsNonAdminAndRangesLongerThanNinetyDays() {
  AuthContext.set(new AuthUser("keeper-1", "管家", Role.KEEPER, null));
  assertThatThrownBy(() -> service.analyze(new TagAnalyticsRequest(null, List.of(), null, null, null)))
      .isInstanceOf(ApiException.class)
      .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
          .isEqualTo(ApiErrorCodes.FORBIDDEN));

  AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
  assertThatThrownBy(() -> service.analyze(new TagAnalyticsRequest(
      null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0),
      LocalDateTime.of(2026, 7, 16, 0, 0), TagTrendGranularity.DAY)))
      .isInstanceOf(ApiException.class)
      .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
          .isEqualTo(ApiErrorCodes.BAD_REQUEST));
}

private CustomerSearchRequest requestFilter(List<String> keepers) {
  return new CustomerSearchRequest(
      null, null, null, keepers, null, null, null,
      null, null, null, null, null, null, null, null);
}

private TagAnalyticsWindow defaultWindow() {
  return new TagAnalyticsWindow(
      LocalDateTime.of(2026, 7, 9, 12, 0),
      LocalDateTime.of(2026, 7, 16, 12, 0),
      TagTrendGranularity.DAY);
}
```

测试类字段和初始化使用以下代码，避免 mock 行为由测试间共享：

```java
private TagAnalyticsRepository repository;
private CustomerAccessScopeResolver accessScopeResolver;
private TagAnalyticsService service;

@BeforeEach
void setUp() {
  TagCandidateBuilder candidates = mock(TagCandidateBuilder.class);
  when(candidates.build(TagCandidatePurpose.FILTER)).thenReturn(List.of());
  repository = mock(TagAnalyticsRepository.class);
  accessScopeResolver = mock(CustomerAccessScopeResolver.class);
  when(accessScopeResolver.currentScope()).thenReturn(CustomerAccessScope.all());
  service = new TagAnalyticsService(
      new CustomerFilterValidator(candidates),
      accessScopeResolver,
      new CustomerFilterQueryBuilder(),
      repository,
      Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
  AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
}

@AfterEach
void tearDown() {
  AuthContext.clear();
}
```

- [ ] **Step 2: 运行服务测试，确认类不存在或行为未实现**

Run: `mvn -q -Dtest=TagAnalyticsServiceTest test`  
Expected: FAIL，提示 `TagAnalyticsService` 或 `TagAnalyticsRepository` 不存在。

- [ ] **Step 3: 实现服务层和 Repository 最小入口**

`TagAnalyticsService` 必须包含生产构造器和可注入 `Clock` 的测试构造器，核心实现如下：

```java
@Service
public class TagAnalyticsService {

  private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
  private static final Duration MAX_WINDOW = Duration.ofDays(90);

  private final CustomerFilterValidator filterValidator;
  private final CustomerAccessScopeResolver accessScopeResolver;
  private final CustomerFilterQueryBuilder queryBuilder;
  private final TagAnalyticsRepository repository;
  private final Clock clock;

  @Autowired
  public TagAnalyticsService(
      CustomerFilterValidator filterValidator,
      CustomerAccessScopeResolver accessScopeResolver,
      CustomerFilterQueryBuilder queryBuilder,
      TagAnalyticsRepository repository) {
    this(filterValidator, accessScopeResolver, queryBuilder, repository, Clock.systemDefaultZone());
  }

  TagAnalyticsService(
      CustomerFilterValidator filterValidator,
      CustomerAccessScopeResolver accessScopeResolver,
      CustomerFilterQueryBuilder queryBuilder,
      TagAnalyticsRepository repository,
      Clock clock) {
    this.filterValidator = filterValidator;
    this.accessScopeResolver = accessScopeResolver;
    this.queryBuilder = queryBuilder;
    this.repository = repository;
    this.clock = clock;
  }

  public TagAnalyticsResponse analyze(TagAnalyticsRequest request) {
    requireAdmin();
    TagAnalyticsRequest safe = request == null
        ? new TagAnalyticsRequest(null, List.of(), null, null, null)
        : request;
    TagAnalyticsWindow window = normalizeWindow(safe);
    CustomerFilter filter = filterValidator.validate(
        safe.customerFilter() == null ? CustomerFilter.empty() : safe.customerFilter().toFilter());
    CustomerAccessScope scope = accessScopeResolver.currentScope();
    CustomerQuerySpec optionSpec = queryBuilder.build(CustomerFilter.empty(), scope);
    CustomerQuerySpec dataSpec = dataSpec(filter, safe.teamLeaderIds(), scope);
    return repository.analyze(dataSpec, optionSpec, window);
  }

  private CustomerQuerySpec dataSpec(
      CustomerFilter filter,
      List<Long> rawTeamIds,
      CustomerAccessScope scope) {
    List<Long> teamIds = normalizeTeamIds(rawTeamIds);
    if (teamIds.isEmpty()) {
      return queryBuilder.build(filter, scope);
    }
    List<String> teamKeepers = repository.resolveEnabledKeeperPhones(teamIds);
    List<String> keepers = filter.assignedKeepers().isEmpty()
        ? teamKeepers
        : filter.assignedKeepers().stream().filter(teamKeepers::contains).toList();
    if (keepers.isEmpty()) {
      return new CustomerQuerySpec(" WHERE 1=0", List.of(), "c.id ASC");
    }
    return queryBuilder.build(withKeepers(filter, keepers), scope);
  }

  private TagAnalyticsWindow normalizeWindow(TagAnalyticsRequest request) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime from = request.tagFrom();
    LocalDateTime to = request.tagTo();
    if (from == null && to == null) {
      to = now;
      from = to.minus(DEFAULT_WINDOW);
    } else if (from == null) {
      from = to.minus(DEFAULT_WINDOW);
    } else if (to == null) {
      to = from.plus(DEFAULT_WINDOW);
    }
    if (from.isAfter(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "tag analytics window must be between 0 and 90 days");
    }
    TagTrendGranularity granularity = request.granularity() == null
        ? TagTrendGranularity.DAY
        : request.granularity();
    return new TagAnalyticsWindow(from, to, granularity);
  }

  private List<Long> normalizeTeamIds(List<Long> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    if (values.stream().anyMatch(value -> value == null || value <= 0)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "teamLeaderIds must contain positive ids");
    }
    return values.stream().distinct().toList();
  }

  private CustomerFilter withKeepers(CustomerFilter filter, List<String> keepers) {
    return new CustomerFilter(
        filter.keyword(), filter.sourceChannels(), filter.leadTypes(), keepers,
        filter.intendedStores(), filter.intendedProjects(), filter.customerStages(),
        filter.updatedFrom(), filter.updatedTo(), filter.tagGroups(), filter.tagGroupLogic(),
        filter.sortBy(), filter.sortDirection(), filter.page(), filter.pageSize());
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }
}
```

先创建可被 mock 的 Repository 入口，后续任务逐步替换空实现：

```java
@Repository
public class TagAnalyticsRepository {

  private final JdbcTemplate jdbcTemplate;

  public TagAnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<String> resolveEnabledKeeperPhones(List<Long> teamLeaderIds) {
    return List.of();
  }

  public TagAnalyticsResponse analyze(
      CustomerQuerySpec customerSpec,
      CustomerQuerySpec optionSpec,
      TagAnalyticsWindow window) {
    return TagAnalyticsResponse.empty(window);
  }
}
```

- [ ] **Step 4: 运行服务测试**

Run: `mvn -q -Dtest=TagAnalyticsServiceTest test`  
Expected: PASS，默认窗口、团队交集、空交集、400 和 403 均通过。

- [ ] **Step 5: 提交服务编排**

```bash
git add src/main/java/com/privateflow/modules/analytics/TagAnalyticsService.java src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsServiceTest.java
git commit -m "feat: build unified tag analytics query scope"
```

---

### Task 3: 暴露结构化标签统计 API

**Files:**
- Modify: `src/main/java/com/privateflow/modules/analytics/AnalyticsController.java`
- Modify: `src/test/java/com/privateflow/modules/analytics/AnalyticsControllerTest.java`

- [ ] **Step 1: 写失败的 POST 控制器测试**

在测试 setup 中新增 `TagAnalyticsService tagAnalyticsService` mock，并把控制器构造改为 `new AnalyticsController(service, tagAnalyticsService)`。新增：

```java
@Test
void tagAnalyticsBindsStructuredPostBody() throws Exception {
  TagAnalyticsWindow window = new TagAnalyticsWindow(
      LocalDateTime.of(2026, 7, 1, 0, 0),
      LocalDateTime.of(2026, 7, 16, 23, 59),
      TagTrendGranularity.DAY);
  when(tagAnalyticsService.analyze(any())).thenReturn(TagAnalyticsResponse.empty(window));

  mockMvc.perform(post("/admin/api/v1/analytics/tags")
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
              {
                "customerFilter":{"sourceChannels":["企微"],"assignedKeepers":["keeper-1"]},
                "teamLeaderIds":[9],
                "tagFrom":"2026-07-01T00:00:00",
                "tagTo":"2026-07-16T23:59:00",
                "granularity":"DAY"
              }
              """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.summary.activeAssignmentCount").value(0))
      .andExpect(jsonPath("$.data.appliedWindow.granularity").value("DAY"));

  verify(tagAnalyticsService).analyze(argThat(request ->
      request.teamLeaderIds().equals(List.of(9L))
          && request.customerFilter().sourceChannels().equals(List.of("企微"))));
}
```

- [ ] **Step 2: 运行测试，确认 404 或构造器不匹配**

Run: `mvn -q -Dtest=AnalyticsControllerTest test`  
Expected: FAIL，新 POST 路由尚不存在。

- [ ] **Step 3: 修改控制器构造器并增加 POST 方法**

```java
private final AnalyticsService service;
private final TagAnalyticsService tagAnalyticsService;

public AnalyticsController(
    AnalyticsService service,
    TagAnalyticsService tagAnalyticsService) {
  this.service = service;
  this.tagAnalyticsService = tagAnalyticsService;
}

@PostMapping("/admin/api/v1/analytics/tags")
public ApiResponse<TagAnalyticsResponse> tags(@RequestBody(required = false) TagAnalyticsRequest request) {
  return ApiResponse.ok(tagAnalyticsService.analyze(request));
}
```

同时补充 `PostMapping`、`RequestBody` import；现有 GET 方法保持不变。

- [ ] **Step 4: 运行控制器与服务测试**

Run: `mvn -q -Dtest=AnalyticsControllerTest,TagAnalyticsServiceTest test`  
Expected: PASS。

- [ ] **Step 5: 提交 API**

```bash
git add src/main/java/com/privateflow/modules/analytics/AnalyticsController.java src/test/java/com/privateflow/modules/analytics/AnalyticsControllerTest.java
git commit -m "feat: expose tag analytics api"
```

---

### Task 4: 实现当前有效标签概览、分类和标签值统计

**Files:**
- Modify: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java`
- Test: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java`

- [ ] **Step 1: 创建 H2 fixture 并写失败测试**

测试数据库使用 `jdbc:h2:mem:tag_analytics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`。fixture 必须创建：`customers`、`accounts`、`tag_categories`、`tag_values`、`customer_tag_assignments`、`tag_analysis_runs`、`tag_analysis_results`、`unmatched_legacy_tag_values`、`system_tag_suggestions`，列名与生产查询一致。

测试类使用以下完整 fixture；后续 Task 5-7 继续复用这些 helper，不重新定义第二套 schema：

```java
private JdbcTemplate jdbcTemplate;
private TagAnalyticsRepository repository;

@BeforeEach
void setUp() {
  DriverManagerDataSource dataSource = new DriverManagerDataSource(
      "jdbc:h2:mem:tag_analytics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "sa",
      "");
  jdbcTemplate = new JdbcTemplate(dataSource);
  for (String table : List.of(
      "system_tag_suggestions", "unmatched_legacy_tag_values", "tag_analysis_results",
      "tag_analysis_runs", "customer_tag_assignments", "tag_values", "tag_categories",
      "accounts", "customers")) {
    jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
  }
  jdbcTemplate.execute("""
      CREATE TABLE customers (
        id BIGINT PRIMARY KEY,
        phone VARCHAR(20) NOT NULL,
        nickname VARCHAR(100),
        source_channel VARCHAR(50),
        lead_type VARCHAR(20),
        assigned_keeper VARCHAR(50),
        intended_store VARCHAR(100),
        intended_project VARCHAR(100),
        customer_stage VARCHAR(50),
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE accounts (
        id BIGINT PRIMARY KEY,
        phone VARCHAR(50),
        username VARCHAR(50) NOT NULL,
        display_name VARCHAR(100) NOT NULL,
        role VARCHAR(20) NOT NULL,
        leader_id BIGINT,
        is_enabled TINYINT NOT NULL
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE tag_categories (
        id BIGINT PRIMARY KEY,
        category_key VARCHAR(50) NOT NULL,
        category_name VARCHAR(100) NOT NULL,
        bound_field VARCHAR(50),
        is_enabled TINYINT NOT NULL,
        merged_into_id BIGINT,
        use_for_filter TINYINT NOT NULL,
        use_for_statistics TINYINT NOT NULL
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE tag_values (
        id BIGINT PRIMARY KEY,
        category_id BIGINT NOT NULL,
        tag_value VARCHAR(50) NOT NULL,
        display_name VARCHAR(100) NOT NULL,
        is_enabled TINYINT NOT NULL,
        merged_into_id BIGINT
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE customer_tag_assignments (
        id BIGINT PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        category_id BIGINT NOT NULL,
        tag_value_id BIGINT NOT NULL,
        is_active TINYINT NOT NULL,
        source_type VARCHAR(32) NOT NULL,
        created_at DATETIME NOT NULL,
        invalidated_at DATETIME
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE tag_analysis_runs (
        id BIGINT PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        status VARCHAR(20) NOT NULL,
        error_message VARCHAR(1000),
        finished_at DATETIME,
        created_at DATETIME NOT NULL
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE tag_analysis_results (
        id BIGINT PRIMARY KEY,
        analysis_run_id BIGINT NOT NULL,
        category_id BIGINT NOT NULL,
        tag_value_id BIGINT,
        result_type VARCHAR(24) NOT NULL,
        requested_action VARCHAR(20) NOT NULL,
        validation_status VARCHAR(20) NOT NULL,
        validation_reason VARCHAR(1000)
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE unmatched_legacy_tag_values (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        status VARCHAR(20) NOT NULL,
        raw_value VARCHAR(500)
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE system_tag_suggestions (
        id BIGINT PRIMARY KEY,
        customer_id BIGINT,
        tag_value_id BIGINT,
        status VARCHAR(20) NOT NULL
      )
      """);
  repository = new TagAnalyticsRepository(jdbcTemplate);
}

private CustomerQuerySpec allSpec() {
  return new CustomerQuerySpec(" WHERE 1=1", List.of(), "c.id ASC");
}

private TagAnalyticsWindow window() {
  return new TagAnalyticsWindow(
      LocalDateTime.of(2026, 7, 10, 0, 0),
      LocalDateTime.of(2026, 7, 16, 23, 59, 59),
      TagTrendGranularity.DAY);
}

private void seedCustomer(
    long id,
    String source,
    String store,
    String keeper,
    String updatedAt) {
  jdbcTemplate.update("""
      INSERT INTO customers (
        id, phone, nickname, source_channel, lead_type, assigned_keeper,
        intended_store, intended_project, customer_stage, created_at, updated_at
      ) VALUES (?, ?, ?, ?, 'XIAN_SUO', ?, ?, '产后修复', 'PENDING', '2026-07-01 09:00:00', ?)
      """, id, "1380000" + String.format("%04d", id), "客户" + id, source, keeper, store, updatedAt);
}

private void seedAccount(
    long id,
    String account,
    String displayName,
    String role,
    Long leaderId,
    int enabled) {
  jdbcTemplate.update("""
      INSERT INTO accounts (id, phone, username, display_name, role, leader_id, is_enabled)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """, id, account, account, displayName, role, leaderId, enabled);
}

private void seedCategory(
    long id,
    String key,
    String name,
    String boundField,
    int enabled,
    Long mergedIntoId,
    int useForStatistics) {
  jdbcTemplate.update("""
      INSERT INTO tag_categories (
        id, category_key, category_name, bound_field, is_enabled,
        merged_into_id, use_for_filter, use_for_statistics
      ) VALUES (?, ?, ?, ?, ?, ?, 1, ?)
      """, id, key, name, boundField, enabled, mergedIntoId, useForStatistics);
}

private void seedValue(
    long id,
    long categoryId,
    String code,
    String displayName,
    int enabled,
    Long mergedIntoId) {
  jdbcTemplate.update("""
      INSERT INTO tag_values (
        id, category_id, tag_value, display_name, is_enabled, merged_into_id
      ) VALUES (?, ?, ?, ?, ?, ?)
      """, id, categoryId, code, displayName, enabled, mergedIntoId);
}

private void seedAssignment(
    long id,
    long customerId,
    long categoryId,
    long valueId,
    int active,
    String sourceType,
    String createdAt,
    String invalidatedAt) {
  jdbcTemplate.update("""
      INSERT INTO customer_tag_assignments (
        id, customer_id, category_id, tag_value_id, is_active,
        source_type, created_at, invalidated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      """, id, customerId, categoryId, valueId, active, sourceType, createdAt, invalidatedAt);
}

private void seedAnalysisRun(
    long id,
    long customerId,
    String status,
    String finishedAt,
    String errorMessage) {
  jdbcTemplate.update("""
      INSERT INTO tag_analysis_runs (
        id, customer_id, status, error_message, finished_at, created_at
      ) VALUES (?, ?, ?, ?, ?, ?)
      """, id, customerId, status, errorMessage, finishedAt, finishedAt);
}

private void seedAnalysisResult(
    long id,
    long runId,
    long categoryId,
    Long valueId,
    String resultType,
    String action,
    String validationStatus,
    String reason) {
  jdbcTemplate.update("""
      INSERT INTO tag_analysis_results (
        id, analysis_run_id, category_id, tag_value_id, result_type,
        requested_action, validation_status, validation_reason
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      """, id, runId, categoryId, valueId, resultType, action, validationStatus, reason);
}
```

核心失败测试：

```java
@Test
void snapshotCountsOnlyCurrentEnabledUnmergedStatisticsTags() {
  seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedCustomer(2, "抖音", "南城店", "keeper-2", "2026-07-16 11:00:00");
  seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
  seedValue(101, 10, "HIGH", "高意向", 1, null);
  seedValue(102, 10, "LOW", "低意向", 0, null);
  seedAssignment(1001, 1, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);
  seedAssignment(1002, 2, 10, 101, 0, "MANUAL", "2026-07-11 09:00:00", "2026-07-13 09:00:00");
  seedAssignment(1003, 2, 10, 102, 1, "MANUAL", "2026-07-14 09:00:00", null);
  jdbcTemplate.update("INSERT INTO system_tag_suggestions (id, customer_id, tag_value_id, status) VALUES (1, 2, 101, 'PENDING')");

  TagAnalyticsResponse response = repository.analyze(
      new CustomerQuerySpec(" WHERE 1=1", List.of(), "c.id ASC"),
      new CustomerQuerySpec(" WHERE 1=1", List.of(), "c.id ASC"),
      window());

  assertThat(response.summary().matchedCustomerCount()).isEqualTo(2);
  assertThat(response.summary().taggedCustomerCount()).isEqualTo(1);
  assertThat(response.summary().activeAssignmentCount()).isEqualTo(1);
  assertThat(response.summary().coverageRate()).isEqualTo(0.5);
  assertThat(response.categories()).singleElement().satisfies(row -> {
    assertThat(row.categoryKey()).isEqualTo("intent_level");
    assertThat(row.activeAssignmentCount()).isEqualTo(1);
  });
  assertThat(response.tags()).singleElement().satisfies(row -> {
    assertThat(row.valueCode()).isEqualTo("HIGH");
    assertThat(row.displayName()).isEqualTo("高意向");
  });
}
```

再增加一个使用真实 `CustomerFilterQueryBuilder` 的测试，筛选 `sourceChannels=["企微"]` 和有效标签 101，断言 `matchedCustomerCount=1`，证明 Repository 消费统一 QuerySpec 而不是重新解析条件。

```java
@Test
void consumesTheSameStructuredCustomerQuerySpecAsSearch() {
  seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedCustomer(2, "抖音", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
  seedValue(101, 10, "HIGH", "高意向", 1, null);
  seedAssignment(1001, 1, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);
  seedAssignment(1002, 2, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);
  CustomerFilter filter = new CustomerFilter(
      "", List.of("企微"), List.of(), List.of(), List.of(), List.of(), List.of(),
      null, null,
      List.of(new TagFilterGroup(10L, List.of(101L), TagMatchMode.ANY)),
      TagGroupLogic.AND, CustomerSortField.UPDATED_AT, SortDirection.DESC, 1, 20);
  CustomerQuerySpec spec = new CustomerFilterQueryBuilder().build(filter, CustomerAccessScope.all());

  TagAnalyticsResponse response = repository.analyze(spec, allSpec(), window());

  assertThat(response.summary().matchedCustomerCount()).isEqualTo(1);
  assertThat(response.summary().activeAssignmentCount()).isEqualTo(1);
}
```

- [ ] **Step 2: 运行 Repository 测试，确认当前返回全零**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest test`  
Expected: FAIL，概览和列表均为空。

- [ ] **Step 3: 实现快照 SQL 和稳定映射**

在 Repository 中定义唯一有效标签 FROM 片段：

```java
private static final String CURRENT_TAG_FROM = """
    FROM customers c
    JOIN customer_tag_assignments a ON a.customer_id = c.id AND a.is_active = 1
    JOIN tag_categories tc ON tc.id = a.category_id
      AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_statistics = 1
    JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
      AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
    """;
```

实现以下查询：

```java
private TagAnalyticsResponse.Summary loadSnapshotSummary(CustomerQuerySpec spec) {
  Long matched = jdbcTemplate.queryForObject(
      "SELECT COUNT(DISTINCT c.id) FROM customers c " + spec.whereClause(),
      Long.class,
      spec.args().toArray());
  Map<String, Object> tagged = jdbcTemplate.queryForObject("""
      SELECT COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
      """ + CURRENT_TAG_FROM + spec.whereClause(),
      (rs, rowNum) -> Map.of(
          "assignments", rs.getLong("assignment_count"),
          "customers", rs.getLong("customer_count")),
      spec.args().toArray());
  long matchedCount = matched == null ? 0 : matched;
  long taggedCount = ((Number) tagged.get("customers")).longValue();
  return new TagAnalyticsResponse.Summary(
      matchedCount,
      taggedCount,
      ((Number) tagged.get("assignments")).longValue(),
      matchedCount == 0 ? 0.0 : (double) taggedCount / matchedCount,
      0, 0, 0);
}
```

分类和标签查询均使用 `CURRENT_TAG_FROM + spec.whereClause()`，按数量降序、中文名和 ID 升序：

```java
private List<TagAnalyticsResponse.CategoryRow> loadCategories(CustomerQuerySpec spec) {
  return jdbcTemplate.query("""
      SELECT tc.id, tc.category_key, tc.category_name,
             COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
      """ + CURRENT_TAG_FROM + spec.whereClause() + """
      GROUP BY tc.id, tc.category_key, tc.category_name
      ORDER BY assignment_count DESC, tc.category_name ASC, tc.id ASC
      """, (rs, rowNum) -> new TagAnalyticsResponse.CategoryRow(
          rs.getLong("id"),
          rs.getString("category_key"),
          rs.getString("category_name"),
          rs.getLong("assignment_count"),
          rs.getLong("customer_count")), spec.args().toArray());
}

private List<TagAnalyticsResponse.TagRow> loadTags(CustomerQuerySpec spec) {
  return jdbcTemplate.query("""
      SELECT tc.id AS category_id, tc.category_key, tc.category_name,
             tv.id AS value_id, tv.tag_value, tv.display_name,
             COUNT(*) AS assignment_count, COUNT(DISTINCT c.id) AS customer_count
      """ + CURRENT_TAG_FROM + spec.whereClause() + """
      GROUP BY tc.id, tc.category_key, tc.category_name,
               tv.id, tv.tag_value, tv.display_name
      ORDER BY assignment_count DESC, tc.category_name ASC, tv.display_name ASC, tv.id ASC
      """, (rs, rowNum) -> new TagAnalyticsResponse.TagRow(
          rs.getLong("category_id"),
          rs.getString("category_key"),
          rs.getString("category_name"),
          rs.getLong("value_id"),
          rs.getString("tag_value"),
          rs.getString("display_name"),
          rs.getLong("assignment_count"),
          rs.getLong("customer_count")), spec.args().toArray());
}
```

更新 `analyze`，先装配 summary/categories/tags，其余列表仍为空。任何 SQL 都不得引用 `system_tag_suggestions`。

- [ ] **Step 4: 运行快照测试**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest test`  
Expected: PASS 当前快照和统一 QuerySpec 两组测试。

- [ ] **Step 5: 提交快照统计**

```bash
git add src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java
git commit -m "feat: aggregate current effective tags"
```

---

### Task 5: 实现门店、团队、员工和筛选选项

**Files:**
- Modify: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java`
- Modify: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java`

- [ ] **Step 1: 写失败的组织维度和团队解析测试**

```java
@Test
void groupsCurrentTagsByStoreTeamAndEmployeeAndResolvesTeamKeepers() {
  seedAccount(9, "leader-1", "组长一", "LEADER", null, 1);
  seedAccount(11, "keeper-1", "管家一", "KEEPER", 9L, 1);
  seedAccount(12, "keeper-disabled", "停用管家", "KEEPER", 9L, 0);
  seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
  seedValue(101, 10, "HIGH", "高意向", 1, null);
  seedAssignment(1001, 1, 10, 101, 1, "SYSTEM_INFERENCE", "2026-07-12 09:00:00", null);

  TagAnalyticsResponse response = repository.analyze(allSpec(), allSpec(), window());

  assertThat(repository.resolveEnabledKeeperPhones(List.of(9L))).containsExactly("keeper-1");
  assertThat(response.stores()).singleElement().extracting(TagAnalyticsResponse.DimensionRow::label)
      .isEqualTo("万江店");
  assertThat(response.teams()).singleElement().satisfies(row -> {
    assertThat(row.key()).isEqualTo("9");
    assertThat(row.label()).isEqualTo("组长一");
  });
  assertThat(response.employees()).singleElement().satisfies(row -> {
    assertThat(row.key()).isEqualTo("keeper-1");
    assertThat(row.label()).isEqualTo("管家一");
  });
  assertThat(response.filterOptions().employees()).extracting(TagAnalyticsResponse.EmployeeOption::account)
      .containsExactly("keeper-1");
}
```

补一个空值桶测试，断言门店为“未填写门店”、团队为“未归属团队”、员工为“未分配员工”。

- [ ] **Step 2: 运行定向测试，确认维度为空**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest#groupsCurrentTagsByStoreTeamAndEmployeeAndResolvesTeamKeepers test`  
Expected: FAIL。

- [ ] **Step 3: 实现团队解析、维度 SQL 和 optionSpec 查询**

团队解析必须一次 SQL 完成：

```java
public List<String> resolveEnabledKeeperPhones(List<Long> teamLeaderIds) {
  if (teamLeaderIds == null || teamLeaderIds.isEmpty()) {
    return List.of();
  }
  String placeholders = String.join(",", java.util.Collections.nCopies(teamLeaderIds.size(), "?"));
  return jdbcTemplate.queryForList("""
      SELECT COALESCE(phone, username)
      FROM accounts
      WHERE role = 'KEEPER' AND is_enabled = 1 AND leader_id IN (%s)
      ORDER BY id ASC
      """.formatted(placeholders), String.class, teamLeaderIds.toArray());
}
```

门店维度在 `CURRENT_TAG_FROM` 上按 `COALESCE(NULLIF(TRIM(c.intended_store), ''), '未填写门店')` 分组。员工和团队维度追加：

```sql
LEFT JOIN accounts employee
  ON COALESCE(employee.phone, employee.username) = c.assigned_keeper AND employee.is_enabled = 1
LEFT JOIN accounts leader
  ON leader.id = employee.leader_id AND leader.is_enabled = 1
```

团队 key 使用可空 `leader.id` 在 RowMapper 中转为 `"UNASSIGNED_TEAM"`；员工 key 使用空值兜底 `"UNASSIGNED_EMPLOYEE"`。

`filterOptions` 使用 `optionSpec` 而不是 `customerSpec`：

- stores：访问范围内 `customers.intended_store` 去重非空值。
- customerSources：访问范围内 `customers.source_channel` 去重非空值。
- employees：访问范围内客户关联到的启用账号，返回 account/displayName/leaderId。
- teams：上述员工关联到的启用组长，按 leaderId 去重。
- tagSources：访问范围内正式 assignment 的 `source_type` 去重。

- [ ] **Step 4: 运行完整 Repository 测试**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest test`  
Expected: PASS 快照、组织维度、空值桶和筛选选项。

- [ ] **Step 5: 提交分析维度**

```bash
git add src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java
git commit -m "feat: add tag analytics dimensions"
```

---

### Task 6: 实现标签来源和连续日期趋势

**Files:**
- Modify: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java`
- Modify: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java`

- [ ] **Step 1: 写失败的事件来源和趋势测试**

```java
@Test
void aggregatesAddedInvalidatedAndNetTrendInsideEventWindow() {
  seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedCategory(10, "intent_level", "意向等级", "intentLevel", 1, null, 1);
  seedValue(101, 10, "HIGH", "高意向", 1, null);
  seedAssignment(1001, 1, 10, 101, 0, "SYSTEM_INFERENCE", "2026-07-11 09:00:00", "2026-07-13 09:00:00");
  seedAssignment(1002, 1, 10, 101, 1, "MANUAL", "2026-07-14 09:00:00", null);

  TagAnalyticsResponse response = repository.analyze(allSpec(), allSpec(), window());

  assertThat(response.summary().systemAddedCount()).isEqualTo(1);
  assertThat(response.summary().manualAddedOrChangedCount()).isEqualTo(1);
  assertThat(response.tagSources()).extracting(
      TagAnalyticsResponse.SourceRow::sourceType,
      TagAnalyticsResponse.SourceRow::addedAssignmentCount)
      .containsExactly(tuple("MANUAL", 1L), tuple("SYSTEM_INFERENCE", 1L));
  assertThat(response.trend()).hasSize(7);
  assertThat(response.trend().stream().filter(row -> row.date().equals(LocalDate.of(2026, 7, 13))).findFirst())
      .get().satisfies(row -> {
        assertThat(row.invalidatedAssignmentCount()).isEqualTo(1);
        assertThat(row.netChange()).isEqualTo(-1);
      });
}
```

另加测试：窗口外 assignment 不计；`system_tag_suggestions` 和 `unmatched_legacy_tag_values` 即使时间命中也不影响来源或趋势。

- [ ] **Step 2: 运行测试，确认事件统计为零**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest#aggregatesAddedInvalidatedAndNetTrendInsideEventWindow test`  
Expected: FAIL。

- [ ] **Step 3: 实现事件 SQL 和 Java 补零**

事件 FROM 使用正式 assignment 和当前 STATISTICS 目录，但不要求 `a.is_active=1`：

```java
private static final String TAG_EVENT_FROM = """
    FROM customers c
    JOIN customer_tag_assignments a ON a.customer_id = c.id
    JOIN tag_categories tc ON tc.id = a.category_id
      AND tc.is_enabled = 1 AND tc.merged_into_id IS NULL AND tc.use_for_statistics = 1
    JOIN tag_values tv ON tv.id = a.tag_value_id AND tv.category_id = a.category_id
      AND tv.is_enabled = 1 AND tv.merged_into_id IS NULL
    """;
```

来源查询追加 `AND a.created_at >= ? AND a.created_at <= ?`，按 `a.source_type` 分组，返回 `COUNT(*)` 和 `COUNT(DISTINCT c.id)`。

新增与失效分别查询日期聚合，合并到 `Map<LocalDate, MutableTrend>`，再执行：

```java
List<TagAnalyticsResponse.TrendRow> rows = new ArrayList<>();
for (LocalDate date = window.from().toLocalDate();
     !date.isAfter(window.to().toLocalDate());
     date = date.plusDays(1)) {
  MutableTrend item = values.getOrDefault(date, MutableTrend.empty());
  rows.add(new TagAnalyticsResponse.TrendRow(
      date,
      item.added(),
      item.invalidated(),
      item.added() - item.invalidated(),
      item.systemAdded(),
      item.manualAdded()));
}
```

来源中文：`SYSTEM_INFERENCE -> 系统推断`、`MANUAL -> 人工设置`、`LEGACY_MIGRATION -> 历史迁移`，未知值原样展示。

- [ ] **Step 4: 运行 Repository 测试**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest test`  
Expected: PASS，趋势连续 7 天且来源计数正确。

- [ ] **Step 5: 提交事件统计**

```bash
git add src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java
git commit -m "feat: add tag source and trend analytics"
```

---

### Task 7: 实现系统未更新数量和原因命中

**Files:**
- Modify: `src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java`
- Modify: `src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java`

- [ ] **Step 1: 写失败的未更新原因测试**

fixture 插入五类客户并断言每个固定原因码出现：

```java
@Test
void reportsCurrentGapsAndEventWindowNoUpdateReasons() {
  seedCustomer(1, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedCustomer(2, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedAnalysisRun(201, 2, "REJECTED", "2026-07-14 09:00:00", "客户版本冲突");
  seedAnalysisResult(301, 201, 10, null, "UNABLE_TO_DETERMINE", "NONE", "REJECTED", "证据不足");
  seedCustomer(3, "企微", "万江店", "keeper-1", "2026-07-16 10:00:00");
  seedAnalysisRun(202, 3, "NO_CHANGE", "2026-07-15 09:00:00", null);
  seedAnalysisResult(302, 202, 10, null, "KEEP_CURRENT", "NONE", "REJECTED", "保持当前标签");
  jdbcTemplate.update("INSERT INTO unmatched_legacy_tag_values (customer_id, status, raw_value) VALUES (3, 'PENDING', '旧标签')");

  TagAnalyticsResponse response = repository.analyze(allSpec(), allSpec(), window());

  assertThat(response.summary().systemDecidedNoUpdateCount()).isEqualTo(2);
  assertThat(response.unupdatedReasons()).extracting(TagAnalyticsResponse.ReasonRow::reasonCode)
      .contains("NO_ANALYSIS", "LATEST_RUN_REJECTED", "LATEST_RUN_NO_CHANGE", "UNMATCHED_LEGACY_VALUE");
  assertThat(response.unupdatedReasons().stream()
      .filter(row -> row.reasonCode().equals("LATEST_RUN_REJECTED"))
      .findFirst()).get().satisfies(row -> {
        assertThat(row.scope()).isEqualTo("EVENT_WINDOW");
        assertThat(row.sampleReason()).contains("客户版本冲突");
      });
}
```

增加 `CUSTOMER_UPDATED_AFTER_TAG_CHANGE`：客户有正式 assignment，`customers.updated_at` 晚于最后 `created_at/invalidated_at`，断言命中；无 assignment 的客户不能只因更新时间命中该原因。

- [ ] **Step 2: 运行测试，确认原因列表为空**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest#reportsCurrentGapsAndEventWindowNoUpdateReasons test`  
Expected: FAIL。

- [ ] **Step 3: 用结构化状态实现固定原因查询**

实现五个独立计数，全部在 `customers c + customerSpec.whereClause()` 范围内：

```sql
-- NO_ANALYSIS
AND NOT EXISTS (SELECT 1 FROM tag_analysis_runs r WHERE r.customer_id = c.id)
```

```sql
-- LATEST_RUN_REJECTED / LATEST_RUN_NO_CHANGE
AND EXISTS (
  SELECT 1 FROM tag_analysis_runs r
  WHERE r.id = (SELECT MAX(r2.id) FROM tag_analysis_runs r2 WHERE r2.customer_id = c.id)
    AND r.finished_at >= ? AND r.finished_at <= ? AND r.status = ?
)
```

```sql
-- UNMATCHED_LEGACY_VALUE
AND EXISTS (
  SELECT 1 FROM unmatched_legacy_tag_values u
  WHERE u.customer_id = c.id AND u.status = 'PENDING'
)
```

```sql
-- CUSTOMER_UPDATED_AFTER_TAG_CHANGE
AND EXISTS (SELECT 1 FROM customer_tag_assignments a0 WHERE a0.customer_id = c.id)
AND c.updated_at > (
  SELECT MAX(COALESCE(a1.invalidated_at, a1.created_at))
  FROM customer_tag_assignments a1 WHERE a1.customer_id = c.id
)
```

`systemDecidedNoUpdateCount` 使用窗口内 `tag_analysis_results`，条件为运行状态不是 `COMPLETED`，或结果 `validation_status='REJECTED'`，或 `requested_action='NONE'`；按 result 行计数。`sampleReason` 优先级：`error_message`、`validation_reason`、`result_type`。

- [ ] **Step 4: 运行 Repository 与服务测试**

Run: `mvn -q -Dtest=TagAnalyticsRepositoryTest,TagAnalyticsServiceTest test`  
Expected: PASS。

- [ ] **Step 5: 提交原因统计**

```bash
git add src/main/java/com/privateflow/modules/analytics/TagAnalyticsRepository.java src/test/java/com/privateflow/modules/analytics/TagAnalyticsRepositoryTest.java
git commit -m "feat: explain missing tag updates"
```

---

### Task 8: 用统一有效标签替换旧线索漏斗意向字段判断

**Files:**
- Modify: `src/main/java/com/privateflow/modules/analytics/AnalyticsRepository.java`
- Test: `src/test/java/com/privateflow/modules/analytics/AnalyticsRepositoryTagFunnelTest.java`

- [ ] **Step 1: 写失败的旧字段隔离测试**

H2 fixture 创建 `customers`、`customer_tag_assignments`、`tag_categories`、`tag_values`。客户 `intent_level='HIGH'`，但初始没有 assignment：

```java
@Test
void xianSuoIntentStepsRequireCurrentStatisticsTagAssignment() {
  jdbcTemplate.update("""
      INSERT INTO customers (
        id, phone, lead_type, intent_level, followup_notes, purchased_project,
        arrived, customer_stage, created_at, updated_at
      ) VALUES (1, '13800000001', 'XIAN_SUO', 'HIGH', '已沟通', '修复卡',
                '是', '已到店', '2026-07-01 09:00:00', '2026-07-10 09:00:00')
      """);

  Map<String, Object> before = repository.funnels("XIAN_SUO", new AnalyticsScope(Role.ADMIN, "admin", null));
  assertThat(stageCount(before, "intentConfirmed")).isZero();

  seedIntentDirectoryAndAssignment(1L, 10L, 101L, "HIGH");
  Map<String, Object> after = repository.funnels("XIAN_SUO", new AnalyticsScope(Role.ADMIN, "admin", null));
  assertThat(stageCount(after, "intentConfirmed")).isEqualTo(1);
  assertThat(stageCount(after, "purchased")).isEqualTo(1);

  jdbcTemplate.update("UPDATE tag_values SET is_enabled = 0 WHERE id = 101");
  Map<String, Object> disabled = repository.funnels("XIAN_SUO", new AnalyticsScope(Role.ADMIN, "admin", null));
  assertThat(stageCount(disabled, "intentConfirmed")).isZero();
}

@SuppressWarnings("unchecked")
private long stageCount(Map<String, Object> result, String stageKey) {
  Map<String, Object> xianSuo = (Map<String, Object>) result.get("xianSuo");
  List<Map<String, Object>> stages = (List<Map<String, Object>>) xianSuo.get("stages");
  return stages.stream()
      .filter(stage -> stageKey.equals(stage.get("stageKey")))
      .map(stage -> ((Number) stage.get("count")).longValue())
      .findFirst()
      .orElseThrow();
}

private void seedIntentDirectoryAndAssignment(
    long customerId,
    long categoryId,
    long valueId,
    String valueCode) {
  jdbcTemplate.update("""
      INSERT INTO tag_categories (
        id, category_key, category_name, bound_field, is_enabled,
        merged_into_id, use_for_statistics
      ) VALUES (?, 'intent_level', '意向等级', 'intentLevel', 1, NULL, 1)
      """, categoryId);
  jdbcTemplate.update("""
      INSERT INTO tag_values (
        id, category_id, tag_value, display_name, is_enabled, merged_into_id
      ) VALUES (?, ?, ?, '高意向', 1, NULL)
      """, valueId, categoryId, valueCode);
  jdbcTemplate.update("""
      INSERT INTO customer_tag_assignments (
        id, customer_id, category_id, tag_value_id, is_active
      ) VALUES (1001, ?, ?, ?, 1)
      """, customerId, categoryId, valueId);
}
```

测试 `@BeforeEach` 使用以下精确 schema，确保 `funnels()` 内部调用的 `lifecycle()` 也能执行：

```java
@BeforeEach
void setUp() {
  DriverManagerDataSource dataSource = new DriverManagerDataSource(
      "jdbc:h2:mem:analytics_funnel;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "sa",
      "");
  jdbcTemplate = new JdbcTemplate(dataSource);
  jdbcTemplate.execute("DROP TABLE IF EXISTS customer_tag_assignments");
  jdbcTemplate.execute("DROP TABLE IF EXISTS tag_values");
  jdbcTemplate.execute("DROP TABLE IF EXISTS tag_categories");
  jdbcTemplate.execute("DROP TABLE IF EXISTS customers");
  jdbcTemplate.execute("""
      CREATE TABLE customers (
        id BIGINT PRIMARY KEY,
        phone VARCHAR(20) NOT NULL,
        lead_type VARCHAR(20),
        intent_level VARCHAR(20),
        assigned_keeper VARCHAR(50),
        followup_notes VARCHAR(1000),
        purchased_project VARCHAR(200),
        arrived VARCHAR(10),
        customer_stage VARCHAR(50),
        last_followup_at DATETIME,
        next_followup_at DATETIME,
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE tag_categories (
        id BIGINT PRIMARY KEY,
        category_key VARCHAR(50) NOT NULL,
        category_name VARCHAR(100) NOT NULL,
        bound_field VARCHAR(50),
        is_enabled TINYINT NOT NULL,
        merged_into_id BIGINT,
        use_for_statistics TINYINT NOT NULL
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE tag_values (
        id BIGINT PRIMARY KEY,
        category_id BIGINT NOT NULL,
        tag_value VARCHAR(50) NOT NULL,
        display_name VARCHAR(100) NOT NULL,
        is_enabled TINYINT NOT NULL,
        merged_into_id BIGINT
      )
      """);
  jdbcTemplate.execute("""
      CREATE TABLE customer_tag_assignments (
        id BIGINT PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        category_id BIGINT NOT NULL,
        tag_value_id BIGINT NOT NULL,
        is_active TINYINT NOT NULL
      )
      """);
  repository = new AnalyticsRepository(jdbcTemplate);
}
```

- [ ] **Step 2: 运行测试，确认旧字段仍会错误命中**

Run: `mvn -q -Dtest=AnalyticsRepositoryTagFunnelTest test`  
Expected: FAIL，`before` 中 intentConfirmed 为 1。

- [ ] **Step 3: 替换漏斗条件**

定义统一标签谓词常量：

```java
private static final String CONFIRMED_INTENT_TAG = """
     AND EXISTS (
       SELECT 1
       FROM customer_tag_assignments intent_assignment
       JOIN tag_categories intent_category
         ON intent_category.id = intent_assignment.category_id
        AND intent_category.bound_field = 'intentLevel'
        AND intent_category.is_enabled = 1
        AND intent_category.merged_into_id IS NULL
        AND intent_category.use_for_statistics = 1
       JOIN tag_values intent_value
         ON intent_value.id = intent_assignment.tag_value_id
        AND intent_value.category_id = intent_assignment.category_id
        AND intent_value.tag_value IN ('HIGH', 'MEDIUM')
        AND intent_value.is_enabled = 1
        AND intent_value.merged_into_id IS NULL
       WHERE intent_assignment.customer_id = c.id
         AND intent_assignment.is_active = 1
     )
    """;
```

把 XIAN_SUO 的 `intentConfirmed`、`purchased`、`arrived` 三步中的 `intent_level IN ('HIGH','MEDIUM')` 替换为该谓词；团购漏斗和非标签业务条件不变。

- [ ] **Step 4: 运行漏斗、analytics Controller 和全部 analytics 定向测试**

Run: `mvn -q -Dtest='com.privateflow.modules.analytics.*Test' test`  
Expected: PASS。

- [ ] **Step 5: 提交漏斗修正**

```bash
git add src/main/java/com/privateflow/modules/analytics/AnalyticsRepository.java src/test/java/com/privateflow/modules/analytics/AnalyticsRepositoryTagFunnelTest.java
git commit -m "fix: use unified intent tags in analytics funnel"
```

---

### Task 9: 创建前端标签统计类型、请求和 CSV helper

**Files:**
- Create: `desktop/src/renderer/modules/admin/tagAnalytics.ts`
- Create: `desktop/src/renderer/modules/admin/tagAnalytics.test.ts`

- [ ] **Step 1: 写失败的 helper 测试**

```ts
import { describe, expect, it } from 'vitest';
import { buildTagAnalyticsRequest, tagAnalyticsCsvSections } from './tagAnalytics';

describe('tag analytics helpers', () => {
  it('serializes customer, team, event-window and tag filters', () => {
    const payload = buildTagAnalyticsRequest({
      sourceChannels: ['企微'],
      leadTypes: ['XIAN_SUO'],
      assignedKeepers: ['keeper-1'],
      intendedStores: ['万江店'],
      updatedFrom: '2026-07-01T00:00:00',
      updatedTo: '2026-07-16T23:59:59',
      teamLeaderIds: [9],
      tagFrom: '2026-07-10T00:00:00',
      tagTo: '2026-07-16T23:59:59',
      tagGroups: [{ categoryId: 10, valueIds: [101], match: 'ANY' }],
      tagGroupLogic: 'AND'
    });

    expect(payload).toMatchObject({
      customerFilter: {
        sourceChannels: ['企微'],
        assignedKeepers: ['keeper-1'],
        intendedStores: ['万江店'],
        tagGroups: [{ categoryId: 10, valueIds: [101], match: 'ANY' }]
      },
      teamLeaderIds: [9],
      granularity: 'DAY'
    });
  });

  it('exports display names and internal codes', () => {
    const sections = tagAnalyticsCsvSections({
      summary: { matchedCustomerCount: 2, taggedCustomerCount: 1, activeAssignmentCount: 1, coverageRate: 0.5, systemAddedCount: 1, manualAddedOrChangedCount: 0, systemDecidedNoUpdateCount: 1 },
      categories: [],
      tags: [{ categoryId: 10, categoryKey: 'intent_level', categoryName: '意向等级', valueId: 101, valueCode: 'HIGH', displayName: '高意向', activeAssignmentCount: 1, taggedCustomerCount: 1 }],
      stores: [], teams: [], employees: [], tagSources: [], unupdatedReasons: [], trend: [],
      filterOptions: { stores: [], teams: [], employees: [], customerSources: [], tagSources: [] },
      appliedWindow: { tagFrom: '2026-07-10T00:00:00', tagTo: '2026-07-16T23:59:59', granularity: 'DAY' }
    });

    expect(sections.join('\n')).toContain('高意向');
    expect(sections.join('\n')).toContain('HIGH');
  });
});
```

- [ ] **Step 2: 运行 helper 测试，确认模块不存在**

Run: `cd desktop; npm test -- --run src/renderer/modules/admin/tagAnalytics.test.ts`  
Expected: FAIL，找不到 `tagAnalytics.ts`。

- [ ] **Step 3: 实现完整类型和纯函数**

`tagAnalytics.ts` 必须导出：

- `TagAnalyticsResponse` 及所有行类型，与后端字段逐一一致。
- `TagAnalyticsRequestInput`，包含客户条件、`teamLeaderIds`、`tagFrom/tagTo`、动态 `tagGroups`。
- `buildTagAnalyticsRequest(input)`：去掉空字符串、复制数组、固定 `granularity:'DAY'`，不发送分页和排序。
- `tagAnalyticsCsvSections(data)`：生成概览、分类、标签、门店、团队、员工、标签来源、未更新原因、趋势九个 CSV 字符串；所有单元格使用本文件内 `csvCell` 转义双引号、逗号和换行。
- `tagSourceLabel` 和 `reasonScopeLabel`：提供中文标签和未知值兜底。

类型定义使用以下字段，不使用 `AnyRecord` 掩盖契约错误：

```ts
export type TagMatchMode = 'ANY' | 'ALL';
export type TagGroupLogic = 'AND' | 'OR';

export type TagAnalyticsRequestInput = {
  keyword?: string;
  sourceChannels?: string[];
  leadTypes?: string[];
  assignedKeepers?: string[];
  intendedStores?: string[];
  intendedProjects?: string[];
  customerStages?: string[];
  updatedFrom?: string;
  updatedTo?: string;
  teamLeaderIds?: number[];
  tagFrom?: string;
  tagTo?: string;
  tagGroups?: Array<{ categoryId: number; valueIds: number[]; match: TagMatchMode }>;
  tagGroupLogic?: TagGroupLogic;
};

export type TagAnalyticsRequest = {
  customerFilter: {
    keyword: string;
    sourceChannels: string[];
    leadTypes: string[];
    assignedKeepers: string[];
    intendedStores: string[];
    intendedProjects: string[];
    customerStages: string[];
    updatedFrom: string | null;
    updatedTo: string | null;
    tagGroups: Array<{ categoryId: number; valueIds: number[]; match: TagMatchMode }>;
    tagGroupLogic: TagGroupLogic;
  };
  teamLeaderIds: number[];
  tagFrom: string | null;
  tagTo: string | null;
  granularity: 'DAY';
};

export type TagAnalyticsResponse = {
  summary: {
    matchedCustomerCount: number;
    taggedCustomerCount: number;
    activeAssignmentCount: number;
    coverageRate: number;
    systemAddedCount: number;
    manualAddedOrChangedCount: number;
    systemDecidedNoUpdateCount: number;
  };
  categories: Array<{ categoryId: number; categoryKey: string; categoryName: string; activeAssignmentCount: number; taggedCustomerCount: number }>;
  tags: Array<{ categoryId: number; categoryKey: string; categoryName: string; valueId: number; valueCode: string; displayName: string; activeAssignmentCount: number; taggedCustomerCount: number }>;
  stores: Array<{ key: string; label: string; activeAssignmentCount: number; taggedCustomerCount: number }>;
  teams: Array<{ key: string; label: string; activeAssignmentCount: number; taggedCustomerCount: number }>;
  employees: Array<{ key: string; label: string; activeAssignmentCount: number; taggedCustomerCount: number }>;
  tagSources: Array<{ sourceType: string; sourceLabel: string; addedAssignmentCount: number; affectedCustomerCount: number }>;
  unupdatedReasons: Array<{ reasonCode: string; reasonLabel: string; scope: 'CURRENT_GAP' | 'EVENT_WINDOW'; customerCount: number; decisionCount: number; sampleReason: string | null }>;
  trend: Array<{ date: string; addedAssignmentCount: number; invalidatedAssignmentCount: number; netChange: number; systemAddedCount: number; manualAddedOrChangedCount: number }>;
  filterOptions: {
    stores: Array<{ value: string; label: string }>;
    teams: Array<{ leaderId: number; label: string }>;
    employees: Array<{ account: string; label: string; leaderId: number | null }>;
    customerSources: Array<{ value: string; label: string }>;
    tagSources: Array<{ value: string; label: string }>;
  };
  appliedWindow: { tagFrom: string; tagTo: string; granularity: 'DAY' };
};

function compact(values: string[] | undefined): string[] {
  return [...new Set((values ?? []).map((value) => value.trim()).filter(Boolean))];
}
```

核心 payload 实现：

```ts
export function buildTagAnalyticsRequest(input: TagAnalyticsRequestInput): TagAnalyticsRequest {
  return {
    customerFilter: {
      keyword: input.keyword?.trim() || '',
      sourceChannels: compact(input.sourceChannels),
      leadTypes: compact(input.leadTypes),
      assignedKeepers: compact(input.assignedKeepers),
      intendedStores: compact(input.intendedStores),
      intendedProjects: compact(input.intendedProjects),
      customerStages: compact(input.customerStages),
      updatedFrom: input.updatedFrom || null,
      updatedTo: input.updatedTo || null,
      tagGroups: (input.tagGroups ?? []).map((group) => ({
        categoryId: Number(group.categoryId),
        valueIds: group.valueIds.map(Number),
        match: group.match
      })),
      tagGroupLogic: input.tagGroupLogic ?? 'AND'
    },
    teamLeaderIds: (input.teamLeaderIds ?? []).map(Number),
    tagFrom: input.tagFrom || null,
    tagTo: input.tagTo || null,
    granularity: 'DAY'
  };
}
```

CSV 实现使用以下纯函数，不依赖 DOM：

```ts
function csvCell(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function csvSection(title: string, headers: string[], rows: unknown[][]): string {
  return [
    csvCell(title),
    headers.map(csvCell).join(','),
    ...rows.map((row) => row.map(csvCell).join(','))
  ].join('\n');
}

export function tagSourceLabel(value: string): string {
  return ({
    SYSTEM_INFERENCE: '系统推断',
    MANUAL: '人工设置',
    LEGACY_MIGRATION: '历史迁移'
  } as Record<string, string>)[value] ?? value;
}

export function reasonScopeLabel(value: string): string {
  return value === 'CURRENT_GAP' ? '当前覆盖缺口' : value === 'EVENT_WINDOW' ? '时间窗口事件' : value;
}

export function tagAnalyticsCsvSections(data: TagAnalyticsResponse): string[] {
  return [
    csvSection('标签统计概览', ['指标', '数值'], [
      ['匹配客户', data.summary.matchedCustomerCount],
      ['已打标签客户', data.summary.taggedCustomerCount],
      ['当前有效标签', data.summary.activeAssignmentCount],
      ['覆盖率', `${(data.summary.coverageRate * 100).toFixed(2)}%`],
      ['系统新增', data.summary.systemAddedCount],
      ['人工新增或修改', data.summary.manualAddedOrChangedCount],
      ['系统判断未更新', data.summary.systemDecidedNoUpdateCount]
    ]),
    csvSection('标签分类', ['分类名称', '分类代码', '有效标签数', '客户数'],
      data.categories.map((row) => [row.categoryName, row.categoryKey, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('标签值', ['分类名称', '分类代码', '标签名称', '标签代码', '有效标签数', '客户数'],
      data.tags.map((row) => [row.categoryName, row.categoryKey, row.displayName, row.valueCode, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('门店', ['门店', '有效标签数', '客户数'],
      data.stores.map((row) => [row.label, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('团队', ['团队', '团队标识', '有效标签数', '客户数'],
      data.teams.map((row) => [row.label, row.key, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('员工', ['员工', '账号', '有效标签数', '客户数'],
      data.employees.map((row) => [row.label, row.key, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('标签来源', ['来源', '来源代码', '新增标签数', '客户数'],
      data.tagSources.map((row) => [row.sourceLabel || tagSourceLabel(row.sourceType), row.sourceType, row.addedAssignmentCount, row.affectedCustomerCount])),
    csvSection('未更新原因', ['原因', '原因代码', '范围', '客户数', '判断数', '示例原因'],
      data.unupdatedReasons.map((row) => [row.reasonLabel, row.reasonCode, reasonScopeLabel(row.scope), row.customerCount, row.decisionCount, row.sampleReason ?? ''])),
    csvSection('标签趋势', ['日期', '新增', '失效', '净变化', '系统新增', '人工新增或修改'],
      data.trend.map((row) => [row.date, row.addedAssignmentCount, row.invalidatedAssignmentCount, row.netChange, row.systemAddedCount, row.manualAddedOrChangedCount]))
  ];
}
```

- [ ] **Step 4: 运行 helper 测试和 typecheck**

Run: `cd desktop; npm test -- --run src/renderer/modules/admin/tagAnalytics.test.ts`  
Expected: PASS，2 tests。  
Run: `cd desktop; npm run typecheck`  
Expected: PASS。

- [ ] **Step 5: 提交 helper**

```bash
git add desktop/src/renderer/modules/admin/tagAnalytics.ts desktop/src/renderer/modules/admin/tagAnalytics.test.ts
git commit -m "feat: add tag analytics frontend helpers"
```

---

### Task 10: 接入运营看板、局部失败和 CSV

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Modify: `desktop/src/renderer/styles.css`

- [ ] **Step 1: 扩展 mock 数据并写失败的看板集成测试**

在 `apiData` 增加 `/admin/api/v1/analytics/tags` 的完整响应；`postJson` mock 在该路径返回数据。新增测试：

```ts
it('loads, renders and filters tag analytics independently', async () => {
  const { app, host } = await mountConsole();
  findSubnavButton(host, '运营分析看板').click();
  await flushSave();

  expect(mainText(host)).toContain('标签统计');
  expect(mainText(host)).toContain('正式标签 3');
  expect(mainText(host)).toContain('高意向');
  expect(mainText(host)).toContain('系统推断');

  setInputValue(controlByLabel<HTMLSelectElement>(host, '标签统计门店'), '万江店');
  setInputValue(controlByLabel<HTMLSelectElement>(host, '标签统计团队'), '9');
  findButton(host, '刷新标签统计').click();
  await flushSave();

  expect(apiMocks.postJson).toHaveBeenLastCalledWith(
      '/admin/api/v1/analytics/tags',
      expect.objectContaining({
        customerFilter: expect.objectContaining({ intendedStores: ['万江店'] }),
        teamLeaderIds: [9],
        granularity: 'DAY'
      }));
  app.unmount();
});
```

新增失败降级测试：标签 POST 抛 `tag analytics timeout`，断言现有“同事效能”“张三”仍显示、标签区显示“标签统计刷新失败”和“重试标签统计”。

- [ ] **Step 2: 运行 AdminConsole 定向测试，确认标签区不存在**

Run: `cd desktop; npm test -- --run src/renderer/modules/admin/AdminConsole.test.ts`  
Expected: FAIL，新文本和 POST 调用不存在。

- [ ] **Step 3: 实现状态、payload、加载和展示**

在 `<script setup>` 导入 helper 类型与函数，增加独立状态：

```ts
const tagAnalytics = ref<TagAnalyticsResponse | null>(null);
const tagAnalyticsStatus = reactive({ loading: false, error: '' });
const tagAnalyticsFilters = reactive({
  updatedFrom: '',
  updatedTo: '',
  tagFrom: '',
  tagTo: '',
  sourceChannel: '',
  intendedStore: '',
  teamLeaderId: '',
  assignedKeeper: '',
  tagGroupLogic: 'AND' as 'AND' | 'OR'
});
const analyticsTagSelections = reactive<Record<string, string | string[]>>({});
const analyticsTagMatchModes = reactive<Record<string, 'ANY' | 'ALL'>>({});
```

请求组装必须使用以下函数，日期控件的日期值转换为后端需要的本地日期时间：

```ts
function localDayStart(value: string): string | undefined {
  return value ? `${value}T00:00:00` : undefined;
}

function localDayEnd(value: string): string | undefined {
  return value ? `${value}T23:59:59` : undefined;
}

function analyticsTagGroups() {
  return customerFilterCategories.value.flatMap((category) => {
    const raw = analyticsTagSelections[String(category.id)];
    const values = (Array.isArray(raw) ? raw : raw ? [raw] : [])
      .map(Number)
      .filter((value) => Number.isFinite(value));
    return values.length === 0
      ? []
      : [{
          categoryId: Number(category.id),
          valueIds: values,
          match: String(category.selectionMode).toUpperCase() === 'MULTI'
            ? (analyticsTagMatchModes[String(category.id)] || 'ANY')
            : 'ANY'
        }];
  });
}

function tagAnalyticsRequestInput(): TagAnalyticsRequestInput {
  return {
    leadTypes: analyticsLeadType() ? [analyticsLeadType()] : [],
    sourceChannels: tagAnalyticsFilters.sourceChannel ? [tagAnalyticsFilters.sourceChannel] : [],
    assignedKeepers: tagAnalyticsFilters.assignedKeeper ? [tagAnalyticsFilters.assignedKeeper] : [],
    intendedStores: tagAnalyticsFilters.intendedStore ? [tagAnalyticsFilters.intendedStore] : [],
    updatedFrom: localDayStart(tagAnalyticsFilters.updatedFrom),
    updatedTo: localDayEnd(tagAnalyticsFilters.updatedTo),
    teamLeaderIds: tagAnalyticsFilters.teamLeaderId ? [Number(tagAnalyticsFilters.teamLeaderId)] : [],
    tagFrom: localDayStart(tagAnalyticsFilters.tagFrom),
    tagTo: localDayEnd(tagAnalyticsFilters.tagTo),
    tagGroups: analyticsTagGroups(),
    tagGroupLogic: tagAnalyticsFilters.tagGroupLogic
  };
}

const tagAnalyticsSummaryCards = computed(() => {
  const summary = tagAnalytics.value?.summary;
  return [
    { label: '匹配客户', value: summary?.matchedCustomerCount ?? 0 },
    { label: '已打标签客户', value: summary?.taggedCustomerCount ?? 0 },
    { label: '正式标签', value: summary?.activeAssignmentCount ?? 0 },
    { label: '覆盖率', value: `${(((summary?.coverageRate ?? 0) * 100)).toFixed(2)}%` }
  ];
});

const tagAnalyticsDetailSections = computed(() => {
  const data = tagAnalytics.value;
  return [
    { key: 'categories', title: '标签分类', columns: ['分类', '有效标签数', '客户数'], keys: ['categoryName', 'activeAssignmentCount', 'taggedCustomerCount'], rows: data?.categories ?? [] },
    { key: 'tags', title: '标签值', columns: ['分类', '标签', '代码', '有效标签数', '客户数'], keys: ['categoryName', 'displayName', 'valueCode', 'activeAssignmentCount', 'taggedCustomerCount'], rows: data?.tags ?? [] },
    { key: 'stores', title: '门店', columns: ['门店', '有效标签数', '客户数'], keys: ['label', 'activeAssignmentCount', 'taggedCustomerCount'], rows: data?.stores ?? [] },
    { key: 'teams', title: '团队', columns: ['团队', '有效标签数', '客户数'], keys: ['label', 'activeAssignmentCount', 'taggedCustomerCount'], rows: data?.teams ?? [] },
    { key: 'employees', title: '员工', columns: ['员工', '有效标签数', '客户数'], keys: ['label', 'activeAssignmentCount', 'taggedCustomerCount'], rows: data?.employees ?? [] },
    { key: 'sources', title: '标签来源', columns: ['来源', '新增标签数', '客户数'], keys: ['sourceLabel', 'addedAssignmentCount', 'affectedCustomerCount'], rows: data?.tagSources ?? [] },
    { key: 'reasons', title: '未更新原因', columns: ['原因', '范围', '客户数', '判断数'], keys: ['reasonLabel', 'scope', 'customerCount', 'decisionCount'], rows: data?.unupdatedReasons ?? [] },
    { key: 'trend', title: '标签趋势', columns: ['日期', '新增', '失效', '净变化'], keys: ['date', 'addedAssignmentCount', 'invalidatedAssignmentCount', 'netChange'], rows: data?.trend ?? [] }
  ];
});

function tagAnalyticsCell(row: AnyRecord, key: string): string {
  const value = row?.[key];
  if (key === 'sourceLabel') return String(value || tagSourceLabel(String(row?.sourceType ?? '')));
  if (key === 'scope') return reasonScopeLabel(String(value ?? ''));
  if (key === 'displayName') return `${String(value ?? '-')}${row?.valueCode ? `（${row.valueCode}）` : ''}`;
  if (value === null || value === undefined || value === '') return '-';
  return translateValue(value);
}
```

新增完整加载函数：

```ts
async function loadTagAnalytics() {
  tagAnalyticsStatus.loading = true;
  tagAnalyticsStatus.error = '';
  try {
    const response = await postJson<TagAnalyticsResponse>(
      '/admin/api/v1/analytics/tags',
      buildTagAnalyticsRequest(tagAnalyticsRequestInput()));
    tagAnalytics.value = dataFromResponse(response) as TagAnalyticsResponse;
  } catch (error) {
    tagAnalyticsStatus.error = errorText(error);
  } finally {
    tagAnalyticsStatus.loading = false;
  }
}
```

`tagAnalyticsRequestInput()` 必须从响应 `filterOptions`、独立筛选状态和 Step 9A 动态标签目录组装，不读取客户列表分页状态。进入 analytics 页面时并行执行旧 `loadAnalyticsDashboard({silent:true})` 和 `loadTagAnalytics()`；5 分钟定时刷新也同时调用二者，但标签失败不得 reject 旧看板刷新。

模板在现有分析详情后增加一个不嵌套卡片的 `section.tag-analytics-section`：

```vue
<section class="tag-analytics-section">
  <div class="ops-panel-head">
    <div>
      <h2>标签统计</h2>
      <p>只统计当前正式有效标签；标签建议和历史失效记录不会进入当前数量。</p>
    </div>
    <button class="secondary small" type="button" :disabled="tagAnalyticsStatus.loading" @click="loadTagAnalytics">刷新标签统计</button>
  </div>
  <div class="tag-analytics-filter-grid">
    <label><span class="ops-label-title">客户更新时间起</span><input v-model="tagAnalyticsFilters.updatedFrom" type="date" /></label>
    <label><span class="ops-label-title">客户更新时间止</span><input v-model="tagAnalyticsFilters.updatedTo" type="date" /></label>
    <label><span class="ops-label-title">标签事件起</span><input v-model="tagAnalyticsFilters.tagFrom" type="date" /></label>
    <label><span class="ops-label-title">标签事件止</span><input v-model="tagAnalyticsFilters.tagTo" type="date" /></label>
    <label><span class="ops-label-title">客户来源</span><select v-model="tagAnalyticsFilters.sourceChannel"><option value="">全部客户来源</option><option v-for="item in tagAnalytics?.filterOptions.customerSources || []" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
    <label><span class="ops-label-title">标签统计门店</span><select v-model="tagAnalyticsFilters.intendedStore"><option value="">全部门店</option><option v-for="item in tagAnalytics?.filterOptions.stores || []" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
    <label><span class="ops-label-title">标签统计团队</span><select v-model="tagAnalyticsFilters.teamLeaderId"><option value="">全部团队</option><option v-for="item in tagAnalytics?.filterOptions.teams || []" :key="item.leaderId" :value="String(item.leaderId)">{{ item.label }}</option></select></label>
    <label><span class="ops-label-title">标签统计员工</span><select v-model="tagAnalyticsFilters.assignedKeeper"><option value="">全部员工</option><option v-for="item in tagAnalytics?.filterOptions.employees || []" :key="item.account" :value="item.account">{{ item.label }}</option></select></label>
  </div>
  <div v-if="customerFilterCategories.length" class="customer-tag-filters">
    <label class="customer-tag-logic"><span>标签分类组合</span><select v-model="tagAnalyticsFilters.tagGroupLogic"><option value="AND">全部分类</option><option value="OR">任一分类</option></select></label>
    <div v-for="category in customerFilterCategories" :key="`analytics-${category.id}`" class="customer-tag-filter-group">
      <label><span>{{ category.categoryName || category.categoryKey }}</span><select v-model="analyticsTagSelections[String(category.id)]" :multiple="String(category.selectionMode).toUpperCase() === 'MULTI'"><option v-for="value in category.values || []" :key="value.id" :value="String(value.id)">{{ value.displayName || value.tagValue }}</option></select></label>
      <label v-if="String(category.selectionMode).toUpperCase() === 'MULTI'"><span>匹配方式</span><select v-model="analyticsTagMatchModes[String(category.id)]"><option value="ANY">任一标签</option><option value="ALL">全部标签</option></select></label>
    </div>
  </div>
  <div class="ops-analytics-grid tag-analytics-summary"><div v-for="card in tagAnalyticsSummaryCards" :key="card.label" class="ops-analytics-block"><h3>{{ card.label }}</h3><p>{{ card.value }}</p></div></div>
  <p v-if="tagAnalyticsStatus.error" class="ops-empty compact error">标签统计刷新失败：{{ tagAnalyticsStatus.error }} <button class="secondary tiny" type="button" @click="loadTagAnalytics">重试</button></p>
  <div v-for="section in tagAnalyticsDetailSections" :key="section.key" class="ops-detail-box"><strong>{{ section.title }}</strong><div v-if="section.rows.length" class="ops-mini-table"><div class="ops-mini-row head"><span v-for="column in section.columns" :key="column">{{ column }}</span></div><div v-for="(row, rowIndex) in section.rows" :key="`${section.key}-${rowIndex}`" class="ops-mini-row"><span v-for="key in section.keys" :key="key">{{ tagAnalyticsCell(row, key) }}</span></div></div><p v-else class="ops-empty compact">{{ tagAnalyticsStatus.loading ? '正在刷新' : '暂无数据' }}</p></div>
</section>
```

- 筛选栏：客户更新时间起止、标签事件起止、客户来源、门店、团队、员工。
- 动态标签分类：复用分类选择模式，MULTI 提供 ANY/ALL，分类间提供 AND/OR。
- 4 个概览值：匹配客户、已打标签客户、正式标签、覆盖率。
- 明细表：标签、门店、团队、员工、来源、未更新原因、趋势。
- 加载、空、错误和重试状态。

样式新增 `.tag-analytics-filter-grid`，桌面使用 `repeat(3, minmax(0, 1fr))`，现有移动端 media query 中降为 1 列；日期和最长中文选项必须不溢出。

- [ ] **Step 4: 把标签统计追加到现有 CSV 并运行测试**

修改 `downloadAnalyticsCsv()`：

```ts
const tagSections = tagAnalytics.value ? tagAnalyticsCsvSections(tagAnalytics.value) : [];
downloadTextFile(
  `analytics-${new Date().toISOString().slice(0, 10)}.csv`,
  [...sections, ...tagSections].join('\n\n')
);
```

Run: `cd desktop; npm test -- --run src/renderer/modules/admin/tagAnalytics.test.ts src/renderer/modules/admin/AdminConsole.test.ts`  
Expected: PASS。  
Run: `cd desktop; npm run typecheck`  
Expected: PASS。

- [ ] **Step 5: 提交看板接入**

```bash
git add desktop/src/renderer/modules/admin/AdminConsole.vue desktop/src/renderer/modules/admin/AdminConsole.test.ts desktop/src/renderer/styles.css
git commit -m "feat: add tag analytics dashboard"
```

---

### Task 11: 注册开发调试接口并执行全量验收

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminDevConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminDevConsole.test.ts`
- Modify: `dev-progress/tag_skill_llm_tasklist_056.md`
- Create: `dev-progress/tag_skill_llm_step9b_breakpoint_067.md`

- [ ] **Step 1: 写失败的开发调试台 POST 契约测试**

```ts
it('posts the structured tag analytics example', async () => {
  const { app, host } = await mountDevConsole();
  findButton(host, '数据分析').click();
  await flushUi();
  const panel = findActionPanel(host, '标签统计');
  findButton(panel, '执行').click();
  await flushRequests();

  expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/analytics/tags', {
    customerFilter: {
      sourceChannels: [], leadTypes: [], assignedKeepers: [], intendedStores: [],
      intendedProjects: [], customerStages: [], updatedFrom: null, updatedTo: null,
      tagGroups: [], tagGroupLogic: 'AND'
    },
    teamLeaderIds: [], tagFrom: null, tagTo: null, granularity: 'DAY'
  });
  app.unmount();
});
```

- [ ] **Step 2: 运行测试，确认 action 不存在**

Run: `cd desktop; npm test -- --run src/renderer/modules/admin/AdminDevConsole.test.ts`  
Expected: FAIL。

- [ ] **Step 3: 注册 POST action 并通过前端定向测试**

在 analytics section 的 actions 中增加：

```ts
{
  name: '标签统计',
  method: 'POST',
  pathTemplate: '/admin/api/v1/analytics/tags',
  body: {
    customerFilter: {
      sourceChannels: [], leadTypes: [], assignedKeepers: [], intendedStores: [],
      intendedProjects: [], customerStages: [], updatedFrom: null, updatedTo: null,
      tagGroups: [], tagGroupLogic: 'AND'
    },
    teamLeaderIds: [], tagFrom: null, tagTo: null, granularity: 'DAY'
  }
}
```

Run: `cd desktop; npm test -- --run src/renderer/modules/admin/AdminDevConsole.test.ts src/renderer/modules/admin/tagAnalytics.test.ts src/renderer/modules/admin/AdminConsole.test.ts`  
Expected: PASS。

- [ ] **Step 4: 执行全量验证，记录实际数字**

Run: `mvn -q test`  
Expected: 全部后端测试通过；记录 tests/failures/errors/skips。  
Run: `cd desktop; npm test -- --run`  
Expected: 全部 Vitest 通过；记录文件数和测试数。  
Run: `cd desktop; npm run typecheck`  
Expected: PASS。  
Run: `cd desktop; npm run build`  
Expected: PASS。  
Run: `cd desktop; npm run renderer:smoke`  
Expected: `renderer_smoke=passed`。  
Run: `cd desktop; npm run electron:smoke`  
Expected: `electron_smoke=passed`。

验证数据库边界时只执行只读查询：确认 6 条 `system_tag_suggestions.status='PENDING'` 的原文和状态未变化；确认两个 LLM 开关仍为 false。

- [ ] **Step 5: 更新任务清单、写断点并提交文档**

`tag_skill_llm_tasklist_056.md`：

- 勾选 Step 9B 统计项和旧漏斗硬编码项。
- 记录 API、统一 QuerySpec、有效标签边界、维度、来源、原因、趋势、前端 CSV 和完整验证数字。
- 明确 Step 9C/9D 仍未开始。

`tag_skill_llm_step9b_breakpoint_067.md` 必须记录：

- 分支和最终 HEAD。
- 本轮提交列表。
- 新增/修改文件。
- API 请求与响应边界。
- 测试命令和实际结果。
- 无迁移、6 条 PENDING 未修改、LLM 开关未开启、Step 8 未修改。
- 下一步是 Step 9C 跟进规则动态标签条件，开始前重新走 brainstorming/spec/plan。

```bash
git add dev-progress/tag_skill_llm_tasklist_056.md dev-progress/tag_skill_llm_step9b_breakpoint_067.md
git commit -m "docs: record step 9b breakpoint"
```

最后运行：`git status --short --branch`  
Expected: 工作区干净，分支只领先新的 Step 9B 提交。

---

## 最终验收清单

- [ ] POST `/admin/api/v1/analytics/tags` 使用结构化请求并保持 ADMIN-only。
- [ ] 客户条件、标签筛选和权限全部复用 Step 9A QuerySpec。
- [ ] 当前数量只含 active、启用、未合并、`use_for_statistics=1` 的正式 assignment。
- [ ] 6 条 PENDING 建议、未匹配旧值和第二字典不计正式数量。
- [ ] 门店、团队、员工、客户来源筛选和标签来源统计正确。
- [ ] 系统添加、人工添加/修改、未更新原因和连续趋势正确。
- [ ] 线索漏斗不再使用 `customers.intent_level IN ('HIGH','MEDIUM')`。
- [ ] 标签统计失败不拖垮旧 analytics，看板 CSV 与当前渲染一致。
- [ ] 未新增数据库迁移，未改 Step 8，未开启 LLM 开关。
- [ ] 后端、前端、typecheck、build、renderer smoke、electron smoke 全部通过。

## 计划自检记录

- 规格第 1-2 节的统一客户条件、双时间口径和正式标签边界：Task 2、Task 4、Task 6、Task 7。
- 规格第 3-4 节的 POST 请求、响应结构、团队交集和筛选选项：Task 1、Task 2、Task 3、Task 5。
- 规格第 5 节的当前覆盖缺口、事件窗口原因和多原因命中：Task 7。
- 规格第 6 节的 Repository 聚合、无 N+1、统计目录和旧漏斗标签语义：Task 4、Task 5、Task 6、Task 8。
- 规格第 7 节的看板筛选、局部失败、动态标签和 CSV：Task 9、Task 10。
- 规格第 8-10 节的错误、窗口上限、零值、全量测试和验收记录：Task 2、Task 7、Task 10、Task 11。
- 规格第 11 节的 Step 9C/9D 非目标、无迁移、LLM 开关和 Step 8 保护：实施约束、Task 11。
- 计划中没有占位标记、被省略的 SQL 片段或未定义的“稍后补充”步骤；所有测试 helper、schema 和关键序列化逻辑均已给出具体内容。
