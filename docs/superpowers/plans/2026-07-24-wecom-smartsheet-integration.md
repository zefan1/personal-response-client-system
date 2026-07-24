# WeCom Smart Sheet Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder table gateway with the official WeCom Smart Sheet API so the existing system can query, add, and update records in `私域客资管理表` without touching the concurrent workbench work.

**Architecture:** Keep the existing `SheetClient` and `WecomTableClient` contracts. Add focused components for environment-only configuration, access-token caching, authenticated JSON transport, visible-field metadata, cell conversion, and record operations; then reduce `HttpWecomTableClient` to an adapter over those components. Use JDK `HttpClient`, Jackson, Spring dependency injection, and the existing table retry services; do not add dependencies, migrations, desktop changes, or shared admin configuration changes.

**Tech Stack:** Java 17, Spring Boot 3.3, JDK `HttpClient`, Jackson, JUnit 5, AssertJ, Mockito, JDK `HttpServer`, Maven.

---

## File Structure

Create or modify only these implementation files:

- Create `src/main/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfig.java`: reads and validates environment-only connection settings.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetException.java`: carries a sanitized operation name and WeCom error code.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomAccessTokenProvider.java`: gets, caches, refreshes, and invalidates `access_token`.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClient.java`: sends authenticated official API requests and retries once after token expiry.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetField.java`: immutable visible-field metadata.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetFieldCatalog.java`: loads and caches fields from the configured view.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetValueCodec.java`: converts Java values to and from WeCom cell values.
- Create `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClient.java`: performs paginated query, duplicate-safe add, and record-ID update.
- Modify `src/main/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClient.java`: delegate the two existing interfaces to `WecomSmartSheetRecordClient`.
- Create `src/test/java/com/privateflow/modules/tablewrite/client/WecomTestHttpServer.java`: reusable local HTTP recorder for token and transport tests.

Create matching tests under `src/test/java/com/privateflow/modules/tablewrite/`. Do not modify `desktop/`, `modules/api/chat/`, `ChatController`, `ConfigAdminService`, `SystemConfigRepository`, `pom.xml`, or any database migration.

Use this Maven executable for every command in this worktree:

```powershell
$mvn = 'C:\Users\85314\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd'
```

### Task 1: Environment-Only Smart Sheet Configuration

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfig.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfigTest.java`

- [ ] **Step 1: Write failing configuration tests**

```java
package com.privateflow.modules.tablewrite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WecomSmartSheetConfigTest {

  @Test
  void validatesTheConfiguredTargetWithoutRevealingSecret() {
    WecomSmartSheetConfig config = configured();

    config.requireConfigured();
    config.requireTarget("s3_doc", "私域客资管理表");

    assertThat(config.documentId()).isEqualTo("s3_doc");
    assertThat(config.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
    assertThat(config.toString()).doesNotContain("app-secret-value");
  }

  @Test
  void reportsMissingVariableNamesButNeverTheirValues() {
    WecomSmartSheetConfig config = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "", "app-secret-value", "", "", "", "", "", ZoneId.of("Asia/Shanghai"));

    assertThatThrownBy(config::requireConfigured)
        .hasMessageContaining("WECOM_CORP_ID", "WECOM_SMARTSHEET_DOC_ID")
        .hasMessageNotContaining("app-secret-value");
  }

  @Test
  void rejectsASecondDocumentOrSourceTable() {
    WecomSmartSheetConfig config = configured();

    assertThatThrownBy(() -> config.requireTarget("other", "私域客资管理表"))
        .hasMessageContaining("document");
    assertThatThrownBy(() -> config.requireTarget("s3_doc", "其他表"))
        .hasMessageContaining("source table");
  }

  private WecomSmartSheetConfig configured() {
    return new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "corp-id", "app-secret-value", "s3_doc", "tSheet", "vView",
        "私域客资管理表", "联系方式", ZoneId.of("Asia/Shanghai"));
  }
}
```

- [ ] **Step 2: Run the configuration test and verify it fails**

Run:

```powershell
& $mvn '-Dtest=WecomSmartSheetConfigTest' test
```

Expected: FAIL because `WecomSmartSheetConfig` does not exist.

- [ ] **Step 3: Implement the configuration object**

Use a Spring component with an environment-injected constructor and a public explicit-values constructor used by focused client tests:

```java
package com.privateflow.modules.tablewrite.config;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class WecomSmartSheetConfig {
  private final String apiBaseUrl;
  private final String corpId;
  private final String appSecret;
  private final String documentId;
  private final String sheetId;
  private final String viewId;
  private final String sourceTable;
  private final String uniqueFieldTitle;
  private final ZoneId zoneId;

  @Autowired
  public WecomSmartSheetConfig(
      @Value("${WECOM_API_BASE_URL:https://qyapi.weixin.qq.com}") String apiBaseUrl,
      @Value("${WECOM_CORP_ID:}") String corpId,
      @Value("${WECOM_APP_SECRET:}") String appSecret,
      @Value("${WECOM_SMARTSHEET_DOC_ID:}") String documentId,
      @Value("${WECOM_SMARTSHEET_SHEET_ID:}") String sheetId,
      @Value("${WECOM_SMARTSHEET_VIEW_ID:}") String viewId,
      @Value("${WECOM_SMARTSHEET_SOURCE_TABLE:}") String sourceTable,
      @Value("${WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE:}") String uniqueFieldTitle) {
    this(apiBaseUrl, corpId, appSecret, documentId, sheetId, viewId, sourceTable, uniqueFieldTitle,
        ZoneId.of("Asia/Shanghai"));
  }

  public WecomSmartSheetConfig(String apiBaseUrl, String corpId, String appSecret, String documentId,
      String sheetId, String viewId, String sourceTable, String uniqueFieldTitle, ZoneId zoneId) {
    this.apiBaseUrl = cleanBase(apiBaseUrl);
    this.corpId = clean(corpId);
    this.appSecret = clean(appSecret);
    this.documentId = clean(documentId);
    this.sheetId = clean(sheetId);
    this.viewId = clean(viewId);
    this.sourceTable = clean(sourceTable);
    this.uniqueFieldTitle = clean(uniqueFieldTitle);
    this.zoneId = zoneId;
  }

  public void requireConfigured() {
    List<String> missing = new ArrayList<>();
    require(missing, "WECOM_CORP_ID", corpId);
    require(missing, "WECOM_APP_SECRET", appSecret);
    require(missing, "WECOM_SMARTSHEET_DOC_ID", documentId);
    require(missing, "WECOM_SMARTSHEET_SHEET_ID", sheetId);
    require(missing, "WECOM_SMARTSHEET_VIEW_ID", viewId);
    require(missing, "WECOM_SMARTSHEET_SOURCE_TABLE", sourceTable);
    require(missing, "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE", uniqueFieldTitle);
    if (!missing.isEmpty()) throw new IllegalStateException("missing WeCom configuration: " + String.join(", ", missing));
  }

  public void requireTarget(String requestedDocumentId, String requestedSourceTable) {
    requireConfigured();
    if (!documentId.equals(clean(requestedDocumentId))) throw new IllegalArgumentException("unsupported WeCom document");
    if (!sourceTable.equals(clean(requestedSourceTable))) throw new IllegalArgumentException("unsupported WeCom source table");
  }

  private static void require(List<String> missing, String name, String value) { if (value.isBlank()) missing.add(name); }
  private static String clean(String value) { return value == null ? "" : value.trim(); }
  private static String cleanBase(String value) { return clean(value).replaceAll("/+$", ""); }

  public String apiBaseUrl() { return apiBaseUrl; }
  public String corpId() { return corpId; }
  public String appSecret() { return appSecret; }
  public String documentId() { return documentId; }
  public String sheetId() { return sheetId; }
  public String viewId() { return viewId; }
  public String sourceTable() { return sourceTable; }
  public String uniqueFieldTitle() { return uniqueFieldTitle; }
  public ZoneId zoneId() { return zoneId; }
}
```

Do not generate a `toString()` containing field values.

- [ ] **Step 4: Run the configuration tests**

Run: `& $mvn '-Dtest=WecomSmartSheetConfigTest' test`

Expected: 3 tests PASS.

- [ ] **Step 5: Check and commit Task 1**

```powershell
git diff --check
git add src/main/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfig.java src/test/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfigTest.java
git commit -m 'feat: add WeCom smart sheet environment config'
```

### Task 2: Access Token Cache

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetException.java`
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomAccessTokenProvider.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/WecomTestHttpServer.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/WecomAccessTokenProviderTest.java`

- [ ] **Step 1: Write failing token tests with a local HTTP server**

Cover all of these behaviors in `WecomAccessTokenProviderTest`:

```java
@Test
void cachesTokenUntilItsRefreshDeadline() {
  server.respond("/cgi-bin/gettoken", 200, "{\"errcode\":0,\"access_token\":\"token-one\",\"expires_in\":7200}");
  WecomAccessTokenProvider provider = provider(server.baseUrl(), mutableClock);

  assertThat(provider.get()).isEqualTo("token-one");
  assertThat(provider.get()).isEqualTo("token-one");
  assertThat(server.requestCount()).isEqualTo(1);
}

@Test
void refreshesAfterDeadlineAndSupportsExplicitInvalidation() {
  server.respondInOrder("/cgi-bin/gettoken",
      "{\"errcode\":0,\"access_token\":\"token-one\",\"expires_in\":600}",
      "{\"errcode\":0,\"access_token\":\"token-two\",\"expires_in\":600}",
      "{\"errcode\":0,\"access_token\":\"token-three\",\"expires_in\":600}");
  WecomAccessTokenProvider provider = provider(server.baseUrl(), mutableClock);

  assertThat(provider.get()).isEqualTo("token-one");
  mutableClock.advance(Duration.ofMinutes(11));
  assertThat(provider.get()).isEqualTo("token-two");
  provider.invalidate("token-two");
  assertThat(provider.get()).isEqualTo("token-three");
}

@Test
void errorsNeverContainSecretOrTokenUrl() {
  server.respond("/cgi-bin/gettoken", 200, "{\"errcode\":40013,\"errmsg\":\"invalid corpid\"}");
  WecomAccessTokenProvider provider = provider(server.baseUrl(), mutableClock);

  assertThatThrownBy(provider::get)
      .hasMessageContaining("errcode=40013")
      .hasMessageNotContaining("app-secret-value", "corpid=", "corpsecret=");
}
```

Create `WecomTestHttpServer` with this exact test-facing API:

```java
final class WecomTestHttpServer implements AutoCloseable {
  record Reply(int status, String body) {}

  static WecomTestHttpServer start() throws IOException
  void respond(String path, int status, String body)
  void respondInOrder(String path, Reply... replies)
  String baseUrl()
  int requestCount()
  String lastQuery()
  JsonNode lastJson()
  @Override public void close()
}
```

Internally use `HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)`, a `ConcurrentHashMap<String, Deque<Reply>>`, and synchronized captured request records. A context handler reads the UTF-8 request body, records path/query/body, removes the next reply, writes its status/body, and closes the exchange. Each test closes the helper in `@AfterEach`.

Define a private `MutableClock extends Clock` inside `WecomAccessTokenProviderTest`; store an `Instant`, return UTC from `getZone()`, implement `withZone`, return the stored value from `instant()`, and advance it with `void advance(Duration duration)`.

- [ ] **Step 2: Run the token test and verify it fails**

Run: `& $mvn '-Dtest=WecomAccessTokenProviderTest' test`

Expected: FAIL because token classes do not exist.

- [ ] **Step 3: Implement the sanitized exception and token cache**

`WecomSmartSheetException` must not accept or store a request URL/body:

```java
package com.privateflow.modules.tablewrite.client;

public final class WecomSmartSheetException extends IllegalStateException {
  private final String operation;
  private final int errcode;

  public WecomSmartSheetException(String operation, int errcode, String errmsg) {
    super("WeCom " + operation + " failed, errcode=" + errcode + ", errmsg=" + safe(errmsg));
    this.operation = operation;
    this.errcode = errcode;
  }

  public WecomSmartSheetException(String operation, String message, Throwable cause) {
    super("WeCom " + operation + " failed: " + message, cause);
    this.operation = operation;
    this.errcode = -1;
  }

  public String operation() { return operation; }
  public int errcode() { return errcode; }
  private static String safe(String value) { return value == null ? "unknown" : value.replaceAll("[\\r\\n]", " "); }
}
```

`WecomAccessTokenProvider` must use `GET /cgi-bin/gettoken`, URL-encode CorpID and Secret, cache a private `Token(value, refreshAt)`, refresh five minutes early, and expose:

```java
public String get()
public synchronized void invalidate(String rejectedToken)
```

Use a production constructor `(ObjectMapper, WecomSmartSheetConfig)` and a package-private constructor accepting `HttpClient` and `Clock`. Validate `errcode == 0`, nonblank `access_token`, and positive `expires_in`. Catch `IOException` and `InterruptedException` without including the URI.

- [ ] **Step 4: Run token and configuration tests**

Run: `& $mvn '-Dtest=WecomAccessTokenProviderTest,WecomSmartSheetConfigTest' test`

Expected: all tests PASS and request count proves caching.

- [ ] **Step 5: Check and commit Task 2**

```powershell
git diff --check
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetException.java src/main/java/com/privateflow/modules/tablewrite/client/WecomAccessTokenProvider.java src/test/java/com/privateflow/modules/tablewrite/client/WecomTestHttpServer.java src/test/java/com/privateflow/modules/tablewrite/client/WecomAccessTokenProviderTest.java
git commit -m 'feat: cache WeCom access tokens safely'
```

### Task 3: Authenticated Official API Transport

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClient.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClientTest.java`

- [ ] **Step 1: Write failing transport tests**

Use JDK `HttpServer` to capture JSON and return controlled responses. Cover:

```java
@Test
void postsJsonAndReturnsSuccessfulRoot() {
  tokenProviderReturns("token-one");
  server.respond("/cgi-bin/wedoc/smartsheet/get_fields", 200,
      "{\"errcode\":0,\"errmsg\":\"ok\",\"total\":0,\"fields\":[]}");

  JsonNode root = client().post("get_fields", Map.of("docid", "s3_doc"), Duration.ofSeconds(2));

  assertThat(root.path("errcode").asInt()).isZero();
  assertThat(server.lastQuery()).isEqualTo("access_token=token-one");
  assertThat(server.lastJson().path("docid").asText()).isEqualTo("s3_doc");
}

@Test
void invalidatesExpiredTokenAndRetriesExactlyOnce() {
  when(tokenProvider.get()).thenReturn("old-token", "new-token");
  server.respondInOrder("/cgi-bin/wedoc/smartsheet/get_records",
      "{\"errcode\":42001,\"errmsg\":\"access_token expired\"}",
      "{\"errcode\":0,\"errmsg\":\"ok\",\"records\":[]}");

  client().post("get_records", Map.of(), Duration.ofSeconds(2));

  verify(tokenProvider).invalidate("old-token");
  assertThat(server.requestCount()).isEqualTo(2);
}

@Test
void rejectsHttpFailureWithoutLeakingToken() {
  when(tokenProvider.get()).thenReturn("secret-token");
  server.respond("/cgi-bin/wedoc/smartsheet/get_records", 500, "{}");

  assertThatThrownBy(() -> client().post("get_records", Map.of(), Duration.ofSeconds(2)))
      .hasMessageContaining("get_records", "HTTP 500")
      .hasMessageNotContaining("secret-token");
}

@Test
void rejectsInvalidJsonWithoutLeakingResponseOrToken() {
  when(tokenProvider.get()).thenReturn("secret-token");
  server.respond("/cgi-bin/wedoc/smartsheet/get_records", 200, "not-json-secret-body");

  assertThatThrownBy(() -> client().post("get_records", Map.of(), Duration.ofSeconds(2)))
      .hasMessageContaining("get_records", "invalid JSON")
      .hasMessageNotContaining("secret-token", "not-json-secret-body");
}

@Test
void retriesTokenErrorOnlyOnce() {
  when(tokenProvider.get()).thenReturn("old-token", "new-token");
  server.respondInOrder("/cgi-bin/wedoc/smartsheet/get_records",
      new Reply(200, "{\"errcode\":40014,\"errmsg\":\"invalid token\"}"),
      new Reply(200, "{\"errcode\":40014,\"errmsg\":\"invalid token\"}"));

  assertThatThrownBy(() -> client().post("get_records", Map.of(), Duration.ofSeconds(2)))
      .isInstanceOf(WecomSmartSheetException.class)
      .hasMessageContaining("errcode=40014");
  assertThat(server.requestCount()).isEqualTo(2);
}
```

- [ ] **Step 2: Run the transport test and verify it fails**

Run: `& $mvn '-Dtest=WecomSmartSheetApiClientTest' test`

Expected: FAIL because `WecomSmartSheetApiClient` does not exist.

- [ ] **Step 3: Implement POST transport and one token retry**

The public operation API is:

```java
public JsonNode post(String operation, Object body, Duration timeout)
```

Map operation names to fixed paths so no caller can inject a URL:

```java
private static final Map<String, String> PATHS = Map.of(
    "get_fields", "/cgi-bin/wedoc/smartsheet/get_fields",
    "get_records", "/cgi-bin/wedoc/smartsheet/get_records",
    "add_records", "/cgi-bin/wedoc/smartsheet/add_records",
    "update_records", "/cgi-bin/wedoc/smartsheet/update_records");
private static final Set<Integer> TOKEN_ERRORS = Set.of(40014, 42001);
```

Serialize with Jackson, send `Content-Type: application/json`, parse UTF-8, check HTTP 2xx, then check `errcode`. On the first token error call `tokenProvider.invalidate(token)` and repeat once; never retry any other `errcode` inside this transport.

- [ ] **Step 4: Run transport and token tests**

Run: `& $mvn '-Dtest=WecomSmartSheetApiClientTest,WecomAccessTokenProviderTest' test`

Expected: all tests PASS.

- [ ] **Step 5: Check and commit Task 3**

```powershell
git diff --check
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClient.java src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClientTest.java
git commit -m 'feat: call official WeCom smart sheet API'
```

### Task 4: Visible Field Catalog and Safe Cell Conversion

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetField.java`
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetFieldCatalog.java`
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetValueCodec.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetFieldCatalogTest.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetValueCodecTest.java`

- [ ] **Step 1: Write failing field-catalog tests**

Mock `WecomSmartSheetApiClient.post()` and capture its body. Return visible text, phone, date, select, and formula definitions:

```java
when(api.post(eq("get_fields"), any(), any())).thenReturn(json("""
  {"errcode":0,"total":5,"fields":[
    {"field_id":"fName","field_title":"备注称呼","field_type":"FIELD_TYPE_TEXT"},
    {"field_id":"fPhone","field_title":"联系方式","field_type":"FIELD_TYPE_PHONE_NUMBER"},
    {"field_id":"fNext","field_title":"下次跟进时间","field_type":"FIELD_TYPE_DATE_TIME",
      "property_date_time":{"format":"yyyy-mm-dd hh:mm"}},
    {"field_id":"fStage","field_title":"客户阶段","field_type":"FIELD_TYPE_SINGLE_SELECT",
      "property_single_select":{"options":[{"id":"opt1","text":"跟进中","style":1}]}},
    {"field_id":"fFormula","field_title":"是否加微（公式）","field_type":"FIELD_TYPE_FORMULA"}
  ]}
  """));

Map<String, WecomSmartSheetField> fields = catalog.visibleFields(Duration.ofSeconds(2));

assertThat(capturedBody.path("view_id").asText()).isEqualTo("vView");
assertThat(fields.get("客户阶段").optionId("跟进中")).contains("opt1");
assertThat(fields.get("下次跟进时间").dateTimeIncludesTime()).isTrue();
assertThat(fields.get("是否加微（公式）").writable()).isFalse();
assertThatThrownBy(() -> catalog.requireWritable("是否加微（公式）", Duration.ofSeconds(2)))
    .hasMessageContaining("是否加微（公式）");
```

Also test duplicate titles, missing fields, pagination by `total`, and five-minute caching.

- [ ] **Step 2: Write failing value-codec tests**

Cover exact conversions:

```java
assertThat(codec.decode(textField, json("[{\"type\":\"text\",\"text\":\"张三\"}]"))).isEqualTo("张三");
assertThat(codec.decode(selectField, json("[{\"id\":\"opt1\",\"text\":\"跟进中\"}]"))).isEqualTo("跟进中");
assertThat(codec.decode(dateField, json("\"1784822400000\""))).isEqualTo("2026-07-24T00:00:00");
assertThat(codec.encode(phoneField, "13800000001").asText()).isEqualTo("13800000001");
assertThat(codec.encode(textField, "").path(0).path("text").asText()).isEmpty();
assertThat(codec.encode(selectField, "跟进中").path(0).path("id").asText()).isEqualTo("opt1");
assertThatThrownBy(() -> codec.encode(selectField, "不存在的选项")).hasMessageContaining("不存在的选项");
```

Include number, checkbox, date-only, date-time, multi-select, email, null omission at caller level, and unsupported field type rejection.

- [ ] **Step 3: Run both tests and verify they fail**

Run: `& $mvn '-Dtest=WecomSmartSheetFieldCatalogTest,WecomSmartSheetValueCodecTest' test`

Expected: FAIL because field and codec classes do not exist.

- [ ] **Step 4: Implement immutable field metadata**

`WecomSmartSheetField` contains `fieldId`, `title`, `type`, `optionIdsByText`, and `dateTimeIncludesTime`. Its `writable()` returns true only for:

```java
Set.of("FIELD_TYPE_TEXT", "FIELD_TYPE_PHONE_NUMBER", "FIELD_TYPE_NUMBER", "FIELD_TYPE_CHECKBOX",
    "FIELD_TYPE_DATE_TIME", "FIELD_TYPE_SINGLE_SELECT", "FIELD_TYPE_SELECT", "FIELD_TYPE_EMAIL")
```

Return option maps as unmodifiable copies and expose `Optional<String> optionId(String text)`.

- [ ] **Step 5: Implement visible-field loading and caching**

`WecomSmartSheetFieldCatalog.visibleFields(timeout)` posts:

```java
Map.of(
    "docid", config.documentId(),
    "sheet_id", config.sheetId(),
    "view_id", config.viewId(),
    "offset", offset,
    "limit", 1000)
```

Continue until the number loaded reaches `total`. Reject blank IDs/titles and duplicate titles. Parse select options and date format. Cache the entire immutable map for five minutes using an injected `Clock`. `requireWritable(title, timeout)` distinguishes unknown from visible-but-read-only fields.

- [ ] **Step 6: Implement value decode and encode**

`WecomSmartSheetValueCodec` must use the configured Asia/Shanghai zone and these methods:

```java
public String decode(WecomSmartSheetField field, JsonNode value)
public JsonNode encode(WecomSmartSheetField field, Object value)
```

Text uses `[{"type":"text","text":"..."}]`; single/multi-select writes existing option IDs only; date reads/writes millisecond Unix timestamps and returns ISO `LocalDate` or `LocalDateTime`; phone/email use strings; number and checkbox retain JSON primitive types. Explicit empty strings clear text, phone, date, or select values. Throw a sanitized exception containing only the field title for unsupported values.

- [ ] **Step 7: Run field and codec tests**

Run: `& $mvn '-Dtest=WecomSmartSheetFieldCatalogTest,WecomSmartSheetValueCodecTest' test`

Expected: all tests PASS.

- [ ] **Step 8: Check and commit Task 4**

```powershell
git diff --check
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetField.java src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetFieldCatalog.java src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetValueCodec.java src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetFieldCatalogTest.java src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetValueCodecTest.java
git commit -m 'feat: map visible WeCom smart sheet fields'
```

### Task 5: Paginated Record Query

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClient.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClientTest.java`

- [ ] **Step 1: Write failing paginated-query tests**

Mock two `get_records` pages. The first returns `has_more=true,next=2`; the second returns `has_more=false`. Include a record before and after `modifiedAfter`, plus text, phone, select, formula, and update timestamps.

```java
List<SheetRow> rows = client.fetchIncrementalRows(
    new SheetSource(1L, "s3_doc", "私域客资管理表"),
    LocalDateTime.of(2026, 7, 24, 9, 0),
    10,
    Duration.ofSeconds(2));

assertThat(rows).extracting(SheetRow::rowId).containsExactly("r-new-1", "r-new-2");
assertThat(rows.get(0).values()).containsEntry("联系方式", "13800000001");
assertThat(capturedOffsets).containsExactly(0, 2);
```

Also test: non-advancing `next` fails, `limit` is applied after local update-time filtering, rows are stable-sorted by `update_time` then `record_id`, a wrong document/source table is rejected before HTTP, and values outside the configured view are ignored.

- [ ] **Step 2: Run the record query test and verify it fails**

Run: `& $mvn '-Dtest=WecomSmartSheetRecordClientTest' test`

Expected: FAIL because `WecomSmartSheetRecordClient` does not exist.

- [ ] **Step 3: Implement complete pagination and local incremental filtering**

Create:

```java
public List<SheetRow> fetchIncrementalRows(
    SheetSource source, LocalDateTime modifiedAfter, int limit, Duration timeout)
```

For each page post `docid`, `sheet_id`, `view_id`, `key_type=CELL_VALUE_KEY_TYPE_FIELD_TITLE`, `offset`, and `limit=1000`. Decode only field titles present in `fieldCatalog.visibleFields(timeout)`. Convert `update_time` milliseconds to `LocalDateTime` with `config.zoneId()`. Read all pages, filter strictly after `modifiedAfter`, stable-sort, then apply the requested positive limit. If `has_more=true`, require `next > currentOffset` and stop after 100 pages, matching the official 100,000-row table limit.

- [ ] **Step 4: Run query, catalog, and codec tests**

Run: `& $mvn '-Dtest=WecomSmartSheetRecordClientTest,WecomSmartSheetFieldCatalogTest,WecomSmartSheetValueCodecTest' test`

Expected: all tests PASS.

- [ ] **Step 5: Check and commit Task 5**

```powershell
git diff --check
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClient.java src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClientTest.java
git commit -m 'feat: query WeCom records with pagination'
```

### Task 6: Duplicate-Safe Add and Record-ID Update

**Files:**
- Modify: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClient.java`
- Modify: `src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClientTest.java`

- [ ] **Step 1: Write failing add tests**

```java
@Test
void addsOneRecordWithFieldIdsAndReturnsRecordId() {
  apiReturnsNoExactPhoneMatch();
  apiReturnsAddedRecord("r-created");

  String rowId = client.createRow("私域客资管理表", Map.of(
      "联系方式", "13800000001",
      "备注称呼", "企微联调-20260724"), Duration.ofSeconds(2));

  assertThat(rowId).isEqualTo("r-created");
  assertThat(addBody.path("key_type").asText()).isEqualTo("CELL_VALUE_KEY_TYPE_FIELD_ID");
  assertThat(addBody.path("records").path(0).path("values").has("fPhone")).isTrue();
}

@Test
void returnsExistingIdInsteadOfCreatingDuplicatePhone() {
  apiReturnsExactPhoneMatches("r-existing");

  assertThat(client.createRow("私域客资管理表", fields(), timeout)).isEqualTo("r-existing");
  verify(api, never()).post(eq("add_records"), any(), any());
}

@Test
void rejectsMissingPhoneMultipleMatchesFormulaAndUnknownOptionBeforeWrite() {
  assertThatThrownBy(() -> client.createRow("私域客资管理表", Map.of("备注称呼", "无电话"), timeout))
      .hasMessageContaining("联系方式");

  apiReturnsExactPhoneMatches("r-one", "r-two");
  assertThatThrownBy(() -> client.createRow("私域客资管理表", fields(), timeout))
      .hasMessageContaining("multiple");

  assertThatThrownBy(() -> client.createRow("私域客资管理表", Map.of(
      "联系方式", "13800000002", "是否加微（公式）", "是"), timeout))
      .hasMessageContaining("是否加微（公式）");

  assertThatThrownBy(() -> client.createRow("私域客资管理表", Map.of(
      "联系方式", "13800000003", "客户阶段", "不存在的选项"), timeout))
      .hasMessageContaining("不存在的选项");

  verify(api, never()).post(eq("add_records"), any(), any());
}
```

Add a concurrency test with two threads and a blocking mock response; both calls must return the same row ID and `add_records` must be called once.

- [ ] **Step 2: Write failing update tests**

```java
client.updateRow("私域客资管理表", "r-existing", Map.of(
    "跟进记录", "第二次跟进",
    "下次跟进时间", ""), Duration.ofSeconds(2));

assertThat(updateBody.path("records").path(0).path("record_id").asText()).isEqualTo("r-existing");
assertThat(updateBody.path("records").path(0).path("values").has("fNextFollowup")).isTrue();
```

Also reject blank record ID, formula/system/hidden/unknown fields, and an empty effective field map without making an HTTP request.

- [ ] **Step 3: Run focused write tests and verify they fail**

Run: `& $mvn '-Dtest=WecomSmartSheetRecordClientTest' test`

Expected: query tests PASS; new write tests FAIL because methods are missing.

- [ ] **Step 4: Implement exact-value lookup and add locking**

Add:

```java
public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout)
```

Require the configured unique title `联系方式` and a nonblank value. Use a `ConcurrentHashMap<String, Object>` keyed by the normalized phone. Inside `synchronized(lock)`, call a private paginated lookup requesting only the unique field and compare the decoded value with exact string equality. Behavior: zero matches calls `add_records`; one returns its `record_id`; more than one throws an ambiguity error. Remove the lock with `locks.remove(key, lock)` in `finally`.

Encode all non-null fields through `fieldCatalog.requireWritable()` and `valueCodec.encode()`, key them by field ID, and post one record. Require exactly one nonblank returned `record_id`.

- [ ] **Step 5: Implement update by record ID**

Add:

```java
public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout)
```

Validate the source and record ID, encode non-null values by field ID, preserve explicit empty strings, and post:

```java
Map.of(
    "docid", config.documentId(),
    "sheet_id", config.sheetId(),
    "key_type", "CELL_VALUE_KEY_TYPE_FIELD_ID",
    "records", List.of(Map.of("record_id", sourceRowId, "values", encodedFields)))
```

Require the response `records` array to contain the same record ID.

- [ ] **Step 6: Run all record-client tests**

Run: `& $mvn '-Dtest=WecomSmartSheetRecordClientTest' test`

Expected: all query, add, duplicate, concurrency, update, and write-protection tests PASS.

- [ ] **Step 7: Check and commit Task 6**

```powershell
git diff --check
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClient.java src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetRecordClientTest.java
git commit -m 'feat: add and update WeCom smart sheet records'
```

### Task 7: Existing Contract Adapter and Regression Suite

**Files:**
- Modify: `src/main/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClient.java`
- Create: `src/test/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClientTest.java`

- [ ] **Step 1: Write a failing delegation test**

```java
@Test
void delegatesExistingReadCreateAndUpdateContracts() {
  when(configProvider.get()).thenReturn(new TableConfig("", "", 10000, 5, 60, 1, "ADMIN", 100, 1000));
  when(recordClient.fetchIncrementalRows(any(), any(), eq(20), any())).thenReturn(List.of(row));
  when(recordClient.createRow(eq("私域客资管理表"), anyMap(), any())).thenReturn("r-created");

  HttpWecomTableClient client = new HttpWecomTableClient(recordClient, configProvider);

  assertThat(client.fetchIncrementalRows(source, modifiedAfter, 20)).containsExactly(row);
  assertThat(client.createRow("私域客资管理表", fields, Duration.ofSeconds(2))).isEqualTo("r-created");
  client.updateRow("私域客资管理表", "r-created", fields, Duration.ofSeconds(2));

  verify(recordClient).updateRow("私域客资管理表", "r-created", fields, Duration.ofSeconds(2));
}
```

Assert the query timeout uses `TableConfig.writeTimeoutMs()`. The test must not configure `table.api_base_url` or `table.api_key`, proving the placeholder gateway settings are no longer required.

- [ ] **Step 2: Run the adapter test and verify it fails**

Run: `& $mvn '-Dtest=HttpWecomTableClientTest' test`

Expected: FAIL because the production constructor still expects `ObjectMapper` and the old gateway implementation.

- [ ] **Step 3: Replace the placeholder gateway body with delegation**

Keep the existing Spring annotations and interfaces. The whole class becomes:

```java
@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "false", matchIfMissing = true)
public class HttpWecomTableClient implements WecomTableClient, SheetClient {
  private final WecomSmartSheetRecordClient recordClient;
  private final TableConfigProvider configProvider;

  public HttpWecomTableClient(WecomSmartSheetRecordClient recordClient, TableConfigProvider configProvider) {
    this.recordClient = recordClient;
    this.configProvider = configProvider;
  }

  @Override
  public List<SheetRow> fetchIncrementalRows(SheetSource source, LocalDateTime modifiedAfter, int limit) {
    return recordClient.fetchIncrementalRows(source, modifiedAfter, limit,
        Duration.ofMillis(configProvider.get().writeTimeoutMs()));
  }

  @Override
  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    return recordClient.createRow(sourceTable, fields, timeout);
  }

  @Override
  public void updateRow(String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    recordClient.updateRow(sourceTable, sourceRowId, fields, timeout);
  }
}
```

- [ ] **Step 4: Run new and existing table tests**

Run:

```powershell
& $mvn '-Dtest=WecomSmartSheetConfigTest,WecomAccessTokenProviderTest,WecomSmartSheetApiClientTest,WecomSmartSheetFieldCatalogTest,WecomSmartSheetValueCodecTest,WecomSmartSheetRecordClientTest,HttpWecomTableClientTest,TableConfigProviderTest,NewCustomerRowCreatorTest,ExistingCustomerUpdaterTest,ManualSaveHandlerTest,CustomerSyncSchedulerTest,DatasourceAdminServiceTest,QueueRetryManagerTest' test
```

Expected: all tests PASS, including the original 22-test baseline and queue retry coverage.

- [ ] **Step 5: Compile the complete backend**

Run: `& $mvn '-DskipTests' package`

Expected: `BUILD SUCCESS` with no production compilation error.

- [ ] **Step 6: Check isolation before commit**

```powershell
git diff --check
git diff --name-only 045e5af..HEAD
git status --short
git -C '..\reply-queue-archive' status --short
```

Expected: this branch contains only the approved table client/config/tests/docs; the other worktree retains its own chat files and no tablewrite files.

- [ ] **Step 7: Commit the adapter**

```powershell
git add src/main/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClient.java src/test/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClientTest.java
git commit -m 'feat: connect table contracts to official WeCom API'
```

### Task 8: Review, Secure Server Setup, and Real Acceptance

**Files:**
- No production code changes.
- Update checklist status in this plan only after each item has current evidence.

- [ ] **Step 1: Run Superpowers code review**

Use `superpowers:requesting-code-review`. Review against the design, this plan, the exact forbidden-file list, token secrecy, duplicate prevention, and official request/response shapes. Fix findings with failing regression tests and focused commits.

- [ ] **Step 2: Run final local verification from a clean status**

```powershell
git diff --check
& $mvn '-Dtest=WecomSmartSheetConfigTest,WecomAccessTokenProviderTest,WecomSmartSheetApiClientTest,WecomSmartSheetFieldCatalogTest,WecomSmartSheetValueCodecTest,WecomSmartSheetRecordClientTest,HttpWecomTableClientTest,TableConfigProviderTest,NewCustomerRowCreatorTest,ExistingCustomerUpdaterTest,ManualSaveHandlerTest,CustomerSyncSchedulerTest,DatasourceAdminServiceTest,QueueRetryManagerTest' test
& $mvn '-DskipTests' package
git status --short --branch
```

Expected: tests and package PASS; branch is clean.

- [ ] **Step 3: Confirm the WeCom application is callable by the sheet**

In the WeCom Smart Sheet permission page, verify application AgentId `1000034` is in the sheet's “可调用应用” list. Trusted domain/IP are prerequisites but do not replace this permission.

- [ ] **Step 4: Wait for the shared-server merge gate**

Do not replace the server application while `feat/reply-queue-archive` is still being developed or tested. Merge both completed branches into one integration commit, rerun both branches' focused tests, and build one unified JAR before deployment.

- [ ] **Step 5: Enter credentials securely on the server**

Use an interactive server editor, never chat or a command containing the Secret:

```bash
sudoedit /opt/private-domain-assistant/config/production.env
```

Add `WECOM_CORP_ID`, `WECOM_APP_SECRET`, `WECOM_SMARTSHEET_DOC_ID=s3_AY0AdQapAP8CN3A4oIQ1jT0SEgWNX`, `WECOM_SMARTSHEET_SHEET_ID=tBXC1l`, `WECOM_SMARTSHEET_VIEW_ID=v86jaL`, `WECOM_SMARTSHEET_SOURCE_TABLE=私域客资管理表`, and `WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE=联系方式`. Confirm file permissions remain limited to the service account/root.

- [ ] **Step 6: Perform real query acceptance after unified deployment**

From the existing admin data-source page, configure or confirm the target document/subtable mapping, run manual sync, and verify a dedicated non-personal test record is read into the system with its WeCom `record_id`. Do not expose any existing customer row in logs or reports.

- [ ] **Step 7: Perform real add acceptance**

Through the normal employee flow, create one dedicated test customer using a unique test phone/value and marker `企微联调-<timestamp>`. Confirm exactly one row appears in `私域客资管理表`; repeat the same operation and confirm no second row appears.

- [ ] **Step 8: Perform real update and formula-protection acceptance**

Use the existing customer `save-to-table` flow to change one normal text field on the test row and read it back. Then attempt to write `是否加微（公式）`; confirm the system rejects it before a WeCom request and the formula cell remains unchanged.

- [ ] **Step 9: Record completion honestly**

Report separately: commits, automated test counts, unified deployment version, real query result, real add result, real update result, formula protection, and whether the test row was manually removed. Until Steps 4-8 are complete, report “代码完成，等待统一部署和真实连通验证”, not “已连通”.

## Plan Self-Review

- Spec coverage: environment secrecy, token caching, official endpoints, visible fields, formula/hidden protection, pagination, date/value conversion, query/add/update, duplicate protection, regression tests, isolation, shared-server deployment gate, and real acceptance each map to a task above.
- Scope: no desktop, chat, admin-service, shared-config, dependency, or migration file is modified.
- Type consistency: `WecomSmartSheetConfig`, `WecomAccessTokenProvider`, `WecomSmartSheetApiClient`, `WecomSmartSheetFieldCatalog`, `WecomSmartSheetValueCodec`, and `WecomSmartSheetRecordClient` are introduced before later tasks use them; existing `SheetClient` and `WecomTableClient` signatures remain unchanged.
- No implementation step depends on a custom external gateway or a new third-party dependency.
