package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class WecomSmartSheetRecordClientTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final SheetSource SOURCE = new SheetSource(7L, "doc-1", "Customers");
  private static final LocalDateTime MODIFIED_AFTER = local("2026-07-23T16:00:00Z");

  @Test
  void loadsAllPagesThenReturnsOnlyNewerRowsSortedByTimestampWithVisibleDecodedValues() throws Exception {
    ScriptedApi api = api("""
        {"errcode":0,"has_more":true,"next":2,"total":4,"records":[
          {"record_id":"rec-old","update_time":"1784822399000","values":{"Phone":"13900000000","Outside view":"ignored"}},
          {"record_id":"rec-equal","update_time":1784822400000,"values":{"Phone":"13800000000"}}
        ]}""", """
        {"errcode":0,"has_more":false,"total":4,"records":[
          {"record_id":"rec-later","update_time":"1784822410000","values":{"Phone":"13700000000","Formula":"computed","Outside view":"ignored"}},
          {"record_id":"rec-first","update_time":1784822401000,"values":{"Phone":"13600000000"}}
        ]}""");

    List<SheetRow> rows = client(api).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 10, TIMEOUT);

    assertThat(rows).extracting(SheetRow::rowId).containsExactly("rec-first", "rec-later");
    assertThat(rows.get(1).values()).containsEntry("Phone", "13700000000")
        .containsEntry("Formula", "\"computed\"").doesNotContainKey("Outside view");
    assertThat(recordOffsets(api)).containsExactly(0L, 2L);
    assertThat(recordBodies(api)).extracting(JsonNode::toString).containsExactly(
        "{\"docid\":\"doc-1\",\"sheet_id\":\"sheet-1\",\"view_id\":\"view-1\","
            + "\"key_type\":\"CELL_VALUE_KEY_TYPE_FIELD_TITLE\",\"offset\":0,\"limit\":1000}",
        "{\"docid\":\"doc-1\",\"sheet_id\":\"sheet-1\",\"view_id\":\"view-1\","
            + "\"key_type\":\"CELL_VALUE_KEY_TYPE_FIELD_TITLE\",\"offset\":2,\"limit\":1000}");
  }

  @Test
  void appliesRequestedLimitOnlyAfterReadingAndSortingEveryPage() throws Exception {
    ScriptedApi api = api("""
        {"errcode":0,"has_more":true,"next":1,"records":[
          {"record_id":"rec-middle","update_time":1784822420000,"values":{"Phone":"13800000000"}}
        ]}""", """
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"rec-earliest-later-page","update_time":1784822401000,"values":{"Phone":"13700000000"}}
        ]}""");

    List<SheetRow> rows = client(api).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT);

    assertThat(rows).extracting(SheetRow::rowId).containsExactly("rec-earliest-later-page");
    assertThat(recordOffsets(api)).containsExactly(0L, 1L);
  }

  @Test
  void ordersEqualUpdateTimesByRecordId() throws Exception {
    ScriptedApi api = api(records("""
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"rec-b","update_time":1784822401000,"values":{}},
          {"record_id":"rec-a","update_time":1784822401000,"values":{}}
        ]}"""));

    assertThat(client(api).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 10, TIMEOUT))
        .extracting(SheetRow::rowId).containsExactly("rec-a", "rec-b");
  }

  @Test
  void rejectsWrongTargetBeforeCallingTheApi() throws Exception {
    ScriptedApi api = api();

    assertThatThrownBy(() -> client(api).fetchIncrementalRows(
        new SheetSource(7L, "different-doc", "Customers"), MODIFIED_AFTER, 1, TIMEOUT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client(api).fetchIncrementalRows(
        new SheetSource(7L, "doc-1", "Other"), MODIFIED_AFTER, 1, TIMEOUT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(api.bodies).isEmpty();
  }

  @TestFactory
  Stream<DynamicTest> rejectsInvalidArgumentsBeforeCallingTheApi() {
    return Stream.of(
        DynamicTest.dynamicTest("null source", () -> assertNoApiForInvalid(null, MODIFIED_AFTER, 1, TIMEOUT)),
        DynamicTest.dynamicTest("null modifiedAfter", () -> assertNoApiForInvalid(SOURCE, null, 1, TIMEOUT)),
        DynamicTest.dynamicTest("zero limit", () -> assertNoApiForInvalid(SOURCE, MODIFIED_AFTER, 0, TIMEOUT)),
        DynamicTest.dynamicTest("negative limit", () -> assertNoApiForInvalid(SOURCE, MODIFIED_AFTER, -1, TIMEOUT)),
        DynamicTest.dynamicTest("null timeout", () -> assertNoApiForInvalid(SOURCE, MODIFIED_AFTER, 1, null)),
        DynamicTest.dynamicTest("zero timeout", () -> assertNoApiForInvalid(SOURCE, MODIFIED_AFTER, 1, Duration.ZERO)),
        DynamicTest.dynamicTest("negative timeout", () -> assertNoApiForInvalid(SOURCE, MODIFIED_AFTER, 1, Duration.ofMillis(-1))));
  }

  @TestFactory
  Stream<DynamicTest> rejectsInvalidPageMetadataWithoutExposingResponseContent() {
    return Stream.of(
        "{\"errcode\":0,\"has_more\":true,\"next\":0,\"records\":[]}",
        "{\"errcode\":0,\"has_more\":true,\"records\":[]}",
        "{\"errcode\":0,\"has_more\":true,\"next\":\"two\",\"records\":[]}",
        "{\"errcode\":0,\"has_more\":\"yes\",\"records\":[]}",
        "{\"errcode\":0,\"has_more\":false,\"records\":{}}",
        "{\"errcode\":0,\"has_more\":false}",
        "{\"errcode\":0,\"has_more\":false,\"total\":-1,\"records\":[]}",
        "{\"errcode\":0,\"has_more\":false,\"total\":\"one\",\"records\":[]}")
        .map(response -> DynamicTest.dynamicTest("rejects malformed page metadata", () ->
            assertSafeFailure(() -> client(api(records(response))).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT),
                response)));
  }

  @Test
  void rejectsRecordsPageMissingHasMoreWithoutExposingValues() throws Exception {
    String pii = "pii-missing-has-more-13900000000";

    assertSafeFailure(() -> client(api(records("""
        {"errcode":0,"records":[
          {"record_id":"rec-1","update_time":1784822401000,"values":{"Phone":"pii-missing-has-more-13900000000"}}
        ]}"""))).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT), pii);
  }

  @Test
  void rejectsPageCapsAndUnstableOrExceededTotals() throws Exception {
    List<String> capped = new ArrayList<>();
    for (int page = 0; page < 101; page++) {
      capped.add("{\"errcode\":0,\"has_more\":true,\"next\":" + (page + 1) + ",\"records\":[]}");
    }
    assertSafeFailure(() -> client(api(records(capped.toArray(String[]::new))))
        .fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT), "has_more");
    assertThatThrownBy(() -> client(api(records(
        "{\"errcode\":0,\"has_more\":true,\"next\":1,\"total\":1,\"records\":[]}",
        "{\"errcode\":0,\"has_more\":false,\"total\":2,\"records\":[]}")))
        .fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT)).isInstanceOf(WecomSmartSheetException.class);
    assertSafeFailure(() -> client(api(records(
        "{\"errcode\":0,\"has_more\":false,\"total\":0,\"records\":[{\"record_id\":\"rec-1\",\"update_time\":1784822401000,\"values\":{\"Phone\":\"pii-total\"}}]}")))
        .fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT), "pii-total");
    assertSafeFailure(() -> client(api(records(
        "{\"errcode\":0,\"has_more\":false,\"total\":2,\"records\":[{\"record_id\":\"rec-1\",\"update_time\":1784822401000,\"values\":{\"Phone\":\"pii-terminal-total-13900000000\"}}]}")))
        .fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT), "pii-terminal-total-13900000000");
  }

  @TestFactory
  Stream<DynamicTest> rejectsMalformedRecordsAndDuplicateIdsWithoutPiiLeaks() {
    return Stream.of(
        "{\"update_time\":1784822401000,\"values\":{\"Phone\":\"pii-missing-id\"}}",
        "{\"record_id\":\" \",\"update_time\":1784822401000,\"values\":{\"Phone\":\"pii-blank-id\"}}",
        "{\"record_id\":123,\"update_time\":1784822401000,\"values\":{\"Phone\":\"pii-id-type\"}}",
        "{\"record_id\":\"rec-1\",\"update_time\":\"not-a-time-pii\",\"values\":{\"Phone\":\"pii-time\"}}",
        "{\"record_id\":\"rec-1\",\"update_time\":-1,\"values\":{\"Phone\":\"pii-negative\"}}",
        "{\"record_id\":\"rec-1\",\"update_time\":9223372036854775808,\"values\":{\"Phone\":\"pii-overflow\"}}",
        "{\"record_id\":\"rec-1\",\"update_time\":1784822401000,\"values\":\"pii-not-object\"}")
        .map(record -> DynamicTest.dynamicTest("rejects malformed record", () -> {
          String response = "{\"errcode\":0,\"has_more\":false,\"records\":[" + record + "]}";
          assertSafeFailure(() -> client(api(records(response))).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT), record);
        }));
  }

  @Test
  void rejectsDuplicateRecordIdsAcrossPagesWithoutExposingValues() throws Exception {
    String pii = "pii-duplicate-13900000000";
    assertSafeFailure(() -> client(api(records(
        "{\"errcode\":0,\"has_more\":true,\"next\":1,\"records\":[{\"record_id\":\"rec-1\",\"update_time\":1784822401000,\"values\":{\"Phone\":\"first\"}}]}",
        "{\"errcode\":0,\"has_more\":false,\"records\":[{\"record_id\":\"rec-1\",\"update_time\":1784822402000,\"values\":{\"Phone\":\"" + pii + "\"}}]}")))
        .fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 10, TIMEOUT), pii);
  }

  @Test
  void readsFormulaValuesButIgnoresUnknownTitles() throws Exception {
    ScriptedApi api = api(records("""
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"rec-1","update_time":1784822401000,"values":{"Formula":"formula result","Outside view":"pii-outside-13900000000"}}
        ]}"""));

    SheetRow row = client(api).fetchIncrementalRows(SOURCE, MODIFIED_AFTER, 1, TIMEOUT).get(0);

    assertThat(row.values()).containsEntry("Formula", "\"formula result\"").doesNotContainKey("Outside view");
  }

  private static void assertNoApiForInvalid(
      SheetSource source, LocalDateTime modifiedAfter, int limit, Duration timeout) throws Exception {
    ScriptedApi api = api();
    assertThatThrownBy(() -> client(api).fetchIncrementalRows(source, modifiedAfter, limit, timeout))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(api.bodies).isEmpty();
  }

  private static void assertSafeFailure(ThrowingRunnable action, String... pii) {
    assertThatThrownBy(action::run).satisfies(error -> {
      StringWriter output = new StringWriter();
      error.printStackTrace(new PrintWriter(output));
      for (String sensitive : pii) {
        assertThat(error.getMessage()).doesNotContain(sensitive);
        assertThat(output.toString()).doesNotContain(sensitive);
      }
    });
  }

  private static WecomSmartSheetRecordClient client(ScriptedApi api) {
    WecomSmartSheetConfig config = config();
    return new WecomSmartSheetRecordClient(config, api, new WecomSmartSheetFieldCatalog(api, config),
        new WecomSmartSheetValueCodec(config));
  }

  private static ScriptedApi api(String... recordResponses) throws Exception {
    return new ScriptedApi(fields(), records(recordResponses));
  }

  private static String fields() {
    return """
        {"errcode":0,"total":3,"fields":[
          {"field_id":"f-phone","field_title":"Phone","field_type":"FIELD_TYPE_PHONE_NUMBER"},
          {"field_id":"f-name","field_title":"Name","field_type":"FIELD_TYPE_TEXT"},
          {"field_id":"f-formula","field_title":"Formula","field_type":"FIELD_TYPE_FORMULA"}
        ]}""";
  }

  private static String[] records(String... records) {
    return records;
  }

  private static List<JsonNode> recordBodies(ScriptedApi api) {
    return api.bodies.stream().filter(call -> call.operation.equals("get_records")).map(call -> call.body).toList();
  }

  private static List<Long> recordOffsets(ScriptedApi api) {
    return recordBodies(api).stream().map(body -> body.path("offset").longValue()).toList();
  }

  private static LocalDateTime local(String instant) {
    return LocalDateTime.ofInstant(Instant.parse(instant), ZoneId.of("Asia/Shanghai"));
  }

  private static WecomSmartSheetConfig config() {
    return new WecomSmartSheetConfig("http://127.0.0.1", "corp", "secret", "doc-1", "sheet-1", "view-1",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class ScriptedApi extends WecomSmartSheetApiClient {
    private final Deque<JsonNode> fieldResponses = new ArrayDeque<>();
    private final Deque<JsonNode> recordResponses = new ArrayDeque<>();
    private final List<Call> bodies = new ArrayList<>();

    private ScriptedApi(String fields, String... records) throws Exception {
      super(JSON, config(), null);
      fieldResponses.add(JSON.readTree(fields));
      for (String record : records) {
        recordResponses.add(JSON.readTree(record));
      }
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      bodies.add(new Call(operation, JSON.valueToTree(body)));
      if (operation.equals("get_fields")) {
        return fieldResponses.removeFirst();
      }
      if (operation.equals("get_records")) {
        return recordResponses.removeFirst();
      }
      throw new AssertionError("unexpected operation");
    }
  }

  private record Call(String operation, JsonNode body) {}
}
