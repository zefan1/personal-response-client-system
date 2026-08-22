package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
  void readsExactlyTheRowsNamedByACallback() throws Exception {
    ScriptedApi api = api("""
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"rec-2","update_time":1784822410000,"values":{"Phone":"13800000002"}},
          {"record_id":"rec-1","update_time":1784822410000,"values":{"Phone":"13800000001"}}
        ]}""");
    AuxiliarySmartSheetTarget target = new AuxiliarySmartSheetTarget(
        "PRIMARY", "doc-1", "sheet-1", "view-1", "Phone", "");

    List<SheetRow> rows = client(api).fetchRecords(target, List.of("rec-2", "rec-1", "rec-2"), TIMEOUT);

    assertThat(rows).extracting(SheetRow::rowId).containsExactly("rec-2", "rec-1");
    assertThat(onlyBody(api, "get_records").path("record_ids"))
        .extracting(JsonNode::asText).containsExactly("rec-2", "rec-1");
  }

  @Test
  void rejectsCallbackReadWhenWeComDoesNotReturnEveryNamedRow() throws Exception {
    ScriptedApi api = api("""
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"rec-1","update_time":1784822410000,"values":{"Phone":"13800000001"}}
        ]}""");
    AuxiliarySmartSheetTarget target = new AuxiliarySmartSheetTarget(
        "PRIMARY", "doc-1", "sheet-1", "view-1", "Phone", "");

    assertThatThrownBy(() -> client(api).fetchRecords(target, List.of("rec-1", "rec-2"), TIMEOUT))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("did not contain every requested record");
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

  @Test
  void addsOneRecordWithFieldIdsAndReturnsRecordId() throws Exception {
    ScriptedApi api = api(emptyRecords()).responds("add_records", """
        {"errcode":0,"records":[{"record_id":"r-created"}]}""");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("Phone", "13800000001");
    fields.put("Name", "Customer name");
    fields.put("Tier", null);

    String rowId = client(api).createRow("Customers", fields, TIMEOUT);

    assertThat(rowId).isEqualTo("r-created");
    JsonNode lookupBody = operationBodies(api, "get_records").get(0);
    assertThat(lookupBody.path("key_type").asText()).isEqualTo("CELL_VALUE_KEY_TYPE_FIELD_ID");
    assertThat(lookupBody.path("field_ids")).hasSize(1);
    assertThat(lookupBody.path("field_ids").get(0).asText()).isEqualTo("f-phone");
    JsonNode addBody = onlyBody(api, "add_records");
    assertThat(addBody.path("docid").asText()).isEqualTo("doc-1");
    assertThat(addBody.path("sheet_id").asText()).isEqualTo("sheet-1");
    assertThat(addBody.path("key_type").asText()).isEqualTo("CELL_VALUE_KEY_TYPE_FIELD_ID");
    assertThat(addBody.path("records")).hasSize(1);
    JsonNode values = addBody.path("records").get(0).path("values");
    assertThat(values.has("f-phone")).isTrue();
    assertThat(values.path("f-phone").asText()).isEqualTo("13800000001");
    assertThat(values.has("f-name")).isTrue();
    assertThat(values.has("f-tier")).isFalse();
    assertThat(values.has("Phone")).isFalse();
  }

  @Test
  void returnsExistingIdInsteadOfCreatingDuplicatePhone() throws Exception {
    ScriptedApi api = api("""
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"r-existing","values":{"f-phone":"13800000001"}}
        ]}""");

    String rowId = client(api).createRow("Customers", Map.of("Phone", "13800000001"), TIMEOUT);

    assertThat(rowId).isEqualTo("r-existing");
    assertThat(operationBodies(api, "add_records")).isEmpty();
  }

  @Test
  void rejectsAmbiguousExactPhoneMatchesWithoutExposingCustomerData() throws Exception {
    String phone = "13800000002";
    ScriptedApi api = api("""
        {"errcode":0,"has_more":false,"records":[
          {"record_id":"r-private-one","values":{"f-phone":"13800000002"}},
          {"record_id":"r-private-two","values":{"f-phone":"13800000002"}}
        ]}""");

    assertSafeFailure(() -> client(api).createRow("Customers", Map.of("Phone", phone), TIMEOUT),
        phone, "r-private-one", "r-private-two");
    assertThat(operationBodies(api, "add_records")).isEmpty();
  }

  @TestFactory
  Stream<DynamicTest> rejectsMissingOrBlankUniquePhoneBeforeHttp() {
    return Stream.of(
        DynamicTest.dynamicTest("missing unique field", () -> assertCreateRejectedBeforeHttp(Map.of("Name", "private-name"))),
        DynamicTest.dynamicTest("blank unique field", () -> assertCreateRejectedBeforeHttp(Map.of("Phone", "   "))),
        DynamicTest.dynamicTest("null fields", () -> assertCreateRejectedBeforeHttp(null)));
  }

  @TestFactory
  Stream<DynamicTest> rejectsProtectedUnknownAndInvalidCreateFieldsBeforeAdd() {
    return Stream.of(
        new InvalidField("formula", "Formula", "private-formula"),
        new InvalidField("system", "Created by", "private-system"),
        new InvalidField("hidden", "Hidden field", "private-hidden"),
        new InvalidField("unknown", "Unknown field", "private-unknown"),
        new InvalidField("invalid option", "Tier", "private-option"))
        .map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> {
          ScriptedApi api = api();
          Map<String, Object> fields = new LinkedHashMap<>();
          fields.put("Phone", "13800000003");
          fields.put(fixture.title(), fixture.value());

          assertSafeFailure(() -> client(api).createRow("Customers", fields, TIMEOUT),
              "13800000003", fixture.value());
          assertThat(operationBodies(api, "add_records")).isEmpty();
          assertThat(operationBodies(api, "get_records")).isEmpty();
        }));
  }

  @Test
  void duplicateLookupReadsLaterPagesAndUsesExactDecodedValueOnly() throws Exception {
    ScriptedApi api = api("""
        {"errcode":0,"has_more":true,"next":1,"total":2,"records":[
          {"record_id":"r-formatted","values":{"f-phone":"138 0000 0004","f-name":"ignored"}}
        ]}""", """
        {"errcode":0,"has_more":false,"total":2,"records":[
          {"record_id":"r-exact","values":{"f-phone":"13800000004"}}
        ]}""");

    String rowId = client(api).createRow("Customers", Map.of("Phone", "13800000004"), TIMEOUT);

    assertThat(rowId).isEqualTo("r-exact");
    assertThat(recordOffsets(api)).containsExactly(0L, 1L);
    assertThat(recordBodies(api)).allSatisfy(body -> {
      assertThat(body.path("field_ids")).hasSize(1);
      assertThat(body.path("field_ids").get(0).asText()).isEqualTo("f-phone");
    });
    assertThat(operationBodies(api, "add_records")).isEmpty();
  }

  @Test
  void duplicateLookupRejectsNonAdvancingPageMaximumAndDuplicateRecordIds() throws Exception {
    ScriptedApi stalled = api("""
        {"errcode":0,"has_more":true,"next":0,"records":[]}""");
    assertSafeFailure(() -> client(stalled).createRow("Customers", Map.of("Phone", "13800000005"), TIMEOUT),
        "13800000005");

    List<String> capped = new ArrayList<>();
    for (int page = 0; page < 100; page++) {
      capped.add("{\"errcode\":0,\"has_more\":true,\"next\":" + (page + 1) + ",\"records\":[]}");
    }
    ScriptedApi maximum = api(capped.toArray(String[]::new));
    assertSafeFailure(() -> client(maximum).createRow("Customers", Map.of("Phone", "13800000006"), TIMEOUT),
        "13800000006");

    ScriptedApi duplicate = api(
        "{\"errcode\":0,\"has_more\":true,\"next\":1,\"records\":[{\"record_id\":\"r-private-duplicate\",\"values\":{\"f-phone\":\"other\"}}]}",
        "{\"errcode\":0,\"has_more\":false,\"records\":[{\"record_id\":\"r-private-duplicate\",\"values\":{\"f-phone\":\"13800000007\"}}]}");
    assertSafeFailure(() -> client(duplicate).createRow("Customers", Map.of("Phone", "13800000007"), TIMEOUT),
        "13800000007", "r-private-duplicate");
    assertThat(operationBodies(stalled, "add_records")).isEmpty();
    assertThat(operationBodies(maximum, "add_records")).isEmpty();
    assertThat(operationBodies(duplicate, "add_records")).isEmpty();
  }

  @Test
  void duplicateLookupRejectsChangingExceededAndTruncatedTotals() throws Exception {
    List<ScriptedApi> invalid = List.of(
        api(
            "{\"errcode\":0,\"has_more\":true,\"next\":1,\"total\":1,\"records\":[]}",
            "{\"errcode\":0,\"has_more\":false,\"total\":2,\"records\":[]}"),
        api("{\"errcode\":0,\"has_more\":false,\"total\":0,\"records\":[{\"record_id\":\"r-private\",\"values\":{\"f-phone\":\"other\"}}]}"),
        api("{\"errcode\":0,\"has_more\":false,\"total\":2,\"records\":[{\"record_id\":\"r-private\",\"values\":{\"f-phone\":\"other\"}}]}"));

    for (ScriptedApi api : invalid) {
      assertSafeFailure(() -> client(api).createRow("Customers", Map.of("Phone", "13800000012"), TIMEOUT),
          "13800000012", "r-private");
      assertThat(operationBodies(api, "add_records")).isEmpty();
    }
  }

  @Test
  void serializesConcurrentCreatesForTheSamePhoneAndAddsExactlyOnce() throws Exception {
    ConcurrentCreateApi api = new ConcurrentCreateApi();
    WecomSmartSheetRecordClient client = client(api);
    AtomicReference<String> firstResult = new AtomicReference<>();
    AtomicReference<String> secondResult = new AtomicReference<>();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    Thread first = createThread("create-first", client, firstResult, firstFailure);
    SignalingFields secondFields = new SignalingFields(Map.of("Phone", "13800000011"), false);
    Thread second = createThread("create-second", client, secondFields, TIMEOUT, secondResult, secondFailure);

    try {
      first.start();
      assertThat(api.addStarted.await(2, TimeUnit.SECONDS)).isTrue();
      second.start();
      assertThat(secondFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      api.releaseAdd.countDown();
      first.join(2_000);
      second.join(2_000);

      assertThat(first.isAlive()).isFalse();
      assertThat(second.isAlive()).isFalse();
      assertThat(firstFailure.get()).isNull();
      assertThat(secondFailure.get()).isNull();
      assertThat(firstResult.get()).isEqualTo("r-created");
      assertThat(secondResult.get()).isEqualTo("r-created");
      assertThat(api.addCalls.get()).isEqualTo(1);
      assertThat(operationBodies(api, "get_records")).hasSize(2);
    } finally {
      api.releaseAdd.countDown();
      first.interrupt();
      second.interrupt();
      first.join(2_000);
      second.join(2_000);
    }
  }

  @Test
  void keepsThreeNormalizedPhoneContendersOnOneLockAfterFirstFailure() throws Exception {
    ThreeContenderApi api = new ThreeContenderApi();
    WecomSmartSheetRecordClient client = client(api);
    AtomicReference<String> firstResult = new AtomicReference<>();
    AtomicReference<String> secondResult = new AtomicReference<>();
    AtomicReference<String> thirdResult = new AtomicReference<>();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    AtomicReference<Throwable> thirdFailure = new AtomicReference<>();
    Thread first = createThread("create-normalized-first", client, "138-0000-0013", firstResult, firstFailure);
    SignalingFields secondFields = new SignalingFields(Map.of("Phone", "13800000013"), false);
    SignalingFields thirdFields = new SignalingFields(Map.of("Phone", "13800000013"), false);
    Thread second = createThread(
        "create-normalized-second", client, secondFields, TIMEOUT, secondResult, secondFailure);
    Thread third = createThread(
        "create-normalized-third", client, thirdFields, TIMEOUT, thirdResult, thirdFailure);

    try {
      first.start();
      assertThat(api.firstAddStarted.await(2, TimeUnit.SECONDS)).isTrue();
      second.start();
      assertThat(secondFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      api.releaseFirstAdd.countDown();
      first.join(2_000);
      assertThat(first.isAlive()).isFalse();
      assertThat(api.secondLookupStarted.await(2, TimeUnit.SECONDS)).isTrue();
      third.start();
      assertThat(thirdFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      api.releaseSecondLookup.countDown();
      second.join(2_000);
      third.join(2_000);

      assertThat(second.isAlive()).isFalse();
      assertThat(third.isAlive()).isFalse();
      assertThat(firstResult.get()).isNull();
      assertThat(firstFailure.get()).isInstanceOf(WecomSmartSheetException.class);
      assertThat(secondFailure.get()).isNull();
      assertThat(thirdFailure.get()).isNull();
      assertThat(secondResult.get()).isEqualTo("r-created");
      assertThat(thirdResult.get()).isEqualTo("r-created");
      assertThat(api.addCalls.get()).isEqualTo(2);
      assertThat(api.recordCalls.get()).isEqualTo(3);
    } finally {
      api.releaseFirstAdd.countDown();
      api.releaseSecondLookup.countDown();
      first.interrupt();
      second.interrupt();
      third.interrupt();
      first.join(2_000);
      second.join(2_000);
      third.join(2_000);
    }
  }

  @Test
  void reusesConfirmedIdWhenOverlappingNormalizedPhonesRemainInvisibleToLookup() throws Exception {
    VisibilityLagApi api = new VisibilityLagApi();
    WecomSmartSheetRecordClient client = client(api);
    AtomicReference<String> firstResult = new AtomicReference<>();
    AtomicReference<String> secondResult = new AtomicReference<>();
    AtomicReference<String> thirdResult = new AtomicReference<>();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    AtomicReference<Throwable> thirdFailure = new AtomicReference<>();
    Thread first = createThread("create-lag-first", client, "138-0000-0015", firstResult, firstFailure);
    SignalingFields secondFields = new SignalingFields(Map.of("Phone", "13800000015"), false);
    SignalingFields thirdFields = new SignalingFields(Map.of("Phone", "(138) 0000 0015"), false);
    Thread second = createThread("create-lag-second", client, secondFields, TIMEOUT, secondResult, secondFailure);
    Thread third = createThread("create-lag-third", client, thirdFields, TIMEOUT, thirdResult, thirdFailure);

    try {
      first.start();
      assertThat(api.firstAddStarted.await(2, TimeUnit.SECONDS)).isTrue();
      second.start();
      third.start();
      assertThat(secondFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(thirdFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      api.releaseFirstAdd.countDown();
      first.join(2_000);
      second.join(2_000);
      third.join(2_000);

      assertThat(first.isAlive()).isFalse();
      assertThat(second.isAlive()).isFalse();
      assertThat(third.isAlive()).isFalse();
      assertThat(firstFailure.get()).isNull();
      assertThat(secondFailure.get()).isNull();
      assertThat(thirdFailure.get()).isNull();
      assertThat(firstResult.get()).isEqualTo("r-created");
      assertThat(secondResult.get()).isEqualTo("r-created");
      assertThat(thirdResult.get()).isEqualTo("r-created");
      assertThat(api.recordCalls.get()).isEqualTo(3);
      assertThat(api.addCalls.get()).isEqualTo(1);
    } finally {
      api.releaseFirstAdd.countDown();
      first.interrupt();
      second.interrupt();
      third.interrupt();
      first.join(2_000);
      second.join(2_000);
      third.join(2_000);
    }
  }

  @Test
  void reusesRecentConfirmedIdAcrossSequentialVisibilityLag() throws Exception {
    SequentialLagApi api = new SequentialLagApi();
    WecomSmartSheetRecordClient client = client(api);

    String first = client.createRow("Customers", Map.of("Phone", "138-0000-0017"), TIMEOUT);
    String second = client.createRow("Customers", Map.of("Phone", "13800000017"), TIMEOUT);

    assertThat(first).isEqualTo("r-created");
    assertThat(second).isEqualTo("r-created");
    assertThat(api.recordCalls.get()).isEqualTo(2);
    assertThat(api.addCalls.get()).isEqualTo(1);
  }

  @Test
  void expiresRecentConfirmedIdAtConfiguredTtl() throws Exception {
    Duration ttl = Duration.ofMinutes(5);
    MutableClock clock = new MutableClock(Instant.parse("2026-07-24T00:00:00Z"));
    SequentialLagApi api = new SequentialLagApi();
    WecomSmartSheetRecordClient client = client(api, clock, ttl, 2);

    assertThat(client.createRow("Customers", Map.of("Phone", "138-0000-0018"), TIMEOUT))
        .isEqualTo("r-created");
    clock.advance(ttl.minusNanos(1));
    assertThat(client.createRow("Customers", Map.of("Phone", "13800000018"), TIMEOUT))
        .isEqualTo("r-created");
    clock.advance(Duration.ofNanos(1));
    assertThat(client.createRow("Customers", Map.of("Phone", "13800000018"), TIMEOUT))
        .isEqualTo("r-duplicate-2");
    assertThat(api.recordCalls.get()).isEqualTo(3);
    assertThat(api.addCalls.get()).isEqualTo(2);
  }

  @Test
  void evictsLeastRecentlyUsedConfirmedIdAtConfiguredCapacity() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-24T00:00:00Z"));
    SequentialLagApi api = new SequentialLagApi();
    WecomSmartSheetRecordClient client = client(api, clock, Duration.ofMinutes(5), 2);

    assertThat(client.createRow("Customers", Map.of("Phone", "13800000021"), TIMEOUT))
        .isEqualTo("r-created");
    assertThat(client.createRow("Customers", Map.of("Phone", "13800000022"), TIMEOUT))
        .isEqualTo("r-duplicate-2");
    assertThat(client.createRow("Customers", Map.of("Phone", "138-0000-0021"), TIMEOUT))
        .isEqualTo("r-created");
    assertThat(client.createRow("Customers", Map.of("Phone", "13800000023"), TIMEOUT))
        .isEqualTo("r-duplicate-3");
    assertThat(client.createRow("Customers", Map.of("Phone", "138-0000-0022"), TIMEOUT))
        .isEqualTo("r-duplicate-4");
    assertThat(api.recordCalls.get()).isEqualTo(5);
    assertThat(api.addCalls.get()).isEqualTo(4);
  }

  @Test
  void retainsExactRemoteMatchForSequentialVisibilityLag() throws Exception {
    ScriptedApi api = api(
        "{\"errcode\":0,\"has_more\":false,\"records\":[{\"record_id\":\"r-existing\",\"values\":{\"f-phone\":\"138-0000-0024\"}}]}",
        emptyRecords());
    WecomSmartSheetRecordClient client = client(api);

    assertThat(client.createRow("Customers", Map.of("Phone", "138-0000-0024"), TIMEOUT))
        .isEqualTo("r-existing");
    assertThat(client.createRow("Customers", Map.of("Phone", "13800000024"), TIMEOUT))
        .isEqualTo("r-existing");
    assertThat(operationBodies(api, "get_records")).hasSize(2);
    assertThat(operationBodies(api, "add_records")).isEmpty();
  }

  @Test
  void registersNormalizedParticipantBeforeEncodingCanPause() throws Exception {
    VisibilityLagApi api = new VisibilityLagApi();
    WecomSmartSheetRecordClient client = client(api);
    AtomicReference<String> firstResult = new AtomicReference<>();
    AtomicReference<String> secondResult = new AtomicReference<>();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    SignalingFields secondFields = new SignalingFields(Map.of("Phone", "13800000016"), true);
    Thread first = createThread(
        "create-encoding-first", client, "138-0000-0016", firstResult, firstFailure);
    Thread second = createThread(
        "create-encoding-second", client, secondFields, TIMEOUT, secondResult, secondFailure);

    try {
      first.start();
      assertThat(api.firstAddStarted.await(2, TimeUnit.SECONDS)).isTrue();
      second.start();
      assertThat(secondFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      boolean encodingPausedBeforeFirstCompleted =
          secondFields.encodingStarted.await(500, TimeUnit.MILLISECONDS);
      api.releaseFirstAdd.countDown();
      first.join(2_000);
      assertThat(first.isAlive()).isFalse();
      if (!encodingPausedBeforeFirstCompleted) {
        assertThat(secondFields.encodingStarted.await(2, TimeUnit.SECONDS)).isTrue();
      }
      secondFields.releaseEncoding.countDown();
      second.join(2_000);

      assertThat(second.isAlive()).isFalse();
      assertThat(firstFailure.get()).isNull();
      assertThat(secondFailure.get()).isNull();
      assertThat(firstResult.get()).isEqualTo("r-created");
      assertThat(secondResult.get()).isEqualTo("r-created");
      assertThat(api.recordCalls.get()).isEqualTo(2);
      assertThat(api.addCalls.get()).isEqualTo(1);
    } finally {
      api.releaseFirstAdd.countDown();
      secondFields.releaseEncoding.countDown();
      first.interrupt();
      second.interrupt();
      first.join(2_000);
      second.join(2_000);
    }
  }

  @Test
  void timesOutWaitingForCreateLockAndAllowsLaterRequest() throws Exception {
    ConcurrentCreateApi api = new ConcurrentCreateApi();
    WecomSmartSheetRecordClient client = client(api);
    AtomicReference<String> holderResult = new AtomicReference<>();
    AtomicReference<Throwable> holderFailure = new AtomicReference<>();
    AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
    AtomicReference<Duration> waiterElapsed = new AtomicReference<>();
    SignalingFields waiterFields = new SignalingFields(
        Map.of("Phone", "13800000011", "Name", "private-timeout-value"), false);
    Thread holder = createThread("create-timeout-holder", client, holderResult, holderFailure);
    Thread waiter = new Thread(() -> {
      long started = System.nanoTime();
      try {
        client.createRow("Customers", waiterFields, Duration.ofMillis(100));
      } catch (Throwable ex) {
        waiterFailure.set(ex);
      } finally {
        waiterElapsed.set(Duration.ofNanos(System.nanoTime() - started));
      }
    }, "create-timeout-waiter");

    try {
      holder.start();
      assertThat(api.addStarted.await(2, TimeUnit.SECONDS)).isTrue();
      waiter.start();
      assertThat(waiterFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      waiter.join(1_000);

      assertThat(waiter.isAlive()).isFalse();
      assertThat(waiterFailure.get()).isInstanceOf(WecomSmartSheetException.class);
      assertSafeThrowable(waiterFailure.get(), "13800000011", "private-timeout-value", "r-created");
      assertThat(waiterElapsed.get()).isLessThan(Duration.ofSeconds(1));
      assertThat(api.addCalls.get()).isEqualTo(1);

      api.releaseAdd.countDown();
      holder.join(2_000);
      assertThat(holder.isAlive()).isFalse();
      assertThat(holderFailure.get()).isNull();
      assertThat(holderResult.get()).isEqualTo("r-created");
      assertThat(client.createRow("Customers", Map.of("Phone", "13800000011"), TIMEOUT))
          .isEqualTo("r-created");
    } finally {
      api.releaseAdd.countDown();
      holder.interrupt();
      waiter.interrupt();
      holder.join(2_000);
      waiter.join(2_000);
    }
  }

  @Test
  void interruptingCreateLockWaiterRestoresFlagAndAllowsLaterRequest() throws Exception {
    ConcurrentCreateApi api = new ConcurrentCreateApi();
    WecomSmartSheetRecordClient client = client(api);
    AtomicReference<String> holderResult = new AtomicReference<>();
    AtomicReference<Throwable> holderFailure = new AtomicReference<>();
    AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
    AtomicBoolean interruptRestored = new AtomicBoolean();
    SignalingFields waiterFields = new SignalingFields(
        Map.of("Phone", "13800000011", "Name", "private-interrupt-value"), false);
    Thread holder = createThread("create-interrupt-holder", client, holderResult, holderFailure);
    Thread waiter = new Thread(() -> {
      try {
        client.createRow("Customers", waiterFields, TIMEOUT);
      } catch (Throwable ex) {
        waiterFailure.set(ex);
        interruptRestored.set(Thread.currentThread().isInterrupted());
      }
    }, "create-interrupt-waiter");

    try {
      holder.start();
      assertThat(api.addStarted.await(2, TimeUnit.SECONDS)).isTrue();
      waiter.start();
      assertThat(waiterFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      waiter.interrupt();
      waiter.join(1_000);

      assertThat(waiter.isAlive()).isFalse();
      assertThat(waiterFailure.get()).isInstanceOf(WecomSmartSheetException.class);
      assertThat(interruptRestored.get()).isTrue();
      assertSafeThrowable(waiterFailure.get(), "13800000011", "private-interrupt-value", "r-created");
      assertThat(api.addCalls.get()).isEqualTo(1);

      api.releaseAdd.countDown();
      holder.join(2_000);
      assertThat(holder.isAlive()).isFalse();
      assertThat(holderFailure.get()).isNull();
      assertThat(holderResult.get()).isEqualTo("r-created");
      assertThat(client.createRow("Customers", Map.of("Phone", "13800000011"), TIMEOUT))
          .isEqualTo("r-created");
    } finally {
      api.releaseAdd.countDown();
      holder.interrupt();
      waiter.interrupt();
      holder.join(2_000);
      waiter.join(2_000);
    }
  }

  @Test
  void passesOnlyRemainingDeadlineBudgetAfterWaitingForCreateLock() throws Exception {
    ConcurrentCreateApi api = new ConcurrentCreateApi();
    WecomSmartSheetRecordClient client = client(api);
    Duration waiterTimeout = Duration.ofMillis(800);
    AtomicReference<String> holderResult = new AtomicReference<>();
    AtomicReference<String> waiterResult = new AtomicReference<>();
    AtomicReference<Throwable> holderFailure = new AtomicReference<>();
    AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
    SignalingFields waiterFields = new SignalingFields(Map.of("Phone", "13800000011"), false);
    Thread holder = createThread("create-budget-holder", client, holderResult, holderFailure);
    Thread waiter = createThread(
        "create-budget-waiter", client, waiterFields, waiterTimeout, waiterResult, waiterFailure);

    try {
      holder.start();
      assertThat(api.addStarted.await(2, TimeUnit.SECONDS)).isTrue();
      waiter.start();
      assertThat(waiterFields.uniqueRead.await(2, TimeUnit.SECONDS)).isTrue();
      waiterFields.encodingStarted.await(200, TimeUnit.MILLISECONDS);
      api.releaseAdd.countDown();
      holder.join(2_000);
      waiter.join(2_000);

      assertThat(holder.isAlive()).isFalse();
      assertThat(waiter.isAlive()).isFalse();
      assertThat(holderFailure.get()).isNull();
      assertThat(waiterFailure.get()).isNull();
      assertThat(holderResult.get()).isEqualTo("r-created");
      assertThat(waiterResult.get()).isEqualTo("r-created");
      assertThat(api.recordTimeouts).hasSize(2);
      assertThat(api.recordTimeouts.get(1)).isPositive().isLessThan(waiterTimeout);
    } finally {
      api.releaseAdd.countDown();
      holder.interrupt();
      waiter.interrupt();
      holder.join(2_000);
      waiter.join(2_000);
    }
  }

  @Test
  void rejectsMalformedAddConfirmationWithoutLeakingRequestOrResponseData() throws Exception {
    String phone = "13800000008";
    String name = "private-name-value";
    String responseValue = "private-add-response";
    ScriptedApi api = api(emptyRecords()).responds("add_records", """
        {"errcode":0,"raw":"private-add-response","records":[
          {"record_id":"r-private-one"},{"record_id":"r-private-two"}
        ]}""");

    assertSafeFailure(() -> client(api).createRow("Customers", Map.of("Phone", phone, "Name", name), TIMEOUT),
        phone, name, responseValue, "r-private-one", "r-private-two");
  }

  @TestFactory
  Stream<DynamicTest> rejectsMissingBlankAndNontextualAddedRecordIds() {
    return Stream.of(
        "{\"errcode\":0,\"records\":[]}",
        "{\"errcode\":0,\"records\":[{\"record_id\":\"   \"}]}",
        "{\"errcode\":0,\"records\":[{\"record_id\":123}]}"
    ).map(response -> DynamicTest.dynamicTest("rejects invalid add confirmation", () -> {
      ScriptedApi api = api(emptyRecords()).responds("add_records", response);

      assertSafeFailure(() -> client(api).createRow("Customers", Map.of("Phone", "13800000014"), TIMEOUT),
          "13800000014", response);
    }));
  }

  @Test
  void updatesOneRecordByIdAndPreservesExplicitEmptyString() throws Exception {
    ScriptedApi api = api().responds("update_records", """
        {"errcode":0,"records":[{"record_id":"r-existing"}]}""");

    client(api).updateRow("Customers", "r-existing", Map.of("Name", "", "Phone", "13800000009"), TIMEOUT);

    JsonNode updateBody = onlyBody(api, "update_records");
    assertThat(updateBody.path("docid").asText()).isEqualTo("doc-1");
    assertThat(updateBody.path("sheet_id").asText()).isEqualTo("sheet-1");
    assertThat(updateBody.path("key_type").asText()).isEqualTo("CELL_VALUE_KEY_TYPE_FIELD_ID");
    assertThat(updateBody.path("records")).hasSize(1);
    assertThat(updateBody.path("records").get(0).path("record_id").asText()).isEqualTo("r-existing");
    JsonNode values = updateBody.path("records").get(0).path("values");
    assertThat(values.has("f-name")).isTrue();
    assertThat(values.path("f-name").get(0).path("text").asText()).isEmpty();
    assertThat(values.path("f-phone").asText()).isEqualTo("13800000009");
  }

  @TestFactory
  Stream<DynamicTest> rejectsBlankRecordIdAndEmptyUpdateFieldsBeforeHttp() {
    Map<String, Object> allNull = new LinkedHashMap<>();
    allNull.put("Name", null);
    return Stream.of(
        DynamicTest.dynamicTest("null record id", () -> assertUpdateRejectedBeforeHttp(null, Map.of("Name", "value"))),
        DynamicTest.dynamicTest("blank record id", () -> assertUpdateRejectedBeforeHttp("   ", Map.of("Name", "value"))),
        DynamicTest.dynamicTest("null fields", () -> assertUpdateRejectedBeforeHttp("r-existing", null)),
        DynamicTest.dynamicTest("empty fields", () -> assertUpdateRejectedBeforeHttp("r-existing", Map.of())),
        DynamicTest.dynamicTest("all null fields", () -> assertUpdateRejectedBeforeHttp("r-existing", allNull)));
  }

  @TestFactory
  Stream<DynamicTest> rejectsProtectedUnknownAndInvalidUpdateFieldsBeforeUpdate() {
    return Stream.of(
        new InvalidField("formula", "Formula", "private-formula"),
        new InvalidField("system", "Created by", "private-system"),
        new InvalidField("hidden", "Hidden field", "private-hidden"),
        new InvalidField("unknown", "Unknown field", "private-unknown"),
        new InvalidField("invalid option", "Tier", "private-option"))
        .map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> {
          ScriptedApi api = api();

          assertSafeFailure(() -> client(api).updateRow(
              "Customers", "r-private-update", Map.of(fixture.title(), fixture.value()), TIMEOUT),
              "r-private-update", fixture.value());
          assertThat(operationBodies(api, "update_records")).isEmpty();
        }));
  }

  @Test
  void rejectsWrongWriteTargetBeforeHttp() throws Exception {
    ScriptedApi createApi = api();
    assertThatThrownBy(() -> client(createApi).createRow(
        "Other", Map.of("Phone", "13800000010"), TIMEOUT)).isInstanceOf(IllegalArgumentException.class);
    assertThat(createApi.bodies).isEmpty();

    ScriptedApi updateApi = api();
    assertThatThrownBy(() -> client(updateApi).updateRow(
        "Other", "r-private", Map.of("Name", "private"), TIMEOUT)).isInstanceOf(IllegalArgumentException.class);
    assertThat(updateApi.bodies).isEmpty();
  }

  @Test
  void recentSuccessCacheIsScopedToSmartSheetTargetAndSourceTable() throws Exception {
    ScriptedApi api = api(emptyRecords(), emptyRecords());
    api.fieldResponses.add(JSON.readTree(fields()));
    api.responds("add_records",
        "{\"errcode\":0,\"records\":[{\"record_id\":\"r-assignment-a\"}]}",
        "{\"errcode\":0,\"records\":[{\"record_id\":\"r-assignment-b\"}]}");
    java.util.concurrent.atomic.AtomicReference<AuxiliarySmartSheetTarget> current =
        new java.util.concurrent.atomic.AtomicReference<>(new AuxiliarySmartSheetTarget(
            "ASSIGNMENT", "doc-a", "sheet-a", "view-a", "Phone", ""));
    AuxiliarySmartSheetTargets targets = new AuxiliarySmartSheetTargets() {
      @Override
      public java.util.Optional<AuxiliarySmartSheetTarget> assignment() {
        return java.util.Optional.of(current.get());
      }
    };
    WecomSmartSheetConfig config = config();
    WecomSmartSheetRecordClient recordClient = new WecomSmartSheetRecordClient(
        config, api, new WecomSmartSheetFieldCatalog(api, config),
        new WecomSmartSheetValueCodec(config), targets,
        Clock.systemUTC(), Duration.ofMinutes(5), 1024);

    String first = recordClient.createRow("ASSIGNMENT:sheet-a", Map.of("Phone", "13800000021"), TIMEOUT);
    current.set(new AuxiliarySmartSheetTarget("ASSIGNMENT", "doc-b", "sheet-b", "view-b", "Phone", ""));
    String second = recordClient.createRow("ASSIGNMENT:sheet-b", Map.of("Phone", "13800000021"), TIMEOUT);

    assertThat(first).isEqualTo("r-assignment-a");
    assertThat(second).isEqualTo("r-assignment-b");
    assertThat(operationBodies(api, "add_records")).extracting(body -> body.path("docid").asText())
        .containsExactly("doc-a", "doc-b");
  }

  @Test
  void rejectsMismatchedUpdateConfirmationWithoutLeakingRequestOrResponseData() throws Exception {
    String recordId = "r-private-request";
    String value = "private-update-value";
    String responseValue = "private-update-response";
    ScriptedApi api = api().responds("update_records", """
        {"errcode":0,"raw":"private-update-response","records":[{"record_id":"r-private-other"}]}""");

    assertSafeFailure(() -> client(api).updateRow("Customers", recordId, Map.of("Name", value), TIMEOUT),
        recordId, value, responseValue, "r-private-other");
  }

  private static void assertNoApiForInvalid(
      SheetSource source, LocalDateTime modifiedAfter, int limit, Duration timeout) throws Exception {
    ScriptedApi api = api();
    assertThatThrownBy(() -> client(api).fetchIncrementalRows(source, modifiedAfter, limit, timeout))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(api.bodies).isEmpty();
  }

  private static void assertCreateRejectedBeforeHttp(Map<String, Object> fields) throws Exception {
    ScriptedApi api = api();
    assertThatThrownBy(() -> client(api).createRow("Customers", fields, TIMEOUT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(api.bodies).isEmpty();
  }

  private static void assertUpdateRejectedBeforeHttp(String recordId, Map<String, Object> fields) throws Exception {
    ScriptedApi api = api();
    assertThatThrownBy(() -> client(api).updateRow("Customers", recordId, fields, TIMEOUT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(api.bodies).isEmpty();
  }

  private static void assertSafeFailure(ThrowingRunnable action, String... pii) {
    assertThatThrownBy(action::run).satisfies(error -> assertSafeThrowable(error, pii));
  }

  private static void assertSafeThrowable(Throwable error, String... pii) {
    StringWriter output = new StringWriter();
    error.printStackTrace(new PrintWriter(output));
    for (String sensitive : pii) {
      assertThat(error.getMessage()).doesNotContain(sensitive);
      assertThat(output.toString()).doesNotContain(sensitive);
    }
  }

  private static WecomSmartSheetRecordClient client(WecomSmartSheetApiClient api) {
    WecomSmartSheetConfig config = config();
    return new WecomSmartSheetRecordClient(config, api, new WecomSmartSheetFieldCatalog(api, config),
        new WecomSmartSheetValueCodec(config));
  }

  private static WecomSmartSheetRecordClient client(
      WecomSmartSheetApiClient api, Clock clock, Duration recentSuccessTtl, int recentSuccessLimit) {
    WecomSmartSheetConfig config = config();
    return new WecomSmartSheetRecordClient(config, api, new WecomSmartSheetFieldCatalog(api, config),
        new WecomSmartSheetValueCodec(config), clock, recentSuccessTtl, recentSuccessLimit);
  }

  private static ScriptedApi api(String... recordResponses) throws Exception {
    return new ScriptedApi(fields(), records(recordResponses));
  }

  private static String emptyRecords() {
    return "{\"errcode\":0,\"has_more\":false,\"records\":[]}";
  }

  private static String fields() {
    return """
        {"errcode":0,"total":5,"fields":[
          {"field_id":"f-phone","field_title":"Phone","field_type":"FIELD_TYPE_PHONE_NUMBER"},
          {"field_id":"f-name","field_title":"Name","field_type":"FIELD_TYPE_TEXT"},
          {"field_id":"f-formula","field_title":"Formula","field_type":"FIELD_TYPE_FORMULA"},
          {"field_id":"f-created-by","field_title":"Created by","field_type":"FIELD_TYPE_CREATED_USER"},
          {"field_id":"f-tier","field_title":"Tier","field_type":"FIELD_TYPE_SINGLE_SELECT",
           "property_single_select":{"options":[{"id":"o-gold","text":"Gold"}]}}
        ]}""";
  }

  private static String[] records(String... records) {
    return records;
  }

  private static List<JsonNode> recordBodies(ScriptedApi api) {
    return operationBodies(api, "get_records");
  }

  private static List<JsonNode> operationBodies(ScriptedApi api, String operation) {
    return api.bodies.stream().filter(call -> call.operation.equals(operation)).map(call -> call.body).toList();
  }

  private static JsonNode onlyBody(ScriptedApi api, String operation) {
    assertThat(operationBodies(api, operation)).hasSize(1);
    return operationBodies(api, operation).get(0);
  }

  private static List<Long> recordOffsets(ScriptedApi api) {
    return recordBodies(api).stream().map(body -> body.path("offset").longValue()).toList();
  }

  private static LocalDateTime local(String instant) {
    return LocalDateTime.ofInstant(Instant.parse(instant), ZoneId.of("Asia/Shanghai"));
  }

  private static WecomSmartSheetConfig config() {
    return new WecomSmartSheetConfig("http://127.0.0.1", "corp", "secret", "doc-1", "sheet-1", "view-1",
        "Customers", "Phone", ZoneId.of("Asia/Shanghai"));
  }

  private static Thread createThread(
      String name,
      WecomSmartSheetRecordClient client,
      AtomicReference<String> result,
      AtomicReference<Throwable> failure) {
    return createThread(name, client, "13800000011", result, failure);
  }

  private static Thread createThread(
      String name,
      WecomSmartSheetRecordClient client,
      String phone,
      AtomicReference<String> result,
      AtomicReference<Throwable> failure) {
    return createThread(name, client, Map.of("Phone", phone), TIMEOUT, result, failure);
  }

  private static Thread createThread(
      String name,
      WecomSmartSheetRecordClient client,
      Map<String, Object> fields,
      Duration timeout,
      AtomicReference<String> result,
      AtomicReference<Throwable> failure) {
    return new Thread(() -> {
      try {
        result.set(client.createRow("Customers", fields, timeout));
      } catch (Throwable ex) {
        failure.set(ex);
      }
    }, name);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class SignalingFields extends AbstractMap<String, Object> {
    private final Map<String, Object> delegate;
    private final boolean blockEncoding;
    private final CountDownLatch uniqueRead = new CountDownLatch(1);
    private final CountDownLatch encodingStarted = new CountDownLatch(1);
    private final CountDownLatch releaseEncoding = new CountDownLatch(1);

    private SignalingFields(Map<String, Object> delegate, boolean blockEncoding) {
      this.delegate = Map.copyOf(delegate);
      this.blockEncoding = blockEncoding;
    }

    @Override public Object get(Object key) {
      Object value = delegate.get(key);
      if ("Phone".equals(key)) {
        uniqueRead.countDown();
      }
      return value;
    }

    @Override public Set<Entry<String, Object>> entrySet() {
      encodingStarted.countDown();
      if (blockEncoding) {
        try {
          if (!releaseEncoding.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("timed out waiting to release field encoding");
          }
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new AssertionError("field encoding interrupted");
        }
      }
      return delegate.entrySet();
    }
  }

  private record InvalidField(String name, String title, String value) {}

  private static final class ScriptedApi extends WecomSmartSheetApiClient {
    private final Deque<JsonNode> fieldResponses = new ArrayDeque<>();
    private final Deque<JsonNode> recordResponses = new ArrayDeque<>();
    private final Deque<JsonNode> addResponses = new ArrayDeque<>();
    private final Deque<JsonNode> updateResponses = new ArrayDeque<>();
    private final List<Call> bodies = new ArrayList<>();

    private ScriptedApi(String fields, String... records) throws Exception {
      super(JSON, config(), null);
      fieldResponses.add(JSON.readTree(fields));
      for (String record : records) {
        recordResponses.add(JSON.readTree(record));
      }
    }

    private ScriptedApi responds(String operation, String... responses) throws Exception {
      Deque<JsonNode> target = switch (operation) {
        case "add_records" -> addResponses;
        case "update_records" -> updateResponses;
        default -> throw new IllegalArgumentException("unsupported scripted operation");
      };
      for (String response : responses) {
        target.add(JSON.readTree(response));
      }
      return this;
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      bodies.add(new Call(operation, JSON.valueToTree(body)));
      if (operation.equals("get_fields")) {
        return fieldResponses.removeFirst();
      }
      if (operation.equals("get_records")) {
        return recordResponses.removeFirst();
      }
      if (operation.equals("add_records")) {
        return addResponses.removeFirst();
      }
      if (operation.equals("update_records")) {
        return updateResponses.removeFirst();
      }
      throw new AssertionError("unexpected operation");
    }

    @Override public JsonNode postForTarget(String operation, Object body, Duration timeout) {
      return post(operation, body, timeout);
    }

    @Override public JsonNode postForTarget(
        String operation, Object body, Duration timeout, boolean primaryTarget) {
      return post(operation, body, timeout);
    }
  }

  private static final class ConcurrentCreateApi extends WecomSmartSheetApiClient {
    private final JsonNode fieldResponse;
    private final JsonNode emptyResponse;
    private final JsonNode existingResponse;
    private final JsonNode addResponse;
    private final List<Call> bodies = new CopyOnWriteArrayList<>();
    private final List<Duration> recordTimeouts = new CopyOnWriteArrayList<>();
    private final CountDownLatch addStarted = new CountDownLatch(1);
    private final CountDownLatch releaseAdd = new CountDownLatch(1);
    private final AtomicInteger addCalls = new AtomicInteger();
    private final AtomicBoolean addCompleted = new AtomicBoolean();

    private ConcurrentCreateApi() throws Exception {
      super(JSON, config(), null);
      fieldResponse = JSON.readTree(fields());
      emptyResponse = JSON.readTree(emptyRecords());
      existingResponse = JSON.readTree("""
          {"errcode":0,"has_more":false,"records":[
            {"record_id":"r-created","values":{"f-phone":"13800000011"}}
          ]}""");
      addResponse = JSON.readTree("""
          {"errcode":0,"records":[{"record_id":"r-created"}]}""");
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      bodies.add(new Call(operation, JSON.valueToTree(body)));
      return switch (operation) {
        case "get_fields" -> fieldResponse;
        case "get_records" -> {
          recordTimeouts.add(timeout);
          yield addCompleted.get() ? existingResponse : emptyResponse;
        }
        case "add_records" -> add();
        default -> throw new AssertionError("unexpected operation");
      };
    }

    private JsonNode add() {
      addCalls.incrementAndGet();
      addStarted.countDown();
      try {
        if (!releaseAdd.await(2, TimeUnit.SECONDS)) {
          throw new AssertionError("timed out waiting to release add");
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("add interrupted");
      }
      addCompleted.set(true);
      return addResponse;
    }
  }

  private static final class ThreeContenderApi extends WecomSmartSheetApiClient {
    private final JsonNode fieldResponse;
    private final JsonNode emptyResponse;
    private final JsonNode existingResponse;
    private final JsonNode addResponse;
    private final CountDownLatch firstAddStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstAdd = new CountDownLatch(1);
    private final CountDownLatch secondLookupStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSecondLookup = new CountDownLatch(1);
    private final AtomicInteger recordCalls = new AtomicInteger();
    private final AtomicInteger addCalls = new AtomicInteger();
    private final AtomicBoolean created = new AtomicBoolean();

    private ThreeContenderApi() throws Exception {
      super(JSON, config(), null);
      fieldResponse = JSON.readTree(fields());
      emptyResponse = JSON.readTree(emptyRecords());
      existingResponse = JSON.readTree("""
          {"errcode":0,"has_more":false,"records":[
            {"record_id":"r-created","values":{"f-phone":"13800000013"}}
          ]}""");
      addResponse = JSON.readTree("""
          {"errcode":0,"records":[{"record_id":"r-created"}]}""");
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      return switch (operation) {
        case "get_fields" -> fieldResponse;
        case "get_records" -> records();
        case "add_records" -> add();
        default -> throw new AssertionError("unexpected operation");
      };
    }

    private JsonNode records() {
      int call = recordCalls.incrementAndGet();
      if (call == 1) {
        return emptyResponse;
      }
      if (created.get()) {
        return existingResponse;
      }
      if (call == 2) {
        secondLookupStarted.countDown();
      }
      await(releaseSecondLookup, "lookup release");
      return emptyResponse;
    }

    private JsonNode add() {
      if (addCalls.incrementAndGet() == 1) {
        firstAddStarted.countDown();
        await(releaseFirstAdd, "first add release");
        throw new WecomSmartSheetException("add_records", "safe first failure", null);
      }
      created.set(true);
      return addResponse;
    }

    private static void await(CountDownLatch latch, String description) {
      try {
        if (!latch.await(2, TimeUnit.SECONDS)) {
          throw new AssertionError("timed out waiting for " + description);
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(description + " interrupted");
      }
    }
  }

  private static final class VisibilityLagApi extends WecomSmartSheetApiClient {
    private final JsonNode fieldResponse;
    private final JsonNode emptyResponse;
    private final CountDownLatch firstAddStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstAdd = new CountDownLatch(1);
    private final AtomicInteger recordCalls = new AtomicInteger();
    private final AtomicInteger addCalls = new AtomicInteger();

    private VisibilityLagApi() throws Exception {
      super(JSON, config(), null);
      fieldResponse = JSON.readTree(fields());
      emptyResponse = JSON.readTree(emptyRecords());
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      return switch (operation) {
        case "get_fields" -> fieldResponse;
        case "get_records" -> records();
        case "add_records" -> add();
        default -> throw new AssertionError("unexpected operation");
      };
    }

    private JsonNode records() {
      recordCalls.incrementAndGet();
      return emptyResponse;
    }

    private JsonNode add() {
      int call = addCalls.incrementAndGet();
      if (call == 1) {
        firstAddStarted.countDown();
        await(releaseFirstAdd);
      }
      var response = JSON.createObjectNode().put("errcode", 0);
      response.putArray("records").addObject()
          .put("record_id", call == 1 ? "r-created" : "r-duplicate-" + call);
      return response;
    }

    private static void await(CountDownLatch latch) {
      try {
        if (!latch.await(2, TimeUnit.SECONDS)) {
          throw new AssertionError("timed out waiting to release first add");
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("first add interrupted");
      }
    }
  }

  private static final class SequentialLagApi extends WecomSmartSheetApiClient {
    private final JsonNode fieldResponse;
    private final JsonNode emptyResponse;
    private final AtomicInteger recordCalls = new AtomicInteger();
    private final AtomicInteger addCalls = new AtomicInteger();

    private SequentialLagApi() throws Exception {
      super(JSON, config(), null);
      fieldResponse = JSON.readTree(fields());
      emptyResponse = JSON.readTree(emptyRecords());
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      return switch (operation) {
        case "get_fields" -> fieldResponse;
        case "get_records" -> records();
        case "add_records" -> add();
        default -> throw new AssertionError("unexpected operation");
      };
    }

    private JsonNode records() {
      recordCalls.incrementAndGet();
      return emptyResponse;
    }

    private JsonNode add() {
      int call = addCalls.incrementAndGet();
      var response = JSON.createObjectNode().put("errcode", 0);
      response.putArray("records").addObject()
          .put("record_id", call == 1 ? "r-created" : "r-duplicate-" + call);
      return response;
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override public Instant instant() {
      return instant;
    }
  }

  private static List<JsonNode> operationBodies(ConcurrentCreateApi api, String operation) {
    return api.bodies.stream().filter(call -> call.operation.equals(operation)).map(call -> call.body).toList();
  }

  private record Call(String operation, JsonNode body) {}
}
