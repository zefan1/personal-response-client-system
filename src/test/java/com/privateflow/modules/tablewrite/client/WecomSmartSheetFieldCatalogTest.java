package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WecomSmartSheetFieldCatalogTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void loadsOfficialVisibleFieldShapeAndRejectsFormulaWrites() throws Exception {
    ScriptedClient api = client("""
        {"errcode":0,"total":5,"fields":[
          {"field_id":"fName","field_title":"\u5907\u6ce8\u79f0\u547c","field_type":"FIELD_TYPE_TEXT"},
          {"field_id":"fPhone","field_title":"\u8054\u7cfb\u65b9\u5f0f","field_type":"FIELD_TYPE_PHONE_NUMBER"},
          {"field_id":"fNext","field_title":"\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4","field_type":"FIELD_TYPE_DATE_TIME","property_date_time":{"format":"yyyy-mm-dd hh:mm"}},
          {"field_id":"fStage","field_title":"\u5ba2\u6237\u9636\u6bb5","field_type":"FIELD_TYPE_SINGLE_SELECT","property_single_select":{"options":[{"id":"opt1","text":"\u8ddf\u8fdb\u4e2d","style":1}]}},
          {"field_id":"fFormula","field_title":"\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09","field_type":"FIELD_TYPE_FORMULA"}
        ]}""");
    WecomSmartSheetFieldCatalog catalog = catalog(api, Clock.systemUTC());

    Map<String, WecomSmartSheetField> fields = catalog.visibleFields(Duration.ofSeconds(1));

    assertThat(api.bodies).singleElement().satisfies(body -> {
      assertThat(body.path("docid").asText()).isEqualTo("doc-1");
      assertThat(body.path("sheet_id").asText()).isEqualTo("sheet-1");
      assertThat(body.path("view_id").asText()).isEqualTo("vView");
      assertThat(body.path("offset").asInt()).isZero();
      assertThat(body.path("limit").asInt()).isEqualTo(1000);
    });
    assertThat(fields.get("\u5907\u6ce8\u79f0\u547c").fieldId()).isEqualTo("fName");
    assertThat(fields.get("\u5907\u6ce8\u79f0\u547c").type()).isEqualTo("FIELD_TYPE_TEXT");
    assertThat(fields.get("\u8054\u7cfb\u65b9\u5f0f").fieldId()).isEqualTo("fPhone");
    assertThat(fields.get("\u8054\u7cfb\u65b9\u5f0f").type()).isEqualTo("FIELD_TYPE_PHONE_NUMBER");
    assertThat(fields.get("\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4").fieldId()).isEqualTo("fNext");
    assertThat(fields.get("\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4").type()).isEqualTo("FIELD_TYPE_DATE_TIME");
    assertThat(fields.get("\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4").dateTimeIncludesTime()).isTrue();
    assertThat(fields.get("\u5ba2\u6237\u9636\u6bb5").fieldId()).isEqualTo("fStage");
    assertThat(fields.get("\u5ba2\u6237\u9636\u6bb5").type()).isEqualTo("FIELD_TYPE_SINGLE_SELECT");
    assertThat(fields.get("\u5ba2\u6237\u9636\u6bb5").optionId(" \u8ddf\u8fdb\u4e2d ")).contains("opt1");
    assertThat(fields.get("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09").fieldId()).isEqualTo("fFormula");
    assertThat(fields.get("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09").type()).isEqualTo("FIELD_TYPE_FORMULA");
    assertThat(fields.get("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09").writable()).isFalse();
    assertThatThrownBy(() -> catalog.requireWritable("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09", Duration.ofSeconds(1)))
        .hasMessageContaining("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09");
  }

  @Test
  void paginatesWithLoadedCountAndReturnsImmutableMergedFields() throws Exception {
    ScriptedClient api = client(
        "{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"One\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"f2\",\"field_title\":\"Two\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");

    Map<String, WecomSmartSheetField> fields = catalog(api, Clock.systemUTC()).visibleFields(Duration.ofSeconds(1));

    assertThat(fields).containsKeys("One", "Two");
    assertThat(api.bodies).extracting(body -> body.path("offset").asInt()).containsExactly(0, 1);
    assertThatThrownBy(() -> fields.put("Three", fields.get("One"))).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void cachesForFiveMinutesThenRefreshes() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    ScriptedClient api = client(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"First\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f2\",\"field_title\":\"Second\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");
    WecomSmartSheetFieldCatalog catalog = catalog(api, clock);

    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    clock.advance(Duration.ofMinutes(5));
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("Second");
    assertThat(api.bodies).hasSize(2);
  }

  @Test
  void startsTheCacheLifetimeAfterTheSuccessfulLoadCompletes() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    ScriptedClient api = client(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"First\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f2\",\"field_title\":\"Second\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");
    api.advanceClockBeforeFirstResponse(clock, Duration.ofMinutes(4));
    WecomSmartSheetFieldCatalog catalog = catalog(api, clock);

    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(api.bodies).hasSize(1);
    clock.advance(Duration.ofMinutes(4).plusSeconds(59));
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(api.bodies).hasSize(1);
    clock.advance(Duration.ofSeconds(1));
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("Second");
    assertThat(api.bodies).hasSize(2);
  }

  @Test
  void parsesMultiSelectOptionsAsImmutableAndRejectsDuplicateIds() throws Exception {
    WecomSmartSheetFieldCatalog catalog = catalog(client("""
        {"errcode":0,"total":1,"fields":[{"field_id":"fTags","field_title":"Tags","field_type":"FIELD_TYPE_SELECT","property_select":{"options":[{"id":"o1","text":"A","style":1},{"id":"o2","text":"B","style":1}]}}]}"""), Clock.systemUTC());
    WecomSmartSheetField tags = catalog.visibleFields(Duration.ofSeconds(1)).get("Tags");
    assertThat(tags.optionId("A")).contains("o1");
    assertThatThrownBy(() -> tags.optionIdsByText().put("C", "o3")).isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> catalog(client("""
        {"errcode":0,"total":1,"fields":[{"field_id":"fTags","field_title":"Tags","field_type":"FIELD_TYPE_SELECT","property_select":{"options":[{"id":"o1","text":"A","style":1},{"id":"o1","text":"B","style":1}]}}]}"""), Clock.systemUTC()).visibleFields(Duration.ofSeconds(1)))
        .hasMessageNotContaining("o1");
  }

  @Test
  void rejectsDuplicateFieldIdsAcrossPages() throws Exception {
    WecomSmartSheetFieldCatalog catalog = catalog(client(
        "{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"One\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"Two\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}"), Clock.systemUTC());
    assertThatThrownBy(() -> catalog.visibleFields(Duration.ofSeconds(1))).hasMessageNotContaining("f1");
  }

  @Test
  void sharesOneExpiredRefreshFailureThenAllowsRetry() throws Exception {
    BlockingThenRetryClient api = new BlockingThenRetryClient("""
        {"errcode":0,"total":1,"fields":[{"field_id":"f2","field_title":"Recovered","field_type":"FIELD_TYPE_TEXT"}]}""");
    WecomSmartSheetFieldCatalog catalog = catalog(api, Clock.systemUTC());
    ExecutorService workers = Executors.newFixedThreadPool(4);
    CountDownLatch ready = new CountDownLatch(4);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<RuntimeException>> failures = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        failures.add(workers.submit(() -> {
          ready.countDown();
          start.await(2, TimeUnit.SECONDS);
          try {
            catalog.visibleFields(Duration.ofSeconds(1));
            throw new AssertionError("expected refresh failure");
          } catch (RuntimeException ex) {
            return ex;
          }
        }));
      }
      assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(api.firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
      api.releaseFirst.countDown();
      RuntimeException first = failures.get(0).get(2, TimeUnit.SECONDS);
      for (Future<RuntimeException> failure : failures) {
        RuntimeException error = failure.get(2, TimeUnit.SECONDS);
        assertThat(error).isSameAs(first);
        assertThat(error.getMessage()).doesNotContain("raw-refresh-pii");
      }
      assertThat(api.calls.get()).isEqualTo(1);
      assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("Recovered");
      assertThat(api.calls.get()).isEqualTo(2);
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void preservesAlreadySanitizedApiFailures() {
    WecomSmartSheetException expected = new WecomSmartSheetException("get_fields", 40058, "remote API returned an error");
    WecomSmartSheetApiClient api = new WecomSmartSheetApiClient(JSON, config(), null) {
      @Override public JsonNode post(String operation, Object body, Duration timeout) {
        throw expected;
      }
    };
    WecomSmartSheetFieldCatalog catalog = catalog(api, Clock.systemUTC());

    assertThatThrownBy(() -> catalog.visibleFields(Duration.ofSeconds(1))).isSameAs(expected);
  }

  @Test
  void preservesSafeMissingConfigurationDiagnosisBeforeProviderLoad() {
    WecomSmartSheetConfig missing = new WecomSmartSheetConfig("http://127.0.0.1", "", "", "", "", "", "", "",
        ZoneId.of("Asia/Shanghai"));
    WecomSmartSheetApiClient api = new WecomSmartSheetApiClient(JSON, config(), null) {
      @Override public JsonNode post(String operation, Object body, Duration timeout) {
        throw new IllegalStateException("raw-provider-response");
      }
    };
    WecomSmartSheetFieldCatalog catalog = new WecomSmartSheetFieldCatalog(api, missing, Clock.systemUTC());

    assertThatThrownBy(() -> catalog.visibleFields(Duration.ofSeconds(1))).satisfies(error -> {
      assertThat(error.getMessage()).contains("WECOM_CORP_ID", "WECOM_APP_SECRET", "WECOM_SMARTSHEET_DOC_ID",
          "WECOM_SMARTSHEET_SHEET_ID", "WECOM_SMARTSHEET_VIEW_ID", "WECOM_SMARTSHEET_SOURCE_TABLE",
          "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE").doesNotContain("raw-provider-response");
    });
  }

  @Test
  void neverServesAnExpiredSnapshotAfterRefreshFailureAndLaterReplacesIt() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    SnapshotRecoveryClient api = new SnapshotRecoveryClient(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f-old\",\"field_title\":\"Old\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f-new\",\"field_title\":\"New\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");
    WecomSmartSheetFieldCatalog catalog = catalog(api, clock);

    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsOnlyKeys("Old");
    clock.advance(Duration.ofMinutes(5));
    assertThatThrownBy(() -> catalog.visibleFields(Duration.ofSeconds(1))).hasMessageNotContaining("Old");
    assertThat(api.calls.get()).isEqualTo(2);
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsOnlyKeys("New");
    assertThat(api.calls.get()).isEqualTo(3);
  }

  @Test
  void rejectsInvalidAndStalledCatalogPagesWithoutResponseContents() throws Exception {
    List<String> invalidResponses = List.of(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"Same\",\"field_type\":\"FIELD_TYPE_TEXT\"},{\"field_id\":\"f2\",\"field_title\":\"Same\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"Pick\",\"field_type\":\"FIELD_TYPE_SELECT\",\"property_select\":{\"options\":[{\"id\":\"o1\",\"text\":\"A\",\"style\":\"x\"},{\"id\":\"o2\",\"text\":\"A\",\"style\":\"y\"}]}}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\" \",\"field_title\":\"Bad\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":\"one\",\"fields\":[]}",
        "{\"errcode\":0,\"total\":1,\"fields\":{}}");
    for (String response : invalidResponses) {
      assertThatThrownBy(() -> catalog(client(response), Clock.systemUTC()).visibleFields(Duration.ofSeconds(1)))
          .hasMessageNotContaining(response).hasMessageNotContaining("errcode");
    }
    assertThatThrownBy(() -> catalog(client("{\"errcode\":0,\"total\":1,\"fields\":[]}"), Clock.systemUTC())
        .visibleFields(Duration.ofSeconds(1))).hasMessageNotContaining("fields");
  }

  @Test
  void distinguishesUnknownAndReadOnlyTitlesWithoutExposingJson() throws Exception {
    WecomSmartSheetFieldCatalog catalog = catalog(client("""
        {"errcode":0,"total":1,"fields":[{"field_id":"f1","field_title":"Formula title","field_type":"FIELD_TYPE_FORMULA"}]}"""), Clock.systemUTC());

    assertThatThrownBy(() -> catalog.requireWritable("Not present", Duration.ofSeconds(1)))
        .hasMessageContaining("Not present").hasMessageNotContaining("errcode");
    assertThatThrownBy(() -> catalog.requireWritable("Formula title", Duration.ofSeconds(1)))
        .hasMessageContaining("Formula title").hasMessageNotContaining("errcode");
  }

  private static WecomSmartSheetFieldCatalog catalog(WecomSmartSheetApiClient api, Clock clock) {
    return new WecomSmartSheetFieldCatalog(api, config(), clock);
  }

  private static WecomSmartSheetConfig config() {
    return new WecomSmartSheetConfig("http://127.0.0.1", "corp", "secret", "doc-1", "sheet-1", "vView",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }

  private static ScriptedClient client(String... responses) throws Exception {
    return new ScriptedClient(responses);
  }

  private static final class ScriptedClient extends WecomSmartSheetApiClient {
    private final ArrayDeque<JsonNode> responses = new ArrayDeque<>();
    private final List<JsonNode> bodies = new ArrayList<>();
    private Runnable beforeFirstResponse = () -> {};

    private ScriptedClient(String... source) throws Exception {
      super(JSON, config(), null);
      for (String value : source) {
        responses.add(JSON.readTree(value));
      }
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      bodies.add(JSON.valueToTree(body));
      if (bodies.size() == 1) {
        beforeFirstResponse.run();
      }
      return responses.removeFirst();
    }

    void advanceClockBeforeFirstResponse(MutableClock clock, Duration duration) {
      beforeFirstResponse = () -> clock.advance(duration);
    }
  }

  private static final class BlockingThenRetryClient extends WecomSmartSheetApiClient {
    private final JsonNode retryResponse;
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch firstStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);

    private BlockingThenRetryClient(String retryResponse) throws Exception {
      super(JSON, config(), null);
      this.retryResponse = JSON.readTree(retryResponse);
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      if (calls.incrementAndGet() == 1) {
        firstStarted.countDown();
        try {
          if (!releaseFirst.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("raw-refresh-pii");
          }
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("raw-refresh-pii");
      }
      return retryResponse;
    }
  }

  private static final class SnapshotRecoveryClient extends WecomSmartSheetApiClient {
    private final JsonNode oldResponse;
    private final JsonNode newResponse;
    private final AtomicInteger calls = new AtomicInteger();

    private SnapshotRecoveryClient(String oldResponse, String newResponse) throws Exception {
      super(JSON, config(), null);
      this.oldResponse = JSON.readTree(oldResponse);
      this.newResponse = JSON.readTree(newResponse);
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      return switch (calls.incrementAndGet()) {
        case 1 -> oldResponse;
        case 2 -> throw new IllegalStateException("raw-refresh-failure");
        default -> newResponse;
      };
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private MutableClock(Instant instant) { this.instant = instant; }
    void advance(Duration duration) { instant = instant.plus(duration); }
    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
  }
}
